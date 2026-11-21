package com.commicom.itda.infra.video.storyboard;

import com.commicom.itda.infra.video.storyboard.Storyboard.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OneTakePlanTest {

    private static Scene scene(int id, double duration, int image, String motion, String prompt) {
        return new Scene(id, id, "menu", duration, image, motion, prompt, null, "나레이션 " + id, null);
    }

    @Test
    void 같은_사진이_이어지는_장면은_한_컷으로_묶고_첫_프롬프트를_쓴다() {
        // 0, 0, 1, 1, 3(외관) → 컷 3개. 컷마다 그 사진 → 다음 사진으로 넘어가는 AI 클립 하나
        List<Scene> scenes = List.of(
                scene(1, 2, 0, "ai", "mochi wobbles, then the camera glides to the next table"),
                scene(2, 3, 0, "static", null),
                scene(3, 3, 1, "ai", "cake glistens, then the camera pulls back to the storefront"),
                scene(4, 3.5, 1, "static", null),
                scene(5, 3.5, 3, "zoom_in", null));

        List<StoryboardRenderer.Shot> shots = StoryboardRenderer.planShots(scenes);

        assertThat(shots).hasSize(3);
        assertThat(shots.get(0)).isEqualTo(new StoryboardRenderer.Shot(0, 0, 0, 5, "mochi wobbles, then the camera glides to the next table"));
        assertThat(shots.get(1)).isEqualTo(new StoryboardRenderer.Shot(1, 2, 5, 6.5, "cake glistens, then the camera pulls back to the storefront"));
        // 외관 컷은 프롬프트가 없어 앞 컷의 AI 클립이 데려다준 화면에서 줌으로 이어짐
        assertThat(shots.get(2)).isEqualTo(new StoryboardRenderer.Shot(3, 4, 11.5, 3.5, null));
    }

    @Test
    void 컷_안에서_프롬프트가_뒤_장면에_있어도_쓴다() {
        List<Scene> scenes = List.of(
                scene(1, 2, 0, "static", " "),
                scene(2, 3, 0, "ai", "stew bubbling vigorously"));
        assertThat(StoryboardRenderer.planShots(scenes))
                .containsExactly(new StoryboardRenderer.Shot(0, 0, 0, 5, "stew bubbling vigorously"));
    }

    @Test
    void 원테이크는_컷이_길어도_짧게_요청하고_마지막으로_이동할_때만_1초_더_쓴다() {
        var longShot = new StoryboardRenderer.Shot(0, 0, 0, 8, "glide to the next picture");
        var shortShot = new StoryboardRenderer.Shot(0, 0, 0, 2, "glide to the next picture");

        assertThat(StoryboardRenderer.oneTakeTransitionSeconds(longShot, false)).isEqualTo(3);
        assertThat(StoryboardRenderer.oneTakeTransitionSeconds(longShot, true)).isEqualTo(4);
        assertThat(StoryboardRenderer.oneTakeTransitionSeconds(shortShot, true)).isEqualTo(3);
    }
}
