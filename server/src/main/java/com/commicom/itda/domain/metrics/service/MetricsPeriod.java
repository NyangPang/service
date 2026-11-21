package com.commicom.itda.domain.metrics.service;

import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;

import java.time.LocalDate;

/**
 * 성과지표 기간 (KST 날짜, 양 끝 포함).
 * <pre>
 * 7d    최근 7일 (오늘 포함)    ↔ 그 앞 7일
 * 30d   최근 30일 (오늘 포함)   ↔ 그 앞 30일
 * all   전체 기간 (가게 등록일 ~ 오늘), 비교 기간 없음
 * </pre>
 */
public enum MetricsPeriod {

    WEEK("7d"),
    MONTH("30d"),
    ALL("all");

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

    /** previousFrom·previousTo는 비교 기간이 없으면(전체 기간) null */
    public record Range(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
        public int days() {
            return (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        }

        public boolean hasPrevious() {
            return previousFrom != null;
        }
    }

    /** @param since 전체 기간의 시작 (가게 등록일). 오늘보다 늦거나 null이면 오늘 */
    public Range range(LocalDate today, LocalDate since) {
        return switch (this) {
            case WEEK -> rolling(today, 7);
            case MONTH -> rolling(today, 30);
            case ALL -> new Range(since == null || since.isAfter(today) ? today : since, today, null, null);
        };
    }

    private static Range rolling(LocalDate today, int days) {
        LocalDate from = today.minusDays(days - 1L);
        LocalDate prevTo = from.minusDays(1);
        return new Range(from, today, prevTo.minusDays(days - 1L), prevTo);
    }
}
