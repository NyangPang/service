package com.commicom.itda.domain.generation.storyboard;

import com.commicom.itda.domain.generation.storyboard.MenuData.Menu;
import com.commicom.itda.domain.generation.storyboard.MenuData.Photo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryboardRenderPreparationTest {

    private final MenuData menu = new MenuData("광운 카페", "CAFE_BAKERY_PUB",
            List.of(new Menu("아메리카노", "4,000", 4000, null, false)), List.of(),
            List.of(new Photo(0, "upload", "store", null, "창가"),
                    new Photo(1, "upload", "food", "아메리카노", "커피"),
                    new Photo(2, "upload", "menu_board", null, "가격표")), List.of());

    @Test
    void 영상에_쓸_수_없는_사진과_잘못된_가격은_안전하게_보정한다() throws IOException {
        Storyboard draft = StoryboardJson.parseStoryboard("""
                {"title":"아메리카노 9,000원", "scenes":[
                  {"id":9,"type":"menu","image_index":2,"menu_ref":"아메리카노","motion":"ai",
                   "narration":"아메리카노가 4,000원이에요", "caption":{"text":"햇볕 드는 창가예요 ☀️"}},
                  {"id":9,"type":"menu","image_index":1,"menu_ref":"아메리카노","duration":1,
                   "narration":"아메리카노 4000원이에요", "caption":{"text":"아메리카노 사천 원"}}
                ]}
                """);

        Storyboard result = StoryboardRenderPreparation.prepare(draft, menu);

        assertThat(result.title()).isEqualTo("광운 카페");
        assertThat(result.scenes()).extracting(Storyboard.Scene::id).containsExactly(1, 2);
        assertThat(result.scenes()).extracting(Storyboard.Scene::duration).containsExactly(7.5, 7.5);
        Storyboard.Scene store = result.scenes().getFirst();
        assertThat(store.imageIndex()).isZero();
        assertThat(store.type()).isEqualTo("review");
        assertThat(store.motion()).isEqualTo("zoom_in");
        assertThat(store.menuRef()).isNull();
        assertThat(store.narration()).isNull();
        assertThat(store.caption().text()).isEqualTo("햇볕 드는 창가예요");
        Storyboard.Scene food = result.scenes().getLast();
        assertThat(food.narration()).isEqualTo("아메리카노 4,000원이에요");
        assertThat(food.caption().text()).isEqualTo(food.narration());
    }

    @Test
    void 긴_자막과_반복되는_어미를_허용한다() throws IOException {
        String text = "창가에 햇살이 들어와서 차분하게 쉬어 가기 좋은 공간이에요";
        Storyboard draft = StoryboardJson.parseStoryboard("""
                {"scenes":[{"image_index":0,"narration":"%s"},{"image_index":0,"narration":"%s"}]}
                """.formatted(text, text));

        assertThat(StoryboardRenderPreparation.prepare(draft, menu).scenes())
                .extracting(scene -> scene.caption().text()).containsExactly(text, text);
    }
}
