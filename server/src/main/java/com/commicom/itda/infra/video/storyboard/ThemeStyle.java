package com.commicom.itda.infra.video.storyboard;

/**
 * 장면 JSON의 theme(영상 주제) → FFmpeg xfade 장면 전환.
 *
 * <pre>
 * emotional (감성)     부드러운 슬라이드
 * value     (가성비)   회전하듯 쓸어 넘김
 * trendy    (트렌디)   빠르게 확대하며 넘김
 * expert    (전문)     깔끔한 페이드
 * fresh     (신선함)   옆으로 밀어 넘김
 * </pre>
 */
public enum ThemeStyle {

    EMOTIONAL("smoothleft", 0.6),
    VALUE("radial", 0.4),
    TRENDY("zoomin", 0.35),
    EXPERT("fade", 0.5),
    FRESH("slideleft", 0.4),
    /** theme 없는 예전 장면 JSON */
    DEFAULT("fade", 0.35);

    /** FFmpeg xfade transition 이름 */
    final String transition;
    /** 장면 사이 겹쳐 넘어가는 시간(초) */
    final double transitionSec;

    ThemeStyle(String transition, double transitionSec) {
        this.transition = transition;
        this.transitionSec = transitionSec;
    }

    public static ThemeStyle of(String theme) {
        if (theme == null) return DEFAULT;
        return switch (theme) {
            case "emotional" -> EMOTIONAL;
            case "value" -> VALUE;
            case "trendy" -> TRENDY;
            case "expert" -> EXPERT;
            case "fresh" -> FRESH;
            default -> DEFAULT;
        };
    }
}
