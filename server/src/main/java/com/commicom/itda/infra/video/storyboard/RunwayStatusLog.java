package com.commicom.itda.infra.video.storyboard;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Runway 작업을 폴링하면서 상태가 바뀔 때만 로그를 남기고,
 * 동시 실행 한도 때문에 대기(THROTTLED)한 시간을 따로 잰다.
 * 클립 3개를 동시에 보냈는데 한도 대기가 길게 찍히면, 코드는 병렬이어도 Runway 쪽에서 차례로 돌린 것이다.
 */
final class RunwayStatusLog {

    private final String label;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final long start;
    private String last = "";
    private long throttledSince;
    private long throttledNanos;

    RunwayStatusLog(String label, Consumer<String> log) {
        this(label, log, System::nanoTime);
    }

    RunwayStatusLog(String label, Consumer<String> log, LongSupplier clock) {
        this.label = label;
        this.log = log;
        this.clock = clock;
        this.start = clock.getAsLong();
    }

    /** 폴링할 때마다 부른다. 직전과 같은 상태면 아무것도 남기지 않는다. */
    void update(String status) {
        if (status.equals(last)) return;
        long now = clock.getAsLong();
        if ("THROTTLED".equals(last)) throttledNanos += now - throttledSince;
        if ("THROTTLED".equals(status)) throttledSince = now;
        last = status;
        if ("SUCCEEDED".equals(status)) {
            log.accept("    " + label + " 완료 · 총 " + seconds(now - start) + "초, "
                    + (throttledNanos == 0 ? "한도 대기 없음" : "한도 대기 " + seconds(throttledNanos) + "초"));
        } else {
            log.accept("    " + label + " → " + describe(status) + " (" + seconds(now - start) + "초)");
        }
    }

    String lastStatus() {
        return last.isEmpty() ? "없음" : last;
    }

    private static String describe(String status) {
        return switch (status) {
            case "PENDING" -> "PENDING (차례 기다리는 중)";
            case "THROTTLED" -> "THROTTLED (동시 실행 한도로 대기 중)";
            case "RUNNING" -> "RUNNING (생성 중)";
            default -> status;
        };
    }

    private static long seconds(long nanos) {
        return Math.round(nanos / 1_000_000_000.0);
    }
}
