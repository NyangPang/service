# 잇다 client

React + Vite + TypeScript, Tailwind v4, React Query, Zustand, axios, react-kakao-maps-sdk

## 실행

```bash
cd client
npm install
npm run dev            # http://localhost:5173
```

### 환경변수 (`client/.env`)

`client/.env` 파일을 만들고 아래 값을 채웁니다 (git에 안 올라감). 배포 빌드는 GitHub Actions 변수(`.github/workflows/deploy-client.yml`)를 씁니다.

```bash
# [필수] 카카오맵 JavaScript 키 (developers.kakao.com > 내 애플리케이션 > 앱 키)
VITE_KAKAO_MAP_KEY=

# [선택] true면 서버 없이 목 데이터로 화면만 봄 (기본 false)
VITE_USE_MOCK=false
# [선택] true면 방문 인증 GPS 거리 검사 건너뜀 — 개발·시연용 (기본 false)
VITE_QUEST_GPS_BYPASS=false
# [선택] API 주소. 비우면 같은 주소(/api)로 요청 → dev 서버 프록시가 중계
# VITE_API_BASE_URL=
# [선택] dev 서버 프록시 대상 (기본 http://localhost:8080). MEMBER_API_TARGET은 /api/members만 다른 서버로
# API_PROXY_TARGET=
# MEMBER_API_TARGET=
```

- `/api/*` 요청은 dev 서버가 `http://localhost:8080`(Spring Boot)으로 프록시합니다.
- 서버 없이 화면만 볼 때는 `.env`에 `VITE_USE_MOCK=true`를 넣습니다.
- 카카오 developers 앱의 플랫폼 > Web에 `http://localhost:5173`을 등록해야 지도가 뜹니다.

## 폴더

```
src/
├── app/        # router, Provider, 공통 레이아웃(상단 바 + 하단 3탭)
├── pages/      # 라우트 단위 화면 (features 조합만)
├── features/   # 담당자별 기능 (api.ts, hooks.ts, schema.ts, components/)
│   └── map/    # 잇다 맵
├── shared/     # api(axios + 공통 응답 처리), ui, lib
├── stores/     # Zustand (authStore)
├── assets/     # 비둘기, 아이콘, 로고
└── styles/     # Tailwind + 디자인 토큰
```

API 응답은 `{ isSuccess, code, message, result }` 형식이에요. `shared/api/client.ts`의 `request()`로 감싸면 `result`만 받아요. 실패하면 `ApiError(code, message)`를 던져요.
