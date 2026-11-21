package com.commicom.itda.domain.metrics.service;

import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;

import java.time.LocalDate;

/**
 * 성과지표 기간 (KST 날짜, 양 끝 포함). 비교 기간은 바로 앞의 같은 길이.
 * <pre>
 * 7d     최근 7일 (오늘 포함)      ↔ 그 앞 7일
 * 30d    최근 30일 (오늘 포함)     ↔ 그 앞 30일
 * month  이번 달 1일 ~ 오늘        ↔ 지난달 1일부터 같은 날수 (지난달이 짧으면 말일까지)
 * </pre>
 */
public enum MetricsPeriod {

    WEEK("7d"),
    MONTH("30d"),
    THIS_MONTH("month");

    public final String code;

    MetricsPeriod(String code) {
        this.code = code;
    }

    public static MetricsPeriod of(String code) {
        if (code == null || code.isBlank()) return WEEK;
        for (MetricsPeriod p : values()) {
            if (p.code.equalsIgnoreCase(code.strip())) return p;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    public record Range(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
        public int days() {
            return (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        }
    }

    public Range range(LocalDate today) {
        return switch (this) {
            case WEEK -> rolling(today, 7);
            case MONTH -> rolling(today, 30);
            case THIS_MONTH -> {
                LocalDate from = today.withDayOfMonth(1);
                LocalDate prevFrom = from.minusMonths(1);
                int days = today.getDayOfMonth();
                LocalDate prevTo = prevFrom.plusDays(Math.min(days, prevFrom.lengthOfMonth()) - 1L);
                yield new Range(from, today, prevFrom, prevTo);
            }
        };
    }

    private static Range rolling(LocalDate today, int days) {
        LocalDate from = today.minusDays(days - 1L);
        LocalDate prevTo = from.minusDays(1);
        return new Range(from, today, prevTo.minusDays(days - 1L), prevTo);
    }
}
