package com.commicom.itda.domain.generation.service;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.storyboard.MenuData;
import com.commicom.itda.domain.generation.storyboard.MenuExtractionValidator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 사장님이 메뉴 확인 화면에서 확정한 메뉴 → 영상 메뉴 데이터 */
class ConfirmedMenuTest {

    @Test
    void 확정_메뉴를_메뉴_데이터로_바꾸고_검증을_통과한다() {
        List<MenuData.Menu> menus = GenerationVideoService.toMenus(List.of(
                new MenuItemDto(" 아메리카노 ", 4500, null, null),
                new MenuItemDto("크로플", 12000, "버터 크로플 2조각", true),
                new MenuItemDto("시즌 음료", null, " ", false)));

        assertThat(menus).extracting(MenuData.Menu::name).containsExactly("아메리카노", "크로플", "시즌 음료");
        assertThat(menus.get(0).priceText()).isEqualTo("4,500");
        assertThat(menus.get(1).signature()).isTrue();
        assertThat(menus.get(1).description()).isEqualTo("버터 크로플 2조각");
        assertThat(menus.get(2).price()).isNull();
        assertThat(menus.get(2).description()).isNull();

        MenuData data = new MenuData("가게", "CAFE_BAKERY_PUB", menus, List.of(), List.of(), List.of(
                new MenuData.ImageRequest("크로플", "golden croffle on a white plate, cafe table, soft light"),
                new MenuData.ImageRequest("아메리카노", "iced americano in a clear glass, wooden table"),
                new MenuData.ImageRequest(null, "croffle and americano together on a cafe table")));
        assertThat(new MenuExtractionValidator().validate(data, 0)).isEmpty();
    }

    @Test
    void 이름이_비거나_겹치면_빼고_가격으로_보기_어려우면_null() {
        List<MenuData.Menu> menus = GenerationVideoService.toMenus(Arrays.asList(
                new MenuItemDto("떡볶이", 5000, null, false),
                new MenuItemDto("떡볶이 ", 6000, null, false),
                new MenuItemDto("  ", 3000, null, false),
                null,
                new MenuItemDto("순대", 45, null, false),
                new MenuItemDto("튀김", 3333, null, false)));

        assertThat(menus).extracting(MenuData.Menu::name).containsExactly("떡볶이", "순대", "튀김");
        assertThat(menus.get(0).price()).isEqualTo(5000);
        assertThat(menus.get(1).price()).isNull();
        assertThat(menus.get(2).price()).isNull();
    }

    @Test
    void 확정_메뉴가_없으면_추가_정보는_그대로() {
        assertThat(GenerationVideoService.toMenus(null)).isEmpty();
        assertThat(GenerationVideoService.extraInfo("지도 링크: x", List.of())).isEqualTo("지도 링크: x");
    }

    @Test
    void 확정_메뉴는_추가_정보_맨_앞에_이름_그대로_적는다() {
        String info = GenerationVideoService.extraInfo("수정 요청(글·문구): 학생 할인",
                GenerationVideoService.toMenus(List.of(
                        new MenuItemDto("크로플", 4500, "버터", true),
                        new MenuItemDto("라떼", null, null, false))));

        assertThat(info).startsWith("사장님이 확인한 메뉴");
        assertThat(info).contains("\n- 크로플 (4,500원) [대표 메뉴] — 버터", "\n- 라떼\n", "수정 요청(글·문구): 학생 할인");
    }
}
