package com.commicom.itda.domain.generation.storyboard;

/**
 * 숏폼 생성 느낌 — 영상 스타일 (앱 "어떤 느낌으로 만들까요?" 3개 중 1개).
 * 스타일마다 장면 JSON 프롬프트(prompts/storyboard-style-*.txt), 메뉴 추출 단계의 이미지 생성 요청,
 * 검사 규칙, 나레이션 목소리가 달라진다. 고르지 않으면(null) 기존 프롬프트·기본 목소리 그대로.
 */
public enum VideoStyle {

    /** 메뉴가 잘 보이는 영상 — 대표 메뉴 두 개 근접샷 + 한 상 샷, 메뉴·가격만 짧게 */
    MENU_FOCUS("메뉴가 잘 보이는 영상", false, 1),
    /** 인물이 소개하는 영상 — 기존 프롬프트 그대로, 남자 목소리 */
    PERSON_INTRO("인물이 소개하는 영상", true, 0),
    /** 신메뉴 및 할인 알림 영상 — 신메뉴 + 할인·이벤트, 외관으로 마무리, 남자 목소리 */
    NEWS_PROMO("신메뉴 및 할인 알림 영상", true, 2);

    private final String label;
    private final boolean maleVoice;
    private final int extraImages;

    VideoStyle(String label, boolean maleVoice, int extraImages) {
        this.label = label;
        this.maleVoice = maleVoice;
        this.extraImages = extraImages;
    }

    public String label() {
        return label;
    }

    /** 나레이션을 남자 목소리로 읽는지 */
    public boolean maleVoice() {
        return maleVoice;
    }

    /** 메뉴 추출 단계에서 기본 개수(3 − 쓸 수 있는 사진 수)보다 더 요청할 수 있는 생성 이미지 수 */
    public int extraImages() {
        return extraImages;
    }

    /** 프롬프트 파일에 따로 규칙이 있는 스타일인지 (PERSON_INTRO는 기존 프롬프트 그대로) */
    public boolean hasOwnPrompt() {
        return this != PERSON_INTRO;
    }
}
