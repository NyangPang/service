package com.commicom.itda.domain.metrics.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사장님 성과지표 조회.
 *
 * @param views       게시물 조회수 (이번 기간 / 비교 기간, 전체 기간이면 비교 없음)
 * @param scraps      그 기간에 새로 생긴 스크랩 수
 * @param visits      QR 방문 인증 수
 * @param viewToVisit 조회 후 방문율: 이번 기간 방문 인증 중 그 손님이 방문 전 7일 안에 이 가게 게시물을 본 건수 ÷ 이번 기간 조회수
 * @param dailyVisits 이번 기간 날짜별 방문 인증 (빈 날은 0)
 * @param videos      공개 게시물별 전체 조회수·현재 스크랩 수 (최신순)
 */
public record StoreMetricsResponse(
        Long storeId,
        String storeName,
        String period,
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        Count views,
        Count scraps,
        Count visits,
        ViewToVisit viewToVisit,
        List<DailyCount> dailyVisits,
        List<VideoMetrics> videos
) {

    /** current: 이번 기간, previous: 바로 앞 같은 길이 기간 (전체 기간이면 null) */
    public record Count(long current, Long previous) {}

    /** rate: 퍼센트(소수 첫째 자리). 조회가 0이면 null */
    public record ViewToVisit(long views, long visitsAfterView, Double rate) {}

    public record DailyCount(LocalDate date, long count) {}

    public record VideoMetrics(Long shortformId, String title, String imageUrl, String videoUrl,
                               OffsetDateTime postedAt, long views, long scraps) {}
}
