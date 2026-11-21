package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuData.ImageRequest;
import com.commicom.itda.domain.generation.storyboard.MenuData.Menu;
import com.commicom.itda.domain.generation.storyboard.MenuData.Photo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메뉴 추출 결과 검증. 스토리보드 단계가 믿고 쓸 수 있도록 가격·사진 번호·메뉴 연결을 확인한다.
 * 오류 문구는 그대로 LLM 재요청에 붙는다.
 */
@Component
public class MenuExtractionValidator {

    static final Set<String> KINDS = Set.of("food", "store", "exterior", "menu_board");
    /** 영상에 쓸 사진 목표 수. 쓸 수 있는 사진이 이보다 적으면 모자란 만큼 이미지를 만든다 */
    public static final int TARGET_PHOTOS = 3;
    static final int MIN_PRICE = 100;
    static final int MAX_PRICE = 1_000_000;
    static final int MAX_IMAGE_REQUESTS = 3;
    static final int IMAGE_PROMPT_MAX_WORDS = 40;

    private static final Pattern COMMA_NUMBER = Pattern.compile("\\d{1,3}(,\\d{3})+");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d{3,7}");
    private static final Pattern THOUSAND_DECIMAL = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
    private static final Pattern THOUSAND_SHORT = Pattern.compile("\\d{1,2}");
    private static final Pattern KOREAN_UNITS = Pattern.compile("(?:(\\d+)만)?(?:(\\d+)천)?");

    /** @param photoCount 사진 칸에 올린 이미지 수 (메뉴판 칸 제외). photos[]가 정확히 이만큼이어야 한다 */
    public List<String> validate(MenuData data, int photoCount) {
        return validate(data, photoCount, 0);
    }

