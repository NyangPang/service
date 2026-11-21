package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;

/**
 * 장면 JSON (A 파트 출력). 형식은 server/docs/storyboard-schema.md.
 * B 파트는 A 파트 검증을 통과한 값만 받는다고 보고 최소한만 확인한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Storyboard(
        @JsonProperty("schema_version") String schemaVersion,
        String title,
        @JsonProperty("total_duration") double totalDuration,
        /** 영상 주제 (emotional / value / trendy / expert / fresh). 없으면 페이드 전환 */
        String theme,
        Style style,
        Bgm bgm,
        List<Scene> scenes,
        JsonNode meta
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Style(String mood, String font, @JsonProperty("main_color") String mainColor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bgm(String mood) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scene(
            int id,
            int order,
            String type,
            double duration,
            @JsonProperty("image_index") int imageIndex,
            String motion,
            @JsonProperty("video_prompt") String videoPrompt,
            @JsonProperty("menu_ref") String menuRef,
            String narration,
            Caption caption
    ) {
        public boolean hasNarration() {
            return narration != null && !narration.isBlank();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Caption(String text, String position, String emphasis) {}

    /** 템플릿 왼쪽 위 태그에 띄울 가게 이름 (서버가 meta.store_name에 채움). 없으면 null */
    public String storeName() {
        if (meta == null) return null;
        String n = meta.path("store_name").asText("");
        return n.isBlank() ? null : n.strip();
    }

    /** 화면 배치 템플릿: meta.template 이 있으면 그것, 없으면 theme(주제)으로 고름 */
    public VideoTemplate template() {
        VideoTemplate named = VideoTemplate.named(meta == null || !meta.hasNonNull("template") ? null : meta.get("template").asText());
        return named != null ? named : VideoTemplate.forTheme(theme);
    }

    /** 재생 순서(order)대로 정렬한 장면 */
    public List<Scene> orderedScenes() {
        return scenes.stream().sorted(Comparator.comparingInt(Scene::order)).toList();
    }
}
