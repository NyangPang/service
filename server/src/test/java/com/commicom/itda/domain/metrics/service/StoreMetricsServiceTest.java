package com.commicom.itda.domain.metrics.service;

import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse.DailyCount;
import com.commicom.itda.domain.quest.entity.QuestVisit;
import com.commicom.itda.domain.shortform.entity.ShortformView;
import com.commicom.itda.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreMetricsServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 21);

    private static QuestVisit visit(long memberId, LocalDate date) {
        return new QuestVisit(memberId, 1L, 10L, date);
    }

    private static ShortformView view(Long memberId, LocalDate date) {
        return new ShortformView(100L, 10L, memberId, date);
    }

    @Test
    void 방문_전_7일_안에_게시물을_본_손님의_방문만_센다() {
        List<QuestVisit> visits = List.of(
                visit(1, D),                // 3일 전에 봄 → 셈
                visit(2, D),                // 8일 전에 봄 → 너무 오래됨
                visit(3, D),                // 안 봄
                visit(4, D.minusDays(5)),   // 방문 뒤에만 봄 → 안 셈
                visit(5, D));               // 같은 날 봄 → 셈
        List<ShortformView> views = List.of(
                view(1L, D.minusDays(3)),
                view(2L, D.minusDays(8)),
                view(4L, D),
                view(5L, D),
                view(null, D));             // 비로그인 조회는 손님을 알 수 없어 연결 안 함
        assertThat(StoreMetricsService.visitsAfterView(visits, views, 7)).isEqualTo(2);
    }

    @Test
    void 방문율은_퍼센트_소수_첫째_자리이고_조회가_없으면_null() {
        assertThat(StoreMetricsService.rate(36, 1248)).isEqualTo(2.9);
        assertThat(StoreMetricsService.rate(1, 3)).isEqualTo(33.3);
        assertThat(StoreMetricsService.rate(0, 0)).isNull();
    }

    @Test
    void 날짜별_방문은_빈_날을_0으로_채운다() {
        List<DailyCount> daily = StoreMetricsService.daily(
                List.of(visit(1, D), visit(2, D), visit(3, D.minusDays(2))), D.minusDays(2), D);
        assertThat(daily).extracting(DailyCount::count).containsExactly(1L, 0L, 2L);
        assertThat(daily.get(0).date()).isEqualTo(D.minusDays(2));
    }

    @Test
    void 기간_계산() {
        MetricsPeriod.Range week = MetricsPeriod.of("7d").range(D);
        assertThat(week.from()).isEqualTo(D.minusDays(6));
        assertThat(week.previousTo()).isEqualTo(D.minusDays(7));
        assertThat(week.previousFrom()).isEqualTo(D.minusDays(13));
        assertThat(week.days()).isEqualTo(7);

        MetricsPeriod.Range month = MetricsPeriod.of("30d").range(D);
        assertThat(month.days()).isEqualTo(30);
        assertThat(month.previousFrom()).isEqualTo(D.minusDays(59));

        // 이번 달: 3/31 기준이면 지난달(2월)은 말일까지만
        MetricsPeriod.Range thisMonth = MetricsPeriod.of("month").range(LocalDate.of(2026, 3, 31));
        assertThat(thisMonth.from()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(thisMonth.previousFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(thisMonth.previousTo()).isEqualTo(LocalDate.of(2026, 2, 28));

        assertThat(MetricsPeriod.of(null)).isEqualTo(MetricsPeriod.WEEK);
        assertThatThrownBy(() -> MetricsPeriod.of("1y")).isInstanceOf(BusinessException.class);
    }
}
