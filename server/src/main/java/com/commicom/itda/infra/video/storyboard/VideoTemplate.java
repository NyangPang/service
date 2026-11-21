package com.commicom.itda.infra.video.storyboard;

import java.util.Locale;

/**
 * 영상 템플릿 = 화면 배치 (사진·제목·자막이 어디에 어떤 모양으로 놓이는지). 내용(장면·문장)은 장면 JSON이 정한다.
 * 장면 JSON의 meta.template 이 있으면 그것, 없으면 headline (팀 결정: 템플릿은 A 꽉 찬 화면형 하나) — {@link #forTheme}.
 *
 * <pre>
 * headline  꽉 찬 화면형   사진 화면 전체 + 위에 고정 제목 상자 2줄(검정·노랑) + 왼쪽 위 가게 이름 태그
 *                          본문 자막은 화면 중앙에 고정 크기 흰 글씨와 검은 상자
 * band      위아래 띠형    위 검은 띠에 고정 제목(흰·노랑 글씨), 가운데 사진, 아래 띠에 자막
 * card      카드형         크림색 배경 + 고정 제목(형광펜 밑줄), 둥근 사진 카드, 카드 아래 끝에 주황 알약 자막
 * classic   예전 방식      제목 없음, 자막 쪽을 어둡게 깔고 강조색 막대 + 큰 글씨 (지정했을 때만)
 * </pre>
 */
public enum VideoTemplate {

    HEADLINE(null),
    BAND(new Box(0, 540, 1080, 920)),
    CARD(new Box(90, 500, 900, 980)),
    CLASSIC(null);

    /** 사진(배경 클립)이 놓이는 칸. null이면 화면 전체 */
    public record Box(int x, int y, int w, int h) {}

    final Box photoBox;

    VideoTemplate(Box photoBox) {
        this.photoBox = photoBox;
    }

    /** meta.template 이름. 모르는 이름이나 null이면 null (→ 기본 headline) */
    public static VideoTemplate named(String name) {
        if (name == null || name.isBlank()) return null;
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "headline" -> HEADLINE;
            case "band" -> BAND;
            case "card" -> CARD;
            case "classic" -> CLASSIC;
            default -> null;
        };
    }

    /**
     * 주제 → 템플릿. 팀 결정으로 지금은 주제와 상관없이 꽉 찬 화면형(headline) 하나만 쓴다.
     * band·card는 meta.template 이나 video.template-override 로 지정했을 때만 쓰인다.
     */
    public static VideoTemplate forTheme(String theme) {
        return HEADLINE;
    }

    /** 영상 내내 고정 제목(머리)을 띄우는지 */
    boolean hasHeader() {
        return this != CLASSIC;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
