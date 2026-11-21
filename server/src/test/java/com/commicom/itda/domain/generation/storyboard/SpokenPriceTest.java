package com.commicom.itda.domain.generation.storyboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpokenPriceTest {

    @Test
    void 가격을_한글로_읽는다() {
        assertThat(SpokenPrice.read(12000)).isEqualTo("만 이천");
        assertThat(SpokenPrice.read(25900)).isEqualTo("이만 오천구백");
        assertThat(SpokenPrice.read(8000)).isEqualTo("팔천");
        assertThat(SpokenPrice.read(4500)).isEqualTo("사천오백");
        assertThat(SpokenPrice.read(10000)).isEqualTo("만");
        assertThat(SpokenPrice.read(150000)).isEqualTo("십오만");
        assertThat(SpokenPrice.read(1110)).isEqualTo("천백십");
    }

    @Test
    void 문장_속_가격만_바꾼다() {
        assertThat(SpokenPrice.speak("12,000원에 이 두께라고?")).isEqualTo("만 이천 원에 이 두께라고?");
        assertThat(SpokenPrice.speak("라떼 4500원, 쿠키까지")).isEqualTo("라떼 사천오백 원, 쿠키까지");
        assertThat(SpokenPrice.speak("가격 없는 문장")).isEqualTo("가격 없는 문장");
    }
}
