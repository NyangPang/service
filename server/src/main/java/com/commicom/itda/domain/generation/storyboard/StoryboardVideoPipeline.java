package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.infra.video.storyboard.MenuImageGenerator;
import com.commicom.itda.infra.video.storyboard.MenuImageService;
import com.commicom.itda.infra.video.storyboard.NarrationVoice;
import com.commicom.itda.infra.video.storyboard.StoryboardVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 메뉴 데이터 + 사진 → (이미지 생성) → 장면 JSON (A 파트) → mp4 (B 파트).
 * 음식 사진이 없어 메뉴 데이터에 image_requests가 있으면 Runway로 이미지를 먼저 만들어 photos[] 끝에 붙인다.
 * 두 파트는 장면 JSON 문자열로만 주고받는다 — B 파트는 A 파트 검증을 통과한 JSON만 받는다.
 */
@Lazy
@Service
@RequiredArgsConstructor
public class StoryboardVideoPipeline {

    private final StoryboardGenerator generator;
    private final StoryboardVideoService videoService;
    private final MenuImageService imageService;

    /**
     * @param photos menu.photos()와 같은 순서의 로컬 사진 (메뉴판 제외). 음식 사진이 없으면 0장이어도 된다
     * @throws StoryboardGenerator.StoryboardGenerationException 장면 JSON이 검증을 끝내 통과하지 못하면 (영상은 만들지 않음)
     */
    public Result make(MenuData menu, String appeal, List<Path> photos, boolean useRunway) throws Exception {
        return make(menu, appeal, photos, useRunway, null);
    }

    /** @param storeAddress 제목에 쓸 동네 이름을 추출할 때 사용하는 주소 (없으면 null) */
    public Result make(MenuData menu, String appeal, List<Path> photos, boolean useRunway, String storeAddress) throws Exception {
        return make(menu, appeal, photos, useRunway, storeAddress, VideoOptions.NONE);
    }

    /** @param options 생성 느낌 — 장면 프롬프트·검사 규칙과 나레이션 목소리(남자/기본)를 고른다 */
    public Result make(MenuData menu, String appeal, List<Path> photos, boolean useRunway, String storeAddress,
                       VideoOptions options) throws Exception {
        VideoOptions o = options == null ? VideoOptions.NONE : options;
        Images images = addGeneratedImages(menu, photos);
        checkPhotos(images.menu(), images.photos());
        StoryboardGenerator.Result generated = generator.generate(images.menu(), appeal, storeAddress, o);
        Storyboard sb = generated.storyboard();
        if (sb.meta() != null) sb = sb.withMeta(sb.meta().withStoreName(images.menu().storeName()));
        StoryboardVideoService.Result video = videoService.render(StoryboardJson.toJson(sb), images.photos(), useRunway,
                o.maleVoice() ? NarrationVoice.MALE : NarrationVoice.DEFAULT);
        return new Result(sb, generated.attempts(), video, images);
    }

    /**
     * image_requests가 있으면 Runway로 메뉴 이미지를 만들어 photos[] 끝에 source=generated로 붙이고
     * 사진 파일 목록에도 같은 순서로 붙인다. 요청이 없으면 그대로 돌려준다.
     * 같은 프롬프트는 캐시에서 재사용하므로 다시 돌려도 크레딧이 또 들지 않는다.
     */
    public Images addGeneratedImages(MenuData menu, List<Path> photos) throws Exception {
        if (menu.imageRequests() == null || menu.imageRequests().isEmpty()) {
            return new Images(menu, photos, List.of(), 0);
        }
        MenuImageService.Result r = imageService.generate(StoryboardJson.toJson(menu), photos);
        return new Images(StoryboardJson.parseMenu(r.menuJson()), r.photos(), r.generated(), r.creditsSpent());
    }

    /** 이미 만든(검증된) 장면 JSON으로 영상만 만든다 */
    public StoryboardVideoService.Result render(Storyboard storyboard, List<Path> photos, boolean useRunway) throws Exception {
        return videoService.render(StoryboardJson.toJson(storyboard), photos, useRunway);
    }

    /** 사진 장수가 photos[]와 다르면 image_index가 엉뚱한 사진을 가리키므로 LLM을 부르기 전에 막는다 */
    static void checkPhotos(MenuData menu, List<Path> photos) {
        int expected = menu.photos() == null ? 0 : menu.photos().size();
        if (photos.size() != expected) {
            throw new IllegalArgumentException("사진이 " + photos.size() + "장인데 메뉴 데이터 photos[]는 "
                    + expected + "장이에요. 같은 순서로 같은 장수를 넣어 주세요.");
        }
        if (photos.isEmpty()) {
            throw new IllegalArgumentException("영상에 쓸 사진이 없어요.");
        }
    }

    public record Result(Storyboard storyboard, List<StoryboardGenerator.Attempt> attempts,
                         StoryboardVideoService.Result video, Images images) {}

    /** 이미지 생성 뒤의 메뉴 데이터(photos[]에 생성 이미지 포함, image_requests는 [])와 사진 파일 목록 */
    public record Images(MenuData menu, List<Path> photos, List<MenuImageGenerator.Generated> generated, int creditsSpent) {}
}
