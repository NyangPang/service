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
