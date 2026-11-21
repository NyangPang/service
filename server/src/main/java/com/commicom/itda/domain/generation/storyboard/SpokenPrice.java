package com.commicom.itda.domain.generation.storyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 나레이션 속 가격("12,000원")을 TTS가 읽는 그대로("만 이천 원") 바꾼다.
 * TTS마다 숫자를 읽는 방식이 달라(영어로 읽거나 "일만"으로 읽음) 미리 한글로 풀어 준다.
 * 검사기도 글자 수를 셀 때 이 읽는 형태로 센다.
 */
public final class SpokenPrice {

    /** "12,000원", "4500원" */
    static final Pattern PRICE = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");

    private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL_UNITS = {"천", "백", "십", ""};

    private SpokenPrice() {
    }

    /** 문장 속 가격을 모두 한글 읽기로 바꾼다. 가격이 없으면 그대로 */
    public static String speak(String text) {
        if (text == null) return null;
        Matcher m = PRICE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long value = Long.parseLong(m.group(1).replace(",", ""));
            m.appendReplacement(out, Matcher.quoteReplacement(read(value) + " 원"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** 12000 → "만 이천", 25900 → "이만 오천구백", 8000 → "팔천" */
    static String read(long n) {
        if (n == 0) return "영";
        List<String> parts = new ArrayList<>();
        long eok = n / 100_000_000;
        long man = n / 10_000 % 10_000;
        long rest = n % 10_000;
        if (eok > 0) parts.add(group((int) eok) + "억");
        if (man > 0) parts.add((man == 1 ? "" : group((int) man)) + "만");
        if (rest > 0) parts.add(group((int) rest));
        return String.join(" ", parts);
    }

    /** 0~9999 */
    private static String group(int n) {
        StringBuilder sb = new StringBuilder();
        int[] d = {n / 1000, n / 100 % 10, n / 10 % 10, n % 10};
        for (int i = 0; i < 4; i++) {
            if (d[i] == 0) continue;
            boolean unit = i < 3;
            sb.append(d[i] == 1 && unit ? "" : DIGITS[d[i]]).append(SMALL_UNITS[i]);
        }
        return sb.toString();
    }
}
