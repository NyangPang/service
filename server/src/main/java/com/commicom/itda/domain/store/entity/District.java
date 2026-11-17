package com.commicom.itda.domain.store.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 주소에서 자치구(예: "노원구")를 뽑는다.
 * 가게에는 주소 문자열만 저장돼 있어 지역 랭킹·검색은 이 값으로 나눈다.
 */
public final class District {

    /** 주소 맨 앞 몇 토큰 중 "~구"로 끝나는 첫 단어 (예: "서울 노원구 광운로 20" → 노원구) */
    private static final Pattern GU = Pattern.compile("(?:^|\\s)([가-힣]{1,5}구)(?=\\s|$)");

    private District() {
    }

    /** 자치구 이름. 주소에 "~구"가 없으면 null */
    public static String of(String address) {
        if (address == null) return null;
        Matcher m = GU.matcher(address.strip());
        return m.find() ? m.group(1) : null;
    }
}
