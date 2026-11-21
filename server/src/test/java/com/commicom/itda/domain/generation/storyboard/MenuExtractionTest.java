package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt.Slot;
import com.commicom.itda.infra.ai.AiImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuExtractionTest {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MenuExtractionValidator validator = new MenuExtractionValidator();
    private final MenuExtractionPrompt prompt = new MenuExtractionPrompt(
            new ClassPathResource("prompts/menu-extraction.txt"),
            new ClassPathResource("prompts/menu-extraction-fix.txt"));

    // ── 검증 ──

    @ParameterizedTest
    @CsvSource({"01-donkatsu, 4", "02-cafe, 3", "03-izakaya, 2"})
    void 더미_메뉴_데이터는_통과한다(String sample, int imageCount) throws IOException {
        assertThat(validator.validate(menu(sample), imageCount)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12,000|12000", "12,000원|12000", "12000|12000", "₩ 9,500|9500",
            "4.0|4000", "4.5|4500", "12|12000", "1만2천원|12000", "5천원|5000", "2만|20000"})
    void 가격_글자를_원으로_바꾼다(String text, int expected) {
        assertThat(MenuExtractionValidator.parsePriceText(text)).isEqualTo(expected);
    }

    @Test
    void 애매한_가격_글자는_검사하지_않는다() {
        assertThat(MenuExtractionValidator.parsePriceText("시가")).isNull();
        assertThat(MenuExtractionValidator.parsePriceText("4.0/4.5")).isNull();
    }

    @Test
    void price_text와_price가_다르면_오류() throws IOException {
        List<String> errors = errorsOf("02-cafe", 3, d -> menuAt(d, 1).put("price", 35000));
        assertThat(errors).anyMatch(e -> e.contains("\"3.5\"는 3,500원인데 price가 35,000원"));
    }

    @Test
    void 가격으로_보기_어려운_값은_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> menuAt(d, 0).put("price", 12345).putNull("price_text"));
        assertThat(errors).anyMatch(e -> e.contains("12345원은 가격으로 보기 어렵습니다"))
                .anyMatch(e -> e.contains("price_text에 그 가격의 원래 글자"));
    }

    @Test
    void 메뉴_이름이_겹치면_오류() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> menuAt(d, 1).put("name", "로스카츠"));
        assertThat(errors).anyMatch(e -> e.contains("같은 이름의 메뉴가 두 번"));
    }

    @Test
    void 사진_개수와_번호가_맞아야_한다() throws IOException {
        assertThat(validator.validate(menu("01-donkatsu"), 5))
                .anyMatch(e -> e.contains("photos가 4개입니다"))
                .anyMatch(e -> e.contains("[사진 index=4] 이 사진이 photos에 없습니다"));

        List<String> errors = errorsOf("01-donkatsu", 4, d -> photoAt(d, 3).put("index", 2));
        assertThat(errors).anyMatch(e -> e.contains("같은 index가 두 번"))
                .anyMatch(e -> e.contains("[사진 index=3] 이 사진이 photos에 없습니다"));
    }

    @Test
    void menu_ref_규칙() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> {
            photoAt(d, 0).put("menu_ref", "로스까스");     // 없는 메뉴
            photoAt(d, 3).put("menu_ref", "로스카츠");     // 외관 사진에 메뉴 연결
        });
        assertThat(errors).anyMatch(e -> e.contains("\"로스까스\"는 menus에 없습니다"))
                .anyMatch(e -> e.contains("exterior 사진의 menu_ref는 null"));
    }

    @Test
    void kind와_source_값을_검사한다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> photoAt(d, 1).put("kind", "dish").put("source", "generated"));
        assertThat(errors).anyMatch(e -> e.contains("kind 값 \"dish\""))
                .anyMatch(e -> e.contains("source는 \"upload\""));
    }

    @Test
    void 메뉴판이_없으면_빈_메뉴도_괜찮다() throws IOException {
        List<String> errors = errorsOf("03-izakaya", 2, d -> {
            d.putArray("menus");
            d.putArray("image_requests"); // 그릴 메뉴를 모르니 사진이 모자라도 만들지 않음
            photoAt(d, 0).putNull("menu_ref");
            photoAt(d, 1).putNull("menu_ref");
        });
        assertThat(errors).isEmpty();
    }

    // ── 이미지 생성 요청 (image_requests) ──

    private static final String GOOD_PROMPT = "thick golden pork cutlet sliced on a wooden tray with shredded cabbage, "
            + "warm side lighting, close-up, realistic food photography, vertical composition";

    /** 돈가스 더미(사진 4장)에서 음식 사진 3장을 메뉴판 사진으로 바꿔 "쓸 수 있는 사진 1장(외관)" 상황을 만든다 → 2장 만들어야 함 */
    private static void noFoodPhotos(ObjectNode d) {
        for (int i = 0; i < 3; i++) photoAt(d, i).put("kind", "menu_board").putNull("menu_ref");
    }

    private static void request(ObjectNode d, String menuRef, String prompt) {
        d.withArray("image_requests").addObject().put("menu_ref", menuRef).put("prompt", prompt);
    }

    @Test
    void 쓸_수_있는_사진이_3장보다_적으면_모자란_만큼_요청해야_한다() throws IOException {
        assertThat(errorsOf("01-donkatsu", 4, MenuExtractionTest::noFoodPhotos))
                .anyMatch(e -> e.contains("쓸 수 있는 사진(메뉴판 제외)이 1장") && e.contains("정확히 2개"));
    }

    @Test
    void 모자란_만큼_요청하면_통과한다_메뉴_조합도_된다() throws IOException {
        assertThat(errorsOf("01-donkatsu", 4, d -> {
            noFoodPhotos(d);
            request(d, "로스카츠", GOOD_PROMPT);
            request(d, null, GOOD_PROMPT.replace("pork cutlet", "pork cutlet and cheese cutlet side by side"));
        })).isEmpty();
    }

    @Test
    void 사진이_3장_이상이면_이미지를_요청하지_않는다() throws IOException {
        assertThat(errorsOf("01-donkatsu", 4, d -> request(d, "로스카츠", GOOD_PROMPT)))
                .anyMatch(e -> e.contains("정확히 0개"));
    }

    @Test
    void 메뉴가_없으면_이미지를_요청하지_않는다() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> {
            noFoodPhotos(d);
            d.putArray("menus");
            request(d, "로스카츠", GOOD_PROMPT);
        });
        assertThat(errors).anyMatch(e -> e.contains("menus가 비어 있으면 image_requests도 빈 배열"));
    }

    @Test
    void image_requests가_빠지면_오류() throws IOException {
        assertThat(errorsOf("01-donkatsu", 4, d -> d.remove("image_requests")))
                .anyMatch(e -> e.contains("image_requests가 없습니다"));
    }

    @Test
    void 이미지_요청_메뉴_규칙() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> {
            noFoodPhotos(d);
            request(d, "로스카츠", GOOD_PROMPT);
            request(d, "로스카츠", GOOD_PROMPT);
            request(d, "로스까스", GOOD_PROMPT);
        });
        assertThat(errors).anyMatch(e -> e.contains("정확히 2개") && e.contains("현재 3개"))
                .anyMatch(e -> e.contains("\"로스카츠\" 하나만 담은 이미지를 두 번 요청"))
                .anyMatch(e -> e.contains("menu_ref \"로스까스\"는 menus에 없습니다"));
    }

    @Test
    void 이미지_프롬프트_금지어() throws IOException {
        List<String> errors = errorsOf("01-donkatsu", 4, d -> {
            noFoodPhotos(d);
            request(d, "로스카츠", "chef hand holding a pork cutlet under a neon sign with the store logo");
            request(d, "히레카츠", "바삭한 히레카츠");
        });
        assertThat(errors).anyMatch(e -> e.contains("[image_requests[0]] prompt에 글자·간판·로고"))
                .anyMatch(e -> e.contains("[image_requests[0]] prompt에 사람·신체") && e.contains("\"chef\", \"hand\""))
                .anyMatch(e -> e.contains("[image_requests[0]] prompt에 손이 있어야 하는 동작(\"holding\")"))
                .anyMatch(e -> e.contains("[image_requests[1]] prompt는 영어로만"));
    }

    @Test
    void 이미지_프롬프트는_40단어_이내() throws IOException {
        String longPrompt = String.join(" ", java.util.Collections.nCopies(41, "crispy"));
        assertThat(errorsOf("01-donkatsu", 4, d -> {
            noFoodPhotos(d);
            request(d, "로스카츠", longPrompt);
        })).anyMatch(e -> e.contains("prompt가 41단어입니다"));
    }

    @Test
    void 장면_JSON_프롬프트에는_이미지_요청을_넣지_않는다() throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(Files.readString(SAMPLES.resolve("01-donkatsu.menu.json")));
        request(node, "로스카츠", GOOD_PROMPT);
        MenuData data = MAPPER.treeToValue(node, MenuData.class);

        assertThat(StoryboardJson.toPrettyJson(data)).contains("image_requests");
        assertThat(StoryboardJson.toPrettyJson(data.withoutImageRequests())).doesNotContain("image_requests");
    }

    // ── 프롬프트 ──

    @Test
    void 이미지_순서와_올린_칸이_프롬프트에_들어간다() {
        String text = prompt.build("골목카페", "CAFE_BAKERY_PUB", List.of(Slot.PHOTO, Slot.PHOTO, Slot.MENU_BOARD), null);
        assertThat(text).contains("가게 이름: 골목카페")
                .contains("업종: 카페·베이커리·주점")
                .contains("- 1번째 이미지: 사진 index 0 (사장님이 '음식·가게 사진' 칸에 올림)")
                .contains("- 2번째 이미지: 사진 index 1 (사장님이 '음식·가게 사진' 칸에 올림)")
                .contains("- 3번째 이미지: 메뉴판 (메뉴 읽기에만 씀, photos에 넣지 않음)")
                .doesNotContain("index 2")
                .contains("[추가 정보]\n(없음)")
                .contains("[image_requests 작성법]")
                .doesNotContain("{photoList}", "{extraInfo}", "{storeName}", "{category}")
                .doesNotContain("\r"); // Windows에서 CRLF로 체크아웃돼도 줄바꿈은 LF
    }

    // ── 이미지 준비 ──

    @Test
    void 큰_이미지는_줄여서_JPEG로_보낸다() throws IOException {
        BufferedImage big = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(big, "png", png);

        Media media = AiImage.toMedia(png.toByteArray());

        assertThat(media.getMimeType().toString()).isEqualTo("image/jpeg");
        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(media.getDataAsByteArray()));
        assertThat(sent.getWidth()).isEqualTo(1568);
        assertThat(sent.getHeight()).isEqualTo(1045);
    }

    @Test
    void 실제_메뉴판_사진도_변환된다() throws IOException {
        Media media = AiImage.toMedia(Files.readAllBytes(Path.of("test/menu1.png")));
        assertThat(media.getDataAsByteArray()).isNotEmpty();
    }

    @Test
    void 자바가_못_여는_WebP는_그대로_보낸다() throws IOException {
        byte[] webp = Files.readAllBytes(Path.of("test/커피1.png")); // 확장자는 png지만 실제로는 WebP
        Media media = AiImage.toMedia(webp);
        assertThat(media.getMimeType().toString()).isEqualTo("image/webp");
        assertThat(media.getDataAsByteArray()).isEqualTo(webp);
    }

    @Test
    void 이미지가_아니면_알려준다() {
        assertThatThrownBy(() -> AiImage.toMedia("not an image".getBytes()))
                .hasMessageContaining("읽을 수 없는 이미지 형식");
    }

    // ── helpers ──

    private List<String> errorsOf(String sample, int imageCount, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode node = (ObjectNode) MAPPER.readTree(Files.readString(SAMPLES.resolve(sample + ".menu.json")));
        mutation.accept(node);
        return validator.validate(MAPPER.treeToValue(node, MenuData.class), imageCount);
    }

    private static ObjectNode menuAt(ObjectNode d, int i) {
        return (ObjectNode) d.get("menus").get(i);
    }

    private static ObjectNode photoAt(ObjectNode d, int i) {
        return (ObjectNode) d.get("photos").get(i);
    }

    private static MenuData menu(String sample) throws IOException {
        return StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve(sample + ".menu.json")));
    }
}
