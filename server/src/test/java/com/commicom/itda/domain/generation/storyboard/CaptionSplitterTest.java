package com.commicom.itda.domain.generation.storyboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionSplitterTest {

    @Test
    void 짧은_자막은_한_조각() {
        assertThat(CaptionSplitter.split("아니 이 두께 좀 보세요.")).containsExactly("아니 이 두께 좀 보세요.");
        // 18자까지는 한 화면에
        assertThat(CaptionSplitter.split("두툼한 로스카츠 12,000원")).containsExactly("두툼한 로스카츠 12,000원");
    }

    @Test
    void 긴_자막은_띄어쓰기에서_고르게_두_조각() {
        assertThat(CaptionSplitter.split("딸기 복숭아 샤인머스켓 골라 먹는 재미예요."))
                .containsExactly("딸기 복숭아 샤인머스켓", "골라 먹는 재미예요.");
    }

    @Test
    void 더_길면_세_조각까지() {
        List<String> parts = CaptionSplitter.split("매일 아침 직접 두드린 등심을 두툼하게 썰어서 바삭하게 튀겨 내요.");
        assertThat(parts).hasSize(3).allMatch(p -> p.length() <= CaptionSplitter.PART_MAX);
        assertThat(String.join(" ", parts)).isEqualTo("매일 아침 직접 두드린 등심을 두툼하게 썰어서 바삭하게 튀겨 내요.");
        assertThat(CaptionSplitter.fits("매일 아침 직접 두드린 등심을 두툼하게 썰어서 바삭하게 튀겨 내요.")).isTrue();
    }

    @Test
    void 문장부호_뒤에서_끊는_쪽을_고름() {
        assertThat(CaptionSplitter.split("겉은 바삭하고 속은 촉촉해요. 소스까지 직접 만들어요."))
                .containsExactly("겉은 바삭하고 속은 촉촉해요.", "소스까지 직접 만들어요.");
    }

    @Test
    void 의존명사_앞뒤에서_끊지_않음() {
        // 실제 생성 영상에서 "취향 맞는 맛을 고를 / 수 있는 광운 카페예요."로 끊겼던 문장
        assertThat(CaptionSplitter.split("취향 맞는 맛을 고를 수 있는 광운 카페예요."))
                .containsExactly("취향 맞는 맛을", "고를 수 있는 광운 카페예요.");
        assertThat(CaptionSplitter.split("취향에 맞게 고를 수 있는 광운 카페예요."))
                .containsExactly("취향에 맞게", "고를 수 있는 광운 카페예요.");
    }

    @Test
    void 보조_용언은_앞말과_같은_조각() {
        assertThat(CaptionSplitter.split("그 안에 과일 크림이 쫀득하게 차 있고"))
                .containsExactly("그 안에 과일 크림이", "쫀득하게 차 있고");
        assertThat(CaptionSplitter.splitsUnit("차", "있고")).isTrue();
        assertThat(CaptionSplitter.splitsUnit("먹어", "보세요.")).isTrue();
        assertThat(CaptionSplitter.splitsUnit("맵지", "않은")).isTrue();
        assertThat(CaptionSplitter.splitsUnit("크림을", "듬뿍")).isFalse();
        assertThat(CaptionSplitter.splitsUnit("바삭하게", "두툼한")).isFalse();
    }

    @Test
    void 길이가_같으면_조사_뒤에서_끊음() {
        // "무려 수제 그릭요거트 / 크림을 듬뿍…"도 길이 차이는 같지만 "크림을" 뒤가 말이 쉬는 자리
        assertThat(CaptionSplitter.split("무려 수제 그릭요거트 크림을 듬뿍 넣은 쫀득빵을"))
                .containsExactly("무려 수제 그릭요거트 크림을", "듬뿍 넣은 쫀득빵을");
        assertThat(CaptionSplitter.split("다양한 종류의 과일마다 쫀득빵이 따로 있는데"))
                .containsExactly("다양한 종류의 과일마다", "쫀득빵이 따로 있는데");
    }

    @Test
    void 가격은_메뉴_이름과_같은_조각() {
        List<String> parts = CaptionSplitter.split("매일 아침 두드려 튀긴 두툼한 로스카츠 12,000원");
        assertThat(parts).anyMatch(p -> p.endsWith("로스카츠 12,000원"));
        assertThat(parts).noneMatch(p -> p.equals("12,000원"));
    }

    @Test
    void 띄어쓰기가_없으면_나누지_않고_맞지_않다고_알림() {
        String text = "겉바속촉치즈듬뿍로스카츠정식세트메뉴예요.";
        assertThat(CaptionSplitter.split(text)).containsExactly(text);
        assertThat(CaptionSplitter.fits(text)).isFalse();
    }

    @Test
    void 읽는_길이는_가격을_읽는_형태로_공백_문장부호_빼고_셈() {
        assertThat(CaptionSplitter.spokenLength("골라 먹는 재미예요.")).isEqualTo(8);
        // "12,000원" → "만 이천 원" = 4자 (숫자 일곱 글자가 아니라)
        assertThat(CaptionSplitter.spokenLength("로스카츠 12,000원")).isEqualTo(4 + 4);
    }
}
