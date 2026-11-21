package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 수정 요청 변경분 { scene_id, field, before, after } 를 장면 JSON에 적용한다. (스키마 5절)
 * scene_id 가 없으면 최상위 필드(title, style.mood, bgm.mood …)를 바꾼다.
 * field 는 점으로 이어진 경로 (예: "caption.text").
 */
public final class StoryboardChanges {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
            @JsonProperty("scene_id") Integer sceneId,
            String field,
            JsonNode before,
            JsonNode after
    ) {}

    private StoryboardChanges() {
    }

    /** root 를 직접 바꾼다. 반환값은 경고 목록 (before 가 현재 값과 다를 때 등) */
    public static List<String> apply(ObjectNode root, List<Change> changes) {
        List<String> warnings = new ArrayList<>();
        for (Change c : changes) {
            ObjectNode target = c.sceneId() == null ? root : findScene(root, c.sceneId());
            if (target == null) {
                warnings.add("장면 " + c.sceneId() + " 없음 → 건너뜀");
                continue;
            }
            String[] path = c.field().split("\\.");
            ObjectNode parent = target;
            for (int i = 0; i < path.length - 1; i++) {
                parent = parent.with(path[i]);
            }
            String leaf = path[path.length - 1];
            JsonNode current = parent.get(leaf);
            if (c.before() != null && !c.before().equals(current)) {
                warnings.add("장면 " + c.sceneId() + " " + c.field() + ": before(" + c.before() + ")가 현재 값(" + current + ")과 다름 — after로 덮어씀");
            }
            parent.set(leaf, c.after());
        }
        return warnings;
    }

    private static ObjectNode findScene(ObjectNode root, int sceneId) {
        for (JsonNode scene : root.withArray("scenes")) {
            if (scene.path("id").asInt(-1) == sceneId) return (ObjectNode) scene;
        }
        return null;
    }
}
