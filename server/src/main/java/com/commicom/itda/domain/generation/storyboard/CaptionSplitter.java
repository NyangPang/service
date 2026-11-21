package com.commicom.itda.domain.generation.storyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 장면 자막(= 그 장면 나레이션)을 화면에 차례로 띄울 조각으로 나눈다.
 * 예: "딸기 복숭아 샤인머스켓 골라 먹는 재미예요." → "딸기 복숭아 샤인머스켓" / "골라 먹는 재미예요."
 *
 * <p>렌더러는 조각을 나레이션이 읽히는 속도에 맞춰 바꿔 띄우고({@link #spokenLength}),
 * 검증기는 같은 규칙으로 자막이 {@value #MAX_PARTS}조각 안에 들어가는지 본다({@link #fits}).
 */
public final class CaptionSplitter {

    /** 한 번에 화면에 뜨는 조각 하나의 최대 글자 수 (공백 포함) */
    public static final int PART_MAX = 18;
    /** 한 장면 자막을 나누는 최대 조각 수 */
    public static final int MAX_PARTS = 3;
    /** 이보다 짧은 조각은 너무 빨리 지나가서 피한다 */
    private static final int TINY_PART = 4;

    /** "12,000원", "12,000원이라" — 가격은 앞 단어(메뉴 이름)와 같은 조각에 둔다 */
    private static final Pattern PRICE_WORD = Pattern.compile("\\d[\\d,]*원\\S*");
    /** 이 문장부호 뒤는 말이 쉬는 자리라 끊기 가장 좋다 */
    private static final Pattern PAUSE_END = Pattern.compile(".*[,.!?…~]$");
    /**
     * 조사·이어 주는 말로 끝나는 단어 뒤도 끊기 좋다 ("크림을 / 듬뿍", "과일마다 / 쫀득빵이", "있는데 / 그 안에").
     * "는·은"은 꾸미는 말("넣은 쫀득빵")과 구별이 안 돼서 뺐다
     */
    private static final Pattern PHRASE_END = Pattern.compile(
            ".*(을|를|이|가|에|에서|에게|도|만|까지|마다|부터|처럼|보다|랑|이랑|하고|와|과|로|으로|고|서|데|면|며|지만|니까|게|요)$");
    /** 의존명사 — 앞말과 떨어지면 어색하다 ("고를 / 수 있는", "먹을 / 때", "좋을 / 것 같아요") */
    private static final Pattern BOUND_NOUN = Pattern.compile(
            "(수|것|거|줄|때|데|뿐|만큼|듯|척|채|김|적|지|리)(가|는|도|를|을|이|에|에도|에는|만|밖에|라서|라|야|예요|이에요|입니다|죠)?[,.!?…~]*");
    /** 의존명사 뒤에 붙는 말과도 떨어지면 어색하다 ("수 / 있는", "것 / 같아요", "뿐 / 아니라") */
    private static final Pattern BOUND_NOUN_BARE = Pattern.compile("수|줄|것|거|뿐|듯|척|만큼|리|지");
    /** 보조 용언 — "-아/-어/-고/-게/-지" 뒤에 오면 앞말과 한 덩어리 ("차 / 있고", "먹어 / 보세요", "맵지 / 않은") */
    private static final Pattern AUXILIARY = Pattern.compile(
            "(있|없|싶|않|드리|드려|드립|드린|드릴|버리|버려|버린|버렸|놓|놔|봐|봤|봅|본|볼|줘|줍|준|"
                    + "주(고|는|세요|시는|면|니까|죠|던|지|며)|보(고|는|세요|시는|면|니까|죠|던|지|며|여)|두(고|는|세요|면|었|어)|둬|둔).*"
                    + "|못");

    private CaptionSplitter() {
    }

    /**
     * 조각 수가 가장 적도록, 조각마다 {@value #PART_MAX}자 이내로 띄어쓰기에서 나눈다.
     * 그렇게 나누는 방법이 여럿이면 너무 짧은 조각이 없는 쪽 → 한 덩어리인 말(의존명사·보조 용언)을 가르지 않는 쪽
     * → 문장부호·조사·이어 주는 말 뒤에서 끊는 쪽 → 길이가 고른 쪽.
     * {@value #MAX_PARTS}조각으로도 안 되면 넘치는 글자가 가장 적은 {@value #MAX_PARTS}조각 (검증기가 오류로 알림).
     * 띄어쓰기가 없으면 나누지 않는다.
     */
    public static List<String> split(String text) {
        String line = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (line.length() <= PART_MAX) return List.of(line);
        List<String> words = words(line);
        Candidate best = null;
        for (int parts = 2; parts <= Math.min(MAX_PARTS, words.size()); parts++) {
            best = bestOf(words, parts);
            if (best.overflow == 0) return best.parts;
        }
        return best == null ? List.of(line) : best.parts;
    }

    /** 자막이 {@value #MAX_PARTS}조각(조각마다 {@value #PART_MAX}자) 안에 들어가는지 */
    public static boolean fits(String text) {
        return split(text).stream().allMatch(p -> p.length() <= PART_MAX);
    }

    /** 조각을 읽는 데 걸리는 시간에 비례하는 값 — 가격은 "만 이천 원"처럼 읽는 형태로, 공백·문장부호는 빼고 센다 */
    public static int spokenLength(String part) {
        return Math.max(1, StoryboardValidator.countSpokenChars(SpokenPrice.speak(part)));
    }

    /** 띄어쓰기로 나눈 단어. 가격은 앞 단어에 붙여 둔다 ("로스카츠 12,000원"이 갈라지지 않게) */
    private static List<String> words(String line) {
        List<String> words = new ArrayList<>();
        for (String w : line.split(" ")) {
            if (!words.isEmpty() && PRICE_WORD.matcher(w).matches()) {
                words.set(words.size() - 1, words.getLast() + " " + w);
            } else {
                words.add(w);
            }
        }
        return words;
    }

    private record Candidate(List<String> parts, int overflow, int tiny, int awkward, int breakCost, int spread) {

        static Candidate of(List<String> parts) {
            int overflow = 0, tiny = 0, awkward = 0, breakCost = 0, min = Integer.MAX_VALUE, max = 0;
            for (int i = 0; i < parts.size(); i++) {
                String p = parts.get(i);
                overflow += Math.max(0, p.length() - PART_MAX);
                if (p.length() < TINY_PART) tiny++;
                if (i < parts.size() - 1) {
                    String before = lastWord(p);
                    String after = firstWord(parts.get(i + 1));
                    if (splitsUnit(before, after)) awkward++;
                    breakCost += PAUSE_END.matcher(p).matches() ? 0 : PHRASE_END.matcher(before).matches() ? 1 : 2;
                }
                min = Math.min(min, p.length());
                max = Math.max(max, p.length());
            }
            return new Candidate(parts, overflow, tiny, awkward, breakCost, max - min);
        }

        boolean betterThan(Candidate o) {
            if (overflow != o.overflow) return overflow < o.overflow;
            if (tiny != o.tiny) return tiny < o.tiny;
            if (awkward != o.awkward) return awkward < o.awkward;
            if (breakCost != o.breakCost) return breakCost < o.breakCost;
            return spread < o.spread;
        }
    }

    /** before와 after 사이에서 끊으면 한 덩어리인 말이 갈라지는지 */
    static boolean splitsUnit(String before, String after) {
        if (BOUND_NOUN.matcher(after).matches()) return true;
        if (BOUND_NOUN_BARE.matcher(before).matches()) return true;
        return AUXILIARY.matcher(after).matches() && endsLikeConnective(before);
    }

    /** "-아/-어"(차, 넣어, 구워, 해), "-고", "-게", "-지"로 끝나는 말 — 보조 용언이 뒤따를 수 있는 자리 */
    private static boolean endsLikeConnective(String word) {
        String w = word.replaceAll("[^가-힣]", "");
        if (w.isEmpty()) return false;
        char last = w.charAt(w.length() - 1);
        if (last == '고' || last == '게' || last == '지') return true;
        int index = last - '가';
        if (index % 28 != 0) return false;   // 받침이 있으면 "-아/-어" 꼴이 아님
        int vowel = index / 28 % 21;
        // ㅏ ㅐ ㅓ ㅕ ㅘ ㅙ ㅝ
        return vowel == 0 || vowel == 1 || vowel == 4 || vowel == 6 || vowel == 9 || vowel == 10 || vowel == 14;
    }

    private static String lastWord(String part) {
        return part.substring(part.lastIndexOf(' ') + 1);
    }

    private static String firstWord(String part) {
        int sp = part.indexOf(' ');
        return sp < 0 ? part : part.substring(0, sp);
    }

    /** 단어 목록을 parts조각으로 나누는 모든 방법 중 가장 나은 것 (단어는 많아야 스무 개 남짓이라 다 따져 봐도 빠름) */
    private static Candidate bestOf(List<String> words, int parts) {
        Candidate[] best = {null};
        choose(words, parts, 0, new ArrayList<>(), best);
        return best[0];
    }

    private static void choose(List<String> words, int partsLeft, int from, List<String> acc, Candidate[] best) {
        if (partsLeft == 1) {
            List<String> parts = new ArrayList<>(acc);
            parts.add(String.join(" ", words.subList(from, words.size())));
            Candidate c = Candidate.of(parts);
            if (best[0] == null || c.betterThan(best[0])) best[0] = c;
            return;
        }
        // 남은 조각마다 단어가 하나 이상 남도록
        for (int to = from + 1; to <= words.size() - (partsLeft - 1); to++) {
            acc.add(String.join(" ", words.subList(from, to)));
            choose(words, partsLeft - 1, to, acc, best);
            acc.removeLast();
        }
    }
}
