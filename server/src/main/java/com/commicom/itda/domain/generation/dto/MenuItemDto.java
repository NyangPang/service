package com.commicom.itda.domain.generation.dto;

import com.commicom.itda.domain.generation.storyboard.MenuData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 한 개. 메뉴 확인 화면이 받는 값(POST /api/generation/menus)이고,
 * 사장님이 고친 뒤 게시물 생성 요청(menus)으로 그대로 돌려보낸다.
 *
 * @param price       원. 모르면 null
 * @param description 메뉴판에 적힌 설명 (화면엔 안 보여도 그대로 돌려보내면 영상 문구에 씀)
 * @param signature   대표 메뉴 표시 (BEST·추천 등)
 */
public record MenuItemDto(
        @NotBlank(message = "메뉴 이름을 입력해 주세요") @Size(max = 50, message = "메뉴 이름은 50자 이내로 적어 주세요") String name,
        Integer price,
        @Size(max = 200) String description,
        Boolean signature
) {
    public static MenuItemDto from(MenuData.Menu m) {
        return new MenuItemDto(m.name(), m.price(), m.description(), m.signature());
    }
}
