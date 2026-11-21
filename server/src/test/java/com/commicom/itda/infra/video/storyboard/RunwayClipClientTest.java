package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunwayClipClientTest {

    @Test
    void 화면_전환은_처음_끝_화면을_둘_다_받는_모델로_요청() {
        // Gen-4 Turbo는 promptImage position "first"만 받아서 끝 화면(가게 외관)을 정할 수 없다
        JsonNode body = RunwayClipClient.transitionBody(new ObjectMapper(), "data:first", "data:last", "pull back to the storefront");

        assertThat(body.path("model").asText()).isEqualTo("gemini_omni_flash_1.2");
        assertThat(body.path("duration").asInt()).isEqualTo(5);
        assertThat(body.path("ratio").asText()).isEqualTo("720:1280");
        assertThat(body.has("audio")).isFalse();
        assertThat(body.path("promptText").asText()).isEqualTo("pull back to the storefront");
        JsonNode images = body.path("promptImage");
        assertThat(images).hasSize(2);
        assertThat(images.get(0).path("position").asText()).isEqualTo("first");
        assertThat(images.get(0).path("uri").asText()).isEqualTo("data:first");
        assertThat(images.get(1).path("position").asText()).isEqualTo("last");
        assertThat(images.get(1).path("uri").asText()).isEqualTo("data:last");
    }

    @Test
    void 전환은_3초와_4초를_지원하고_API_범위_밖의_길이는_막는다() {
        ObjectMapper json = new ObjectMapper();
        assertThat(RunwayClipClient.transitionBody(json, "a", "b", "p", 3).path("duration").asInt()).isEqualTo(3);
        assertThat(RunwayClipClient.transitionBody(json, "a", "b", "p", 4).path("duration").asInt()).isEqualTo(4);
        assertThatThrownBy(() -> RunwayClipClient.transitionBody(json, "a", "b", "p", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunwayClipClient.transitionBody(json, "a", "b", "p", 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 프롬프트가_비면_기본_카메라_이동으로() {
        // Gemini는 promptText가 필수
        JsonNode body = RunwayClipClient.transitionBody(new ObjectMapper(), "a", "b", " ");
        assertThat(body.path("promptText").asText()).isNotBlank();
    }
}
