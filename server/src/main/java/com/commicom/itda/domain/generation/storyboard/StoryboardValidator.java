package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuData.Menu;
import com.commicom.itda.domain.generation.storyboard.MenuData.Photo;
import com.commicom.itda.domain.generation.storyboard.Storyboard.Caption;
import com.commicom.itda.domain.generation.storyboard.Storyboard.Scene;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 장면 JSON 검증 (docs/storyboard-schema.md 4절의 9개 규칙).
 * 오류 문구는 그대로 LLM 재요청에 붙이므로, 어느 장면의 어떤 필드를 어떻게 고쳐야 하는지 드러나게 쓴다.
 */
@Component
public class StoryboardValidator {

    public static final String SCHEMA_VERSION = "0.1";
    public static final double TOTAL_DURATION = 15;

    /** 영상 주제: 감성 / 가성비(싼 가격·많은 양) / 트렌디 / 전문 / 신선함 */
    static final Set<String> THEMES = Set.of("emotional", "value", "trendy", "expert", "fresh");
    static final Set<String> STYLE_MOODS = Set.of("warm", "fresh", "cozy", "lively");
    static final Set<String> FONTS = Set.of("gothic_bold", "gothic_regular");
    static final Set<String> BGM_MOODS = Set.of("upbeat_acoustic", "calm_lofi", "bright_pop", "jazz");
    static final Set<String> SCENE_TYPES = Set.of("hook", "menu", "detail", "review", "cta");
    /** transition = 앞 장면 사진 → 이 장면 외관 사진을 AI가 이어 주는 장면 ({@link #checkTransition}) */
    static final Set<String> MOTIONS = Set.of("ai", "transition", "zoom_in", "zoom_out", "static");
    static final int MAX_TRANSITIONS = 1;
    /** bottom = 화면 가운데보다 조금 아래 왼쪽(기본), center = 정중앙 (가게 이름) */
    static final Set<String> POSITIONS = Set.of("center", "bottom");
    static final Set<String> EMPHASES = Set.of("normal", "large", "price");

