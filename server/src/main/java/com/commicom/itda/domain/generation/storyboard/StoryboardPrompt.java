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

/** 스토리보드 생성·수정 요청 프롬프트를 만든다 (prompts/storyboard*.txt) */
@Component
public class StoryboardPrompt {

    /** 프롬프트를 바꾸면 올린다 — 결과 JSON의 meta.prompt_version에 남음 */
    public static final String VERSION = "v13";

    private final String template;
    private final String fixTemplate;

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
        return template
                // 영상 첫 자막·마지막 자막에 그대로 들어가므로 "[샘플] " 앞말은 뺀다 (검사도 같은 이름으로)
                .replace("{storeName}", orNone(StoryboardValidator.storeLabel(menu.storeName())))
                .replace("{category}", categoryLabel(menu.category()))
                .replace("{address}", orNone(address))
                .replace("{appeal}", orNone(appeal))
                .replace("{menuJson}", StoryboardJson.toPrettyJson(menu.withoutImageRequests()));
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
