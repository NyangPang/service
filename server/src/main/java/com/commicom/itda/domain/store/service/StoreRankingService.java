package com.commicom.itda.domain.store.service;

import com.commicom.itda.domain.quest.repository.QuestVisitRepository;
import com.commicom.itda.domain.store.dto.StoreRankingDtos.DistrictResponse;
import com.commicom.itda.domain.store.dto.StoreRankingDtos.RankedStore;
import com.commicom.itda.domain.store.dto.StoreRankingDtos.StoreRankingResponse;
import com.commicom.itda.domain.store.entity.District;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.entity.StoreCategory;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 가게 검색·동네 랭킹. 인기 = 기간 내 실제 방문 수 (QR 방문 인증, 한 손님은 한 가게에 하루 1번).
 * 지역은 주소의 자치구, 업종은 대분류 16종.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreRankingService {

    /** 고를 수 있는 집계 기간 (일) */
    public static final Set<Integer> PERIOD_DAYS = Set.of(7, 14, 31);

    private final StoreRepository storeRepository;
    private final QuestVisitRepository visitRepository;

    /**
     * 조건에 맞는 가게를 방문 수 많은 순으로. 방문이 없는 가게도 뒤에 붙는다 (같으면 이름 순).
     *
     * @param district 자치구 (null·빈 값이면 전체 지역)
     * @param category 업종 (null 이면 모든 업종)
     * @param q        가게 이름 검색어 (null·빈 값이면 검색 안 함)
     * @param days     7·14·31
     */
    public StoreRankingResponse getRanking(String district, StoreCategory category, String q, int days) {
        if (!PERIOD_DAYS.contains(days)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String districtFilter = blankToNull(district);
        String query = blankToNull(q);
        LocalDate to = KstTime.today();
        LocalDate from = to.minusDays(days - 1L);

        List<Store> stores = (category == null ? storeRepository.findAll() : storeRepository.findAllByCategory(category))
                .stream()
                .filter(s -> districtFilter == null || districtFilter.equals(District.of(s.getAddress())))
                .filter(s -> query == null || normalize(s.getName()).contains(normalize(query)))
                .toList();

        Map<Long, Long> visits = stores.isEmpty() ? Map.of()
                : visitRepository.countByStoreSince(stores.stream().map(Store::getId).toList(), from).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<Store> sorted = stores.stream()
                .sorted(Comparator.comparingLong((Store s) -> visits.getOrDefault(s.getId(), 0L)).reversed()
                        .thenComparing(Store::getName)
                        .thenComparing(Store::getId))
                .toList();

        List<RankedStore> ranked = new ArrayList<>();
        int rank = 0;
        Long prevCount = null;
        for (int i = 0; i < sorted.size(); i++) {
            Store s = sorted.get(i);
            long count = visits.getOrDefault(s.getId(), 0L);
            // 방문 수가 같으면 같은 순위 (1, 2, 2, 4 …)
            if (!Objects.equals(prevCount, count)) rank = i + 1;
            prevCount = count;
            ranked.add(new RankedStore(rank, s.getId(), s.getName(), s.getCategory(), s.getCategory().getDescription(),
                    s.getAddress(), District.of(s.getAddress()), s.getThumbnailUrl(), count));
        }
        return new StoreRankingResponse(districtFilter, category, query, days, from, to, ranked);
    }

    /** 가게가 있는 자치구 목록 (가나다 순) */
    public List<DistrictResponse> getDistricts() {
        Map<String, Long> counts = storeRepository.findAll().stream()
                .map(s -> District.of(s.getAddress()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(d -> d, TreeMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(e -> new DistrictResponse(e.getKey(), e.getValue())).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** 띄어쓰기·대소문자 무시하고 비교 */
    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
