package com.commicom.itda.infra.ai;

import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.entity.StoreCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostCaptionServiceTest {

    private final Store store = Store.builder()
            .name("[샘플] 광운 카페").category(StoreCategory.CAFE_BAKERY_PUB)
            .address("서울 노원구 광운로 20").businessHours("매일 10:00~21:00")
            .description("햇살 드는 창가 자리가 있는 동네 카페")
            .build();

    @Test
    void 본문_프롬프트에_가게_이름_주소_영업시간_소개_메뉴가_들어간다() {
        String p = PostCaptionService.prompt(store, "카페라떼의 부드러움을 즐겨보세요", "수정 요청(글·문구): 학생 할인 넣어 주세요",
                List.of(new MenuItemDto("시그니처 라떼", 5500, "우유 거품을 듬뿍", true),
                        new MenuItemDto("아메리카노", null, null, false)));

        assertThat(p).contains("가게 이름: [샘플] 광운 카페")
                .contains("주소: 서울 노원구 광운로 20")
                .contains("영업시간: 매일 10:00~21:00")
                .contains("가게 소개: 햇살 드는 창가 자리가 있는 동네 카페")
                .contains("- 시그니처 라떼 5,500원 (대표 메뉴) — 우유 거품을 듬뿍")
                .contains("- 아메리카노\n")
                .contains("사장님 한마디: 카페라떼의 부드러움을 즐겨보세요")
                .contains("학생 할인 넣어 주세요")
                .contains("300자 이내");
    }

    @Test
    void 비어_있는_정보는_없음으로_적는다() {
        Store bare = Store.builder().name("골목 식당").category(StoreCategory.CAFE_BAKERY_PUB).address("서울 중구").build();
        String p = PostCaptionService.prompt(bare, null, null, null);

        assertThat(p).contains("영업시간: 없음").contains("가게 소개: 없음").contains("메뉴:\n없음")
                .contains("사장님 한마디: 없음");
    }

    @Test
    void 너무_길면_문장_끝이나_줄바꿈에서_자른다() {
        String sentence = "따뜻한 라떼 한 잔과 함께 쉬어 가세요. ";
        String longText = sentence.repeat(20);   // 400자 이상

        String cut = PostCaptionService.trim(longText);

        assertThat(cut.length()).isLessThanOrEqualTo(PostCaptionService.MAX_LENGTH);
        assertThat(cut).endsWith("가세요.");
        assertThat(PostCaptionService.trim("짧은 글")).isEqualTo("짧은 글");
    }
}
