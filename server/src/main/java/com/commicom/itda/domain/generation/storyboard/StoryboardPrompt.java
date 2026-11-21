package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.store.entity.StoreCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 스토리보드 생성·수정 요청 프롬프트를 만든다 (prompts/storyboard*.txt) */
@Component
public class StoryboardPrompt {

    /** 프롬프트를 바꾸면 올린다 — 결과 JSON의 meta.prompt_version에 남음 */
    public static final String VERSION = "v17";

    private final String template;
    private final String fixTemplate;
    private final java.util.Map<VideoStyle, String> styleGuides = new java.util.concurrent.ConcurrentHashMap<>();

    public StoryboardPrompt(@Value("classpath:prompts/storyboard.txt") Resource template,
                            @Value("classpath:prompts/storyboard-fix.txt") Resource fixTemplate) {
        this.template = read(template);
        this.fixTemplate = read(fixTemplate);
    }

    public String build(MenuData menu, String appeal) {
        return build(menu, appeal, null);
    }

    /** @param address 가게 주소. 제목 윗줄의 동네 이름을 여기서만 고르게 한다 (없으면 null) */
    public String build(MenuData menu, String appeal, String address) {
        return build(menu, appeal, address, VideoOptions.NONE);
    }

    /**
     * @param options 생성 느낌. 스타일 규칙(prompts/storyboard-style-*.txt)과 강조 포인트를 [영상 스타일]로 붙인다.
     *                고르지 않았으면 기존 프롬프트와 글자 하나 다르지 않다
     */
    public String build(MenuData menu, String appeal, String address, VideoOptions options) {
        VideoOptions o = options == null ? VideoOptions.NONE : options;
        return template
                .replace("{styleNotice}", styleNotice(o))
                .replace("{styleGuide}", styleGuide(o))
                // 영상 첫 자막·마지막 자막에 그대로 들어가므로 "[샘플] " 앞말은 뺀다 (검사도 같은 이름으로)
                .replace("{storeName}", orNone(StoryboardValidator.storeLabel(menu.storeName())))
                .replace("{category}", categoryLabel(menu.category()))
                .replace("{address}", orNone(address))
                .replace("{appeal}", orNone(appeal))
                .replace("{scenePhotoRules}", scenePhotoRules(menu))
                .replace("{menuJson}", StoryboardJson.toPrettyJson(menu.withoutImageRequests()));
    }

    private static String styleNotice(VideoOptions o) {
        if (o.style() == null || !o.style().hasOwnPrompt()) return "";
        return "이번 영상은 \"" + o.style().label() + "\"입니다. 아래 [영상 스타일] 규칙이 다른 규칙과 부딪히면 [영상 스타일] 규칙을 따르세요.\n";
    }

    private String styleGuide(VideoOptions o) {
        StringBuilder sb = new StringBuilder();
        if (o.style() != null && o.style().hasOwnPrompt()) {
            sb.append(styleGuides.computeIfAbsent(o.style(), s -> StyleImages.read("prompts/storyboard-style-" + s.name() + ".txt")));
        }
        if (!o.emphases().isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append("[강조 포인트]\n사장님이 고른 강조 포인트입니다. 위 규칙(특히 장면 4~6개, 합계 15초, 글자 수)을 지키는 범위에서 맞춰 주세요.");
            o.emphases().stream().sorted().forEach(e -> sb.append("\n- ").append(e.label()).append(": ").append(e.hint()));
        }
        return sb.isEmpty() ? "" : "\n" + sb + "\n";
    }

    private String scenePhotoRules(MenuData menu) {
        List<MenuData.Photo> photos = menu.photos() == null ? List.of() : menu.photos();
        List<MenuData.Photo> food = photos.stream().filter(p -> p != null && "food".equals(p.kind())).toList();
        List<Integer> menuIndices = food.stream().map(MenuData.Photo::index).toList();
        List<Integer> detailIndices = food.stream().filter(MenuData.Photo::isUpload).map(MenuData.Photo::index).toList();
        List<String> prices = food.stream().flatMap(p -> menu.findMenu(p.menuRef())
                .filter(m -> m.price() != null)
                .map(m -> String.format(Locale.ROOT, "%d번 사진의 %s %,d원", p.index(), m.name(), m.price()))
                .stream()).toList();
        String sceneRules = menuIndices.isEmpty()
                ? "음식 사진이 없으므로 가운데 장면은 전부 review로 구성합니다. 사진과 사장님 설명을 쓰고, 없던 손님 후기는 만들지 마세요."
                : "menu 장면은 사진 " + menuIndices + "만 사용합니다. " + (detailIndices.isEmpty()
                        ? "detail 장면은 생략합니다."
                        : "detail 장면은 사진 " + detailIndices + "만 사용합니다.");
        String priceRules = prices.isEmpty()
                ? "가격이 확인된 메뉴와 연결된 사진이 없습니다. 제목·대사·자막에 가격을 쓰지 말고 모든 menu_ref는 null로 둡니다."
                : "가격은 " + String.join(", ", prices) + " 중 하나만 선택해 그 사진을 쓰는 장면에서만 넣을 수 있습니다.";
        return sceneRules + "\n" + priceRules;
    }

    public String buildFix(String originalPrompt, String previousOutput, List<String> errors) {
        String errorList = errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n"));
        return fixTemplate
                .replace("{originalPrompt}", originalPrompt)
                .replace("{previousOutput}", previousOutput == null ? "(없음)" : previousOutput.trim())
                .replace("{errors}", errorList);
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
