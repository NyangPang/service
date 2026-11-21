package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** 생성 느낌(영상 스타일·강조 포인트)에 따라 프롬프트·검사가 달라지는지 */
class VideoStyleTest {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StoryboardPrompt prompt = new StoryboardPrompt(
            new ClassPathResource("prompts/storyboard.txt"),
            new ClassPathResource("prompts/storyboard-fix.txt"));
    private final MenuExtractionPrompt extractionPrompt = new MenuExtractionPrompt(
            new ClassPathResource("prompts/menu-extraction.txt"),
            new ClassPathResource("prompts/menu-extraction-fix.txt"));
    private final StoryboardValidator validator = new StoryboardValidator();
    private final MenuExtractionValidator extractionValidator = new MenuExtractionValidator();

    // ── 프롬프트 ──

    @Test
    void 스타일을_고르지_않으면_기존_프롬프트_그대로() throws IOException {
        MenuData menu = menu("01-donkatsu");
        String text = prompt.build(menu, "한마디", "서울 성동구");

        assertThat(text).isEqualTo(prompt.build(menu, "한마디", "서울 성동구", VideoOptions.NONE))
                .doesNotContain("{style", "[영상 스타일", "[강조 포인트]")
                .contains("써 주세요.\n\n[가게 정보]")
                .contains("generated 사진은 detail 장면에 쓰지 않습니다.\n\n[출력 형식 예시]");
    }

    @Test
    void 인물_소개_영상은_기존_프롬프트에_목소리만_바뀐다() throws IOException {
        MenuData menu = menu("01-donkatsu");
        VideoOptions person = VideoOptions.of(VideoStyle.PERSON_INTRO, List.of());

        assertThat(prompt.build(menu, "한마디", null, person)).isEqualTo(prompt.build(menu, "한마디", null));
        assertThat(person.maleVoice()).isTrue();
        assertThat(VideoOptions.of(VideoStyle.MENU_FOCUS, List.of()).maleVoice()).isFalse();
        assertThat(VideoOptions.of(VideoStyle.NEWS_PROMO, List.of()).maleVoice()).isTrue();
    }

    @Test
    void 메뉴_영상과_신메뉴_영상은_스타일_규칙이_붙는다() throws IOException {
        MenuData menu = menu("01-donkatsu");

        String menuFocus = prompt.build(menu, null, null, VideoOptions.of(VideoStyle.MENU_FOCUS, List.of()));
        assertThat(menuFocus).contains("이번 영상은 \"메뉴가 잘 보이는 영상\"입니다")
                .contains("[영상 스타일: 메뉴가 잘 보이는 영상]")
                .contains("가게 이름 \"카츠하우스\"")
                .doesNotContain("{storeName}", "{style");
        // 스타일 규칙은 출력 형식 예시 바로 앞 (다른 규칙보다 뒤에 와서 우선)
        assertThat(menuFocus.indexOf("[영상 스타일: 메뉴가")).isLessThan(menuFocus.indexOf("[출력 형식 예시]"))
                .isGreaterThan(menuFocus.indexOf("[사진 고르기]"));

        String news = prompt.build(menu, "신메뉴 명란카츠 출시 기념 20% 할인", null, VideoOptions.of(VideoStyle.NEWS_PROMO, List.of()));
        assertThat(news).contains("[영상 스타일: 신메뉴 및 할인 알림 영상]")
                .contains("motion \"zoom_in\", video_prompt null", "카메라가 외관으로 이동하는 동작")
                .doesNotContain("[영상 스타일: 메뉴가");
    }

    @Test
    void 강조_포인트는_힌트로_붙고_빠른_느린_템포는_함께_고르면_뺀다() throws IOException {
        MenuData menu = menu("01-donkatsu");
        String text = prompt.build(menu, null, null,
                VideoOptions.of(null, List.of(Emphasis.BIG_CAPTION, Emphasis.FAST_TEMPO, Emphasis.SLOW_TEMPO)));

        assertThat(text).contains("[강조 포인트]").contains("- 큰 자막: ")
                .doesNotContain("- 빠른 템포", "- 느린 템포", "[영상 스타일:");
        assertThat(VideoOptions.of(null, List.of(Emphasis.FAST_TEMPO, Emphasis.SLOW_TEMPO)).emphases()).isEmpty();
    }

    @Test
    void 메뉴_추출_프롬프트에_스타일_이미지_규칙이_붙는다() {
        String none = extractionPrompt.build("가게", "RESTAURANT", List.of(), null);
        assertThat(none).doesNotContain("{styleImages}", "[영상 스타일 이미지:")
                .contains("    - 가게 이름·브랜드 이름\n\n[출력 형식 예시]");

        StyleImages news = StyleImages.of(VideoOptions.of(VideoStyle.NEWS_PROMO, List.of()), "신메뉴 명란카츠 출시");
        String text = extractionPrompt.build("가게", "RESTAURANT", List.of(), null, news);
        assertThat(text).contains("[영상 스타일 이미지: 신메뉴 및 할인 알림 영상]")
                .contains("사장님 한마디: 신메뉴 명란카츠 출시")
                .doesNotContain("{appeal}");
        assertThat(news.extra()).isEqualTo(2);
        assertThat(StyleImages.of(VideoOptions.of(VideoStyle.PERSON_INTRO, List.of()), "x")).isEqualTo(StyleImages.NONE);
    }

    // ── 메뉴 추출 검사: 스타일 이미지 개수 ──

