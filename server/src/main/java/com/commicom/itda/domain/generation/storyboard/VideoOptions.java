package com.commicom.itda.domain.generation.storyboard;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 사장님이 고른 생성 느낌 (영상 스타일 + 강조 포인트).
 * @param style    null이면 스타일을 고르지 않음 → 기존 프롬프트·기본 목소리
 * @param emphases 비어 있을 수 있음. 빠른 템포·느린 템포를 둘 다 고르면 서로 부딪혀 둘 다 뺀다
 */
public record VideoOptions(VideoStyle style, Set<Emphasis> emphases) {

    public static final VideoOptions NONE = new VideoOptions(null, Set.of());

    public VideoOptions {
        Set<Emphasis> copy = emphases == null || emphases.isEmpty() ? EnumSet.noneOf(Emphasis.class) : EnumSet.copyOf(emphases);
        if (copy.contains(Emphasis.FAST_TEMPO) && copy.contains(Emphasis.SLOW_TEMPO)) {
            copy.remove(Emphasis.FAST_TEMPO);
            copy.remove(Emphasis.SLOW_TEMPO);
        }
        emphases = Set.copyOf(copy);
    }

    public static VideoOptions of(VideoStyle style, Collection<Emphasis> emphases) {
        return new VideoOptions(style, emphases == null ? Set.of() : Set.copyOf(emphases));
    }

    public boolean is(VideoStyle s) {
        return style == s;
    }

    /** 나레이션을 남자 목소리로 */
    public boolean maleVoice() {
        return style != null && style.maleVoice();
    }

    /** 메뉴 추출 단계에서 더 요청할 수 있는 생성 이미지 수 */
    public int extraImages() {
        return style == null ? 0 : style.extraImages();
    }
}
