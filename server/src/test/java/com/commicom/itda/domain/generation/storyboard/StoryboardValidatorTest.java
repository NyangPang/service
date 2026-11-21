package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryboardValidatorTest {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StoryboardValidator validator = new StoryboardValidator();

    @ParameterizedTest
    @ValueSource(strings = {"01-donkatsu", "02-cafe", "03-izakaya"})
    void 더미_스토리보드는_모두_통과한다(String sample) throws IOException {
        List<String> errors = validator.validate(storyboard(sample), menu(sample));
        assertThat(errors).isEmpty();
    }

    @Test
    void 코드블록으로_감싼_응답도_파싱한다() throws IOException {
        String raw = "여기 결과입니다.\n```json\n" + read("01-donkatsu.storyboard.json") + "\n```";
        Storyboard sb = StoryboardJson.parseStoryboard(raw);
        assertThat(validator.validate(sb, menu("01-donkatsu"))).isEmpty();
    }

    @Test
    void 한_번_감싼_응답은_벗겨서_읽는다() throws IOException {
        String raw = "{\"storyboard\": " + read("02-cafe.storyboard.json") + "}";
        assertThat(validator.validate(StoryboardJson.parseStoryboard(raw), menu("02-cafe"))).isEmpty();
    }

    @Test
    void 메뉴_데이터나_프롬프트를_넣으면_알려준다() {
        assertThatThrownBy(() -> StoryboardJson.parseStoryboard(read("02-cafe.menu.json")))
                .hasMessageContaining("메뉴 데이터가 들어왔습니다");
        assertThatThrownBy(() -> StoryboardJson.parseStoryboard("당신은 ... [출력 형식 예시] { }"))
                .hasMessageContaining("프롬프트 글이 들어왔습니다");
        assertThatThrownBy(() -> StoryboardJson.parseStoryboard("{\"isSuccess\": true, \"result\": 1}"))
                .hasMessageContaining("[isSuccess, result]");
    }

    // ── 1. 스키마 ──

    @Test
    void 허용되지_않는_enum과_색상() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            ((ObjectNode) sb.get("style")).put("mood", "sad").put("main_color", "orange");
            scene(sb, 1).put("motion", "pan_left");
        });
        assertThat(errors).anyMatch(e -> e.contains("style.mood"))
                .anyMatch(e -> e.contains("main_color"))
                .anyMatch(e -> e.contains("motion") && e.contains("pan_left"));
    }

    @Test
    void 제목이_20자를_넘으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> sb.put("title", "가".repeat(21)));
        assertThat(errors).anyMatch(e -> e.startsWith("title"));
    }

    // ── 2. 시간 ──

    @Test
    void duration_합이_15초가_아니면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 4).put("duration", 4.5));
        assertThat(errors).anyMatch(e -> e.contains("duration 합이 16초"));
    }

    @Test
    void 장면_길이와_hook_길이_제한() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).put("duration", 2.5);   // hook 2초 초과
            scene(sb, 1).put("duration", 3.25);  // 0.5 단위 아님
        });
        assertThat(errors).anyMatch(e -> e.contains("hook 장면은 2초 이하"))
                .anyMatch(e -> e.contains("0.5초 단위"));
    }

    // ── 3. 구성 ──

    @Test
    void hook으로_시작하고_cta로_끝나야_한다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).put("type", "menu");
            scene(sb, 4).put("type", "review");
        });
        assertThat(errors).anyMatch(e -> e.startsWith("첫 장면"))
                .anyMatch(e -> e.startsWith("마지막 장면"));
    }

    @Test
    void order가_끊기거나_id가_겹치면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 2).put("order", 5);
            scene(sb, 3).put("id", 1);
        });
        assertThat(errors).anyMatch(e -> e.contains("order는 3"))
                .anyMatch(e -> e.contains("id 1가 중복"));
    }

    @Test
    void 장면이_3개면_오류() throws IOException {
        List<String> errors = errorsOf("02-cafe", sb -> ((ArrayNode) sb.get("scenes")).remove(2));
        assertThat(errors).anyMatch(e -> e.contains("4~6개"));
    }

    // ── 4·5. 자막·가격 ──

    @Test
    void 자막이_40자를_넘으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu",
                sb -> line(sb, 2, "치즈가 쭉쭉 늘어나는 치즈카츠까지 있어서 고르는 재미가 정말 쏠쏠한 집이에요."));
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=3] caption.text는 공백 포함 40자"));
    }

    @Test
    void 자막이_20자를_넘어도_조각으로_나뉘면_자막_오류가_없음() throws IOException {
        // 예전(20자 제한)에는 장면 구성 3번이 모두 이 길이에서 막혀 생성이 실패했다
        List<String> errors = errorsOf("01-donkatsu", sb -> line(sb, 2, "치즈가 쭉쭉 늘어나는 치즈카츠까지 있어요."));
        assertThat(errors).noneMatch(e -> e.contains("caption.text는"));
    }

    @Test
    void 띄어쓰기_없이_길게_붙은_자막은_나눌_수_없어_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> line(sb, 2, "겉바속촉치즈듬뿍로스카츠정식세트메뉴예요."));
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=3] caption.text는 화면에 띄어쓰기 단위로 18자 조각 3개까지"));
    }

    @Test
    void 자막_가격이_메뉴_가격과_다르면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> caption(sb, 1).put("text", "로스카츠 13,000원"));
        assertThat(errors).anyMatch(e -> e.contains("13,000원") && e.contains("12,000원과 다릅니다"));
    }

    @Test
    void 가격_자막인데_menu_ref가_없으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 1).putNull("menu_ref"));
        assertThat(errors).anyMatch(e -> e.contains("menu_ref에 그 메뉴 이름"));
    }

    @Test
    void 가격_형식이_틀리면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> caption(sb, 1).put("text", "로스카츠 12000원"));
        assertThat(errors).anyMatch(e -> e.contains("\"12,000원\" 형식"));
    }

    @Test
    void 메뉴에_없는_menu_ref는_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 1).put("menu_ref", "로스까스"));
        assertThat(errors).anyMatch(e -> e.contains("\"로스까스\"는 메뉴 데이터에 없습니다"));
    }

    // ── 6. 나레이션 ──

    @Test
    void 나레이션에_가격_말고_숫자나_한글_금액이_있으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 1).put("narration", "메뉴만 열두 가지, 3분이면 나와요");
            scene(sb, 2).put("narration", "치즈카츠는 만사천원.");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=2] narration에 숫자"))
                .anyMatch(e -> e.contains("[장면 id=3] narration의 한글 금액"));
    }

    @Test
    void 나레이션_가격은_menu_ref_가격과_같으면_통과() throws IOException {
        // 2번 장면 menu_ref 로스카츠 12,000원
        assertThat(errorsOf("01-donkatsu", sb -> line(sb, 1, "12,000원에 이 두께라고?"))).isEmpty();
        assertThat(errorsOf("01-donkatsu", sb -> line(sb, 1, "13,000원에 이 두께라고?")))
                .anyMatch(e -> e.contains("[장면 id=2] narration 가격 13,000원이 메뉴 \"로스카츠\"의 가격 12,000원과 다릅니다"));
        assertThat(errorsOf("01-donkatsu", sb -> line(sb, 1, "12000원에 이 두께라고?")))
                .anyMatch(e -> e.contains("\"12,000원\" 형식"));
        // 1번 장면은 menu_ref가 없음
        assertThat(errorsOf("01-donkatsu", sb -> line(sb, 0, "12,000원 실화?")))
                .anyMatch(e -> e.contains("[장면 id=1] narration에 가격(12,000원)이 있으면 menu_ref"));
    }

    @Test
    void 나레이션이_없는_장면은_오류() throws IOException {
        assertThat(errorsOf("01-donkatsu", sb -> scene(sb, 2).putNull("narration")))
                .anyMatch(e -> e.contains("[장면 id=3] narration이 없습니다"));
    }

    @Test
    void 나레이션이_duration에_비해_길면_오류() throws IOException {
        // hook 2초 → 최대 14자
        List<String> errors = errorsOf("01-donkatsu", sb ->
                scene(sb, 0).put("narration", "웨이팅이 이렇게 긴 데는 다 이유가 있습니다."));
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=1] narration이") && e.contains("14자 이내"));
    }

    @Test
    void 자막이나_나레이션에_이모지가_있으면_오류() throws IOException {
        List<String> errors = errorsOf("02-cafe", sb -> {
            caption(sb, 0).put("text", "☀️ 창가 자리");
            scene(sb, 1).put("narration", "버터향 가득해요😋");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=1] caption.text에 이모지") && e.contains("\"☀\""))
                .anyMatch(e -> e.contains("[장면 id=2] narration에 이모지") && e.contains("\"😋\""));
    }

    @Test
    void 이모지_판별() {
        assertThat(StoryboardValidator.findEmoji("★맛집★ 인정")).isEqualTo("\"★\"");
        assertThat(StoryboardValidator.findEmoji("1️⃣ 등")).isEqualTo("이모지"); // 1️⃣ 키캡
        // 자막에 흔히 쓰는 문장부호·기호는 통과
        assertThat(StoryboardValidator.findEmoji("숯불 향이 솔솔~ \"진짜\" 분위기·친절! 12,000원 (1+1)")).isNull();
    }

    @Test
    void 글자수는_공백과_문장부호를_뺀다() {
        assertThat(StoryboardValidator.countSpokenChars("웨이팅엔 이유가 있죠.")).isEqualTo(9);
    }

    // ── 7. 영상 프롬프트 ──

    @Test
    void ai_장면이_3개를_넘으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 1).put("motion", "ai").put("video_prompt", "pork cutlet on a plate, slow push-in, warm light");
            scene(sb, 3).put("motion", "ai").put("video_prompt", "pork cutlet on a plate, slow pull-back, warm light");
        });
        assertThat(errors).anyMatch(e -> e.contains("\"ai\"인 장면이 4개"));
    }

    @Test
    void ai_장면끼리_같은_사진을_쓰면_오류() throws IOException {
        // 장면 1(hook, 0번 사진, ai)과 같은 사진으로 장면 2도 ai로 만듦
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 1).put("motion", "ai")
                .put("video_prompt", "thick pork cutlet on a plate, slow pull-back, warm light"));
        assertThat(errors).anyMatch(e -> e.startsWith("[장면 id=2] 0번 사진은 이미 [장면 id=1]의 AI 클립"));
    }

    @Test
    void 같은_사진이라도_ai가_아닌_장면은_괜찮다() throws IOException {
        // 더미의 장면 1(ai)과 장면 2(zoom_in)는 둘 다 0번 사진
        List<String> errors = errorsOf("01-donkatsu", sb -> {});
        assertThat(errors).noneMatch(e -> e.contains("AI 클립에 썼습니다"));
    }

    @Test
    void 영상_프롬프트_규칙() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).put("video_prompt", "돈가스 클로즈업, slow push-in");
            scene(sb, 2).put("video_prompt", "cheese cutlet with a neon sign behind it, slow push-in, warm light");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=1] video_prompt는 영어로만"))
                .anyMatch(e -> e.contains("[장면 id=3]") && e.contains("neon sign"));
    }

    @Test
    void 영상_프롬프트에_사람이나_신체가_있으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).put("video_prompt", "chef's fingers placing a pork cutlet, slow push-in, warm light");
            scene(sb, 2).put("video_prompt", "Customer smiling at a cheese cutlet, close-up, soft light");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=1]") && e.contains("사람·신체")
                        && e.contains("\"chef\", \"fingers\""))
                .anyMatch(e -> e.contains("[장면 id=3]") && e.contains("\"customer\""));
    }

    @Test
    void 장갑_낀_손과_도구로_하는_동작은_허용() throws IOException {
        // 카메라 무빙만으로는 정적이라, 집게로 집기·포크와 나이프로 찢기·단면 보여 주기 같은 동작을 쓰게 한다
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).put("video_prompt", "a gloved hand uses metal tongs to pick up the pork cutlet and place it on a tray, slow push-in, warm light");
            scene(sb, 2).put("video_prompt", "a fork and knife tear the cheese cutlet apart, revealing the melting cheese inside, close-up, warm light");
        });
        assertThat(errors).isEmpty();
    }

    @Test
    void 영상_프롬프트에_입이_필요한_동작이_있으면_오류() throws IOException {
        // 먹는 동작을 쓰면 Runway가 얼굴을 그려 넣는다
        List<String> errors = errorsOf("01-donkatsu", sb ->
                scene(sb, 2).put("video_prompt", "close-up of biting into the cheese cutlet, sauce pouring, slow motion"));
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=3]") && e.contains("입이 있어야 하는 동작")
                && e.contains("동작(\"biting\")"));
    }

    @Test
    void 외관_사진으로_넘어가는_마지막_장면은_transition으로_쓸_수_있음() throws IOException {
        // 01-donkatsu: 4번째 장면은 음식(1번 사진), 마지막 장면은 외관(3번 사진)
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 4).put("motion", "transition").put("video_prompt",
                "the camera pulls back from the cutlet on the table, glides out through the window and reveals the storefront from the street, warm evening light"));
        assertThat(errors).isEmpty();
    }

    @Test
    void transition은_외관_사진으로_넘어갈_때만_한_번() throws IOException {
        String prompt = "the camera glides from the cutlet to the next plate, smooth continuous motion";
        List<String> first = errorsOf("01-donkatsu", sb -> scene(sb, 0).put("motion", "transition").put("video_prompt", prompt));
        assertThat(first).anyMatch(e -> e.contains("[장면 id=1] 첫 장면은 motion \"transition\"일 수 없습니다"));

        List<String> notExterior = errorsOf("01-donkatsu", sb -> scene(sb, 3).put("motion", "transition").put("video_prompt", prompt));
        assertThat(notExterior).anyMatch(e -> e.contains("[장면 id=4] motion \"transition\"은 가게 외관(exterior) 사진으로"));

        List<String> noPrompt = errorsOf("01-donkatsu", sb -> scene(sb, 4).put("motion", "transition").putNull("video_prompt"));
        assertThat(noPrompt).anyMatch(e -> e.contains("[장면 id=5] motion이 \"transition\"이면 video_prompt가 있어야"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mouth-watering pork cutlet with steam rising, slow push-in, warm light",
            "spicy stir-fried pork sizzling on a hot plate, slow push-in",
            "pour-over coffee in a glass server, steam rising, soft morning light",
            "freshly poured draft beer with foam settling, close-up, warm amber light",
            "handmade dumplings on a bamboo steamer, steam rising, slow push-in",
            "warm uplifting mood, golden surface of a pancake, slow orbit",
            "steaming bowl of ramen, broth glistening, slow push-in, cozy mood"
    })
    void 음식_묘사에_흔한_표현은_사람_검사에_걸리지_않는다(String videoPrompt) throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 0).put("video_prompt", videoPrompt));
        assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a woman sipping iced latte by the window",
            "crowded cozy izakaya interior, warm light",
            "close-up of a face smiling over the cutlet, warm light"
    })
    void 사람을_떠올리게_하는_표현은_막는다(String videoPrompt) throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 0).put("video_prompt", videoPrompt));
        assertThat(errors).anyMatch(e -> e.startsWith("[장면 id=1] video_prompt에")
                && (e.contains("사람·신체") || e.contains("입이 있어야 하는 동작")));
    }

    @Test
    void ai_장면에_프롬프트가_없거나_60단어를_넘으면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 0).putNull("video_prompt");
            scene(sb, 2).put("video_prompt", "word ".repeat(61).replace("word", "cheese").trim());
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=1] motion이 \"ai\"이면 video_prompt"))
                .anyMatch(e -> e.contains("[장면 id=3] video_prompt가 61단어"));
    }

    @Test
    void 동작을_담은_45단어_프롬프트는_통과() throws IOException {
        // 동작 + 음식 묘사 + 카메라 + 조명 + 분위기를 쓰면 40단어를 쉽게 넘어서, 40단어 제한에 걸려 생성이 실패했었다
        String prompt = "golden cheese cutlet on a wooden board, a gloved hand uses a knife to slice it in half and pulls the pieces apart "
                + "so the molten cheese stretches between them, steam rising from the crust, slow push-in, warm side lighting, "
                + "cozy appetizing mood";
        assertThat(prompt.split("\\s+").length).isBetween(41, 60);
        assertThat(errorsOf("01-donkatsu", sb -> scene(sb, 2).put("video_prompt", prompt))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "milk pouring into the latte from above and forming latte art, close-up on the cup, soft light",
            "honey syrup drizzling over the cutlet with steam rising, a slice of cake beside it, slow orbit"
    })
    void 붓기와_곁들임_묘사는_된다(String videoPrompt) throws IOException {
        assertThat(errorsOf("01-donkatsu", sb -> scene(sb, 2).put("video_prompt", videoPrompt))).isEmpty();
    }

    // ── 8·9. 사진 ──

    @Test
    void 사진_칸에_올린_메뉴판은_쓸_수_없다() throws IOException {
        // 메뉴판 칸 이미지는 photos에 안 들어오지만, 사장님이 사진 칸에 메뉴판을 올리면 menu_board로 들어온다
        MenuData menu = mutateMenu("02-cafe", m -> ((ArrayNode) m.get("photos")).addObject()
                .put("index", 3).put("source", "upload").put("kind", "menu_board").putNull("menu_ref")
                .put("description", "손글씨 메뉴판"));
        ObjectNode node = (ObjectNode) MAPPER.readTree(read("02-cafe.storyboard.json"));
        scene(node, 3).put("image_index", 3);
        List<String> errors = validator.validate(MAPPER.treeToValue(node, Storyboard.class), menu);
        assertThat(errors).anyMatch(e -> e.contains("메뉴판 사진"));
    }

    @Test
    void 없는_사진_번호는_오류() throws IOException {
        List<String> errors = errorsOf("03-izakaya", sb -> scene(sb, 4).put("image_index", 5));
        assertThat(errors).anyMatch(e -> e.contains("5번 사진이 없습니다"));
    }

    @Test
    void menu_ref와_사진_메뉴가_다르면_오류() throws IOException {
        // 로스카츠 가격 자막 + 히레카츠 사진
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 1).put("image_index", 1));
        assertThat(errors).anyMatch(e -> e.contains("menu_ref가 \"로스카츠\"인데 1번 사진은 \"히레카츠\" 사진"));
    }

    @Test
    void 가게_사진은_menu_detail_장면에_못_쓴다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            ObjectNode s = scene(sb, 3).put("type", "detail").put("image_index", 3);
            s.putNull("menu_ref");
        });
        assertThat(errors).anyMatch(e -> e.contains("가게 사진이라 detail 장면"));
    }

    @Test
    void 생성_사진은_detail에는_못_쓰지만_ai는_된다() throws IOException {
        MenuData menu = mutateMenu("01-donkatsu", m ->
                ((ObjectNode) m.get("photos").get(2)).put("source", "generated"));
        Storyboard sb = storyboard("01-donkatsu"); // 장면 3: detail + ai + 2번 사진
        assertThat(validator.validate(sb, menu)).containsExactly(
                "[장면 id=3] 2번은 AI 생성 음식 사진이라 detail 장면에 쓸 수 없습니다.");
    }

    @Test
    void 쓰는_사진마다_ai_장면이_있어야_한다() throws IOException {
        // 1번 사진(히레카츠)의 유일한 장면을 줌으로 바꾸면 그 사진은 흐린 정지 화면이 된다
        List<String> errors = errorsOf("01-donkatsu", sb -> scene(sb, 3).put("motion", "zoom_out").putNull("video_prompt"));
        assertThat(errors).anyMatch(e -> e.startsWith("1번 사진을 쓰는 장면에 motion \"ai\"가 하나도 없습니다"));
    }

    @Test
    void 외관_사진은_ai가_없어도_된다() throws IOException {
        // 01-donkatsu 마지막 장면(3번 외관)은 zoom_in
        assertThat(errorsOf("01-donkatsu", sb -> {})).isEmpty();
    }

    // ── 주제 · 시작과 끝 · 자막 · 나레이션 ──

    @Test
    void 주제가_없거나_목록에_없으면_오류() throws IOException {
        assertThat(errorsOf("02-cafe", sb -> sb.remove("theme"))).anyMatch(e -> e.startsWith("theme 값 null"));
        assertThat(errorsOf("02-cafe", sb -> sb.put("theme", "cute"))).anyMatch(e -> e.startsWith("theme 값 \"cute\""));
    }

    @Test
    void 제목은_띄어쓰기_있는_후킹_문구() throws IOException {
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "바삭함이 다른 두툼 로스카츠"))).isEmpty();
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "점심엔 12,000원 로스카츠"))).isEmpty();
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "바삭두툼로스카츠맛집")))
                .anyMatch(e -> e.contains("띄어쓰기가 없습니다"));
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "카츠하우스")))
                .anyMatch(e -> e.contains("title이 가게 이름뿐"));
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "점심엔 무조건 🍖 로스카츠")))
                .anyMatch(e -> e.contains("title에 이모지"));
        assertThat(errorsOf("01-donkatsu", sb -> sb.put("title", "점심엔 9,900원 로스카츠")))
                .anyMatch(e -> e.contains("title 가격 \"9,900원\""));
    }

    @Test
    void 첫_장면은_가게_이름_대신_훅으로_시작해도_된다() throws IOException {
        assertThat(errorsOf("01-donkatsu", sb -> line(sb, 0, "이 두께 실화예요?"))).isEmpty();
    }

    @Test
    void 샘플_가게_앞말은_빼고_비교한다() throws IOException {
        MenuData menu = mutateMenu("02-cafe", m -> m.put("store_name", "[샘플] 골목카페"));
        assertThat(validator.validate(storyboard("02-cafe"), menu)).isEmpty();
    }

    @Test
    void 외관_사진이_있으면_외관과_가게_이름으로_끝낸다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 4).put("image_index", 1);
            caption(sb, 4).put("text", "오늘 점심은 여기서").put("position", "bottom");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=5] 가게 외관 사진(3번)이 있으니 마지막 장면은 외관 사진으로"))
                .anyMatch(e -> e.contains("[장면 id=5] 마지막 장면 자막은 가게 이름 \"카츠하우스\"만"));
    }

    @Test
    void 외관_사진이_없어도_마지막_자막은_가게_이름() throws IOException {
        // 02-cafe 가게 사진은 내부(store) — 외관 강제는 없지만 마지막 자막은 가게 이름
        assertThat(errorsOf("02-cafe", sb -> {})).isEmpty();
        assertThat(errorsOf("02-cafe", sb -> caption(sb, 3).put("text", "햇살 드는 창가").put("position", "bottom")))
                .anyMatch(e -> e.contains("[장면 id=4] 마지막 장면 자막은 가게 이름 \"골목카페\"만"))
                .noneMatch(e -> e.contains("외관 사진"));
    }

    @Test
    void 자막은_나레이션을_그대로_쓴다() throws IOException {
        // 문장으로 끝나는 자막도 나레이션과 같으면 통과 (대사 = 나레이션 = 자막)
        assertThat(errorsOf("02-cafe", sb -> line(sb, 2, "라떼까지 시키면 완벽하죠."))).isEmpty();
        // 띄어쓰기가 여러 칸인 것만 봐준다
        assertThat(errorsOf("02-cafe", sb -> caption(sb, 2).put("text", "바닐라라떼 한 잔  곁들이면 딱이죠."))).isEmpty();
        // 요약 자막은 오류 — 고칠 문장을 알려 준다
        assertThat(errorsOf("02-cafe", sb -> caption(sb, 2).put("text", "바닐라라떼 한 잔")))
                .anyMatch(e -> e.startsWith("[장면 id=3] 자막은 나레이션과 같은 글이어야 합니다")
                        && e.contains("\"바닐라라떼 한 잔 곁들이면 딱이죠.\"로 바꾸세요"));
        // 문장부호도 같아야 한다
        assertThat(errorsOf("02-cafe", sb -> caption(sb, 0).put("text", "버터 향 맡아보셨어요")))
                .anyMatch(e -> e.startsWith("[장면 id=1] 자막은 나레이션과 같은 글이어야 합니다"));
    }

    @Test
    void 마지막_장면은_자막이_가게_이름이고_나레이션이_가게_이름을_부른다() throws IOException {
        // 마지막 장면만 자막과 나레이션이 다르다
        assertThat(errorsOf("02-cafe", sb -> {})).noneMatch(e -> e.contains("자막은 나레이션과 같은 글"));
        assertThat(errorsOf("02-cafe", sb -> scene(sb, 3).put("narration", "창가에서 쉬어 가세요.")))
                .anyMatch(e -> e.contains("[장면 id=4] 마지막 장면 나레이션에 가게 이름 \"골목카페\"을 넣으세요"));
    }

    @Test
    void 마지막_장면_나레이션은_가게_이름으로_끝내지_않는다() throws IOException {
        assertThat(errorsOf("02-cafe", sb -> scene(sb, 3).put("narration", "창가에서 쉬어 가기 딱 좋은 골목카페.")))
                .anyMatch(e -> e.contains("[장면 id=4] 마지막 장면 나레이션이 가게 이름으로 끝납니다"));
        assertThat(errorsOf("02-cafe", sb -> scene(sb, 3).put("narration", "창가에서 쉬어 가기 딱 좋은 골목카페예요.")))
                .noneMatch(e -> e.contains("가게 이름으로 끝납니다"));
    }

    @Test
    void 자막_위치는_bottom이나_center만() throws IOException {
        assertThat(errorsOf("02-cafe", sb -> caption(sb, 1).put("position", "top")))
                .anyMatch(e -> e.contains("caption.position 값 \"top\""));
    }

    @Test
    void 나레이션은_같은_어미로_끝나지_않는다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", sb -> {
            scene(sb, 1).put("narration", "로스카츠가 있어요");
            scene(sb, 2).put("narration", "치즈카츠도 있어요");
        });
        assertThat(errors).anyMatch(e -> e.contains("[장면 id=3] 나레이션이 [장면 id=2]과 같은 \"…있어요\""));
    }

    // ── helpers ──

    private List<String> errorsOf(String sample, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(read(sample + ".storyboard.json"));
        mutation.accept(node);
        return validator.validate(MAPPER.treeToValue(node, Storyboard.class), menu(sample));
    }

    private MenuData mutateMenu(String sample, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(read(sample + ".menu.json"));
        mutation.accept(node);
        return MAPPER.treeToValue(node, MenuData.class);
    }

    private static ObjectNode scene(ObjectNode sb, int i) {
        return (ObjectNode) sb.get("scenes").get(i);
    }

    private static ObjectNode caption(ObjectNode sb, int i) {
        return (ObjectNode) scene(sb, i).get("caption");
    }

    /** 대사 한 조각: 나레이션과 자막을 같은 글로 바꾼다 */
    private static void line(ObjectNode sb, int i, String text) {
        scene(sb, i).put("narration", text);
        caption(sb, i).put("text", text);
    }

    private Storyboard storyboard(String sample) throws IOException {
        return StoryboardJson.parseStoryboard(read(sample + ".storyboard.json"));
    }

    private MenuData menu(String sample) throws IOException {
        return StoryboardJson.parseMenu(read(sample + ".menu.json"));
    }

    private static String read(String file) throws IOException {
        return Files.readString(SAMPLES.resolve(file));
    }
}
