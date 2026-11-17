package com.commicom.itda.domain.store.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistrictTest {

    @Test
    void 도로명_지번_주소에서_자치구를_뽑는다() {
        assertThat(District.of("서울 노원구 광운로 20")).isEqualTo("노원구");
        assertThat(District.of("서울특별시 노원구 월계동 123-4")).isEqualTo("노원구");
        assertThat(District.of("  서울 중구 세종대로 110 ")).isEqualTo("중구");
        assertThat(District.of("서울 노원구 월계동 (샘플 주소)")).isEqualTo("노원구");
    }

    @Test
    void 구가_없거나_도로명_일부면_null() {
        assertThat(District.of(null)).isNull();
        assertThat(District.of("경기 가평군 가평읍")).isNull();
        // "구"로 끝나지만 단어 중간인 경우는 무시 (예: 구로 시작하는 도로명)
        assertThat(District.of("서울 구로구 구로동로 1")).isEqualTo("구로구");
    }
}
