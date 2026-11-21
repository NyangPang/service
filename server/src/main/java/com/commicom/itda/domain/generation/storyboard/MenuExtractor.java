package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuExtractionPrompt.Slot;
import com.commicom.itda.domain.generation.storyboard.StoryboardGenerator.Attempt;
import com.commicom.itda.infra.ai.AiImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 메뉴판·사진 → 메뉴 데이터 (A 파트 1단계).
 * 메뉴판 읽기와 사진 분류(kind·menu_ref)를 한 번의 호출로 하고, 검증에 실패하면 오류를 붙여 최대 2회 재요청한다.
 */
@Slf4j
@Lazy
@Service
@RequiredArgsConstructor
public class MenuExtractor {

    static final int MAX_RETRIES = 2;

    private final ChatClient chatClient;
    private final MenuExtractionPrompt prompt;
    private final MenuExtractionValidator validator;

    /** 앱 입력 제한과 같게: 영상에 쓸 사진 4장, 메뉴판 3장까지 */
    public static final int MAX_PHOTOS = 4;
    public static final int MAX_MENU_BOARDS = 3;

    /** 이미지 한 장. PHOTO는 넣은 순서대로 photos[].index(= 영상 photoUrls 순서)가 되고, MENU_BOARD는 메뉴 읽기에만 쓴다 */
    public record Image(byte[] bytes, Slot slot) {}

    public record Result(MenuData menuData, List<Attempt> attempts) {}

    public Result extract(String storeName, String category, String extraInfo, List<Image> input) {
        return extract(storeName, category, extraInfo, input, null);
    }

    public List<MenuData.Menu> readMenuBoards(String storeName, String category, List<Image> boards) {
        if (boards.isEmpty() || boards.size() > MAX_MENU_BOARDS || boards.stream().anyMatch(i -> i.slot() != Slot.MENU_BOARD)) {
            throw new IllegalArgumentException("메뉴판 사진은 1~" + MAX_MENU_BOARDS + "장이 필요해요");
        }
        return extract(storeName, category, prompt.buildMenuRead(storeName, category), boards,
                UnaryOperator.identity(), data -> validator.validateMenus(data.menus())).menuData().menus();
    }

    /**
     * @param fixedMenus 사장님이 메뉴 확인 화면에서 확정한 메뉴. 있으면 모델이 쓴 menus 대신 이 목록을 그대로 쓰고
     *                   (사진의 menu_ref도 이 이름 기준으로 검증), 없으면(null·빈 목록) 메뉴판에서 읽은 그대로
     */
    public Result extract(String storeName, String category, String extraInfo, List<Image> input, List<MenuData.Menu> fixedMenus) {
        return extract(storeName, category, extraInfo, input, fixedMenus, StyleImages.NONE);
    }

    /** @param styleImages 영상 스타일 때문에 더 요청할 수 있는 이미지 (한 상 차림, 신메뉴·이벤트) */
    public Result extract(String storeName, String category, String extraInfo, List<Image> input, List<MenuData.Menu> fixedMenus,
                          StyleImages styleImages) {
        StyleImages style = styleImages == null ? StyleImages.NONE : styleImages;
        boolean fixed = fixedMenus != null && !fixedMenus.isEmpty();
        // 사진을 앞에, 메뉴판을 뒤에 (각자 넣은 순서 유지) — 프롬프트의 사진 index와 첨부 순서를 맞춘다
        List<Image> images = input.stream().sorted(Comparator.comparing(img -> img.slot() != Slot.PHOTO)).toList();
        int photoCount = (int) images.stream().filter(img -> img.slot() == Slot.PHOTO).count();
        if (photoCount > MAX_PHOTOS || images.size() - photoCount > MAX_MENU_BOARDS) {
            throw new IllegalArgumentException("사진은 " + MAX_PHOTOS + "장, 메뉴판은 " + MAX_MENU_BOARDS + "장까지 올릴 수 있어요");
        }

        List<Slot> slots = images.stream().map(Image::slot).toList();
        String original = prompt.build(storeName, category, slots, extraInfo, style);
        return extract(storeName, category, original, images,
                data -> prepareVideoMenu(data, fixed ? fixedMenus : data.menus(), style.extra()),
                data -> validator.validateForVideo(data, photoCount, style.extra()));
    }

    private MenuData prepareVideoMenu(MenuData data, List<MenuData.Menu> menus, int extraImages) {
        MenuData prepared = new MenuData(data.storeName(), data.category(), menus, data.reviewKeywords(),
                data.photos(), data.imageRequests());
        if ((menus != null && menus.isEmpty()) || (prepared.usablePhotoCount() > 0 && !validator.hasValidImageRequests(prepared, extraImages))) {
            return new MenuData(data.storeName(), data.category(), menus, data.reviewKeywords(), data.photos(), List.of());
        }
        return prepared;
    }

    private Result extract(String storeName, String category, String original, List<Image> images,
                           UnaryOperator<MenuData> normalize, Function<MenuData, List<String>> validate) {
        Media[] media = images.stream().map(img -> AiImage.toMedia(img.bytes())).toArray(Media[]::new);
        String request = original;
        List<Attempt> attempts = new ArrayList<>();

        for (int i = 0; i <= MAX_RETRIES; i++) {
            log.info("[메뉴 추출] {} 시도 {}/{} (이미지 {}장)", storeName, i + 1, MAX_RETRIES + 1, images.size());
            String text = request;
            String raw = chatClient.prompt().user(u -> u.text(text).media(media)).call().content();

            List<String> errors;
            MenuData parsed = null;
            try {
                parsed = normalize.apply(StoryboardJson.parseMenu(raw));
                errors = validate.apply(parsed);
            } catch (Exception e) {
                errors = List.of("JSON으로 읽을 수 없습니다: " + e.getMessage()
                        + " — 요청한 형식의 JSON 객체 하나만 출력하세요.");
            }
            attempts.add(new Attempt(raw, errors));

            if (errors.isEmpty()) {
                log.info("[메뉴 추출] {} 검증 통과 (시도 {}): 메뉴 {}개", storeName, i + 1, parsed.menus().size());
                MenuData filled = new MenuData(storeName, category, parsed.menus(), parsed.reviewKeywords(), parsed.photos(),
                        parsed.imageRequests());
                return new Result(filled, attempts);
            }
            log.warn("[메뉴 추출] {} 검증 실패 {}건: {}", storeName, errors.size(), errors);
            request = prompt.buildFix(original, raw, errors);
        }
        throw new MenuExtractionException(attempts);
    }

    public static class MenuExtractionException extends RuntimeException {
        private final List<Attempt> attempts;

        public MenuExtractionException(List<Attempt> attempts) {
            super("메뉴 추출 결과가 검증을 " + attempts.size() + "번 모두 통과하지 못했어요. 마지막 오류: "
                    + attempts.get(attempts.size() - 1).errors());
            this.attempts = attempts;
        }

        public List<Attempt> getAttempts() {
            return attempts;
        }
    }
}