    /** @param extraImages 영상 스타일 때문에 기본 개수보다 더 요청해도 되는 생성 이미지 수 ({@link StyleImages#extra()}) */
    public List<String> validate(MenuData data, int photoCount, int extraImages) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            errors.add("결과가 비어 있습니다.");
            return errors;
        }
        Set<String> names = checkMenus(data.menus(), errors);
        checkPhotos(data.photos(), photoCount, names, errors);
        checkImageRequests(data, names, Math.max(0, extraImages), errors);
        return errors;
    }

    private Set<String> checkMenus(List<Menu> menus, List<String> errors) {
        Set<String> names = new HashSet<>();
        if (menus == null) {
            errors.add("menus가 없습니다. 메뉴를 읽지 못했으면 빈 배열 []로 두세요.");
            return names;
        }
        for (int i = 0; i < menus.size(); i++) {
            Menu m = menus.get(i);
            if (m == null || m.name() == null || m.name().isBlank()) {
                errors.add("menus[" + i + "]의 name이 비어 있습니다.");
                continue;
            }
            String at = "[메뉴 \"" + m.name() + "\"]";
            if (!names.add(m.name())) {
                errors.add(at + " 같은 이름의 메뉴가 두 번 나옵니다. 하나로 합치세요.");
            }
            Integer price = m.price();
            if (price != null && (price < MIN_PRICE || price > MAX_PRICE || price % 10 != 0)) {
                errors.add(at + " price " + price + "원은 가격으로 보기 어렵습니다. 원 단위 정수로 다시 확인하고, 확신이 없으면 null로 두세요.");
            }
            if (price != null && m.priceText() == null) {
                errors.add(at + " price가 있으면 price_text에 그 가격의 원래 글자를 넣으세요.");
            }
            if (price != null && m.priceText() != null) {
                Integer expected = parsePriceText(m.priceText());
                if (expected != null && !expected.equals(price)) {
                    errors.add(at + " price_text \"" + m.priceText() + "\"는 " + won(expected) + "인데 price가 " + won(price)
                            + "입니다. 메뉴판을 다시 보고 맞추세요.");
                }
            }
        }
        return names;
    }

    private void checkPhotos(List<Photo> photos, int photoCount, Set<String> names, List<String> errors) {
        if (photos == null) {
            errors.add("photos가 없습니다. index가 있는 사진마다 하나씩 넣고, 사진이 없으면 빈 배열 []로 두세요.");
            return;
        }
        if (photos.size() != photoCount) {
            errors.add("photos가 " + photos.size() + "개입니다. index가 있는 사진 " + photoCount + "장마다 하나씩, 정확히 "
                    + photoCount + "개여야 합니다. 메뉴판 이미지는 photos에 넣지 마세요.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Photo p : photos) {
            if (p == null) continue;
            String at = "[사진 index=" + p.index() + "]";
            if (p.index() < 0 || p.index() >= photoCount) {
                errors.add(at + " index는 0~" + (photoCount - 1) + " 사이여야 합니다.");
            } else if (!seen.add(p.index())) {
                errors.add(at + " 같은 index가 두 번 나옵니다.");
            }
            if (!"upload".equals(p.source())) {
                errors.add(at + " source는 \"upload\"여야 합니다. (현재: " + quote(p.source()) + ")");
            }
            if (p.kind() == null || !KINDS.contains(p.kind())) {
                errors.add(at + " kind 값 " + quote(p.kind()) + "는 허용되지 않습니다. [food, store, exterior, menu_board] 중 하나를 쓰세요.");
            }
            if (p.menuRef() != null) {
                if (!"food".equals(p.kind())) {
                    errors.add(at + " " + p.kind() + " 사진의 menu_ref는 null이어야 합니다.");
                } else if (!names.contains(p.menuRef())) {
                    errors.add(at + " menu_ref \"" + p.menuRef() + "\"는 menus에 없습니다. menus[].name과 똑같이 쓰거나 null로 두세요.");
                }
            }
            if (p.description() == null || p.description().isBlank()) {
                errors.add(at + " description이 비어 있습니다. 사진에 보이는 것을 한 줄로 쓰세요.");
            }
        }
        for (int i = 0; i < photoCount; i++) {
            if (!seen.contains(i)) {
                errors.add("[사진 index=" + i + "] 이 사진이 photos에 없습니다.");
            }
        }
    }

    /**
     * 쓸 수 있는 사진(메뉴판 제외)이 {@value #TARGET_PHOTOS}장보다 적으면 모자란 만큼 만든다. 3장 이상이면 [].
     * 메뉴 하나(menu_ref)나 여러 메뉴를 함께 담은 모습(menu_ref null) 모두 된다. prompt는 장면 video_prompt와 같은 금지어 규칙
     */
    private void checkImageRequests(MenuData data, Set<String> names, int extra, List<String> errors) {
        List<ImageRequest> requests = data.imageRequests();
        if (requests == null) {
            errors.add("image_requests가 없습니다. 만들 이미지가 없으면 빈 배열 []로 두세요.");
            return;
        }
        long usable = data.usablePhotoCount();
        int needed = (int) Math.max(0, TARGET_PHOTOS - usable);

        if (names.isEmpty()) {
            if (!requests.isEmpty()) {
                errors.add("menus가 비어 있으면 image_requests도 빈 배열 []로 두세요. 그릴 메뉴를 정할 수 없습니다.");
            }
            return;
        }
        if (extra > 0 && (requests.size() < needed || requests.size() > needed + extra)) {
            errors.add("영상에 쓸 수 있는 사진(메뉴판 제외)이 " + usable + "장이므로 image_requests는 " + needed + "개에 "
                    + "[영상 스타일 이미지] 규칙대로 최대 " + extra + "개를 더한 " + needed + "~" + (needed + extra)
                    + "개여야 합니다. (현재 " + requests.size() + "개)");
        } else if (extra == 0 && requests.size() != needed) {
            errors.add("영상에 쓸 수 있는 사진(메뉴판 제외)이 " + usable + "장이므로 image_requests는 정확히 "
                    + needed + "개여야 합니다. (현재 " + requests.size() + "개) 사진이 " + TARGET_PHOTOS
                    + "장이 되도록 모자란 만큼만 만들고, " + TARGET_PHOTOS + "장 이상이면 빈 배열 []로 두세요.");
        }

        Set<String> requested = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            ImageRequest r = requests.get(i);
            if (r == null) continue;
            String at = "[image_requests[" + i + "]]";
            if (r.menuRef() != null) {
                if (!names.contains(r.menuRef())) {
                    errors.add(at + " menu_ref " + quote(r.menuRef()) + "는 menus에 없습니다. menus[].name과 똑같이 쓰거나, 여러 메뉴를 함께 그리면 null로 두세요.");
                } else if (!requested.add(r.menuRef())) {
                    errors.add(at + " \"" + r.menuRef() + "\" 하나만 담은 이미지를 두 번 요청했습니다. 다른 메뉴나 여러 메뉴 조합으로 바꾸세요.");
                }
            }
            checkImagePrompt(r.prompt(), at, errors);
        }
    }

    private void checkImagePrompt(String p, String at, List<String> errors) {
        if (p == null || p.isBlank()) {
            errors.add(at + " prompt가 비어 있습니다. 그릴 음식을 영어로 설명하세요.");
            return;
        }
        if (StoryboardValidator.HANGUL.matcher(p).find()) {
            errors.add(at + " prompt는 영어로만 쓰세요. (\"" + p + "\")");
        }
        int words = p.trim().split("\s+").length;
        if (words > IMAGE_PROMPT_MAX_WORDS) {
            errors.add(at + " prompt가 " + words + "단어입니다. " + IMAGE_PROMPT_MAX_WORDS + "단어 이내로 줄이세요.");
        }
        List<String> text = StoryboardValidator.findAll(StoryboardValidator.TEXT_WORDS, p);
        if (!text.isEmpty()) {
            errors.add(at + " prompt에 글자·간판·로고 관련 단어(" + String.join(", ", text)
                    + ")가 있습니다. 이미지에 글자가 생기지 않도록 빼세요.");
        }
        List<String> people = StoryboardValidator.findAll(StoryboardValidator.PERSON_WORDS, p);
        if (!people.isEmpty()) {
            errors.add(at + " prompt에 사람·신체를 가리키는 단어(" + String.join(", ", people)
                    + ")가 있습니다. 생성 이미지에서 사람과 손·얼굴은 뭉개지니 빼고 음식과 배경만 묘사하세요.");
        }
        List<String> actions = StoryboardValidator.findAll(StoryboardValidator.HAND_ACTION_WORDS, p);
        if (!actions.isEmpty()) {
            errors.add(at + " prompt에 손이 있어야 하는 동작(" + String.join(", ", actions)
                    + ")이 있습니다. 손이 그려지니 접시에 놓인 음식 모습으로 바꾸세요.");
        }
    }

    /**
     * 메뉴판 가격 글자 → 원. 형식이 애매하면 null (그때는 검사하지 않음).
     * "12,000원" → 12000, "4.5" → 4500, "12" → 12000, "1만2천원" → 12000
     */
    static Integer parsePriceText(String text) {
        if (text == null) return null;
        String s = text.replaceAll("[\\s원₩]", "");
        if (s.isEmpty()) return null;
        try {
            if (COMMA_NUMBER.matcher(s).matches()) return Integer.parseInt(s.replace(",", ""));
            if (PLAIN_NUMBER.matcher(s).matches()) return Integer.parseInt(s);
            if (THOUSAND_DECIMAL.matcher(s).matches()) return (int) Math.round(Double.parseDouble(s) * 1000);
            if (THOUSAND_SHORT.matcher(s).matches()) return Integer.parseInt(s) * 1000;
            Matcher m = KOREAN_UNITS.matcher(s);
            if (m.matches() && (m.group(1) != null || m.group(2) != null)) {
                int man = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
                int chun = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                return man * 10_000 + chun * 1_000;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static String won(int price) {
        return String.format("%,d원", price);
    }
}
