package com.commicom.itda.domain.store.controller;

import com.commicom.itda.domain.store.dto.StoreCategoryResponse;
import com.commicom.itda.domain.store.dto.StoreCreateRequest;
import com.commicom.itda.domain.store.dto.StoreDetailResponse;
import com.commicom.itda.domain.store.dto.StoreListResponse;
import com.commicom.itda.domain.store.dto.StoreRankingDtos.DistrictResponse;
import com.commicom.itda.domain.store.dto.StoreRankingDtos.StoreRankingResponse;
import com.commicom.itda.domain.store.dto.StoreUpdateRequest;
import com.commicom.itda.domain.store.entity.StoreCategory;
import com.commicom.itda.domain.store.service.StoreRankingService;
import com.commicom.itda.domain.store.service.StoreService;
import com.commicom.itda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Store", description = "가게 조회·사장님 가게 등록/수정")
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final StoreRankingService rankingService;

    @Operation(summary = "가게 목록 조회", description = "지도에 표시할 가게 목록을 조회한다. category 를 주면 업종으로 필터링한다.")
    @GetMapping
    public ApiResponse<StoreListResponse> getStores(
            @Parameter(description = "업종 필터 (없으면 전체)")
            @RequestParam(required = false) StoreCategory category) {
        return ApiResponse.onSuccess(storeService.getStores(category));
    }

    @Operation(summary = "업종 목록 조회", description = "가게 목록 필터에 쓸 업종 대분류를 화면 표시 순서대로 조회한다.")
    @GetMapping("/categories")
    public ApiResponse<List<StoreCategoryResponse>> getCategories() {
        return ApiResponse.onSuccess(StoreCategoryResponse.all());
    }

    @Operation(summary = "가게 검색·동네 랭킹",
            description = "기간 내 실제 방문 수(QR 방문 인증) 많은 순. 방문이 없는 가게도 뒤에 붙는다. "
                    + "district(자치구)·category(업종)·q(가게 이름)로 거른다. days 는 7(기본)·14·31 만")
    @GetMapping("/ranking")
    public ApiResponse<StoreRankingResponse> getRanking(
            @Parameter(description = "자치구 (예: 노원구). 없으면 전체 지역") @RequestParam(required = false) String district,
            @Parameter(description = "업종 (없으면 모든 업종)") @RequestParam(required = false) StoreCategory category,
            @Parameter(description = "가게 이름 검색어 (띄어쓰기·대소문자 무시)") @RequestParam(required = false) String q,
            @Parameter(description = "집계 기간: 7·14·31일") @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.onSuccess(rankingService.getRanking(district, category, q, days));
    }

    @Operation(summary = "지역(자치구) 목록", description = "가게가 있는 자치구와 가게 수 (가나다 순). 랭킹의 지역 선택에 쓴다")
    @GetMapping("/districts")
    public ApiResponse<List<DistrictResponse>> getDistricts() {
        return ApiResponse.onSuccess(rankingService.getDistricts());
    }

    @Operation(summary = "내 가게 조회", description = "로그인한 사장님의 가게. 아직 등록하지 않았으면 STORE404_2")
    @GetMapping("/me")
    public ApiResponse<StoreDetailResponse> getMyStore(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(storeService.getMyStore(memberId));
    }

    @Operation(summary = "가게 상세 조회")
    @GetMapping("/{storeId}")
    public ApiResponse<StoreDetailResponse> getStore(@PathVariable Long storeId) {
        return ApiResponse.onSuccess(storeService.getStore(storeId));
    }

    @Operation(summary = "가게 등록 (사장님)",
            description = "multipart: data(JSON: name, category, address, latitude, longitude) + image(선택, 대표 사진). 사장님 1명당 1곳")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoreDetailResponse> createStore(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestPart("data") StoreCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ApiResponse.onSuccess(storeService.createStore(memberId, request, image));
    }

    @Operation(summary = "가게 정보 수정 (사장님)", description = "보낸 항목만 바뀐다. 이름을 바꾸면 사장님 닉네임도 같이 바뀐다")
    @PatchMapping("/{storeId}")
    public ApiResponse<StoreDetailResponse> updateStore(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreUpdateRequest request) {
        return ApiResponse.onSuccess(storeService.updateStore(memberId, storeId, request));
    }

    @Operation(summary = "가게 대표 사진 수정 (사장님)")
    @PatchMapping(value = "/{storeId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoreDetailResponse> updateStoreImage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId,
            @RequestPart MultipartFile image) {
        return ApiResponse.onSuccess(storeService.updateStoreImage(memberId, storeId, image));
    }

    @Operation(summary = "가게 사진 업로드 (사장님)",
            description = "게시물에 넣을 메뉴판·음식·가게 사진을 올린다. 반환된 URL을 생성 요청의 photoUrls에 사용한다. 내 가게만 (STORE403_2)")
    @PostMapping(value = "/{storeId}/photos", consumes = "multipart/form-data")
    public ApiResponse<List<String>> uploadPhotos(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId,
            @RequestPart("photos") List<MultipartFile> photos) {
        return ApiResponse.onSuccess(storeService.uploadPhotos(memberId, storeId, photos));
    }
}
