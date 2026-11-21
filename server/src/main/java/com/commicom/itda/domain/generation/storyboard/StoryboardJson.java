package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

/** 장면 JSON·메뉴 데이터 ↔ 문자열 변환. LLM 응답의 ```json 코드블록이나 앞뒤 설명도 걷어 낸다. */
public final class StoryboardJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectMapper PRETTY = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** 스토리보드 프롬프트에만 있는 문구 — 응답 대신 프롬프트를 붙여 넣었는지 알아보는 데 씀 */
    private static final String PROMPT_MARKER = "[출력 형식 예시]";

    private StoryboardJson() {}

    public static Storyboard parseStoryboard(String raw) throws JsonProcessingException {
        if (raw != null && raw.contains(PROMPT_MARKER)) {
            throw new JsonMappingException(null, "프롬프트 글이 들어왔습니다. 프롬프트를 Claude에 보내고 받은 응답(장면 JSON)을 넣으세요.");
        }
        JsonNode node = MAPPER.readTree(extractObject(raw));
        if (node == null || !node.isObject()) {
            throw new JsonMappingException(null, "장면 JSON 객체가 아닙니다.");
        }
        if (!node.has("scenes") && !node.has("schema_version")) {
            // {"storyboard": {...}}처럼 한 번 감싼 응답은 벗겨 낸다
            if (node.size() == 1 && node.elements().next().has("scenes")) {
                node = node.elements().next();
            } else if (node.has("menus") || node.has("store_name")) {
                throw new JsonMappingException(null, "메뉴 데이터가 들어왔습니다. Claude가 만든 장면 JSON을 넣으세요.");
            } else {
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                throw new JsonMappingException(null, "장면 JSON이 아닙니다. 최상위에 scenes가 없고 " + keys + " 키만 있습니다.");
            }
        }
        return MAPPER.treeToValue(node, Storyboard.class);
    }

    public static MenuData parseMenu(String raw) throws JsonProcessingException {
        return MAPPER.readValue(extractObject(raw), MenuData.class);
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 변환 실패", e);
        }
    }

    /** 줄바꿈은 OS와 상관없이 LF (Jackson 기본 들여쓰기는 Windows에서 CRLF) */
    public static String toPrettyJson(Object value) {
        try {
            return PRETTY.writeValueAsString(value).replace("\r\n", "\n");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 변환 실패", e);
        }
    }

    /** 첫 '{'부터 마지막 '}'까지 잘라 낸다 (코드블록·앞뒤 문장 제거) */
    static String extractObject(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) return raw.trim();
        return raw.substring(start, end + 1);
    }
}
