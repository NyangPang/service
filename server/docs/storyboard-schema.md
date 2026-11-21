# 장면 JSON (Storyboard) 스키마 — 초안 v0.1

## 개요

| 파트 | 담당 범위 |
| --- | --- |
| **A 파트 (스토리보드)** | 메뉴 추출 → 장면 JSON 생성 → 검증 → 수정 요청 해석 |
| **B 파트 (영상 생성)** | 장면 JSON → Runway 클립 · TTS · FFmpeg 합성 → 재생성 · 다운로드 |

- **원칙**: A 파트는 검증을 통과한 JSON만 B 파트로 넘긴다. B 파트는 아래 규칙이 지켜졌다고 보고 써도 된다.
- **영상 모델**: Runway Gemini Omni 1.2 Flash(사진 사이 전환), Gen-4 Turbo(단일 사진 클립)
- **더미**: [`storyboard-samples/`](storyboard-samples/) — 업종별 3쌍 (`*.storyboard.json` + 짝이 되는 `*.menu.json`)
- **상태**: 초안. [6. 정할 것](#6-정할-것)을 합의하면 v1.0으로 고정

### 영상 규칙 (2026-11-19 추가)

| 규칙 | 어디서 지키나 |
| --- | --- |
| 쓸 수 있는 사진(메뉴판 제외)이 3장 이상이면 새로 만들지 않고 그중에서 고름. 외관 사진은 꼭, 음식은 대표 메뉴부터 | 메뉴 추출 `image_requests`, 장면 프롬프트 |
| 3장보다 적으면 모자란 만큼 이미지 생성. 외관·건물은 만들지 않고, 메뉴 2~3가지가 함께 있는 모습 우선 | 메뉴 추출 프롬프트 + 검사 (개수 정확히) |
| 작은 사진을 화면 가득 키운 흐린 정지 화면이 나오지 않게: 쓰는 사진마다(외관 제외) ai 장면 하나, 같은 사진의 다른 장면은 그 Runway 클립을 이어 씀 (왕복 재생) | 장면 검사 + B 파트 렌더러 (2026-11-20) |
| 영상마다 주제(`theme`) 하나. 주제에 따라 장면 전환이 바뀜 | 장면 프롬프트, B 파트 `ThemeStyle` |
| Runway 장면은 카메라 무빙 + 메뉴에 맞는 음식 묘사 (만들기: 우유 붓기·소스 뿌리기 / 따뜻함: 김·지글거림 / 신선함: 물방울·얼음 / 곁들임: 옆에 케이크). 사람 몸·손·얼굴과 손·입이 필요한 동작만 금지 | 장면 프롬프트 + 검사 |
| **대사 = 나레이션 = 자막** (2026-11-20, 판다랭크 방식): 장면마다 나레이션 한 조각을 그대로 자막으로 띄움. 긴 문장은 쉼표·이어 주는 말에서 잘라 2~3장면에 나눔. 위치는 화면 가운데보다 조금 아래 왼쪽 (`bottom`) | 장면 프롬프트 + 검사, B 파트 자막 |
| 대사 순서 (2026-11-20): ① 후킹(손해 회피·궁금증) → ② 대표 메뉴 1~2개(모습 + 맛·식감) → ③ 근거(사장님 자랑거리 → 리뷰 키워드) → ④ 실용 정보(입력에 있을 때만) → ⑤ 가게 이름 + 행동 유도. 해요체 구어체, 장면끼리 마지막 세 글자가 같으면 오류 | 장면 프롬프트 + 검사 |
| 첫 장면은 멈춰 보게 만드는 한마디(hook). 마지막 장면의 `caption.text`는 가게 이름, 나레이션은 가게 이름을 부르며 권유. TTS를 쓰면 실제 영상에는 마지막 장면도 나레이션 전체가 한 줄로 표시됨. 외관 사진이 있으면 마지막 장면은 외관 | 장면 검사 + B 파트 자막 |

## 1. 최상위 필드

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `schema_version` | string | ✅ | `"0.1"` |
| `title` | string | ✅ | 영상 위 고정 제목 겸 쇼츠 플랫폼 업로드 제목·게시물 제목. 공백 포함 20자 이내, 띄어쓰기로 두 덩어리(가운데 띄어쓰기에서 윗줄 작게 / 아랫줄 크게). 윗줄 = 동네·상황(동네 이름은 가게 주소에 있는 것만), 아랫줄 = 평가 한마디 + 메뉴·업종 단어(검색용). 예: "성수 점심은 / 실패 없는 돈가스". 내세울 점은 사장님 한마디 → 대표 메뉴 → 설명 → 리뷰 키워드 순. 가게 이름·이모지 금지, 가격은 메뉴 가격과 같아야 함 |
| `total_duration` | number | ✅ | 초 단위, `15` 고정. 장면 `duration` 합과 같음 |
| `theme` | enum | ✅ | `emotional`(감성) / `value`(가성비·싼 가격·많은 양) / `trendy` / `expert`(전문) / `fresh`(신선함). 아래 표 |
| `style.mood` | enum | ✅ | `warm` / `fresh` / `cozy` / `lively` |
| `style.font` | enum | ✅ | `gothic_bold` / `gothic_regular` (Noto Sans CJK KR) |
| `style.main_color` | string | ✅ | `#RRGGBB`, 자막 강조색 |
| `bgm.mood` | enum | ✅ | `upbeat_acoustic` / `calm_lofi` / `bright_pop` / `jazz` |
| `scenes` | array | ✅ | 장면 4~6개 (프롬프트는 5~6개를 요청) |
| `meta` | object | | 서버가 채움 (LLM 출력 아님). `category`, `prompt_version`, `store_name`(템플릿 가게 이름 태그), `template`(`headline`/`band`/`card`/`classic`, 없으면 `headline`) |

### 주제(`theme`)별 효과 (B 파트 `ThemeStyle`)

| theme | 장면 전환 (FFmpeg xfade) |
| --- | --- |
| `emotional` | `smoothleft` 0.6초 (부드러운 슬라이드) |
| `value` | `radial` 0.4초 (회전하듯 쓸어 넘김) |
| `trendy` | `zoomin` 0.35초 |
| `expert` | `fade` 0.5초 |
| `fresh` | `slideleft` 0.4초 |
| 없음 (예전 JSON) | `fade` 0.35초 |

## 2. 장면 필드

```json
{
  "id": 1, "order": 1, "type": "hook", "duration": 2,
  "image_index": 0, "motion": "ai",
  "video_prompt": "crispy pork cutlet sliced in half with steam rising, slow push-in, warm side lighting, appetizing cozy mood",
  "menu_ref": null,
  "narration": "바삭함이 생각날 땐",
  "caption": { "text": "카츠하우스", "position": "center", "emphasis": "large" }
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `id` | int | ✅ | 장면 고유 번호. 순서가 바뀌어도 유지 (수정 변경분이 이 값으로 장면을 가리킴) |
| `order` | int | ✅ | 재생 순서, 1부터 연속 |
| `type` | enum | ✅ | `hook` / `menu` / `detail` / `review` / `cta`. 처음은 `hook`, 마지막은 `cta` |
| `duration` | number | ✅ | 초, 0.5 단위, 1.5~5. `hook`은 2 이하 |
| `image_index` | int | ✅ | `photoUrls` 순번. `menu_board` 사진 사용 불가 |
| `motion` | enum | ✅ | `ai` (컷마다 첫 장면, 영상당 최대 3개) / `zoom_in` (외관 컷) / `zoom_out` / `static` (컷의 나머지 장면). 아래 [원테이크](#원테이크-2026-11-21) 참고. `transition`은 예전 장면 JSON 호환용으로만 받음 |
| `video_prompt` | string \| null | 컷 첫 장면 `ai`일 때 | 영어, 60단어 이내. `[이 사진 음식의 질감·특징이 살아나는 움직임] → [다음 컷 사진으로 넘어가는 카메라 이동]`. 예: "the soft mochi gently wobbles and settles, then the camera glides sideways past the counter to the next table, where a strawberry cake sits", 다음이 외관이면 "…then the camera pulls back through the window and out to the street, revealing the storefront". 마지막 컷이면 이동 없이 질감만. 글자·간판·로고 요청 금지, 사람·손·얼굴(`chef`, `hands`, `gloved`…), 도구(`fork`, `knife`, `tongs`…), 손·입이 필요한 동작(`lifting`, `tearing`, `eating`…) 금지 (손·포크로 찢는 장면이 먹는 장면처럼 보여 어색했음). 붓기·뿌리기는 됨 |

### 원테이크 (2026-11-21)
장면마다 따로 만든 클립을 잇지 않고, 카메라 한 대가 15초 동안 끊김 없이 움직인 것처럼 한 편의 배경 영상을 만든 뒤 장면 길이대로 잘라 쓴다 (`StoryboardRenderer.buildOneTake`).

- **컷** = 같은 사진(`image_index`)을 쓰는 이어진 장면들. 같은 사진을 떨어진 장면에 다시 쓰면 검증 오류 (카메라는 다음 사진으로만 움직임)
- 컷 *i*(마지막 제외): Runway Gemini Omni 1.2 Flash에 **첫 화면 = 이 컷 사진, 끝 화면 = 다음 컷 사진**을 주고 세로 720p 전환 클립을 만든다. 기본 3초, 마지막 컷으로 이동할 때는 현재 컷이 3초보다 길면 4초를 요청한다.
- 마지막 컷: `video_prompt`가 있으면 그 사진에서 시작하는 Gen-4 Turbo 클립, 없으면(가게 외관) 그 사진에서 시작하는 줌
- 컷 길이는 FFmpeg로 맞춘다. 긴 전환 컷은 처음 사진을 조금 더 보여 준 뒤 움직인다. 클립은 동시에 요청하며, 실패하거나 Runway가 꺼진 컷은 줌으로 대체한다.
- 장면 경계는 같은 화면끼리 겹치는 페이드라 보이지 않음. 사진 칸이 화면 일부인 템플릿(띠형·카드형)은 예전처럼 장면별 클립
- 비용: Gemini 720p 전환 3초는 23크레딧, 4초는 30크레딧(첫·끝 사진 포함). 음식 3장과 외관을 3초·3초·4초로 이으면 총 76크레딧. 마지막 컷을 Gen-4로 만들면 별도 과금.
| `menu_ref` | string \| null | 가격 자막 있을 때 | 메뉴 데이터 `menus[].name`과 정확히 일치. 장면 사진도 같은 메뉴여야 함 |
| `narration` | string | ✅ | 한국어 TTS이자 그 장면 자막 (대사 한 조각). 모든 장면에 쓰고, 공백 포함 40자 이내 (화면에서는 18자 안팎 조각 최대 3개로 나뉘어 차례로 뜸 — `caption.text` 참고). 긴 문장은 쉼표·이어 주는 말에서 나눠 다음 장면으로. 공백·문장부호 제외 글자 수 ≤ `duration` × 7 (가격은 "만 이천 원"처럼 읽는 형태로 셈). 숫자는 `menu_ref` 메뉴 가격(`12,000원`)만, 이모지 금지. 장면끼리 마지막 세 글자가 같으면 안 됨 |
| `caption.text` | string | ✅ | 그 장면 `narration`과 같은 글 (띄어쓰기·문장부호까지, 여러 칸 띄어쓰기만 허용). 마지막 장면만 가게 이름. 공백 포함 40자 이내. 렌더러(`CaptionSplitter`)가 띄어쓰기에서 18자 이내 조각 최대 3개로 나누고, 조각마다 읽는 글자 수 비율로 나레이션 구간을 나눠 말하는 속도에 맞춰 바꿔 띄움 (예: "딸기 복숭아 샤인머스켓 / 골라 먹는 재미예요."). 가격은 앞 단어와 같은 조각. 띄어쓰기 없이 18자가 넘으면 나눌 수 없어 오류. 가격 조각은 "메뉴 + 가격"으로 끝내면 가격이 따로 크게 나옴. 가격은 `12,000원` 형식. 이모지 금지 (폰트가 못 그림) |
| `caption.position` | enum | ✅ | `bottom` (기본, 화면 가운데보다 조금 아래 왼쪽) / `center` (정중앙, hook 자막과 가게 이름) |
| `caption.emphasis` | enum | ✅ | `normal` / `large` / `price` |

### 사진 종류별 사용 규칙

| 사진 종류 | 쓸 수 있는 장면 | `motion: ai` |
| --- | --- | --- |
| 실제 음식 (`food` + `upload`) | 전부 | 가능 |
| 생성 음식 (`food` + `generated`) | `detail` 제외 | 가능 |
| 가게 내부 (`store`) | `menu` · `detail` 제외 | 가능 |
| 가게 외관 (`exterior`) | `menu` · `detail` 제외. 있으면 마지막 장면에 꼭 | 가능 |
| 메뉴판 (`menu_board`) | 사용 안 함 (사진 칸에 잘못 올린 메뉴판만 해당) | — |

- **생성 음식은 소개 위주**: 실제와 다를 수 있으니 클로즈업·디테일 장면에 쓰지 않음
- **생성 이미지도 AI 클립 가능**: 사진 3장을 채우려고 만든 이미지라 Runway 장면에도 쓴다 (2026-11-19)
- 사진 종류별 `video_prompt` 스타일은 아직 정하지 않음 ([6. 정할 것](#6-정할-것))

## 3. 메뉴 데이터 (메뉴 추출 결과)

가격·사진 검증의 기준이 되는 데이터. 사장님 확인을 거친 값이 들어간다.

- **입력**: 앱 첫 화면에서 메뉴판(최대 3장)과 음식·가게 사진(최대 4장)을 함께 받는다
- **메뉴판 칸 이미지**: 메뉴를 읽는 데만 쓰고 `photos[]`에 넣지 않는다 (영상에 안 씀)
- **`photos[]`**: 음식·가게 사진 칸 이미지만, 올린 순서대로 `index` 0부터. 영상의 `photoUrls`도 이 사진들만 같은 순서로 (+ 뒤에 생성 이미지)

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `menus[].name` | string | 메뉴판 표기 그대로 |
| `menus[].price_text` | string \| null | 메뉴판 원문 (`"4.0"`, `"12,000원"`) |
| `menus[].price` | int \| null | 원 단위 (`"4.0"` → `4000`). 확신 없으면 `null` → 사장님 확인 |
| `menus[].description` | string \| null | 메뉴판에 있는 설명만 |
| `menus[].is_signature` | bool | 대표메뉴 표시 여부 |
| `photos[].index` | int | `photoUrls` 순번 (음식·가게 사진만, 메뉴판 칸 제외) |
| `photos[].kind` | enum | `food` / `store` (내부) / `exterior` (외관) / `menu_board` |
| `photos[].source` | enum | `upload` (사장님 사진) / `generated` (AI 생성 이미지) |
| `photos[].menu_ref` | string \| null | 사진 속 메뉴의 `name`. 모르면 `null` |
| `photos[].description` | string \| null | 사진 한 줄 설명 (장면 고를 때 참고) |
| `image_requests[].menu_ref` | string \| null | 메뉴 하나만 그리면 그 이름, 여러 메뉴를 함께 그리면 `null` |
| `image_requests[].prompt` | string | Runway 이미지 생성 프롬프트. 영어, 40단어 이내 |

### 생성 이미지 요청 (`image_requests`)

영상에 쓸 사진이 3장보다 적을 때 모자란 만큼 채울 음식 이미지를 만드는 요청. 메뉴 추출 호출에서 함께 받는다 (추가 호출 없음).

- **흐름**: 메뉴 추출 (A, `image_requests` 작성) → 이미지 생성 (B, Runway `gen4_image`, 요청을 동시에 보냄) → 사장님 확인 → 장면 JSON (A) → 영상 (B)
- **몇 개**: 쓸 수 있는 사진(메뉴판·`low` 제외)이 3장 이상이면 `[]`, 적으면 정확히 `3 − 쓸 수 있는 사진 수`개. `menus`가 비면 `[]`
- **무엇을**: 메뉴 2~3가지가 함께 놓인 모습이 기본 (`menu_ref: null`), `is_signature` 메뉴는 꼭 포함. 외관·건물은 만들지 않음
- **`prompt` 규칙**: `[음식과 담음새] + [그릇·배경] + [조명] + [구도]`, `realistic food photography`, `vertical composition` 포함. 메뉴판에 없는 재료를 지어 넣지 않음. 글자·간판·로고, 사람·신체(정지 이미지라 손도 금지), 손이 필요한 동작, 가게 이름 금지
- **B 파트가 할 일**: 요청마다 이미지 1장을 만들어 `photos[]` 끝에 붙인다. `photoUrls`도 같은 순서로 붙인다.

```json
{ "index": 4, "source": "generated", "kind": "food", "menu_ref": null, "description": "AI가 만든 메뉴 조합 이미지: <prompt>" }
```

  - `index`: 업로드 사진 다음 번호부터 이어서
  - `menu_ref`: 요청의 `menu_ref` 그대로
- **장면 JSON 단계**: 이미 `photos[]`에 들어간 뒤라 `image_requests`는 장면 JSON 프롬프트에 넣지 않는다

### 사진 ↔ 메뉴 연결

- **목적**: 가격 자막과 화면 속 메뉴가 다른 상황 방지 (예: 히레카츠 사진 + "치즈카츠 14,000원")
- **흐름**

| 단계 | 내용 |
| --- | --- |
| 1. AI 매칭 | 메뉴 추출 호출에 음식 사진도 함께 보내 `kind`·`menu_ref`를 채움 (추가 호출 없음) |
| 2. 사장님 확인 | 기존 메뉴 확인 화면에 사진별 메뉴 선택 칸 추가. 틀린 것만 수정 |
| 3. 검증 | `menu_ref` 있는 장면은 같은 메뉴 사진만 허용. 연결 안 된 사진은 가격 없는 장면에만 |

## 4. 검증 규칙 (A 파트)

| # | 항목 | 규칙 |
| --- | --- | --- |
| 1 | 스키마 | 필수 필드, 타입, enum 값. `title`은 이모지·띄어쓰기 없음·가게 이름만·틀린 가격 검사 |
| 2 | 시간 | `duration` 합 = `total_duration`, 장면당 1.5~5초, `hook` ≤ 2초 |
| 3 | 구성 | 장면 4~6개, `hook` 시작 · `cta` 끝, `order` 연속, `id` 중복 없음 |
| 4 | 자막 | 40자 이내이고 18자 조각 3개 안에 나뉨, 이모지·그림 기호 없음, 마지막 장면을 빼고 `narration`과 같은 글. 마지막 장면 나레이션에 가게 이름 |
| 5 | 가격 | 자막 속 가격 = `menu_ref` 메뉴의 `price` |
| 6 | 나레이션 | 모든 장면에 있음, 글자 수 ≤ `duration` × 7, 숫자는 `menu_ref` 메뉴 가격만, 이모지 없음 |
| 7 | 영상 프롬프트 | `ai` 장면 3개 이하, `ai` 장면끼리 다른 사진, 쓰는 사진마다(외관 제외) `ai` 하나, 영어, 60단어 이내, 글자 관련 단어 없음, 사람·손·얼굴 단어와 도구·손·입이 필요한 동작 없음 (Runway가 손·얼굴을 뭉개 그림), 같은 사진은 붙은 장면에만 (원테이크) |
| 8 | 사진 | `menu_board` 사진 금지, `menu_ref`와 사진 메뉴 일치 |
| 9 | 사진 종류 | [사진 종류별 사용 규칙](#사진-종류별-사용-규칙)의 장면 · `motion: ai` 제한 |

- **실패 시**: 오류 목록을 붙여 LLM에 최대 2회 재요청 → 그래도 실패하면 B 파트로 넘기지 않고 생성 실패 처리

## 5. 수정 시 재작업 범위

수정 요청은 A 파트가 `{ scene_id, field, before, after }` 목록으로 바꿔 B 파트에 넘긴다.

| 바뀐 필드 | B 파트 작업 | 비용 |
| --- | --- | --- |
| `ai` 장면의 `video_prompt` · `image_index` · `motion` | 해당 장면 Runway 재생성 + 재합성 | 크레딧 |
| 원테이크 컷의 `video_prompt` · 컷 순서·사진 | 그 컷 클립(과 사진이 바뀌면 앞 컷 클립) Runway 재생성 + 재합성 | 전환당 23~30크레딧 |
| 그 외 장면의 `image_index` · `motion` | 해당 장면 FFmpeg 클립 재생성 + 재합성 | 무료 |
| `narration` | 해당 장면 TTS 재합성 + 재합성 | 거의 무료 |
| `caption` · `style` · `bgm` · `order` · `duration` · `title` | 재합성만 | 무료 |

## 6. 정할 것

| 항목 | 제안 | 관련 파트 | 상태 |
| --- | --- | --- | --- |
| AI 클립 수 | 영상당 최대 3개 (약 $0.75) | A · B | ✅ 확정 |
| 사진 구분 · 메뉴 연결 | [사진 ↔ 메뉴 연결](#사진--메뉴-연결) 방식. 첫 화면에서 메뉴판과 사진을 같이 받아 AI가 자동 매칭, 메뉴 확인 화면에서 틀린 것만 수정. 생성 요청에 확인된 메뉴 데이터 필드 추가 | A · B · 프론트 | 방향 확정, API 형식 논의 필요 |
| 입력 개수 | 음식·가게 사진 최대 4장, 메뉴판 최대 3장 (영상 15초에 담을 수 있는 양). 앱에서 막고 서버도 검사 | 프론트 · A | 제안 |
| 메뉴판 사진 | 메뉴 추출에만 사용, `photos[]`·영상 `photoUrls`에서 제외 | A · B · 프론트 | ✅ 확정 |
| 음식 사진이 없을 때 | Gen-4 Image로 메뉴 음식 이미지를 생성해 `photos[]`에 `source: generated`, `menu_ref` 지정으로 추가. 사장님 확인 화면에서 함께 확인 | A · B · 프론트 | ✅ 방향 확정 |
| 사진 종류별 사용 규칙 | [2. 장면 필드](#사진-종류별-사용-규칙)의 표 | A · B | ✅ 확정 |
| 사진 종류별 `video_prompt` 스타일 | 생성 이미지는 이미지 prompt에 넣은 움직일 거리(김·얼음·흘러내리는 소스)를 그대로 움직이고, 없던 음식은 더하지 않음 | A | ✅ 확정 (2026-11-20) |
| 생성 이미지 세부 | [생성 이미지 요청](#생성-이미지-요청-image_requests). 프롬프트는 A가 메뉴 추출 때 작성, 생성은 B가 Runway `gen4_image`로 (영상과 같은 키). 쓸 수 있는 사진이 3장보다 적을 때 모자란 만큼, 메뉴 2~3가지 조합 우선 | A · B | ✅ 방향 확정 |
| 생성 모델 | `gen4_image_turbo`는 참조 이미지가 꼭 있어야 해서 `gen4_image`(글만, 720p 5 credits)로 확정. 모델이 사진을 못 보므로 올린 사진의 그릇·테이블·조명을 이미지 prompt에 글로 옮겨 적음 | B | ✅ 확정 (2026-11-20) |
| TTS 단위 | 장면별 합성 후 `duration`까지 무음 채움 (수정 시 해당 장면만 재생성) | B | 논의 필요 |
| 클립 길이 맞추기 | Runway 5초 클립을 앞에서부터 `duration`만큼 사용 | B | 논의 필요 |
| 자막 렌더링 | 장면별 `caption` + 템플릿(기본 꽉 찬 화면형 `headline`)에서 `title`을 영상 내내 위에 고정 제목으로도 표시 (게시물·쇼츠 업로드 제목 겸용). `classic`이면 제목 없음 | A · B | ✅ A·B 반영 |
| 대사 = 자막 | 판다랭크처럼 나레이션을 그대로 자막으로 (장면 하나 = 자막 한 조각). 지금은 장면당 자막 1개라 문장을 장면 여러 개로 나눠 담음. 장면 안에서 자막을 여러 번 바꾸려면 B 파트가 TTS 타이밍으로 자막을 나눠야 함 | A · B | A 반영 (2026-11-20, 프롬프트 v9), B는 그대로 |
| BGM 파일 | `resources/bgm/`에 `bgm.mood` 4종 파일 준비 | B | 논의 필요 |
| enum 목록 | `style.mood` · `bgm.mood` · `type` · `motion` 값이 렌더링에 충분한지 | A · B | 논의 필요 |
