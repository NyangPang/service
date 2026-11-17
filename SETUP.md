# 로컬 실행 가이드

레포를 받은 뒤 내 컴퓨터에서 서버(Spring Boot)와 화면(React)을 띄우는 방법입니다.

## 0. 준비물

| 도구 | 버전 | 확인 |
| --- | --- | --- |
| Java | **21** (25는 안 됨: 빌드 도구 Gradle 8.14가 아직 지원하지 않음) | `java -version` |
| Node.js | 20 이상 | `node -v` |

Java 21 설치 (macOS):

```bash
brew install openjdk@21
# 이 터미널에서만 21 사용
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
```

## 1. 서버 켜기

```bash
cd server
./gradlew bootRun
```

- `Started ItdaApplication` 이 보이면 성공 → http://localhost:8080/swagger-ui.html
- 처음 실행은 라이브러리를 내려받느라 3~5분 걸립니다.
- `permission denied: ./gradlew` 가 나오면 `chmod +x gradlew` 후 다시 실행하세요.

### 키 없이도 켜집니다

로컬(`local`)에서는 AWS·JWT 키가 없어도 가짜 값으로 서버가 뜹니다.
DB는 메모리(H2)라 서버를 끄면 데이터가 초기화되고, 켤 때 샘플 데이터가 다시 들어갑니다.

| 키 없이 되는 것 | 키가 있어야 되는 것 |
| --- | --- |
| 회원가입·로그인, 가게·지도, 퀘스트, 쿠폰, 스크랩 | AI 게시물 생성(Bedrock), 사진 업로드(S3), 영상(Runway) |

실제 키는 `server/.env.example` 을 복사해 `server/.env` 를 만들고 채웁니다. 키는 팀 DM으로만 공유하고 **절대 커밋하지 마세요** (`.env` 는 git이 무시합니다).

```bash
cp server/.env.example server/.env
```

### 샘플 계정 (비밀번호 모두 `password1234`)

| 이메일 | 유형 |
| --- | --- |
| `owner@test.com` | 사장님 |
| `resident@test.com` | 손님 |
| `newowner@test.com` | 사장님 (가게 미등록) |

이메일 인증번호는 로컬에서는 메일 대신 응답의 `devCode` 로 옵니다.

## 2. 화면 켜기

```bash
cd client
npm install
cp .env.example .env   # 카카오맵 키(VITE_KAKAO_MAP_KEY)를 넣어야 지도가 보임
npm run dev
```

http://localhost:5173 접속. `/api` 요청은 자동으로 로컬 서버(8080)로 넘어갑니다.

- 서버 없이 화면만 볼 때: `client/.env` 에 `VITE_USE_MOCK=true`
- 카카오맵 키: [Kakao Developers](https://developers.kakao.com) → 내 애플리케이션 → JavaScript 키, 플랫폼 > Web 에 `http://localhost:5173` 등록

## 자주 막히는 곳

| 증상 | 원인 · 해결 |
| --- | --- |
| `Unsupported class file major version 69` | Java 25로 실행됨 → 위의 Java 21 설정 |
| `Could not resolve placeholder ...` | `SPRING_PROFILES_ACTIVE` 가 `local` 이 아님 → 비우거나 `local` 로 |
| 지도가 회색 | `VITE_KAKAO_MAP_KEY` 없음 또는 도메인 미등록 |
| 로컬 화면에서 배포 서버로 요청하면 CORS 에러 | 배포 서버가 localhost 주소를 허용하지 않음 → 로컬 서버를 함께 켜서 사용 |
