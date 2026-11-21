package com.commicom.itda.domain.generation.storyboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryboardPromptTest {

    private final StoryboardPrompt prompt = new StoryboardPrompt(
            new ClassPathResource("prompts/storyboard.txt"),
            new ClassPathResource("prompts/storyboard-fix.txt"));

    @Test
    void 가게_정보와_메뉴_JSON이_채워진다() throws IOException {
        MenuData menu = StoryboardJson.parseMenu(
                Files.readString(Path.of("docs/storyboard-samples/02-cafe.menu.json")));

        String text = prompt.build(menu, "매일 아침 직접 굽는 소금빵");

        assertThat(text).contains("가게 이름: 골목카페")
                .contains("업종: 카페·베이커리·주점")
                .contains("사장님 한마디: 매일 아침 직접 굽는 소금빵")
                .contains("\"name\" : \"소금빵\"")
                .contains("\"is_signature\" : true")
                .contains("\"review_keywords\"")
                .doesNotContain("{storeName}", "{category}", "{appeal}", "{menuJson}");
        // 편의 메서드(isUpload 등)가 JSON 필드로 새어 나가지 않음
        assertThat(text).doesNotContain("\"upload\" :", "\"generated\" :");
        // Windows에서 CRLF로 체크아웃돼도 줄바꿈은 LF
        assertThat(text).doesNotContain("\r");
    }

    @Test
    void 한마디가_없으면_없음으로_표시한다() throws IOException {
        MenuData menu = StoryboardJson.parseMenu(
                Files.readString(Path.of("docs/storyboard-samples/03-izakaya.menu.json")));
        assertThat(prompt.build(menu, "  ")).contains("사장님 한마디: (없음)");
    }

    @Test
    void 음식_사진이_없으면_장면과_가격을_제한한다() {
        var menu = new MenuData("카페", "CAFE_BAKERY_PUB",
                List.of(new MenuData.Menu("아메리카노", "4,000", 4000, null, false)), List.of(),
                List.of(new MenuData.Photo(0, "upload", "store", null, "창가 테이블")), List.of());

        assertThat(prompt.build(menu, null))
                .contains("가운데 장면은 전부 review", "모든 menu_ref는 null")
                .doesNotContain("{scenePhotoRules}");
    }

    @Test
    void 가격은_그_메뉴가_연결된_음식_사진에서만_허용한다() {
        var menu = new MenuData("카페", "CAFE_BAKERY_PUB",
                List.of(new MenuData.Menu("아메리카노", "4,000", 4000, null, false)), List.of(),
                List.of(new MenuData.Photo(0, "upload", "store", null, "창가 테이블"),
                        new MenuData.Photo(1, "generated", "food", "아메리카노", "아메리카노")), List.of());

        assertThat(prompt.build(menu, null))
                .contains("menu 장면은 사진 [1]만", "detail 장면은 생략", "1번 사진의 아메리카노 4,000원");
    }

    @Test
    void 가게_주소가_제목용으로_들어간다() throws IOException {
        MenuData menu = StoryboardJson.parseMenu(
                Files.readString(Path.of("docs/storyboard-samples/01-donkatsu.menu.json")));
        assertThat(prompt.build(menu, null, "서울 성동구 성수동2가 1"))
                .contains("가게 주소: 서울 성동구 성수동2가 1")
                .doesNotContain("{address}");
        assertThat(prompt.build(menu, null)).contains("가게 주소: (없음)");
    }

    @Test
    void 수정_요청에_이전_출력과_오류가_들어간다() {
        String fix = prompt.buildFix("원래 프롬프트", "{\"title\":\"x\"}", List.of("오류 A", "오류 B"));
        assertThat(fix).startsWith("원래 프롬프트")
                .contains("{\"title\":\"x\"}")
                .contains("- 오류 A\n- 오류 B");
    }
}
