package com.commicom.itda.domain.metrics.service;

import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse;
import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse.Count;
import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse.DailyCount;
import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse.VideoMetrics;
import com.commicom.itda.domain.metrics.dto.StoreMetricsResponse.ViewToVisit;
import com.commicom.itda.domain.quest.entity.QuestVisit;
import com.commicom.itda.domain.quest.repository.QuestVisitRepository;
import com.commicom.itda.domain.scrap.repository.ShortformScrapRepository;
import com.commicom.itda.domain.shortform.entity.ShortformView;
import com.commicom.itda.domain.shortform.repository.ShortformRepository;
import com.commicom.itda.domain.shortform.repository.ShortformViewRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMetricsService {

    /** 방문 전 며칠 안에 본 게시물까지 "보고 왔다"로 셀지 */
    static final int ATTRIBUTION_DAYS = 7;

    private final StoreRepository storeRepository;
    private final ShortformRepository shortformRepository;
    private final ShortformViewRepository viewRepository;
    private final ShortformScrapRepository scrapRepository;
    private final QuestVisitRepository visitRepository;

    public StoreMetricsResponse get(Long memberId, Long storeId, String periodCode) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.isOwnedBy(memberId)) throw new BusinessException(ErrorCode.STORE_FORBIDDEN);

        MetricsPeriod period = MetricsPeriod.of(periodCode);
        LocalDate registered = store.getCreatedAt() == null ? null : KstTime.fromServerTime(store.getCreatedAt()).toLocalDate();
        MetricsPeriod.Range r = period.range(KstTime.today(), registered);

        long views = viewRepository.countByStoreIdAndViewDateBetween(storeId, r.from(), r.to());
        long scraps = scrapRepository.countByStoreBetween(storeId, serverStart(r.from()), serverStart(r.to().plusDays(1)));
        List<QuestVisit> visits = visitRepository.findAllByStoreIdAndVisitDateBetween(storeId, r.from(), r.to());
        Long prevViews = null, prevScraps = null, prevVisits = null;
        if (r.hasPrevious()) {
            prevViews = viewRepository.countByStoreIdAndViewDateBetween(storeId, r.previousFrom(), r.previousTo());
            prevScraps = scrapRepository.countByStoreBetween(storeId, serverStart(r.previousFrom()), serverStart(r.previousTo().plusDays(1)));
            prevVisits = (long) visitRepository.findAllByStoreIdAndVisitDateBetween(storeId, r.previousFrom(), r.previousTo()).size();
        }

        List<ShortformView> memberViews = viewRepository.findAllByStoreIdAndMemberIdIsNotNullAndViewDateBetween(
                storeId, r.from().minusDays(ATTRIBUTION_DAYS), r.to());
        long visitsAfterView = visitsAfterView(visits, memberViews, ATTRIBUTION_DAYS);

        Map<Long, Long> videoViews = toMap(viewRepository.countByShortform(storeId));
        Map<Long, Long> videoScraps = toMap(scrapRepository.countByShortform(storeId));
        List<VideoMetrics> videos = shortformRepository.findAllByStoreAndPublishedTrueOrderByCreatedAtDesc(store).stream()
                .map(s -> new VideoMetrics(s.getId(), s.getTitle(), s.getImageUrl(), s.getVideoUrl(),
                        KstTime.fromServerTime(s.getCreatedAt()),
                        videoViews.getOrDefault(s.getId(), 0L), videoScraps.getOrDefault(s.getId(), 0L)))
                .toList();

        return new StoreMetricsResponse(store.getId(), store.getName(), period.code,
                r.from(), r.to(), r.previousFrom(), r.previousTo(),
                new Count(views, prevViews), new Count(scraps, prevScraps), new Count((long) visits.size(), prevVisits),
                new ViewToVisit(views, visitsAfterView, rate(visitsAfterView, views)),
                daily(visits, r.from(), r.to()), videos);
    }

    /** 방문 인증 중, 그 손님이 방문일 기준 days일 전 ~ 방문일 사이에 이 가게 게시물을 본 기록이 있는 건수 */
    static long visitsAfterView(Collection<QuestVisit> visits, Collection<ShortformView> views, int days) {
        Map<Long, TreeSet<LocalDate>> viewedOn = new HashMap<>();
        for (ShortformView v : views) {
            if (v.getMemberId() != null) viewedOn.computeIfAbsent(v.getMemberId(), k -> new TreeSet<>()).add(v.getViewDate());
        }
        return visits.stream().filter(visit -> {
            TreeSet<LocalDate> dates = viewedOn.get(visit.getMemberId());
            if (dates == null) return false;
            LocalDate latest = dates.floor(visit.getVisitDate());
            return latest != null && !latest.isBefore(visit.getVisitDate().minusDays(days));
        }).count();
    }

    /** 퍼센트, 소수 첫째 자리. 분모가 0이면 null */
    static Double rate(long part, long whole) {
        if (whole <= 0) return null;
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    static List<DailyCount> daily(Collection<QuestVisit> visits, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (QuestVisit v : visits) byDate.merge(v.getVisitDate(), 1L, Long::sum);
        List<DailyCount> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.add(new DailyCount(d, byDate.getOrDefault(d, 0L)));
        return out;
    }

    /** KST 그날 0시 → 서버 시간대 시각 (createdAt은 서버 시간대로 저장됨) */
    private static LocalDateTime serverStart(LocalDate kstDate) {
        return kstDate.atStartOfDay(KstTime.ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] row : rows) m.put((Long) row[0], (Long) row[1]);
        return m;
    }
}