    static final int TITLE_MAX = 20;
    /**
     * 자막 = 그 장면 나레이션 전체. 렌더러가 {@link CaptionSplitter}로 18자 안팎 조각 3개까지 나눠
     * 말하는 속도에 맞춰 차례로 띄운다. 말 속도는 따로 duration × {@value #NARRATION_CHARS_PER_SEC}자로 막는다
     */
    static final int CAPTION_MAX = 40;
    static final int MIN_SCENES = 4;
    static final int MAX_SCENES = 6;
    static final double MIN_SCENE_SEC = 1.5;
    static final double MAX_SCENE_SEC = 5;
    static final double HOOK_MAX_SEC = 2;
    static final int MAX_AI_SCENES = 3;
    /** 동작 하나 + 음식 묘사 + 카메라 + 조명 + 분위기를 담을 만큼 (Runway promptText는 1000자까지) */
    static final int VIDEO_PROMPT_MAX_WORDS = 60;
    /** 빠르게 말하는 쇼츠 기준 (레퍼런스 맛집 쇼츠가 초당 약 7자). 가격은 읽는 형태("만 이천 원")로 센다 */
    static final int NARRATION_CHARS_PER_SEC = 7;

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    /** 자막 속 가격 후보: 숫자 뒤에 (만)원 */
    private static final Pattern PRICE_IN_TEXT = Pattern.compile("\\d[\\d,.]*\\s*만?\\s*원");
    private static final Pattern PRICE_FORMAT = Pattern.compile("^\\d{1,3}(,\\d{3})*원$");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** 나레이션 속 한글 금액 ("만 원", "만이천원", "천 원") */
    private static final Pattern KOREAN_PRICE = Pattern.compile("[천만]\\s*원");
    /** 같은 어미가 되풀이되는지 볼 때 나레이션 끝 글자 수 */
    static final int ENDING_CHARS = 3;
    static final Pattern HANGUL = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣ]");
    /** Runway가 글자를 뭉개서 그리므로 글자·간판·로고를 떠올리게 하는 단어 금지 */
    static final Pattern TEXT_WORDS = Pattern.compile(
            "\\b(text|texts|letter|letters|lettering|word|words|writing|written|handwriting|sign|signs|signage|signboard|"
                    + "logo|logos|label|labels|caption|captions|subtitle|subtitles|typography|font|fonts|brand|branding|"
                    + "poster|posters|banner|banners|watermark|menu board|chalkboard|neon sign)\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * Runway가 사람 전체·얼굴·손가락을 뭉개서 그리므로 사람과 그런 신체를 가리키는 단어 금지.
     * 손(hand, hands)은 허용한다 — "a gloved hand uses tongs to pick up the bread"처럼 손과 도구만 화면에 나오는 동작이
     * 카메라 무빙보다 영상을 훨씬 살아 있게 만든다. 손가락·팔처럼 더 드러나는 신체는 여전히 막는다.
     * mouth-watering(군침 도는)처럼 음식 묘사에 흔한 표현은 뺀다.
     */
    static final Pattern PERSON_WORDS = Pattern.compile(
            "\\b(person|persons|people|human|humans|man|men|woman|women|boy|boys|girl|girls|child|children|kid|kids|"
                    + "someone|somebody|crowd|crowded|friend|friends|portrait|selfie|"
                    + "chef|chefs|baker|bakers|barista|baristas|bartender|bartenders|waiter|waiters|waitress|waitresses|"
                    + "staff|employee|employees|worker|workers|owner|owners|customer|customers|guest|guests|"
                    + "finger|fingers|fingertip|fingertips|thumb|thumbs|arm|arms|elbow|elbows|"
                    + "face|faces|mouth(?![-\\s]?watering)|lip|lips)\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * 먹기·마시기처럼 입이 있어야 하는 동작을 쓰면 Runway가 얼굴을 그려 넣으므로 금지.
     * 집기·옮기기·찢기·자르기는 손과 도구(집게·포크·나이프)로 하는 동작이라 허용한다.
     */
    static final Pattern MOUTH_ACTION_WORDS = Pattern.compile(
            "\\b(biting|eat|eats|eating|drinking|sip|sips|sipping|chewing|licking|tasting)\\b",
            Pattern.CASE_INSENSITIVE);
    /** 정지 이미지(메뉴 이미지 생성)에는 동작이 없으니 손도 그리지 않는다 — {@link MenuExtractionValidator}에서 씀 */
    static final Pattern STILL_HAND_WORDS = Pattern.compile("\\b(hand|hands|wrist|wrists)\\b", Pattern.CASE_INSENSITIVE);
    /** 정지 이미지에서 손이 있어야 하는 동작 (손이 그려진다). freshly poured / picked처럼 끝난 상태는 허용 */
    static final Pattern HAND_ACTION_WORDS = Pattern.compile(
            "\\b(hold|holds|holding|held|lift|lifts|lifting|lifted|pick|picks|picking|grab|grabs|grabbing|grabbed|"
                    + "stir(?![-\\s]?fr)|stirs|stirring|stirred|scooping|"
                    + "biting|eat|eats|eating|drinking|sip|sips|sipping)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 메뉴가 잘 보이는 영상: 나레이션 전체(공백·문장부호 빼고, 가격은 읽는 대로) 최대 글자 수. 프롬프트는 55자로 안내 */
    static final int MENU_FOCUS_NARRATION_MAX = 65;
    /** 신메뉴 알림 영상에서 사장님 한마디에 있으면 나레이션에 써도 되는 숫자 ("20%", "30", "2.5") */
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)*\\s*%?");

    public List<String> validate(Storyboard sb, MenuData menu) {
        return validate(sb, menu, VideoOptions.NONE, null);
    }

    /**
     * @param options 생성 느낌. 메뉴가 잘 보이는 영상은 사진·나레이션 규칙이, 신메뉴 알림 영상은 숫자 규칙이 달라진다
     * @param appeal  사장님 한마디 (신메뉴 알림 영상에서 할인율·날짜 숫자를 허용할 근거)
     */
    public List<String> validate(Storyboard sb, MenuData menu, VideoOptions options, String appeal) {
        List<String> errors = new ArrayList<>();
        if (sb == null) {
            errors.add("장면 JSON이 비어 있습니다.");
            return errors;
        }
        VideoOptions o = options == null ? VideoOptions.NONE : options;
        boolean menuFocus = o.is(VideoStyle.MENU_FOCUS);
        Set<String> allowedNumbers = o.is(VideoStyle.NEWS_PROMO) ? numberTokens(appeal) : Set.of();
        checkTopLevel(sb, errors);
        checkTitle(sb.title(), menu, errors);

        List<Scene> scenes = sb.scenes() == null ? List.of() : sb.scenes();
        checkStructure(scenes, errors);
        checkDurations(sb, scenes, errors);

        checkOpeningAndEnding(scenes, menu, !menuFocus, errors);
        checkNarrationEndings(scenes, errors);
        if (menuFocus) checkMenuFocus(scenes, menu, errors);

        int aiCount = 0;
        int transitionCount = 0;
        /** 사진 번호 → 그 사진으로 AI 클립을 처음 만든 장면 */
        Map<Integer, String> aiPhotos = new HashMap<>();
        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            if (scene == null) {
                errors.add("scenes[" + i + "]가 비어 있습니다.");
                continue;
            }
            String at = where(scene, i);
            checkSceneFields(scene, at, errors);
            checkCaption(scene, at, menu, errors);
            if (i < scenes.size() - 1) checkCaptionIsNarration(scene, at, errors);
            checkNarration(scene, at, menu, allowedNumbers, errors);
            checkPhoto(scene, at, menu, errors);
            if ("ai".equals(scene.motion())) {
                aiCount++;
                checkVideoPrompt(scene, at, errors);
                checkAiPhotoReuse(scene, at, aiPhotos, errors);
            } else if ("transition".equals(scene.motion())) {
                transitionCount++;
                checkTransition(scenes, i, at, menu, errors);
            }
        }
        checkEveryPhotoMoves(scenes, menu, errors);
        if (aiCount > MAX_AI_SCENES) {
            errors.add("motion이 \"ai\"인 장면이 " + aiCount + "개입니다. 최대 " + MAX_AI_SCENES
                    + "개까지만 쓰고, 나머지는 zoom_in / zoom_out / static으로 바꾸세요.");
        }
        if (transitionCount > MAX_TRANSITIONS) {
            errors.add("motion이 \"transition\"인 장면이 " + transitionCount + "개입니다. 외관 사진으로 넘어가는 장면 " + MAX_TRANSITIONS
                    + "개에만 쓰고, 나머지는 zoom_in / zoom_out / static으로 바꾸세요.");
        }
        return errors;
    }

    // ── 1. 최상위 필드 ───────────────────────────────────────────

    private void checkTopLevel(Storyboard sb, List<String> errors) {
        if (!SCHEMA_VERSION.equals(sb.schemaVersion())) {
            errors.add("schema_version은 \"" + SCHEMA_VERSION + "\"이어야 합니다. (현재: " + quote(sb.schemaVersion()) + ")");
        }
        if (isBlank(sb.title())) {
            errors.add("title이 비어 있습니다.");
        } else if (sb.title().length() > TITLE_MAX) {
            errors.add("title은 " + TITLE_MAX + "자 이내여야 합니다. (현재 " + sb.title().length() + "자: \"" + sb.title() + "\")");
        }
        if (sb.totalDuration() == null || !same(sb.totalDuration(), TOTAL_DURATION)) {
            errors.add("total_duration은 " + fmt(TOTAL_DURATION) + "이어야 합니다. (현재: " + fmt(sb.totalDuration()) + ")");
        }
        checkEnum("theme", sb.theme(), THEMES, errors);
        if (sb.style() == null) {
            errors.add("style이 없습니다. mood, font, main_color를 넣으세요.");
        } else {
            checkEnum("style.mood", sb.style().mood(), STYLE_MOODS, errors);
            checkEnum("style.font", sb.style().font(), FONTS, errors);
            if (sb.style().mainColor() == null || !HEX_COLOR.matcher(sb.style().mainColor()).matches()) {
                errors.add("style.main_color는 #RRGGBB 형식이어야 합니다. (현재: " + quote(sb.style().mainColor()) + ")");
            }
        }
        if (sb.bgm() == null) {
            errors.add("bgm이 없습니다. bgm.mood를 넣으세요.");
        } else {
            checkEnum("bgm.mood", sb.bgm().mood(), BGM_MOODS, errors);
        }
        if (sb.scenes() == null) {
            errors.add("scenes가 없습니다.");
        }
    }

    // ── 1-1. 제목 (영상 위 고정 후킹 문구) ─────────────────────────

    /** 렌더러는 띄어쓰기 빼고 이 글자 수 이하면 한 줄, 넘으면 가운데 띄어쓰기에서 두 줄로 나눈다 */
    static final int TITLE_ONE_LINE_MAX = 9;

    private void checkTitle(String title, MenuData menu, List<String> errors) {
        if (isBlank(title)) return; // 비었거나 긴 제목은 checkTopLevel에서 알림
        String t = title.strip();
        String emoji = findEmoji(t);
        if (emoji != null) {
            errors.add("title에 이모지·그림 기호(" + emoji + ")가 있습니다. 화면 제목 글자가 깨지니 빼세요. (\"" + t + "\")");
        }
        if (t.replace(" ", "").length() > TITLE_ONE_LINE_MAX && !t.contains(" ")) {
            errors.add("title \"" + t + "\"에 띄어쓰기가 없습니다. 화면에서 윗줄(작게) / 아랫줄(크게)로 나뉘도록 "
                    + "\"퇴근길 달달할 땐 골목 끝 소금빵\"처럼 띄어쓰기를 넣어 두 덩어리로 쓰세요.");
        }
        if (menu != null) {
            String name = compact(storeLabel(menu.storeName()));
            if (!name.isEmpty() && compact(t).equals(name)) {
                errors.add("title이 가게 이름뿐입니다. 가게 이름은 화면에 따로 나오니 사장님 한마디나 대표 메뉴로 후킹 문구를 쓰세요.");
            }
            Matcher m = PRICE_IN_TEXT.matcher(t);
            while (m.find()) {
                String p = m.group().replaceAll("\s", "");
                boolean known = PRICE_FORMAT.matcher(p).matches() && menu.menus() != null && menu.menus().stream()
                        .anyMatch(x -> x != null && x.price() != null && x.price() == Integer.parseInt(p.replaceAll("[^0-9]", "")));
                if (!known) {
                    errors.add("title 가격 \"" + m.group() + "\"이 메뉴 데이터의 가격과 맞지 않습니다. 메뉴 가격을 \"5,000원\" 형식으로 쓰거나 빼세요.");
                }
            }
        }
    }

    // ── 3. 구성 ──────────────────────────────────────────────────

    private void checkStructure(List<Scene> scenes, List<String> errors) {
        if (scenes.size() < MIN_SCENES || scenes.size() > MAX_SCENES) {
            errors.add("장면은 " + MIN_SCENES + "~" + MAX_SCENES + "개여야 합니다. (현재 " + scenes.size() + "개)");
        }
        if (scenes.isEmpty()) return;

        Scene first = scenes.get(0);
        Scene last = scenes.get(scenes.size() - 1);
        if (first != null && !"hook".equals(first.type())) {
            errors.add("첫 장면의 type은 \"hook\"이어야 합니다. (현재: " + quote(first.type()) + ")");
        }
        if (last != null && !"cta".equals(last.type())) {
            errors.add("마지막 장면의 type은 \"cta\"여야 합니다. (현재: " + quote(last.type()) + ")");
        }

        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s == null) continue;
            if (s.order() == null || s.order() != i + 1) {
                errors.add("scenes[" + i + "]의 order는 " + (i + 1) + "이어야 합니다. 배열 순서대로 1부터 연속으로 매기세요. (현재: " + s.order() + ")");
            }
            if (s.id() == null) {
                errors.add("scenes[" + i + "]에 id가 없습니다.");
            } else if (!ids.add(s.id())) {
                errors.add("장면 id " + s.id() + "가 중복됩니다. id는 장면마다 달라야 합니다.");
            }
        }
    }

    // ── 3-1. 끝은 가게 외관 + 가게 이름 ─────────────────
    // 시작은 가게 이름이 아니라 멈춰 보게 만드는 한마디(hook)라서 따로 검사하지 않는다.

    /**
     * 메뉴가 잘 보이는 영상: 음식 사진만, 외관으로 넘어가는 transition 없이, 마지막은 한 상 사진(여러 메뉴가 함께 있는 food 사진),
     * 나레이션은 짧게
     */
    private void checkMenuFocus(List<Scene> scenes, MenuData menu, List<String> errors) {
        if (scenes.isEmpty() || menu == null) return;
        int spoken = 0;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s == null) continue;
            String at = where(s, i);
            Optional<Photo> photo = menu.findPhoto(s.imageIndex());
            if (photo.isPresent() && ("exterior".equals(photo.get().kind()) || "store".equals(photo.get().kind()))) {
                errors.add(at + " " + photo.get().index() + "번은 가게 " + ("exterior".equals(photo.get().kind()) ? "외관" : "안")
                        + " 사진입니다. 메뉴가 잘 보이는 영상은 음식(food) 사진만 씁니다.");
            }
            if ("transition".equals(s.motion())) {
                errors.add(at + " 메뉴가 잘 보이는 영상은 외관으로 넘어가지 않으니 motion \"transition\"을 쓰지 마세요.");
            }
            if (!isBlank(s.narration())) spoken += countSpokenChars(SpokenPrice.speak(s.narration()));
        }
        // 한 상 사진: 여러 메뉴가 함께 있는 food 사진(menu_ref null). AI가 만든 것이 있으면 그것 (사장님 사진의 null은 메뉴를 몰라서일 수 있음)
        List<Photo> multi = menu.photos() == null ? List.of() : menu.photos().stream()
                .filter(p -> p != null && "food".equals(p.kind()) && p.menuRef() == null).toList();
        List<Photo> generatedMulti = multi.stream().filter(Photo::isGenerated).toList();
        List<Photo> spreads = generatedMulti.isEmpty() ? multi : generatedMulti;
        Scene last = scenes.get(scenes.size() - 1);
        if (last != null && !spreads.isEmpty() && spreads.stream().noneMatch(p -> Integer.valueOf(p.index()).equals(last.imageIndex()))) {
            errors.add(where(last, scenes.size() - 1) + " 마지막 장면은 메뉴를 한 상에 차린 사진("
                    + String.join(", ", spreads.stream().map(p -> p.index() + "번").toList()) + ")으로 끝내세요.");
        }
        if (spoken > MENU_FOCUS_NARRATION_MAX) {
            errors.add("나레이션을 모두 합쳐 " + spoken + "자입니다. 메뉴가 잘 보이는 영상은 말을 최대한 줄여 공백·문장부호 빼고 "
                    + (MENU_FOCUS_NARRATION_MAX - 10) + "자 안팎으로 씁니다. 메뉴 이름·가격·생김새 한 가지만 남기고 줄이세요.");
        }
    }

    /** 글에 있는 숫자 ("20%", "30", "2.5"), 띄어쓰기 없이 */
    static Set<String> numberTokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        Matcher m = NUMBER_TOKEN.matcher(text);
        while (m.find()) tokens.add(m.group().replaceAll("\\s", ""));
        return tokens;
    }

    private void checkOpeningAndEnding(List<Scene> scenes, MenuData menu, boolean exteriorLast, List<String> errors) {
        if (scenes.isEmpty() || menu == null) return;
        String name = storeLabel(menu.storeName());
        if (name.isEmpty()) return;
        String compactName = compact(name);

        List<Photo> exteriors = menu.photos() == null ? List.of() : menu.photos().stream()
                .filter(p -> p != null && "exterior".equals(p.kind())).toList();
        Scene last = scenes.get(scenes.size() - 1);
        if (last == null) return;
        String at = where(last, scenes.size() - 1);
        boolean onExterior = last.imageIndex() != null && exteriors.stream().anyMatch(p -> p.index() == last.imageIndex());
        if (exteriorLast && !exteriors.isEmpty() && !onExterior) {
            String list = String.join(", ", exteriors.stream().map(p -> String.valueOf(p.index())).toList());
            errors.add(at + " 가게 외관 사진(" + list + "번)이 있으니 마지막 장면은 외관 사진으로 끝내세요.");
        }
        Caption c = last.caption();
        if (c == null || !compactName.equals(compact(c.text())) || !"center".equals(c.position())) {
            errors.add(at + " 마지막 장면 자막은 가게 이름 \"" + name + "\"만 쓰고 position은 \"center\"로 두세요. (현재: "
                    + (c == null ? "없음" : quote(c.text()) + ", " + quote(c.position())) + ")");
        }
        // 화면에 가게 이름이 뜨는 장면이니 말로도 가게 이름을 불러 준다
        if (!isBlank(last.narration()) && !compact(last.narration()).contains(compactName)) {
            errors.add(at + " 마지막 장면 나레이션에 가게 이름 \"" + name + "\"을 넣으세요. 앞의 매력에 대한 감상 안에"
                    + " 자연스럽게 넣고, 광고성 마무리는 피하세요. (현재: \"" + last.narration() + "\")");
        } else if (!isBlank(last.narration()) && lettersOnly(last.narration()).endsWith(lettersOnly(name))) {
            // "이유가 있는 카츠하우스."처럼 명사로 끝나면 말이 끊겨 들린다
            errors.add(at + " 마지막 장면 나레이션이 가게 이름으로 끝납니다. \"" + name + "예요\", \"" + name
                    + "입니다\"처럼 서술어로 끝맺으세요. (현재: \"" + last.narration() + "\")");
        }
    }

    /**
     * 영상에 쓰는 사진마다(외관 제외) ai 장면이 하나씩 있어야 한다. 렌더러는 그 사진의 Runway 클립을
     * 같은 사진을 쓰는 다른 장면에도 이어 쓰므로, 작은 사진을 화면 가득 키운 흐린 정지 화면이 나오지 않는다.
     */
    private void checkEveryPhotoMoves(List<Scene> scenes, MenuData menu, List<String> errors) {
        Set<Integer> used = new java.util.TreeSet<>();
        Set<Integer> moving = new HashSet<>();
        for (Scene s : scenes) {
            if (s == null || s.imageIndex() == null) continue;
            boolean exterior = menu != null && menu.findPhoto(s.imageIndex()).map(p -> "exterior".equals(p.kind())).orElse(false);
            if (!exterior) used.add(s.imageIndex());
            if ("ai".equals(s.motion())) moving.add(s.imageIndex());
        }
        for (Integer i : used) {
            if (!moving.contains(i)) {
                errors.add(i + "번 사진을 쓰는 장면에 motion \"ai\"가 하나도 없습니다. 사진마다 처음 나오는 장면 하나를 ai로 두세요"
                        + " (같은 사진의 다른 장면은 그 영상을 이어 씁니다). ai는 " + MAX_AI_SCENES + "개까지라 외관 말고는 사진을 "
                        + MAX_AI_SCENES + "장까지만 쓰세요.");
            }
        }
    }

    /** 샘플 가게의 "[샘플] " 앞말을 뺀 가게 이름 */
    static String storeLabel(String storeName) {
        return storeName == null ? "" : storeName.replaceFirst("^\\[샘플\\]\\s*", "").strip();
    }

    private static String lettersOnly(String s) {
        return s == null ? "" : s.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s", "");
    }

    // ── 2. 시간 ──────────────────────────────────────────────────

    private void checkDurations(Storyboard sb, List<Scene> scenes, List<String> errors) {
        double sum = 0;
        boolean allPresent = true;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s == null) continue;
            String at = where(s, i);
            Double d = s.duration();
            if (d == null) {
                errors.add(at + " duration이 없습니다.");
                allPresent = false;
                continue;
            }
            sum += d;
            if (!same(d * 2, Math.rint(d * 2))) {
                errors.add(at + " duration은 0.5초 단위여야 합니다. (현재: " + fmt(d) + ")");
            }
            if (d < MIN_SCENE_SEC || d > MAX_SCENE_SEC) {
                errors.add(at + " duration은 " + fmt(MIN_SCENE_SEC) + "~" + fmt(MAX_SCENE_SEC) + "초여야 합니다. (현재: " + fmt(d) + ")");
            }
            if ("hook".equals(s.type()) && d > HOOK_MAX_SEC) {
                errors.add(at + " hook 장면은 " + fmt(HOOK_MAX_SEC) + "초 이하여야 합니다. (현재: " + fmt(d) + ")");
            }
        }
        double target = sb.totalDuration() == null ? TOTAL_DURATION : sb.totalDuration();
        if (allPresent && !scenes.isEmpty() && !same(sum, target)) {
            errors.add("장면 duration 합이 " + fmt(sum) + "초입니다. total_duration(" + fmt(target) + "초)과 같아지도록 조정하세요.");
        }
    }

    // ── 장면 필드 (스키마) ──────────────────────────────────────

    private void checkSceneFields(Scene s, String at, List<String> errors) {
        checkEnum(at + " type", s.type(), SCENE_TYPES, errors);
        checkEnum(at + " motion", s.motion(), MOTIONS, errors);
        if (s.imageIndex() == null) {
            errors.add(at + " image_index가 없습니다.");
        }
        Caption c = s.caption();
        if (c == null) {
            errors.add(at + " caption이 없습니다. text, position, emphasis를 넣으세요.");
            return;
        }
        checkEnum(at + " caption.position", c.position(), POSITIONS, errors);
        checkEnum(at + " caption.emphasis", c.emphasis(), EMPHASES, errors);
    }

    // ── 4. 자막 + 5. 가격 ────────────────────────────────────────

    private void checkCaption(Scene s, String at, MenuData menu, List<String> errors) {
        if (s.menuRef() != null && menu != null && menu.findMenu(s.menuRef()).isEmpty()) {
            errors.add(at + " menu_ref \"" + s.menuRef() + "\"는 메뉴 데이터에 없습니다. menus[].name 중 하나와 똑같이 쓰거나 null로 두세요.");
        }

        Caption c = s.caption();
        if (c == null) return;
        String text = c.text();
        if (isBlank(text)) {
            errors.add(at + " caption.text가 비어 있습니다.");
            return;
        }
        if (text.length() > CAPTION_MAX) {
            errors.add(at + " caption.text는 공백 포함 " + CAPTION_MAX + "자 이내여야 합니다. (현재 " + text.length() + "자: \"" + text + "\")");
        } else if (!CaptionSplitter.fits(text)) {
            errors.add(at + " caption.text는 화면에 띄어쓰기 단위로 " + CaptionSplitter.PART_MAX + "자 조각 "
                    + CaptionSplitter.MAX_PARTS + "개까지 나눠 띄웁니다. 띄어쓰기 없이 이어진 부분이 너무 길어 나눌 수 없으니 띄어 쓰거나 줄이세요. (현재: \""
                    + text + "\" → " + String.join(" / ", CaptionSplitter.split(text)) + ")");
        }
        String emoji = findEmoji(text);
        if (emoji != null) {
            errors.add(at + " caption.text에 이모지·그림 기호(" + emoji + ")가 있습니다. 자막 폰트가 그리지 못해 네모로 깨지니 글자로만 쓰세요. (\""
                    + text + "\")");
        }

        List<String> prices = new ArrayList<>();
        Matcher m = PRICE_IN_TEXT.matcher(text);
        while (m.find()) prices.add(m.group());

        if (prices.isEmpty()) {
            if ("price".equals(c.emphasis())) {
                errors.add(at + " caption.emphasis가 \"price\"인데 자막에 가격이 없습니다. 가격을 넣거나 emphasis를 바꾸세요.");
            }
            return;
        }
        if (s.menuRef() == null) {
            errors.add(at + " 자막에 가격(" + String.join(", ", prices) + ")이 있으면 menu_ref에 그 메뉴 이름을 넣어야 합니다.");
            return;
        }
        Optional<Menu> ref = menu == null ? Optional.empty() : menu.findMenu(s.menuRef());
        for (String p : prices) {
            String compact = p.replaceAll("\\s", "");
            if (!PRICE_FORMAT.matcher(compact).matches()) {
                errors.add(at + " 자막 가격 \"" + p + "\"는 \"12,000원\" 형식으로 쓰세요.");
                continue;
            }
            if (ref.isEmpty()) continue; // menu_ref 오류는 위에서 이미 알림
            Integer expected = ref.get().price();
            int actual = Integer.parseInt(compact.replaceAll("[^0-9]", ""));
            if (expected == null) {
                errors.add(at + " 메뉴 \"" + s.menuRef() + "\"는 가격이 확인되지 않았습니다. 자막에서 가격을 빼세요.");
            } else if (actual != expected) {
                errors.add(at + " 자막 가격 " + compact + "이 메뉴 \"" + s.menuRef() + "\"의 가격 " + won(expected) + "과 다릅니다.");
            }
        }
    }

    /**
     * 대사 = 나레이션 = 자막: TTS가 읽는 말을 그대로 화면에 띄운다 (마지막 가게 이름 장면은 예외, 따로 검사).
     * 띄어쓰기가 여러 칸인 것만 봐주고, 글자·문장부호는 같아야 한다.
     */
    private void checkCaptionIsNarration(Scene s, String at, List<String> errors) {
        if (s.caption() == null || isBlank(s.caption().text()) || isBlank(s.narration())) return; // 빈 값은 다른 검사가 알림
        String caption = oneSpace(s.caption().text());
        String narration = oneSpace(s.narration());
        if (!caption.equals(narration)) {
            errors.add(at + " 자막은 나레이션과 같은 글이어야 합니다 (대사 = 나레이션 = 자막). caption.text를 \"" + narration
                    + "\"로 바꾸세요. 너무 길면 나레이션을 공백 포함 " + CAPTION_MAX + "자 이내로 줄이고 자막도 똑같이 쓰세요. (현재 자막: \""
                    + s.caption().text() + "\")");
        }
    }

    private static String oneSpace(String s) {
        return s.strip().replaceAll("\\s+", " ");
    }

    /** 나레이션끼리 같은 어미로 끝나면 단조롭다 (예: "라떼가 있어요." / "아메리카노가 있어요.") */
    private void checkNarrationEndings(List<Scene> scenes, List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s == null || isBlank(s.narration())) continue;
            String letters = s.narration().replaceAll("[^\\p{L}\\p{N}]", "");
            if (letters.length() < ENDING_CHARS) continue;
            String ending = letters.substring(letters.length() - ENDING_CHARS);
            String first = seen.putIfAbsent(ending, where(s, i));
            if (first != null) {
                errors.add(where(s, i) + " 나레이션이 " + first + "과 같은 \"…" + ending + "\"로 끝납니다. 기승전결이 이어지게 장면마다 어미를 바꾸세요.");
            }
        }
    }

    // ── 6. 나레이션 ──────────────────────────────────────────────

    /** @param allowedNumbers 가격 말고도 나레이션에 써도 되는 숫자 (신메뉴 알림 영상에서 사장님 한마디에 있는 것) */
    private void checkNarration(Scene s, String at, MenuData menu, Set<String> allowedNumbers, List<String> errors) {
        String n = s.narration();
        if (isBlank(n)) {
            errors.add(at + " narration이 없습니다. 나레이션이 장면 사이에서 끊기지 않게 모든 장면에 쓰세요.");
            return;
        }
        // 가격("12,000원")만 숫자로 쓸 수 있다 — 그 장면 menu_ref 메뉴의 가격과 같아야 함
        Matcher pm = SpokenPrice.PRICE.matcher(n);
        while (pm.find()) {
            String p = pm.group().replaceAll("\s", "");
            if (!PRICE_FORMAT.matcher(p).matches()) {
                errors.add(at + " narration 가격 \"" + pm.group() + "\"는 \"12,000원\" 형식으로 쓰세요. (\"" + n + "\")");
                continue;
            }
            if (s.menuRef() == null) {
                errors.add(at + " narration에 가격(" + p + ")이 있으면 menu_ref에 그 메뉴 이름을 넣어야 합니다. (\"" + n + "\")");
                continue;
            }
            Optional<Menu> ref = menu == null ? Optional.empty() : menu.findMenu(s.menuRef());
            if (ref.isEmpty()) continue; // menu_ref 오류는 자막 검사에서 알림
            Integer expected = ref.get().price();
            int actual = Integer.parseInt(p.replaceAll("[^0-9]", ""));
            if (expected == null) {
                errors.add(at + " 메뉴 \"" + s.menuRef() + "\"는 가격이 확인되지 않았습니다. narration에서 가격을 빼세요.");
            } else if (actual != expected) {
                errors.add(at + " narration 가격 " + p + "이 메뉴 \"" + s.menuRef() + "\"의 가격 " + won(expected) + "과 다릅니다.");
            }
        }
        String withoutPrices = SpokenPrice.PRICE.matcher(n).replaceAll("");
        if (!allowedNumbers.isEmpty()) {
            Matcher nm = NUMBER_TOKEN.matcher(withoutPrices);
            StringBuilder rest = new StringBuilder();
            while (nm.find()) nm.appendReplacement(rest, allowedNumbers.contains(nm.group().replaceAll("\\s", "")) ? "" : "$0");
            nm.appendTail(rest);
            withoutPrices = rest.toString();
        }
        if (DIGIT.matcher(withoutPrices).find()) {
            errors.add(at + " narration에 숫자가 있습니다. 숫자는 메뉴 가격(\"12,000원\")"
                    + (allowedNumbers.isEmpty() ? "" : "과 사장님 한마디에 있는 숫자(" + String.join(", ", allowedNumbers) + ")")
                    + "만 쓸 수 있습니다. (\"" + n + "\")");
        } else if (KOREAN_PRICE.matcher(n).find()) {
            errors.add(at + " narration의 한글 금액은 \"12,000원\"처럼 숫자로 쓰세요. (\"" + n + "\")");
        }
        String emoji = findEmoji(n);
        if (emoji != null) {
            errors.add(at + " narration에 이모지·그림 기호(" + emoji + ")가 있습니다. TTS가 읽지 못하니 빼세요. (\"" + n + "\")");
        }
        if (s.duration() != null) {
            int chars = countSpokenChars(SpokenPrice.speak(n));
            int max = (int) Math.floor(s.duration() * NARRATION_CHARS_PER_SEC);
            if (chars > max) {
                errors.add(at + " narration이 " + chars + "자로 깁니다. duration " + fmt(s.duration()) + "초에는 공백·문장부호 빼고 "
                        + max + "자 이내로 줄이세요. (\"" + n + "\")");
            }
        }
    }

    /**
     * 이모지·그림 기호(☀️ 😋 ★ 등)를 찾아 "☀", "😋"처럼 돌려준다. 없으면 null.
     * 자막 폰트(Noto Sans CJK KR)는 이모지를 그리지 못하고, TTS는 읽지 못한다.
     * 그림 글자 없이 이모지 표시용 보조 문자(FE0F, ZWJ, 키캡 20E3)만 있어도 이모지로 본다 (예: 1️⃣).
     */
    static String findEmoji(String text) {
        Set<String> found = new LinkedHashSet<>();
        boolean emojiMarker = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isExtendedPictographic(cp)) {
                found.add("\"" + new String(Character.toChars(cp)) + "\"");
            } else if (cp == 0xFE0F || cp == 0x200D || cp == 0x20E3) {
                emojiMarker = true;
            }
            i += Character.charCount(cp);
        }
        if (!found.isEmpty()) return String.join(", ", found);
        return emojiMarker ? "이모지" : null;
    }

    /** 공백·문장부호를 뺀 글자 수 */
    static int countSpokenChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) count++;
            i += Character.charCount(cp);
        }
        return count;
    }

    // ── 7. 영상 프롬프트 ─────────────────────────────────────────

    private void checkVideoPrompt(Scene s, String at, List<String> errors) {
        String p = s.videoPrompt();
        if (isBlank(p)) {
            errors.add(at + " motion이 \"" + s.motion() + "\"이면 video_prompt가 있어야 합니다.");
            return;
        }
        if (HANGUL.matcher(p).find()) {
            errors.add(at + " video_prompt는 영어로만 쓰세요. (\"" + p + "\")");
        }
        int words = p.trim().split("\\s+").length;
        if (words > VIDEO_PROMPT_MAX_WORDS) {
            errors.add(at + " video_prompt가 " + words + "단어입니다. " + VIDEO_PROMPT_MAX_WORDS + "단어 이내로 줄이세요.");
        }
        Matcher m = TEXT_WORDS.matcher(p);
        if (m.find()) {
            errors.add(at + " video_prompt에 글자·간판·로고 관련 단어(\"" + m.group().toLowerCase(Locale.ROOT)
                    + "\")가 있습니다. 화면에 글자가 생기지 않도록 빼세요.");
        }
        List<String> people = findAll(PERSON_WORDS, p);
        if (!people.isEmpty()) {
            errors.add(at + " video_prompt에 사람·신체를 가리키는 단어(" + String.join(", ", people)
                    + ")가 있습니다. AI 영상에서 사람·얼굴·손가락은 뭉개져 보이니 빼세요. 동작은 \"a gloved hand uses tongs to pick up the bread\"처럼 "
                    + "손과 도구만 화면에 나오게 쓰면 됩니다.");
        }
        List<String> actions = findAll(MOUTH_ACTION_WORDS, p);
        if (!actions.isEmpty()) {
            errors.add(at + " video_prompt에 입이 있어야 하는 동작(" + String.join(", ", actions)
                    + ")이 있습니다. 영상에 얼굴이 그려지니, 집게로 집기·포크와 나이프로 찢기·단면 보여 주기처럼 손과 도구로 하는 동작으로 바꾸세요.");
        }
    }

    /**
     * transition: 앞 장면 사진(첫 화면) → 이 장면 가게 외관 사진(끝 화면)을 AI가 이어 주는 장면.
     * Runway에 처음·끝 화면을 둘 다 주는 모델(Kling)을 쓰므로 영상 전체에서 하나만, 외관 사진으로 넘어갈 때만.
     */
    private void checkTransition(List<Scene> scenes, int i, String at, MenuData menu, List<String> errors) {
        Scene s = scenes.get(i);
        checkVideoPrompt(s, at, errors);
        if (i == 0) {
            errors.add(at + " 첫 장면은 motion \"transition\"일 수 없습니다. 앞 장면 사진에서 이 장면 사진으로 넘어가는 효과입니다.");
            return;
        }
        Scene prev = scenes.get(i - 1);
        if (prev == null || prev.imageIndex() == null || s.imageIndex() == null) return;
        if (prev.imageIndex().equals(s.imageIndex())) {
            errors.add(at + " motion \"transition\"은 앞 장면과 다른 사진으로 넘어갈 때만 씁니다. (앞 장면도 " + s.imageIndex() + "번 사진)");
        }
        if (menu != null && !isExterior(menu, s.imageIndex())) {
            errors.add(at + " motion \"transition\"은 가게 외관(exterior) 사진으로 넘어가는 장면에만 씁니다. 이 장면의 motion을 zoom_in / zoom_out / static으로 바꾸세요.");
        }
        if (menu != null && isExterior(menu, prev.imageIndex())) {
            errors.add(at + " motion \"transition\"의 앞 장면은 음식·가게 안 사진이어야 합니다. 외관 사진에서 외관 사진으로는 넘어가지 않습니다.");
        }
    }

    private static boolean isExterior(MenuData menu, Integer imageIndex) {
        return imageIndex != null && menu.findPhoto(imageIndex).map(p -> "exterior".equals(p.kind())).orElse(false);
    }

    /** 같은 사진으로 AI 클립을 두 번 만들면 거의 같은 영상에 Runway 크레딧을 두 번 쓰게 된다 */
    private void checkAiPhotoReuse(Scene s, String at, Map<Integer, String> aiPhotos, List<String> errors) {
        if (s.imageIndex() == null) return;
        String first = aiPhotos.putIfAbsent(s.imageIndex(), at);
        if (first != null) {
            errors.add(at + " " + s.imageIndex() + "번 사진은 이미 " + first + "의 AI 클립에 썼습니다. AI 장면마다 다른 사진을 쓰고, "
                    + "같은 사진을 다시 쓰려면 이 장면의 motion을 zoom_in / zoom_out / static으로 바꾸세요.");
        }
    }

    /** 패턴에 걸린 단어를 소문자로, 중복 없이 나온 순서대로 */
    static List<String> findAll(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) found.add("\"" + m.group().toLowerCase(Locale.ROOT) + "\"");
        return List.copyOf(found);
    }

    // ── 8. 사진 + 9. 사진 종류 ───────────────────────────────────

    private void checkPhoto(Scene s, String at, MenuData menu, List<String> errors) {
        if (s.imageIndex() == null || menu == null) return;
        Optional<Photo> found = menu.findPhoto(s.imageIndex());
        if (found.isEmpty()) {
            errors.add(at + " image_index " + s.imageIndex() + "번 사진이 없습니다. photos[].index 중에서 고르세요.");
            return;
        }
        Photo photo = found.get();
        String kind = photo.kind();

        if ("menu_board".equals(kind)) {
            errors.add(at + " image_index " + photo.index() + "번은 메뉴판 사진이라 영상에 쓸 수 없습니다.");
            return;
        }
        if (s.menuRef() != null && !s.menuRef().equals(photo.menuRef())) {
            errors.add(at + " menu_ref가 \"" + s.menuRef() + "\"인데 " + photo.index() + "번 사진은 "
                    + (photo.menuRef() == null ? "메뉴가 연결되지 않은 사진" : "\"" + photo.menuRef() + "\" 사진")
                    + "입니다. 같은 메뉴 사진을 쓰거나 가격 없는 자막으로 바꾸고 menu_ref를 null로 두세요.");
        }
        if (("store".equals(kind) || "exterior".equals(kind)) && ("menu".equals(s.type()) || "detail".equals(s.type()))) {
            errors.add(at + " " + photo.index() + "번은 가게 사진이라 " + s.type() + " 장면에 쓸 수 없습니다. 음식 사진을 쓰세요.");
        }
        if ("food".equals(kind) && photo.isGenerated() && "detail".equals(s.type())) {
            errors.add(at + " " + photo.index() + "번은 AI 생성 음식 사진이라 detail 장면에 쓸 수 없습니다.");
        }

    }

    // ── 공통 ─────────────────────────────────────────────────────

    private void checkEnum(String field, String value, Set<String> allowed, List<String> errors) {
        if (value == null || !allowed.contains(value)) {
            errors.add(field + " 값 " + quote(value) + "는 허용되지 않습니다. " + allowed.stream().sorted().toList() + " 중 하나를 쓰세요.");
        }
    }

    private static String where(Scene s, int index) {
        return s.id() != null ? "[장면 id=" + s.id() + "]" : "[scenes[" + index + "]]";
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static String fmt(Double d) {
        if (d == null) return "null";
        return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }

    private static String won(int price) {
        return String.format("%,d원", price);
    }
}