    @Test
    void 스타일_이미지는_기본_개수보다_더_요청할_수_있다() throws IOException {
        // 01-donkatsu: 쓸 수 있는 사진 4장 → 기본 0개
        MenuData oneMore = mutateMenu("01-donkatsu", d -> d.putArray("image_requests").addObject().putNull("menu_ref")
                .put("prompt", "top-down view of a full table spread with pork cutlets and cheese cutlet, steam rising, wooden table, warm light, realistic food photography, vertical composition"));

        assertThat(extractionValidator.validate(oneMore, 4)).anyMatch(e -> e.contains("정확히 0개"));
        assertThat(extractionValidator.validate(oneMore, 4, 1)).isEmpty();
        assertThat(extractionValidator.validate(menu("01-donkatsu"), 4, 1)).isEmpty();
    }

    // ── 장면 검사 ──

    @Test
    void 기존_샘플은_인물_소개_스타일에서도_통과한다() throws IOException {
        assertThat(validator.validate(storyboard("01-donkatsu"), menu("01-donkatsu"),
                VideoOptions.of(VideoStyle.PERSON_INTRO, List.of()), null)).isEmpty();
    }

    @Test
    void 메뉴_영상은_외관_사진을_쓰지_않는다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", VideoStyle.MENU_FOCUS, null, sb -> {});
        assertThat(errors).anyMatch(e -> e.contains("3번은 가게 외관 사진입니다"));
        // 외관으로 끝내라는 기존 규칙은 끈다
        assertThat(errors).noneMatch(e -> e.contains("마지막 장면은 외관 사진으로 끝내세요"));
    }

    @Test
    void 메뉴_영상은_한_상_사진으로_끝낸다() throws IOException {
        MenuData withSpread = mutateMenu("01-donkatsu", d -> d.withArray("photos").addObject()
                .put("index", 4).put("source", "generated").put("kind", "food").putNull("menu_ref")
                .put("description", "AI가 만든 메뉴 조합 이미지: full table spread"));
        Storyboard sb = mutate("01-donkatsu", s -> scene(s, 4).put("image_index", 2));

        List<String> errors = validator.validate(sb, withSpread, VideoOptions.of(VideoStyle.MENU_FOCUS, List.of()), null);
        assertThat(errors).anyMatch(e -> e.contains("한 상에 차린 사진(4번)으로 끝내세요"));

        Storyboard onSpread = mutate("01-donkatsu", s -> scene(s, 4).put("image_index", 4).put("motion", "ai")
                .put("video_prompt", "slow top-down pan across the full table spread of pork cutlets, steam rising, warm light, cozy mood"));
        assertThat(validator.validate(onSpread, withSpread, VideoOptions.of(VideoStyle.MENU_FOCUS, List.of()), null))
                .noneMatch(e -> e.contains("한 상"));
    }

    @Test
    void 메뉴_영상은_나레이션을_짧게() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", VideoStyle.MENU_FOCUS, null, sb -> {
            line(sb, 1, "겉은 바삭하고 속은 촉촉한 로스카츠 12,000원에");
            line(sb, 2, "치즈가 길게 쭉 늘어나는 치즈카츠까지 있고");
            line(sb, 3, "두툼한 등심을 매일 직접 손질해서");
        });
        assertThat(errors).anyMatch(e -> e.contains("나레이션을 모두 합쳐"));
    }

    @Test
    void 신메뉴_영상은_사장님_한마디의_숫자를_쓸_수_있다() throws IOException {
        Consumer<ObjectNode> sale = sb -> line(sb, 2, "이번 달은 20% 할인까지 하는데");
        String appeal = "신메뉴 치즈카츠 출시 기념 이번 달 20% 할인";

        assertThat(errorsOf("01-donkatsu", VideoStyle.NEWS_PROMO, appeal, sale)).noneMatch(e -> e.contains("숫자가 있습니다"));
        // 한마디에 없는 숫자나, 다른 스타일에서는 그대로 막는다
        assertThat(errorsOf("01-donkatsu", VideoStyle.NEWS_PROMO, "신메뉴 출시", sale)).anyMatch(e -> e.contains("숫자가 있습니다"));
        assertThat(errorsOf("01-donkatsu", VideoStyle.PERSON_INTRO, appeal, sale)).anyMatch(e -> e.contains("숫자가 있습니다"));
        assertThat(StoryboardValidator.numberTokens("1+1 이벤트, 20 % 할인, 2.5배")).isEqualTo(Set.of("1", "20%", "2.5"));
    }

    // ── helpers ──

    private List<String> errorsOf(String sample, VideoStyle style, String appeal, Consumer<ObjectNode> mutation) throws IOException {
        return validator.validate(mutate(sample, mutation), menu(sample), VideoOptions.of(style, List.of()), appeal);
    }

    private Storyboard mutate(String sample, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(read(sample + ".storyboard.json"));
        mutation.accept(node);
        return MAPPER.treeToValue(node, Storyboard.class);
    }

    private MenuData mutateMenu(String sample, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(read(sample + ".menu.json"));
        mutation.accept(node);
        return MAPPER.treeToValue(node, MenuData.class);
    }

    private static ObjectNode scene(ObjectNode sb, int i) {
        return (ObjectNode) sb.get("scenes").get(i);
    }

    private static void line(ObjectNode sb, int i, String text) {
        scene(sb, i).put("narration", text);
        ((ObjectNode) scene(sb, i).get("caption")).put("text", text);
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
