package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuData.Menu;
import com.commicom.itda.domain.generation.storyboard.MenuData.Photo;
import com.commicom.itda.domain.generation.storyboard.Storyboard.Caption;
import com.commicom.itda.domain.generation.storyboard.Storyboard.Scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 모델이 만든 장면을 렌더러가 사용할 수 있는 값으로 보정한다. */
final class StoryboardRenderPreparation {

    private static final Pattern PRICE = Pattern.compile(
            "(?:\\d[\\d,.]*(?:\\s*[천만억]\\s*\\d[\\d,.]*)*(?:\\s*[천만억])?|"
                    + "(?<!\\p{L})[영공일이삼사오육칠팔구십백천만억]+(?:\\s+[영공일이삼사오육칠팔구십백천만억]+)*)\\s*원");

    private StoryboardRenderPreparation() {}

    static Storyboard prepare(Storyboard storyboard, MenuData menu) {
        List<Scene> source = storyboard.scenes() == null ? List.of() : storyboard.scenes().stream()
                .filter(Objects::nonNull).limit(StoryboardValidator.MAX_SCENES).toList();
        if (source.isEmpty()) throw new IllegalArgumentException("영상으로 만들 장면이 없습니다.");
        List<Photo> photos = menu.photos() == null ? List.of() : menu.photos().stream()
                .filter(Objects::nonNull).filter(Photo::isUsable).toList();
        if (photos.isEmpty()) throw new IllegalArgumentException("영상에 쓸 사진이 없습니다.");

        String storeName = StoryboardValidator.storeLabel(menu.storeName());
        if (storeName.isBlank()) storeName = "가게 소개";
        String title = cleanText(storyboard.title());
        if (title != null && PRICE.matcher(title).find()) {
            String pricedTitle = title;
            Integer expected = menu.menus() == null ? null : menu.menus().stream().filter(Objects::nonNull)
                    .filter(m -> m.price() != null && m.name() != null && pricedTitle.contains(m.name()))
                    .map(Menu::price).findFirst().orElse(null);
            title = checkPrice(title, expected);
        }
        if (title == null) title = storeName;
        if (title.codePointCount(0, title.length()) > 200) title = title.substring(0, title.offsetByCodePoints(0, 200));

        boolean keepDurations = source.stream().allMatch(s -> s.duration() != null && Double.isFinite(s.duration())
                && s.duration() >= 1 && s.duration() <= StoryboardValidator.TOTAL_DURATION)
                && Math.abs(source.stream().mapToDouble(Scene::duration).sum() - StoryboardValidator.TOTAL_DURATION) < 0.001;
        int halfSeconds = (int) (StoryboardValidator.TOTAL_DURATION * 2);
        List<Scene> scenes = new ArrayList<>();
        Set<Integer> animated = new HashSet<>();
        for (int i = 0; i < source.size(); i++) {
            Scene scene = source.get(i);
            Photo photo = menu.findPhoto(scene.imageIndex()).filter(Photo::isUsable).orElse(photos.get(i % photos.size()));
            double duration = keepDurations ? scene.duration()
                    : (halfSeconds / source.size() + (i < halfSeconds % source.size() ? 1 : 0)) / 2.0;
            scenes.add(prepareScene(scene, photo, menu, storeName, animated, i + 1, duration));
        }
        return new Storyboard(StoryboardValidator.SCHEMA_VERSION, title, StoryboardValidator.TOTAL_DURATION,
                storyboard.theme(), storyboard.style(), storyboard.bgm(), scenes, storyboard.meta());
    }

    private static Scene prepareScene(Scene scene, Photo photo, MenuData menu, String storeName,
                                      Set<Integer> animated, int order, double duration) {
        boolean food = "food".equals(photo.kind());
        String type = scene.type();
        if (type == null || !StoryboardValidator.SCENE_TYPES.contains(type)
                || ("menu".equals(type) && !food) || ("detail".equals(type) && (!food || !photo.isUpload()))) {
            type = "review";
        }
        Menu referenced = food && Objects.equals(scene.menuRef(), photo.menuRef())
                ? menu.findMenu(scene.menuRef()).orElse(null) : null;
        Integer price = referenced == null ? null : referenced.price();
        String narration = checkPrice(cleanText(scene.narration()), price);
        Caption original = scene.caption();
        String caption = checkPrice(cleanText(original == null ? null : original.text()), price);
        if (caption == null) caption = narration == null ? storeName : narration;
        String position = original != null && "center".equals(original.position()) ? "center" : "bottom";
        String emphasis = PRICE.matcher(caption).find() ? "price"
                : original != null && "large".equals(original.emphasis()) ? "large" : "normal";

        String motion = scene.motion();
        boolean ai = "ai".equals(motion);
        boolean transition = "transition".equals(motion);
        String videoPrompt = null;
        if ((ai || transition) && Objects.equals(scene.imageIndex(), photo.index())
                && StoryboardValidator.canAnimate(scene)
                && (!ai || (animated.size() < StoryboardValidator.MAX_AI_SCENES && animated.add(photo.index())))) {
            videoPrompt = scene.videoPrompt();
        } else if (ai || transition || motion == null || !StoryboardValidator.MOTIONS.contains(motion)) {
            motion = "zoom_in";
        }
        return new Scene(order, order, type, duration, photo.index(), motion, videoPrompt,
                referenced == null ? null : referenced.name(), narration, new Caption(caption, position, emphasis));
    }

    private static String cleanText(String text) {
        if (text == null) return null;
        StringBuilder cleaned = new StringBuilder();
        text.codePoints().filter(cp -> !Character.isExtendedPictographic(cp)
                && cp != 0xFE0F && cp != 0x200D && cp != 0x20E3).forEach(cleaned::appendCodePoint);
        String result = cleaned.toString().strip();
        return result.isBlank() ? null : result;
    }

    private static String checkPrice(String text, Integer expected) {
        if (text == null) return null;
        var matches = PRICE.matcher(text);
        while (matches.find()) {
            if (expected == null || !expected.equals(MenuExtractionValidator.parsePriceText(matches.group()))) return null;
        }
        return expected == null ? text : matches.replaceAll(String.format(Locale.ROOT, "%,d원", expected));
    }
}
