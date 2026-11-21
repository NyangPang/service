# 영상 파이프라인 (B 파트)

장면 JSON([storyboard-schema.md](storyboard-schema.md)) + 사진 → 세로 mp4(1080x1920, 30fps) + 썸네일.
코드: `src/main/java/com/commicom/itda/infra/video/storyboard/`

## 바로 돌려 보기

키가 없어도 됩니다. FFmpeg만 있으면 됩니다 (`brew install ffmpeg`).

```bash
cd server
./gradlew renderStoryboard --args="--storyboard docs/storyboard-samples/02-cafe.storyboard.json \
  --photos test/카페3.png,test/커피1.png,test/카페1.png,test/menu1.png --out build/video/cafe.mp4"
```

- `--photos`는 `image_index` 순서대로 넣습니다. 0번 사진이 맨 앞입니다.
- 결과 영상은 `build/video/cafe.mp4`, 썸네일은 `cafe.jpg`에 생깁니다.

수정 요청을 적용해서 다시 만들려면 `--changes`를 붙입니다.

```bash
./gradlew renderStoryboard --args="... --changes docs/storyboard-samples/02-cafe.changes.json --out build/video/cafe-v2.mp4"
```

```
장면 1 (ai, 2.0초)       클립 대체 · 음성 재사용 · 합성 재사용
장면 2 (zoom_in, 4.5초)  클립 재사용 · 음성 재사용 · 합성 새로 만듦   ← 자막만 바뀜
장면 3 (ai, 4.0초)       클립 대체 · 음성 새로 만듦 · 합성 새로 만듦   ← 내레이션 바뀜
장면 4 (zoom_out, 4.5초) 클립 재사용 · 음성 재사용 · 합성 재사용
```

## 장면 하나를 만드는 방법

| 단계 | 키 있음 | 키 없음 (로컬) |
| --- | --- | --- |
| 배경 클립 `motion: ai` | 사진마다 Runway `gen4_turbo` 5초 클립 하나 (사진들 것을 동시에 요청 → 3장이어도 클립 1개 시간). 같은 사진을 쓰는 장면은 모두 이 클립을 이어서 씀 (정방향 + 역방향 왕복 10초에서 앞 장면이 쓴 다음부터) | `zoom_in`으로 대체 |
| 배경 클립 `zoom_in` / `zoom_out` / `static` | FFmpeg (무료) | 같음 |
| 내레이션 | **ElevenLabs** 한국어 목소리 + `eleven_v3` (없으면 AWS Polly `Seoyeon`) | macOS `say` 목소리 Yuna |
| 자막 | 나레이션을 Pretendard 한 줄로 그려, 장면 전환 합성 뒤에 실제 음성이 나오는 구간에 표시 | `caption.text`를 해당 장면 동안 한 줄로 표시 |
| 템플릿 | `headline`(꽉 찬 화면형) 하나, `title`을 영상 내내 위에 고정 제목으로 | 같음 |
| BGM | `resources/bgm/{bgm.mood}.mp3`, 내레이션이 나올 때 작아짐 | 파일이 없으면 생략 |
| 엔딩 카드 | 영상 끝에 2초 덧붙임 (0.3초 페이드): 잇츠 로고(`resources/brand/its-logo.png`, 원본 `its-logo.svg`) + 가게 이름 + "잇츠 보고 왔어요" 말하면 사장님 QR로 방문 인증·퀘스트 보상 안내. 배경은 로고가 잘 보이는 `#CBD7CE`. 영상에는 QR을 넣지 않음 (방문 인증 QR은 사장님이 가게에서 보여 줌). 그래서 완성 영상은 장면 15초 + 2초 = 17초 | 같음 |

