package com.commicom.itda.global.init;

import com.commicom.itda.domain.store.entity.District;
import com.commicom.itda.domain.store.entity.StoreCategory;
import com.commicom.itda.global.init.SampleStoreInitializer.SampleStore;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SampleStoreInitializerTest {

    private final List<SampleStore> samples = SampleStoreInitializer.build();

    @Test
    void 서울_25개_구마다_5곳씩_125곳() {
        assertThat(samples).hasSize(125);
        Map<String, Long> perDistrict = samples.stream()
                .collect(Collectors.groupingBy(s -> District.of(s.address()), Collectors.counting()));
        assertThat(perDistrict.keySet()).containsExactlyInAnyOrderElementsOf(District.SEOUL);
        assertThat(perDistrict.values()).allMatch(n -> n == 5);
    }

    @Test
    void 구마다_프로필_사진_Group1부터_5까지_한_장씩() {
        Map<String, List<String>> photos = samples.stream()
                .collect(Collectors.groupingBy(s -> District.of(s.address()),
                        Collectors.mapping(SampleStore::photo, Collectors.toList())));
        assertThat(photos.values()).allSatisfy(list ->
                assertThat(list).containsExactly("Group1.png", "Group2.png", "Group3.png", "Group4.png", "Group5.png"));
    }

    @Test
    void 가게_이름은_겹치지_않고_식당이_가장_많다() {
        assertThat(new HashSet<>(samples.stream().map(SampleStore::name).toList())).hasSize(125);
        Map<StoreCategory, Long> perCategory = samples.stream()
                .collect(Collectors.groupingBy(SampleStore::category, Collectors.counting()));
        assertThat(perCategory.get(StoreCategory.RESTAURANT))
                .isGreaterThan(perCategory.values().stream().filter(n -> n < perCategory.get(StoreCategory.RESTAURANT))
                        .mapToLong(Long::longValue).max().orElse(0));
        assertThat(perCategory.keySet()).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void 좌표는_서울_안() {
        assertThat(samples).allSatisfy(s -> {
            assertThat(s.lat()).isBetween(37.42, 37.71);
            assertThat(s.lng()).isBetween(126.76, 127.19);
        });
    }
}
