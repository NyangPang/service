# 배포 · CI/CD

```
PR / main 외 브랜치 push ──▶ CI (ci.yml)           서버 테스트 · 프론트 lint·build
main 에 server/ 변경 ─────▶ Deploy server         테스트 → Docker 이미지(GHCR) → EC2 재시작 → 헬스체크
main 에 client/ 변경 ─────▶ Deploy client         빌드 → S3 업로드 → CloudFront 캐시 비우기
```

두 배포 모두 GitHub **Actions 탭 → 워크플로 → Run workflow** 로 수동 실행도 된다.

```
브라우저 ──▶ CloudFront (프론트 배포용)
              ├─ 기본(/*)   ──▶ S3 프론트 버킷 (React 빌드본)
              └─ /api/*     ──▶ EC2 :8080 (Spring Boot, Docker)  ──▶ RDS MySQL
```

프론트와 API가 같은 주소라 CORS·혼합 콘텐츠(https 페이지 → http API) 문제가 없다.

---

## 1. 한 번만 하는 준비

### EC2

1. Docker 설치 (Ubuntu 기준): `sudo apt-get update && sudo apt-get install -y docker.io curl && sudo usermod -aG docker ubuntu` → 다시 접속
2. 운영 환경변수 파일 `/home/ubuntu/itda/.env` 작성 (`server/.env.example` 참고, 값에 따옴표 없이 `KEY=value`)
   - `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`, `JWT_SECRET`, `AWS_*`, `AWS_S3_BUCKET`, `AWS_CLOUDFRONT_DOMAIN`(업로드 파일용), `BEDROCK_MODEL_ID`, `UNSPLASH_ACCESS_KEY`, `CORS_ALLOWED_ORIGINS`(프론트 주소), `MAIL_*`
   - EC2 에 IAM Role 을 붙였다면 `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 는 빼도 됨
3. 보안 그룹: 8080 은 **CloudFront 에서만** 열기 (관리형 프리픽스 목록 `com.amazonaws.global.cloudfront.origin-facing`), 22 는 GitHub Actions 가 접속할 수 있어야 함
4. 이미 손으로 띄운 서버 컨테이너가 있으면 이름을 `itda-server` 로 맞추거나, 아래 `SERVER_CONTAINER` 변수로 그 이름을 알려 준다 (배포 때 같은 이름의 컨테이너를 지우고 새로 띄움)

### RDS (운영 DB)

운영은 `ddl-auto: validate` 라 **새 테이블은 배포 전에 SQL 로 먼저 만들어야** 서버가 뜬다.
`server/docs/sql/` 의 파일 중 아직 반영 안 한 것을 날짜 순으로 실행 (예: 리뷰 기능 → `2026-11-17-review.sql`).

### 프론트용 S3 + CloudFront

> ⚠ 업로드 사진·게시물이 들어 있는 기존 버킷(`AWS_S3_BUCKET`)과 **다른 버킷**을 쓴다. 배포가 `--delete` 로 버킷을 빌드본과 똑같이 맞추기 때문.

1. S3 버킷 생성 (퍼블릭 차단 유지)
2. CloudFront 배포 생성
   - 원본 1: 위 S3 버킷 (OAC 사용), 기본 루트 객체 `index.html`
   - 원본 2: EC2 퍼블릭 DNS, HTTP, 포트 8080
   - 동작 `/api/*` → 원본 2, 캐시 정책 `CachingDisabled`, 원본 요청 정책 `AllViewerExceptHostHeader`, 허용 메서드 GET~DELETE 전부
   - (선택) `/swagger-ui/*`, `/v3/api-docs*` 도 원본 2 로
   - 오류 페이지: 403·404 → `/index.html`, 응답 코드 200 (주소로 바로 들어와도 React 라우터가 화면을 그리게)
3. 카카오 developers → 플랫폼 → Web 사이트 도메인에 CloudFront 주소 등록

---

## 2. GitHub 설정 (Settings → Secrets and variables → Actions)

### Secrets

| 이름 | 값 |
| --- | --- |
| `EC2_HOST` | EC2 퍼블릭 IP 또는 DNS |
| `EC2_USER` | 접속 계정 (Ubuntu AMI 는 `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 개인키(.pem) 내용 전체 |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 프론트 배포용 IAM 사용자 키 (프론트 버킷 `s3:ListBucket`·`PutObject`·`DeleteObject` + `cloudfront:CreateInvalidation` 만) |

### Variables

| 이름 | 값 | 기본값 |
| --- | --- | --- |
| `FRONT_S3_BUCKET` | 프론트 버킷 이름 | (필수) |
| `FRONT_CLOUDFRONT_ID` | 프론트 CloudFront 배포 ID (`E…`) | (필수) |
| `VITE_KAKAO_MAP_KEY` | 카카오 JavaScript 키 | (필수) |
| `VITE_API_BASE_URL` | API 주소. CloudFront `/api/*` 를 쓰면 비워 둠 | 비움 |
| `AWS_REGION` | | `ap-northeast-2` |
| `SERVER_ENV_FILE` | EC2 의 환경변수 파일 경로 | `/home/ubuntu/itda/.env` |
| `SERVER_CONTAINER` | EC2 의 서버 컨테이너 이름 | `itda-server` |
| `SERVER_HOST_PORT` | EC2 에서 열 포트 | `8080` |

서버 이미지는 `ghcr.io/nyangpang/service-server` 에 올라가고, 배포할 때만 쓰는 임시 토큰으로 EC2 가 받아 간다 (따로 설정 없음).

---

## 3. 배포하기

1. 기능 브랜치 → main 으로 PR → CI 통과 확인 → 머지
2. 머지되면 바뀐 쪽(server/·client/)만 자동 배포
3. Actions 탭에서 진행 확인. 서버는 2분 안에 응답이 없으면 실패로 끝나고 컨테이너 로그를 남긴다

### 되돌리기

서버: Actions 의 이전 성공 실행에서 커밋 SHA 를 확인하고 EC2 에서

```bash
docker rm -f itda-server
docker run -d --name itda-server --restart unless-stopped -p 8080:8080 \
  --env-file /home/ubuntu/itda/.env ghcr.io/nyangpang/service-server:<이전 SHA>
```

(GHCR 이 비공개면 먼저 `docker login ghcr.io` — GitHub 개인 토큰 `read:packages`)

프론트: 문제가 된 커밋을 `git revert` 해서 main 에 올리면 이전 화면으로 다시 배포된다.
