# 외부 API · 오픈소스 정리

잇츠가 쓰는 외부 서비스, 발급해야 하는 키, 오픈소스 목록입니다. (2026-11-17 기준)
키는 `server/.env`, `client/.env`에만 넣고 (항목: [docs/deploy.md](deploy.md#서버-환경변수), [client/README.md](../client/README.md)) **절대 커밋하지 않습니다.** 공유는 팀 DM으로 합니다.

## 1. 발급해야 하는 키

| 서비스 | 용도 | 키 이름 | 담당 | 상태 |
| --- | --- | --- | --- | --- |
| AWS (팀 계정, 담당 확인 중) | Bedrock(Claude), S3, CloudFront, Polly | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`, `AWS_CLOUDFRONT_DOMAIN`, `BEDROCK_MODEL_ID` | 성현주 / 임아연 | ⬜ |
| 카카오 Developers | 지도, 주소 검색, 좌표→행정동 | `VITE_KAKAO_MAP_KEY` (JavaScript 키) | 임지오 | ✅ (팀 DM 공유) |
| Runway | AI 영상(·이미지) 생성 | `RUNWAYML_API_SECRET` | 이채원 | ⬜ |

### AWS 준비

1. IAM 사용자 생성 후 액세스 키 발급 (루트 계정 키 사용 금지)
2. Bedrock에서 Claude 모델 사용 신청 (서울 `ap-northeast-2`) → 모델 ID를 `BEDROCK_MODEL_ID`에
3. S3 버킷 생성 (서울) + CloudFront 연결 (사진 주소를 CloudFront 주소로 만듦)
4. 예산 알림 설정 (예: 월 3만 원 초과 시 메일)

> ⚠️ 지금 코드의 AI 이미지 모델 **Amazon Nova Canvas(`amazon.nova-canvas-v1:0`)는 2026-09-30에 서비스 종료**되어 호출하면 `ResourceNotFoundException`이 납니다 ([AWS 모델 카드](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-canvas.md)). 영상 모델 Nova Reel도 같은 날 종료됐습니다. 대체 모델은 아래 3절 참고.

### 카카오 키 (지오)

1. [Kakao Developers](https://developers.kakao.com) → 애플리케이션 추가 → **JavaScript 키** 복사
2. 앱 키 > JavaScript 키 > **JavaScript SDK 도메인**에 `http://localhost:5173`과 배포 주소 등록 (등록 안 된 주소에선 지도가 안 뜸, '앱 대표 도메인' 칸이 아님)
3. 지도·주소 검색·좌표→행정동(`coord2RegionCode`)이 모두 이 키 하나로 동작 (JS SDK `services` 라이브러리)

## 2. 쓰는 외부 API

| API | 어디에 쓰나 | 코드 위치 |
| --- | --- | --- |
| 카카오맵 JS SDK | 가게 지도, 핀 | `client/src/features/map/` |
| 카카오 주소 검색 · 좌표→주소 | 가게 등록 때 주소 → 좌표 | `features/map/kakao.ts` `geocodeAddress` |
| 카카오 좌표→행정동 | "내 동네" 표시, 동네별 랭킹 | `features/map/kakao.ts` `neighborhoodOf` |
| Daum 우편번호 서비스 | 가게 주소 찾기 | `shared/ui/AddressSearch.tsx` |
| AWS Bedrock (Claude) | 게시물 소개 문구 다듬기, 메뉴판 인식(OCR), 영상 대본 | `server/.../infra/ai/` |
| AWS S3 + CloudFront | 사진·영상 저장과 주소 | `infra/storage/StorageService` |
| AWS Polly (한국어 음성 `Seoyeon`) | 영상 내레이션, (쉬운모드) 읽어주기 후보 | `infra/tts/TtsService` |
| Runway | AI 영상 클립 (현재 코드만 있고 미연결) | `infra/video/RunwayService` |
| 브라우저 Geolocation | 내 위치, 방문 인증 GPS | `features/map/hooks.ts`, `features/quest/geo.ts` |
| 브라우저 Web Speech API | (쉬운모드 후보) 말로 입력, 읽어주기 — 무료, 키 불필요 | 미구현 |

### 기획서 근거용 공공데이터

| 데이터 | 출처 |
| --- | --- |
| 서울시 상권분석서비스 (점포, 추정매출, 유동인구) | [서울 열린데이터광장](https://data.seoul.go.kr) |
| 소상공인시장진흥공단 상가(상권)정보 | [공공데이터포털](https://www.data.go.kr) |
| 주민등록 인구통계 | 행정안전부 |

## 3. AI 생성 모델 (숏폼·이미지) — 결정 필요

| 단계 | 추천 | 비용(대략) | 이유 |
| --- | --- | --- | --- |
| 대본 · 소개 문구 | Bedrock Claude (지금 그대로) | 매우 적음 | 이미 연결돼 있음 |
| 영상 클립 | **Runway `gen4_turbo`** (사장님 사진 → 영상) | 5 credits/초, 약 $0.05/초 → 5초 클립 3개 약 $0.75 | 코드가 이미 있음, 실제 가게 사진을 움직이게 하므로 가짜 음식이 나오지 않음 |
| 내레이션 | AWS Polly | 매우 적음 | 이미 코드 있음, 한국어 음성 |
| 자막 · 합성 | FFmpeg (오픈소스) | 무료 | 이미 코드 있음, 한글은 AI가 그리지 않고 코드로 입힘 |
| 홍보 이미지 | **Runway `gen4_image_turbo`** (참고 이미지 입력) 또는 사장님 사진 + 템플릿 | 2 credits/장, 약 $0.02 | 영상과 같은 키 하나로 해결 |

- 가격은 [Runway API 가격 문서](https://docs.dev.runwayml.com/guides/pricing/)의 credit 단가와 외부 자료의 credit당 $0.01 기준입니다. 결제 전에 공식 페이지에서 확인하세요.
- 영상 1개 생성에 1~3분 걸립니다. 본선 시연은 **미리 만들어 둔 결과를 함께 준비**하고, `RunwayService`의 mock 모드(FFmpeg 정적 클립)를 대비책으로 둡니다.
- 대안: 이미지에 한글 문구를 직접 그려야 하면 OpenAI GPT Image 2, 저렴한 이미지 편집이 필요하면 Google Gemini 이미지 모델을 검토합니다(키 추가 발급 필요).

## 4. 오픈소스

| 구분 | 라이브러리 | 라이선스 |
| --- | --- | --- |
| 화면 | React, React DOM, React Router | MIT |
| 화면 | Vite, Tailwind CSS, TypeScript, oxlint | MIT / MIT / Apache-2.0 / MIT |
| 화면 | TanStack Query, Zustand, Axios | MIT |
| 화면 | react-kakao-maps-sdk, qrcode.react | MIT / ISC |
| 서버 | Spring Boot (Web, Security, Data JPA, Validation, Mail) | Apache-2.0 |
| 서버 | Spring AI (Bedrock Converse), AWS SDK for Java v2 | Apache-2.0 |
| 서버 | jjwt (JWT), springdoc-openapi (Swagger) | Apache-2.0 |
| 서버 | jsoup, Playwright for Java (가게 정보 스크래핑) | MIT / Apache-2.0 |
| 서버 | Lombok, H2, MySQL Connector/J | MIT / MPL-2.0·EPL-1.0 / GPL-2.0 (FOSS 예외) |
| 영상 | FFmpeg | LGPL-2.1+ (빌드에 따라 GPL) |
| 폰트 | Pretendard, 여기어때 잘난체 | OFL / 무료 폰트 |
