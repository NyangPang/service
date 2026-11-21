package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunwayClipClientTest {

    @Test
    void 화면_전환은_처음_끝_화면을_둘_다_받는_모델로_요청() {
        // Gen-4 Turbo는 promptImage position "first"만 받아서 끝 화면(가게 외관)을 정할 수 없다
        JsonNode body = RunwayClipClient.transitionBody(new ObjectMapper(), "data:first", "data:last", "pull back to the storefront");

        assertThat(body.path("model").asText()).isEqualTo("kling2.5_turbo_pro");
        assertThat(body.path("duration").asInt()).isEqualTo(5);   // Kling은 5 또는 10만
        assertThat(body.has("ratio")).isFalse();                   // Kling image_to_video는 ratio를 받지 않음
        assertThat(body.path("promptText").asText()).isEqualTo("pull back to the storefront");
        JsonNode images = body.path("promptImage");
        assertThat(images).hasSize(2);
        assertThat(images.get(0).path("position").asText()).isEqualTo("first");
        assertThat(images.get(0).path("uri").asText()).isEqualTo("data:first");
        assertThat(images.get(1).path("position").asText()).isEqualTo("last");
        assertThat(images.get(1).path("uri").asText()).isEqualTo("data:last");
    }

    @Test
    void 긴_컷은_10초_클립으로_요청하고_다른_길이는_막는다() {
        assertThat(RunwayClipClient.transitionBody(new ObjectMapper(), "a", "b", "p", 10).path("duration").asInt()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RunwayClipClient.transitionBody(new ObjectMapper(), "a", "b", "p", 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 프롬프트가_비면_기본_카메라_이동으로() {
        // Kling은 promptText가 필수
        JsonNode body = RunwayClipClient.transitionBody(new ObjectMapper(), "a", "b", " ");
        assertThat(body.path("promptText").asText()).isNotBlank();
    }
}
