package com.commicom.itda.domain.generation.storyboard;

/** 숏폼 생성 느낌 — 강조 포인트 (여러 개). 장면 JSON 프롬프트에 힌트 한 줄씩으로 들어간다 */
public enum Emphasis {

    FAST_CUTS("빠른 장면 전환", "장면을 6개로 나누고 장면 길이를 2~2.5초로 짧게 잡습니다. 바로 붙은 두 장면은 다른 사진을 씁니다."),
    MENU_CLOSEUP("메뉴 클로즈업", "음식 장면의 video_prompt는 음식에 바짝 다가간 extreme close-up, macro shot으로 씁니다. 사진도 음식 사진을 먼저 고릅니다."),
    BIG_CAPTION("큰 자막", "가격 자막(price)이 아닌 모든 자막의 caption.emphasis를 \"large\"로 둡니다."),
    FAST_TEMPO("빠른 템포", "theme은 trendy나 value, bgm.mood는 bright_pop이나 upbeat_acoustic으로 고르고, video_prompt의 움직임을 경쾌하고 빠르게 씁니다."),
    SLOW_TEMPO("느린 템포", "장면을 4개로 줄여 한 장면을 길게 보여 주고, theme은 emotional이나 expert, bgm.mood는 calm_lofi나 jazz로 고릅니다. video_prompt의 움직임은 느리고 부드럽게 씁니다.");

    private final String label;
    private final String hint;

    Emphasis(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    /** 장면 JSON 프롬프트에 넣을 규칙 한 줄 */
    public String hint() {
        return hint;
    }
}
