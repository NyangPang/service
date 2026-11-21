package com.commicom.itda.infra.video.storyboard;

/**
 * 나레이션 목소리. 영상 스타일에 따라 고른다 (인물 소개·신메뉴 알림 영상은 남자 목소리).
 * Polly면 DEFAULT = aws.polly.voice-id(Seoyeon), MALE = aws.polly.male-voice-id(Hyunduk, generative 엔진).
 */
public enum NarrationVoice {
    DEFAULT,
    MALE
}