- **변경 장면만 재생성:** 클립, 음성, 자막, 장면 조각을 입력 내용의 해시로 `build/video-cache/`에 저장합니다. 그래서 바뀐 장면만 새로 만들고 나머지는 재사용합니다. 스키마 5절의 표대로 동작합니다.
- **장면 전환:** 장면 JSON의 `theme`(주제)에 맞춰 넘깁니다 (`ThemeStyle`: 감성 슬라이드, 가성비 회전 쓸기, 트렌디 확대, 전문 페이드, 신선 밀어 넘기기). 앞 장면을 전환 시간만큼 길게 만들어 두어 전체 길이와 장면 시작 시점은 그대로입니다. `theme`이 없으면 0.35초 페이드.
- **내레이션이 장면보다 길 때:** 최대 1.5배속까지 빠르게 합니다. 그래도 넘치면 뒤가 잘립니다. 실행 로그에 표시됩니다.
- **내레이션 속도와 이음새:** 쇼츠처럼 빠르게 말하도록 TTS를 1.1배속으로 만듭니다 (ElevenLabs `voice_settings.speed`, Polly SSML `prosody rate`; 모델이 speed를 받지 않으면 기본 속도로 다시 요청). 첫 장면만 말 앞에 0.2초 여백을 두고, 나머지 장면은 0.05초로 붙여 말이 장면 사이에서 끊기지 않게 합니다. 장면 소리는 크로스페이드하지 않고 각 장면 시작 시점에 그대로 섞습니다.
- **나레이션 속 가격:** `12,000원`은 TTS에 넘기기 전에 `만 이천 원`으로 풀어 씁니다 (`SpokenPrice`).
- **템플릿 (`VideoTemplate`):** 화면 배치를 정합니다. 팀 결정으로 **`headline`(꽉 찬 화면형) 하나만** 씁니다 (사장님이 고르지 않음). `band`·`card`는 `meta.template`이나 `video.template-override`로 지정했을 때만 쓰입니다. 세 템플릿 모두 `title`을 영상 내내 위에 고정 제목으로 띄우고 (가운데 띄어쓰기에서 두 줄, 9자 이하거나 띄어쓰기가 없으면 한 줄), 장면 전환에 같이 밀려가지 않게 장면을 다 이어붙인 뒤에 얹어 처음 0.4초 동안 나타나게 합니다. 자막은 화면을 어둡게 깔지 않고 가운데 정렬입니다.

  | 템플릿 | 쓰이는 때 | 화면 |
  | --- | --- | --- |
  | `headline` 꽉 찬 화면형 | 기본 (항상) | 사진 화면 전체. 잇다 로고 색. 제목은 상자 2줄 (윗줄 `#3CB371` 상자·검정 글씨, 아랫줄 메인 `#086816` 상자·흰 글씨) + 왼쪽 위 가게 이름 태그(`meta.store_name`). 본문 자막은 화면 아래쪽(위 끝 1230px)에 52px 검정 글씨로 `#CBD7CE` 상자 안에 표시하며, 긴 대사는 한 줄씩 나누어 띄움. 화면 가운데는 비워 둠 (글씨색은 배경 대비 4.5 이상) |
  | `band` 위아래 띠형 | 지정했을 때만 | 위 검은 띠(0~540px)에 제목 (흰·노란 글씨), 가운데 사진 칸 1080×920, 아래 띠에 자막 (가격만 노랑) |
  | `card` 카드형 | 지정했을 때만 | 크림색 배경에 제목 (형광펜 밑줄), 둥근 사진 카드 900×980 (그림자), 카드 아래 끝에 걸친 주황 알약 자막 |
  | `classic` | 지정했을 때만 | 고정 제목 없음, 아래 "미니멀 매거진" 자막 |

  - `band`·`card`는 사진 칸이 화면 일부라서 줌 클립을 그 칸 크기로 만들고 (Runway 클립은 가운데를 잘라 씀), 장면마다 바탕 그림(띠·크림색·그림자) 위에 클립을 얹습니다. 카드의 둥근 모서리는 클립 위에 크림색 모서리 덮개를 한 번 더 얹어 만듭니다.
- **자막 모양 (`classic` — 미니멀 매거진):** 글자가 놓이는 쪽을 어둡게 깝니다.
  - `large`: 강조색 짧은 막대 + 큰 글씨
  - `price`: 막대 + 메뉴 이름과 가격을 한 줄에 표시
  - `normal`: 중간 크기 글씨 (`gothic_regular`면 SemiBold)
  - 막대 색은 `style.main_color`를 밝게 한 색입니다.
- **자막 위치:** `classic`의 `bottom`(기본)은 화면 가운데 바로 아래(위 끝 1040px)에서 왼쪽 정렬, `center`는 정중앙 가운데 정렬입니다.
- **폰트:** 스키마의 `gothic_bold`/`gothic_regular`는 Pretendard ExtraBold/SemiBold로 그립니다 (OFL).

## 키 (`server/.env` 또는 환경변수)

| 키 | 없으면 |
| --- | --- |
| `RUNWAYML_API_SECRET` | `ai` 장면도 `zoom_in` |
| `ELEVENLABS_API_KEY` (`ELEVENLABS_VOICE_ID`, `ELEVENLABS_MODEL`) | Polly → macOS `say` 순으로 대체. 키는 "텍스트 음성 변환" 권한만 있으면 됨 |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (`AWS_REGION`, `AWS_POLLY_VOICE_ID`) | macOS `say` 사용, 다른 OS에서는 무음 |
| `FFMPEG_PATH` | `/opt/homebrew/bin/ffmpeg` 또는 `ffmpeg` |

