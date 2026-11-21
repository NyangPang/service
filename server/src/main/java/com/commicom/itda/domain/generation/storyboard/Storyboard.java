package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 장면 JSON (docs/storyboard-schema.md).
 * LLM 출력을 그대로 받으므로 enum 값도 문자열로 둔다 — 잘못된 값은 {@link StoryboardValidator}가 오류로 알려 줌.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Storyboard(
        @JsonProperty("schema_version") String schemaVersion,
        String title,
        @JsonProperty("total_duration") Double totalDuration,
        /** 영상 한 편의 주제 — emotional / value / trendy / expert / fresh. 장면 전환이 이걸 따른다 */
        String theme,
        Style style,
        Bgm bgm,
        List<Scene> scenes,
        Meta meta
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Style(
            String mood,
            String font,
            @JsonProperty("main_color") String mainColor
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bgm(String mood) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scene(
            Integer id,
            Integer order,
            String type,
            Double duration,
            @JsonProperty("image_index") Integer imageIndex,
            String motion,
            @JsonProperty("video_prompt") String videoPrompt,
            @JsonProperty("menu_ref") String menuRef,
            String narration,
            Caption caption
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Caption(
            String text,
            String position,
            String emphasis
    ) {}

    /** 서버가 채우는 값 (LLM 출력 아님) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            String category,
            @JsonProperty("prompt_version") String promptVersion,
            /** 영상 템플릿 왼쪽 위 태그에 띄울 가게 이름. 없으면 생략 */
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("store_name") String storeName
    ) {
        public Meta(String category, String promptVersion) {
            this(category, promptVersion, null);
        }

        public Meta withStoreName(String name) {
            return new Meta(category, promptVersion, blankToNull(name));
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s.strip();
        }
    }

    public Storyboard withMeta(Meta meta) {
        return new Storyboard(schemaVersion, title, totalDuration, theme, style, bgm, scenes, meta);
    }
}
