package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.store.entity.StoreCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 메뉴 추출 요청 프롬프트를 만든다 (prompts/menu-extraction*.txt) */
@Component
public class MenuExtractionPrompt {

    public static final String VERSION = "v6";

    /** 사장님이 이미지를 올린 칸 — 모델이 kind를 고를 때 참고만 함 */
    public enum Slot {
        PHOTO("음식·가게 사진"),
        MENU_BOARD("메뉴판 사진");

        private final String label;

        Slot(String label) {
            this.label = label;
        }
    }

    private final String template;
    private final String fixTemplate;

    public MenuExtractionPrompt(@Value("classpath:prompts/menu-extraction.txt") Resource template,
                                @Value("classpath:prompts/menu-extraction-fix.txt") Resource fixTemplate) {
        this.template = read(template);
        this.fixTemplate = read(fixTemplate);
    }

    /**
     * slots는 첨부 순서 그대로. 사진(PHOTO)만 index를 받고(0부터), 메뉴판은 메뉴 읽기에만 쓴다.
     * 사진이 메뉴판보다 앞에 와야 index와 첨부 순서가 어긋나지 않는다 ({@link MenuExtractor}가 정렬함).
     */
    public String build(String storeName, String category, List<Slot> slots, String extraInfo) {
        return build(storeName, category, slots, extraInfo, StyleImages.NONE);
    }

    /** @param styleImages 영상 스타일 때문에 더 만들 이미지 규칙 (없으면 {@link StyleImages#NONE}) */
    public String build(String storeName, String category, List<Slot> slots, String extraInfo, StyleImages styleImages) {
        int[] photoIndex = {0};
        String photoList = slots.isEmpty()
                ? "(첨부 이미지 없음)"
                : IntStream.range(0, slots.size())
                        .mapToObj(i -> "- " + (i + 1) + "번째 이미지: " + (slots.get(i) == Slot.PHOTO
                                ? "사진 index " + (photoIndex[0]++) + " (사장님이 '" + Slot.PHOTO.label + "' 칸에 올림)"
                                : "메뉴판 (메뉴 읽기에만 씀, photos에 넣지 않음)"))
                        .collect(Collectors.joining("\n"));
        return template
                .replace("{storeName}", orNone(storeName))
                .replace("{category}", categoryLabel(category))
                .replace("{photoList}", photoList)
                .replace("{extraInfo}", orNone(extraInfo))
                .replace("{styleImages}", styleImages == null || styleImages.guide().isBlank() ? "" : "\n" + styleImages.guide() + "\n");
    }

    public String buildFix(String originalPrompt, String previousOutput, List<String> errors) {
        String errorList = errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n"));
        return fixTemplate
                .replace("{originalPrompt}", originalPrompt)
                .replace("{previousOutput}", previousOutput == null ? "(없음)" : previousOutput.trim())
                .replace("{errors}", errorList);
    }

    String buildMenuRead(String storeName, String category) {
        return """
                가게 %s (%s)의 첨부 메뉴판에서 읽은 메뉴를 JSON 객체 하나로 출력하세요.
                형식: {"menus":[{"name":"아메리카노","price_text":"4.5","price":4500,"description":null,"is_signature":false}]}
                메뉴 이름과 설명은 메뉴판에 적힌 내용을 사용하세요. 가격 원문은 price_text, 원 단위 정수는 price에 넣으세요.
                천 원 단위로 줄인 가격은 4.5를 4500으로 읽으세요. 확신할 수 없는 가격은 null로 두세요.
                같은 이름은 기본 옵션의 가격으로 한 번만 적고, 대표·추천 표시가 있을 때만 is_signature를 true로 두세요.
                읽을 수 있는 메뉴가 없으면 {"menus":[]}를 반환하세요. 코드블록이나 부연 설명은 붙이지 마세요.
                """.formatted(orNone(storeName), categoryLabel(category));
    }

    private static String categoryLabel(String code) {
        if (code == null || code.isBlank()) return "정보 없음";
        try {
            return StoreCategory.valueOf(code).getDescription();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "(없음)" : value.trim();
    }

    /** 줄바꿈은 LF로 통일 (Windows에서 git이 CRLF로 체크아웃해도 프롬프트가 같게) */
    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 로딩 실패: " + resource, e);
        }
    }
}
