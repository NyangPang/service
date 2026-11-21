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
2. 운영 환경변수 파일 `/home/ubuntu/itda/.env` 작성 (아래 [서버 환경변수](#서버-환경변수) 참고, 값에 따옴표 없이 `KEY=value`)
   - 서버는 `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 로 직접 접속하므로 EC2 에 IAM Role 이 있어도 키를 넣어야 함
   - `[필수]`·`[운영 필수]` 항목이 하나라도 비면 서버가 뜨지 않거나(DB·키) 로그인·가입이 막힘(CORS·메일)
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

---

## 서버 환경변수

- 로컬: `server/.env` 에 작성 (`./gradlew bootRun` 시 자동으로 읽음. IntelliJ 로 실행하면 Working directory 를 `server` 로)
- 운영(EC2): `/home/ubuntu/itda/.env` 에 두고 `docker run --env-file` 로 넘김
- 값에 따옴표를 붙이지 말 것 — Docker 는 따옴표까지 값으로 넣는다
- 빈 값(`KEY=`)은 "빈 문자열"로 들어가 기본값을 덮는다. 안 쓸 항목은 줄을 지우거나 `#` 로 막아 둘 것

```bash
# ============================================================
# [필수] 비어 있으면 서버가 시작되지 않음 (로컬·운영 공통)
# ============================================================

# local: H2 메모리 DB·샘플 데이터 / prod: MySQL(RDS)·ddl validate. Docker 이미지는 prod 가 기본이라 운영에선 생략 가능
SPRING_PROFILES_ACTIVE=local

# JWT 서명 키 (32바이트 이상 랜덤 문자열. 예: openssl rand -base64 48)
JWT_SECRET=

# AWS — 서버가 이 키로 직접 접속함 (EC2 IAM Role 은 쓰지 않으므로 운영에서도 필요)
# 필요한 권한: 업로드 버킷 S3 PutObject, Bedrock InvokeModel(대화 모델), Polly SynthesizeSpeech(영상 내레이션)
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# 업로드 파일(가게·게시물·리뷰 사진) 버킷과 그 CloudFront 도메인
# ⚠ 프론트 배포용 버킷과 다른 버킷. 도메인은 https:// 없이 (예: d1234abcd.cloudfront.net)
AWS_S3_BUCKET=
AWS_CLOUDFRONT_DOMAIN=

# Bedrock 대화 모델 ID 또는 inference profile ID (게시물 소개 글·메뉴판 인식)
BEDROCK_MODEL_ID=

# Unsplash API 키 (developers.unsplash.com > Your apps > Access Key)
UNSPLASH_ACCESS_KEY=

# ============================================================
# [운영 필수] prod 프로필에서 꼭 채울 것 (로컬 H2 는 안 씀)
# ============================================================

# RDS MySQL. 마지막 itda 는 데이터베이스 이름
# 운영은 ddl-auto: validate 라 server/docs/sql/ 의 SQL 로 테이블을 먼저 만들어야 함
DB_URL=jdbc:mysql://<rds-endpoint>:3306/itda
DB_USERNAME=
DB_PASSWORD=

# 프론트 주소 (쉼표로 여러 개). 운영은 프론트 CloudFront 주소 — 없으면 로그인·글쓰기가 403
# 예) CORS_ALLOWED_ORIGINS=https://d5678efgh.cloudfront.net
CORS_ALLOWED_ORIGINS=http://localhost:5173

# 회원가입 인증 메일 (SMTP). 운영에서 비우면 인증번호를 받을 방법이 없어 가입 불가
# 로컬은 비워도 됨 (인증번호가 응답 devCode 로 옴)
# Gmail: MAIL_HOST=smtp.gmail.com, MAIL_PORT=587, MAIL_PASSWORD=앱 비밀번호 16자리 (Google 계정 > 보안 > 앱 비밀번호)
# MAIL_HOST=
# MAIL_PORT=587
# MAIL_USERNAME=
# MAIL_PASSWORD=
# 보내는 사람 주소 (없으면 MAIL_USERNAME)
# MAIL_FROM=

# ============================================================
# [선택] 비워 두면 기본값 — 바꿀 때만 # 를 지우고 채움
# ============================================================

# 로그인 유지 시간 (ms, 기본 24시간)
# JWT_ACCESS_EXPIRATION_MS=86400000

# 가게 방문 QR 값을 만드는 비밀값 (기본: JWT_SECRET)
# QUEST_QR_SECRET=

# 방문 인증 GPS 반경 검사 건너뛰기 (기본: 로컬 true, 운영 false — 운영에선 켜지 말 것)
# QUEST_GPS_BYPASS=false

# Swagger 문서 공개 (기본 true, 운영에서 숨기려면 false)
# SWAGGER_ENABLED=true

# 시연용 샘플 가게 125곳 + 영상 게시물 (기본 false, 운영 시연 땐 true)
# SAMPLE_DATA_ENABLED=false

# 인증 메일 보내는 사람 이름 (기본: 잇츠)
# MAIL_FROM_NAME=잇츠

# ------------------------------------------------------------
# AI 숏폼 영상 파이프라인 (메뉴 추출 → 장면 JSON → 영상). 자세한 흐름은 server/docs/video-pipeline.md
# ------------------------------------------------------------

# 글 생성(메뉴판 인식·스토리보드·소개 글)을 어디로 보낼지: bedrock-converse(기본, BEDROCK_MODEL_ID 사용) / anthropic
# AI_CHAT_PROVIDER=bedrock-converse
# anthropic 일 때만: Anthropic API 키와 모델
# ANTHROPIC_API_KEY=
# ANTHROPIC_MODEL=

# Runway (사진 → 움직이는 장면, 사진이 모자랄 때 메뉴 이미지). 키가 없으면 줌 효과로 대체
# RUNWAYML_API_SECRET=
# Runway 크레딧 사용 여부 (기본 true — 영상 하나 만들 때마다 크레딧이 나감. false 면 무료 줌 효과)
# GENERATION_RUNWAY=true

# 내레이션 음성: auto(ElevenLabs 키 → AWS Polly → macOS say → 무음 순, 기본) / elevenlabs / polly / say / none
# VIDEO_TTS=auto
# ELEVENLABS_API_KEY=
# ELEVENLABS_VOICE_ID=
# ELEVENLABS_MODEL=eleven_v3
# AWS_POLLY_VOICE_ID=Seoyeon

# 영상 합성 도구와 작업 폴더 (Docker 이미지엔 ffmpeg 가 들어 있어 보통 그대로)
# FFMPEG_PATH=ffmpeg
# VIDEO_WORK_DIR=./tmp/video
```
