package com.commicom.itda.infra.video.storyboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MenuImageGeneratorTest {

    @TempDir
    Path dir;

    /** Runway 대신 1초 걸리는 가짜. 동시에 몇 개가 돌았는지 센다 */
    static class SlowFake extends RunwayImageClient {
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();

        SlowFake() { super("fake-key", s -> {}); }

        @Override
        void generate(String prompt, String ratio, Path outPng) throws Exception {
            calls.incrementAndGet();
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(1000);
                Files.writeString(outPng, prompt);
            } finally {
                running.decrementAndGet();
            }
        }
    }

    @Test
    void 이미지_요청은_동시에_보내고_순서는_요청대로_붙인다() throws Exception {
        ObjectNode menu = (ObjectNode) new ObjectMapper().readTree("""
                {"photos": [],
                 "image_requests": [
                   {"menu_ref": "카페라떼", "prompt": "latte A"},
                   {"menu_ref": null, "prompt": "latte and cake B"},
                   {"menu_ref": "치즈케이크", "prompt": "cheesecake C"}
                 ]}""");
        SlowFake runway = new SlowFake();
        MenuImageGenerator gen = new MenuImageGenerator(runway, dir.resolve("cache"), false, s -> {});

        long start = System.nanoTime();
        MenuImageGenerator.Result r = gen.generate(menu, List.of());
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(runway.maxRunning.get()).isEqualTo(3);
        assertThat(ms).isLessThan(2500);   // 하나씩이면 3초 이상
        assertThat(r.creditsSpent()).isEqualTo(15);
        assertThat(r.menu().path("photos")).hasSize(3);
        assertThat(r.menu().path("photos").get(0).path("menu_ref").asText()).isEqualTo("카페라떼");
        assertThat(r.menu().path("photos").get(1).path("menu_ref").isNull()).isTrue();
        assertThat(Files.readString(r.photos().get(2))).isEqualTo("cheesecake C");
        assertThat(r.menu().path("image_requests")).isEmpty();

        // 같은 요청을 다시 돌리면 캐시 재사용 → 크레딧 0
        MenuImageGenerator.Result again = gen.generate(menu, List.of());
        assertThat(again.creditsSpent()).isZero();
        assertThat(runway.calls.get()).isEqualTo(3);
    }
}
