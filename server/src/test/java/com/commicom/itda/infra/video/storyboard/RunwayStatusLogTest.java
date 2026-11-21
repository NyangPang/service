package com.commicom.itda.infra.video.storyboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RunwayStatusLogTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    void 상태가_바뀔_때만_남기고_한도_대기_시간을_합친다() {
        AtomicLong now = new AtomicLong();
        List<String> lines = new ArrayList<>();
        RunwayStatusLog watch = new RunwayStatusLog("클립", lines::add, now::get);

        now.set(5 * SEC);  watch.update("THROTTLED");
        now.set(10 * SEC); watch.update("THROTTLED");
        now.set(35 * SEC); watch.update("RUNNING");
        now.set(40 * SEC); watch.update("RUNNING");
        now.set(90 * SEC); watch.update("SUCCEEDED");

        assertThat(lines).containsExactly(
                "    클립 → THROTTLED (동시 실행 한도로 대기 중) (5초)",
                "    클립 → RUNNING (생성 중) (35초)",
                "    클립 완료 · 총 90초, 한도 대기 30초");
    }

    @Test
    void 한도에_안_걸리면_대기_없음으로_남긴다() {
        AtomicLong now = new AtomicLong();
        List<String> lines = new ArrayList<>();
        RunwayStatusLog watch = new RunwayStatusLog("클립", lines::add, now::get);

        now.set(5 * SEC);  watch.update("RUNNING");
        now.set(60 * SEC); watch.update("SUCCEEDED");

        assertThat(lines).last().isEqualTo("    클립 완료 · 총 60초, 한도 대기 없음");
        assertThat(watch.lastStatus()).isEqualTo("SUCCEEDED");
    }
}