옵션은 세 가지입니다.
- `--tts elevenlabs|polly|say|none`: 음성을 강제로 고릅니다.
- `--no-runway`: 키가 있어도 크레딧을 쓰지 않습니다.
- `--cache <폴더>`: 캐시 위치를 바꿉니다.

## 서버에서 부르기 (A 파트와 연결)

- `infra/video/storyboard/StoryboardVideoService`: 장면 JSON 문자열 + 로컬 사진 → mp4. 설정은 `video.ffmpeg-path`, `video.work-dir`, `video.tts`, `runway.api-key`
- `domain/generation/storyboard/StoryboardVideoPipeline`: 메뉴 데이터 + 사진 → (이미지 생성) → 장면 JSON 생성·검증(A) → mp4(B). 검증을 끝내 통과하지 못하면 영상은 만들지 않음
  - 메뉴 데이터에 `image_requests`가 있으면 `addGeneratedImages`가 Runway로 이미지를 먼저 만들어 `photos[]`·사진 목록 끝에 붙인 뒤 장면 JSON을 만든다 (`runway=true`와 상관없이, `RUNWAYML_API_SECRET`이 있어야 함)
- 두 파트는 장면 JSON 문자열로만 주고받는다 (`Storyboard` 레코드가 A·B에 따로 있음)
- `video.tts` (`VIDEO_TTS`): `auto`(기본, ElevenLabs 키 → AWS 키(Polly) → macOS say → 무음 순, CLI와 같음) / `elevenlabs` / `polly` / `say` / `none`. Polly는 서버 키의 IAM 사용자(`nyang-pang-server`)에 `polly:SynthesizeSpeech` 권한이 있어야 함 (없으면 403)
- 임시 테스트에서 화면 배치를 고정하려면 서버 시작 인수에 `--video.template-override=headline`을 추가합니다. 인수를 생략하면 장면 JSON의 `meta.template`, 그것도 없으면 `headline`입니다.

로컬 테스트 API (`local` 프로필, 로그인 토큰 필요). `runway=true`를 붙여야 Runway 크레딧을 쓴다.

| API | 하는 일 |
| --- | --- |
| `POST /api/dev/storyboard/samples/{sample}/render` | 샘플 장면 JSON + `photos` → mp4 (LLM 호출 없음) |
| `POST /api/dev/storyboard/samples/{sample}/video` | 샘플 메뉴 + `photos` → 장면 JSON 생성 → mp4 |
| `POST /api/dev/storyboard/video` | `menu`(메뉴 데이터 JSON) + `photos` → (이미지 생성) → 장면 JSON 생성 → mp4. 응답에 `generatedImages`, `imageCredits` |
| `GET /api/dev/storyboard/videos/{id}` | 만든 영상 재생 |

`photos`는 메뉴 데이터 `photos[]` 순서대로 같은 장수 (메뉴판 제외). 메뉴판만 올려 `photos[]`가 비어 있으면 `photos` 없이 보내도 된다 (생성 이미지만으로 영상).

## 남은 일

- [x] BGM 4종(`upbeat_acoustic`, `calm_lofi`, `bright_pop`, `jazz`)을 **CC0 등 레포·앱 배포가 허용된** 음원으로 준비해서 `resources/bgm/`에 넣기 (Artlist 등 구독형 음원은 앱 사용·파일 배포 불가)
- [x] Runway, ElevenLabs 실제 키로 확인
- [x] 팀 IAM 사용자에 Polly 권한 붙이기 (`nyang-pang-server-polly-s3`), Polly 내레이션 확인
- [x] 서버에서 장면 JSON 생성 → 렌더링까지 한 번에 돌리기 (`StoryboardVideoPipeline`)
- [x] 서버 생성 API(`domain/generation`)에 연결하기: 사진 URL 다운로드 → 렌더링 → S3 업로드 → 숏폼 `videoUrl` (`GenerationVideoService`, 아래 "앱 생성 API")
- [ ] 시연용 영상을 미리 만들어 두기

## 앱 생성 API (`POST /api/generation`)

`GenerationPipelineService` → `GenerationVideoService`가 위 단계를 이어 붙입니다.

1. `photoUrls`(음식·가게 사진)와 `menuBoardUrls`(메뉴판)를 받음. 로컬 사본(`video.work-dir/upload-cache`) → S3 → 공개 주소 순으로 찾음
2. 메뉴 추출 (`MenuExtractor`). 사장님이 메뉴 확인 화면(`POST /api/generation/menus`, 같은 추출기)에서 확정한 `menus`가 있으면 메뉴판은 다시 읽지 않고 그 메뉴를 그대로 쓰고 사진 분류만 함. 없으면 메뉴판에서 읽음 (`menuInfo`는 추가 정보)
3. 음식 사진이 없으면 메뉴 이미지 생성 (Runway, 실패하면 있는 사진으로만)
4. 장면 JSON → mp4 (`StoryboardVideoPipeline`), 5. `ShortformVideoService.saveDraft` → 비공개 게시물

