package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 가운데 장면 나레이션은 ㄴ 받침(다른·튀긴·찾는·하이볼은·원)으로 끝나지 않고 이어 주는 말로 끝난다 */
class NarrationEndingTest {

    private static final Path SAMPLES = Path.of("docs/storyboard-samples");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final StoryboardValidator validator = new StoryboardValidator();

    @ParameterizedTest
    @ValueSource(strings = {"맛이 다른", "바삭하게 튀긴", "샐러드까지 곁들인", "손님들이 다시 찾는", "여기 하이볼은", "두툼한 로스카츠 12,000원"})
    void 가운데_장면이_ㄴ_받침으로_끝나면_알려준다(String text) throws IOException {
        assertThat(errorsWithScene2(text)).anyMatch(e -> e.startsWith("[장면 id=2]") && e.contains("ㄴ 받침으로 끝나"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"맛이 다르고", "바삭하게 튀겨서", "샐러드까지 나오고", "양도 좋고", "가격이 착한데", "주문이 되는데", "디저트까지", "로스카츠가 12,000원이라"})
    void 이어_주는_말로_끝나면_통과한다(String text) throws IOException {
        assertThat(errorsWithScene2(text)).noneMatch(e -> e.contains("ㄴ 받침으로 끝나"));
    }

    @Test
    void 마지막_장면은_검사하지_않는다() {
        // 마지막 장면은 "~예요." 같은 서술어로 끝나고, 받침 규칙은 가운데 장면에만
        assertThat(StoryboardValidator.endsWithNieun("카츠하우스예요")).isFalse();
        assertThat(StoryboardValidator.endsWithNieun("다른")).isTrue();
        assertThat(StoryboardValidator.endsWithNieun("12000원")).isTrue();
        assertThat(StoryboardValidator.endsWithNieun("까지")).isFalse();
        assertThat(StoryboardValidator.endsWithNieun("")).isFalse();
    }

    /** 01-donkatsu 둘째 장면(3.5초, 로스카츠 사진)의 나레이션·자막을 바꿔 검사 */
    private List<String> errorsWithScene2(String text) throws IOException {
        ObjectNode sb = (ObjectNode) MAPPER.readTree(Files.readString(SAMPLES.resolve("01-donkatsu.storyboard.json")));
        ObjectNode scene = (ObjectNode) sb.get("scenes").get(1);
        scene.put("narration", text);
        ((ObjectNode) scene.get("caption")).put("text", text);
        MenuData menu = StoryboardJson.parseMenu(Files.readString(SAMPLES.resolve("01-donkatsu.menu.json")));
        return validator.validate(MAPPER.treeToValue(sb, Storyboard.class), menu);
    }
}
