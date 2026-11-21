package com.commicom.itda.infra.video.storyboard;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NarratorFallbackTest {

    @Test
    void 남자_목소리가_실패하면_그_뒤로는_기본_목소리로_읽는다() throws Exception {
        List<String> calls = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Narrator male = fake("male", calls, true);
        Narrator base = fake("base", calls, false);
        Narrator n = Narrator.withFallback(male, base, warnings::add);

        assertThat(n.id()).isEqualTo("male");
        n.synthesize("첫 장면", Path.of("a.wav"), null);
        n.synthesize("둘째 장면", Path.of("b.wav"), null);

        // 남자 목소리는 한 번만 시도하고, 장면마다 목소리가 섞이지 않게 계속 기본 목소리
        assertThat(calls).containsExactly("male:첫 장면", "base:첫 장면", "base:둘째 장면");
        assertThat(n.id()).isEqualTo("base");
        assertThat(warnings).hasSize(1);
    }

    @Test
    void 남자_목소리가_되면_그대로() throws Exception {
        List<String> calls = new ArrayList<>();
        Narrator n = Narrator.withFallback(fake("male", calls, false), fake("base", calls, false), w -> {});
        n.synthesize("한 장면", Path.of("a.wav"), null);
        assertThat(calls).containsExactly("male:한 장면");
    }

    private static Narrator fake(String id, List<String> calls, boolean fail) {
        return new Narrator() {
            public String id() {
                return id;
            }

            public void synthesize(String text, Path outWav, Ffmpeg ff) {
                calls.add(id + ":" + text);
                if (fail) throw new IllegalStateException("voice not available");
            }
        };
    }
}