- 영상이 실패하면 생성 `FAILED` (예전 AI 이미지 게시물 대체는 Nova Canvas 종료로 뺐음)
- Runway 사용 여부는 로그 한 줄로 확인: `ai 장면 N개 → Runway 새 클립 a · 캐시 b · 줌 대체 c`. 새 클립만 크레딧이 듭니다
- `GENERATION_RUNWAY=false`로 크레딧 없이 시험
- 서버 IAM 사용자(`nyang-pang-server`)에 `s3:GetObject`가 없어서 로컬 사본을 먼저 씁니다. 서버를 여러 대 띄우거나 재시작 사이에 생성하면 사본이 없을 수 있으니, 배포 전에 `s3:GetObject` 권한을 붙이는 게 안전합니다

## 이미지 생성 (음식 사진이 없을 때)

스키마 3절 순서에서 **이미지 생성** 단계를 맡습니다.
메뉴 추출 → **이미지 생성** → 사장님 확인 → 장면 JSON → 영상

```bash
./gradlew generateMenuImages --args="--menu docs/storyboard-samples/04-dessert.menu.json \
  --photos test/카페1.png --out build/menu-images/dessert"
```

- `image_requests` 하나마다 이미지 1장을 만들어 `photos[]` 끝에 `{source: generated, kind: food, menu_ref}`로 붙입니다. 그다음 `image_requests`는 `[]`로 비웁니다.
- 결과 폴더의 `menu.json`과 `photo-0…`을 그대로 장면 JSON 생성과 `renderStoryboard --photos`에 넣으면 됩니다.
- 서버에서는 `MenuImageService.generate(메뉴 JSON, 사진 파일 목록)`를 부릅니다. `StoryboardVideoPipeline.make`는 이 단계를 자동으로 거칩니다.

| 항목 | 값 |
| --- | --- |
| 모델 | Runway `gen4_image`. `gen4_image_turbo`는 참고 이미지가 꼭 있어야 해서, 사진이 없는 이 경우엔 쓸 수 없음 |
| 비용·시간 | 720×1280 5 크레딧 (`--hd` 또는 `video.image-hd=true`면 1080×1920 8 크레딧), 1장에 약 20초 |
| 캐시 | 같은 프롬프트·해상도는 재사용. 재시도나 테스트에 크레딧이 다시 들지 않음 |

**프롬프트 메모 (테스트 결과)**
- 메뉴 이름만으로는 모양이 잘 안 나옵니다. 예를 들어 크로플이 그냥 와플처럼 나왔습니다. `flaky croissant dough pressed in a waffle iron`처럼 생김새를 풀어 쓰는 게 좋습니다.
- 프롬프트에 없는 장식(허브 등)이 가끔 추가됩니다. 사장님 확인 단계가 꼭 필요합니다.
- 위 두 가지는 메뉴 추출 프롬프트(`prompts/menu-extraction.txt`)에 반영했습니다: 생김새를 풀어 쓰기, `simple plating`, 올린 사진의 그릇·테이블·조명을 글로 옮기기, 영상으로 움직일 거리(김·얼음·흘러내리는 소스) 넣기, 음식은 위쪽 2/3 (자막 자리).
- 여러 장은 동시에 요청합니다 (3장이어도 약 20초).

## 게시물로 저장 (S3)

렌더링이 끝난 영상은 `ShortformVideoService`가 게시물로 만듭니다.

| 메서드 | 하는 일 |
| --- | --- |
| `saveDraft(store, video, thumbnail, title, caption, photoUrls)` | mp4·썸네일을 `shortforms/{storeId}/`에 올리고 **비공개 게시물**로 저장. 대표 이미지(`imageUrl`)는 썸네일, `videoUrl`은 영상 |
| `uploadGeneratedImages(store, images)` | 이미지 생성 결과를 `stores/{storeId}/generated/`에 올림. 사장님 확인 화면과 게시물 `photoUrls`에 그대로 씀 |

- 영상은 다운로드 이름(`가게이름_잇츠.mp4`)을 붙여 올립니다. 앱의 [다운로드]는 `videoUrl`을 열기만 하면 됩니다.
- 운영 DB에는 `docs/sql/2026-11-19-shortform-video.sql`(`video_url` 칸)을 먼저 실행해야 합니다.
