# 신규 기능 개발 안내 (청년AI해커톤)

기존 앱(월계ON-AI)을 서울 전역 서비스 **잇츠**로 넓히면서 추가하는 기능을 어디에 만들면 되는지 정리한 문서입니다.
실행 방법은 [SETUP.md](../SETUP.md)를 참고하세요.

## 코드 위치

| 기능 | 서버 (`server/src/main/java/com/commicom/itda/`) | 화면 (`client/src/`) |
| --- | --- | --- |
| 리뷰 · 마이페이지 | `domain/review/` (빈 패키지 준비됨) | `features/review/` 새로 만들기 |
| 동네별 랭킹 · 업종 검색 | `domain/store/service/StoreRankingService` (구 단위 방문순, 7/14/31일 — 완료) | `features/search/` (완료) |
| AI 영상 | `infra/video/` (Runway·FFmpeg 코드 있음, 현재 미연결), `domain/generation/` | `features/generation/` |
| 쉬운모드 | — | `styles/index.css`, `app/SettingsDrawer.tsx` |

서버 도메인은 모두 `controller / service / repository / entity / dto` 구조이고, 응답은 `global/response/ApiResponse`, 에러는 `global/exception/ErrorCode` 에 `{도메인}{상태코드}` 형식으로 추가합니다.
API를 만들면 `server/docs/api-spec.md` 에 같은 형식으로 적어 주세요.

## 다시 쓸 수 있는 기존 코드

| 필요한 것 | 이미 있는 것 |
| --- | --- |
| 사진·영상 업로드 | `infra/storage/StorageService` (S3), 화면 `shared/lib/image.ts` |
| 보상 뽑기 | `domain/pigeon/service/PigeonService` 레벨업 뽑기 (쿠폰·먹이·교환권) |
| 실제 방문 기록 | `domain/quest/entity/QuestVisit` (`storeId`, `visitDate`) — 방문순 랭킹이 이미 사용 중 |
| 스크랩 기록 | `domain/scrap/entity/ShortformScrap` — 스크랩순 랭킹 원천 |
| 내 위치 · 내 동네 | 화면 `features/map/hooks.ts` 의 `useMyLocation()`, `useMyNeighborhood()` (카카오 좌표→행정동) |

## 아직 없는 데이터 (만들어야 함)

| 데이터 | 필요한 기능 | 메모 |
| --- | --- | --- |
| 가게의 **동** | 동 단위 랭킹(필요하면) | 구는 `store/entity/District` 가 주소에서 뽑음. 동까지 나누려면 가게 등록 때 카카오 좌표→행정동으로 저장 |
| **조회수** | 조회순 랭킹 | `QuestEventLog` 는 퀘스트용(회원당 1번)이라 랭킹에 못 씀 |
| **좋아요** | 좋아요순 랭킹 | 없음 |
| 업종 **세부 분류** (일식 등) | 업종 검색 | `StoreCategory` 는 대분류(음식점 등)뿐 |

## 서울 확장에서 남은 것

- 샘플 데이터(`LocalDataInitializer`, `client/src/mocks`, `features/*/mock.ts`)는 아직 월계동 가게입니다.
- 로고 이미지(`client/src/assets/logo-itda.svg`)와 첫 화면 문구 "우리 동네 손님과 잇-다"는 디자인·기획 확인이 필요합니다.
- 서버 프로젝트 이름(`itda`)과 Java 패키지(`com.commicom.itda`)는 바꾸면 충돌이 커서 그대로 둡니다.
