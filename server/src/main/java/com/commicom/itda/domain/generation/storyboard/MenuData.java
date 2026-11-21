package com.commicom.itda.domain.generation.storyboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * 메뉴 추출 결과 + 사진 목록 (docs/storyboard-schema.md 3절).
 * 사장님 확인을 거친 값이며, 스토리보드 생성 입력이자 가격·사진 검증의 기준이 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuData(
        @JsonProperty("store_name") String storeName,
        String category,
        List<Menu> menus,
        @JsonProperty("review_keywords") List<String> reviewKeywords,
        List<Photo> photos,
        /** 음식 사진이 없을 때 B 파트가 Runway로 만들 이미지 요청. 만들 게 없으면 [] */
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("image_requests") List<ImageRequest> imageRequests
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Menu(
            String name,
            @JsonProperty("price_text") String priceText,
            Integer price,
            String description,
            @JsonProperty("is_signature") boolean signature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(
            int index,
            /** upload (사장님 사진) / generated (AI 생성 이미지) */
            String source,
            /** food / store(가게 안·분위기) / exterior(가게 외관) / menu_board */
            String kind,
            @JsonProperty("menu_ref") String menuRef,
            String description
    ) {
        @JsonIgnore
        public boolean isUpload() {
            return "upload".equals(source);
        }

        @JsonIgnore
        public boolean isGenerated() {
            return "generated".equals(source);
        }

        /** 영상 장면에 쓸 수 있는 사진: 메뉴판이 아닌 것 */
        @JsonIgnore
        public boolean isUsable() {
            return !"menu_board".equals(kind);
        }
    }

    /**
     * 생성 이미지 한 장 요청 (docs/storyboard-schema.md 3절).
     * B 파트가 prompt로 이미지를 만들어 photos[] 끝에 source=generated, kind=food, menu_ref로 붙인다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageRequest(
            @JsonProperty("menu_ref") String menuRef,
            /** Runway 이미지 생성 프롬프트 (영어) */
            String prompt
    ) {}

    public Optional<Menu> findMenu(String name) {
        if (name == null || menus == null) return Optional.empty();
        return menus.stream().filter(m -> name.equals(m.name())).findFirst();
    }

    public Optional<Photo> findPhoto(Integer index) {
        if (index == null || photos == null) return Optional.empty();
        return photos.stream().filter(p -> p.index() == index).findFirst();
    }

    /** 사장님이 올린 음식 사진이 하나라도 있는지 — 없으면 이미지 생성 대상 */
    public boolean hasUploadFoodPhoto() {
        return photos != null && photos.stream().anyMatch(p -> p.isUpload() && "food".equals(p.kind()));
    }

    /** 장면 JSON 프롬프트에 넣을 때는 이미지 요청을 뺀다 (이미 생성돼 photos[]에 들어간 뒤라 필요 없음) */
    public MenuData withoutImageRequests() {
        return new MenuData(storeName, category, menus, reviewKeywords, photos, null);
    }

    /** 영상에 쓸 수 있는 사진(메뉴판 제외) 수. 이 수가 3장보다 적으면 그만큼 이미지를 만든다 */
    public long usablePhotoCount() {
        return photos == null ? 0 : photos.stream().filter(p -> p != null && p.isUsable()).count();
    }
}
