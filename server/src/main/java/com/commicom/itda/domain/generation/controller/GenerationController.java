package com.commicom.itda.domain.generation.controller;

import com.commicom.itda.domain.generation.dto.GenerationRequest;
import com.commicom.itda.domain.generation.dto.GenerationResponse;
import com.commicom.itda.domain.generation.dto.GenerationStatusResponse;
import com.commicom.itda.domain.generation.dto.MenuItemDto;
import com.commicom.itda.domain.generation.service.GenerationService;
import com.commicom.itda.domain.generation.service.MenuReadService;
import com.commicom.itda.global.response.ApiResponse;
import com.commicom.itda.infra.scraping.MapPlaceReader;
import com.commicom.itda.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Generation", description = "AI 게시물 생성")
@RestController
@RequestMapping("/api/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;
    private final MenuReadService menuReadService;
    private final MapPlaceReader mapPlaceReader;

    @Operation(summary = "메뉴판 사진에서 메뉴 읽기 (메뉴 확인 화면)",
            description = "메뉴판 사진(1~3장)을 AI로 읽어 메뉴 목록을 돌려준다. 영상 만들 때와 같은 메뉴 추출기를 쓴다. "
                    + "사장님이 고친 목록을 POST /api/generation 의 menus로 보내면 영상은 그 메뉴로 만든다. "
                    + "재시도까지 수십 초 걸릴 수 있다. 끝내 못 읽으면 GENERATION502")
    @PostMapping(value = "/menus", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<MenuItemDto>> readMenus(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @RequestParam Long storeId,
            @RequestPart("menuBoards") List<MultipartFile> menuBoards) {
        return ApiResponse.onSuccess(menuReadService.read(memberId, storeId, menuBoards));
    }

    @Operation(summary = "지도 링크에서 가게명·주소 읽기 (메뉴 확인 화면)",
            description = "네이버 지도·카카오맵 공유 링크(단축 링크 포함)의 장소 페이지에서 가게명과 주소를 읽는다. "
                    + "못 읽으면 name·address가 null — 화면은 등록된 가게 정보로 채운다")
    @GetMapping("/place")
    public ApiResponse<MapPlaceReader.Place> readPlace(@RequestParam String url) {
        MapPlaceReader.Place place = mapPlaceReader.read(url);
        return ApiResponse.onSuccess(place != null ? place : new MapPlaceReader.Place(null, null));
    }

    @Operation(summary = "AI 게시물 생성 요청",
            description = "가게 ID를 받아 AI 게시물 생성을 요청한다. 비동기 처리이므로 즉시 완료되지 않는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GenerationResponse> requestGeneration(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody GenerationRequest request) {
        return ApiResponse.of(SuccessStatus.CREATED,
                generationService.requestGeneration(memberId, request));
    }

    @Operation(summary = "AI 게시물 생성 상태 조회",
            description = "생성 요청 ID로 현재 진행 상태를 조회한다. COMPLETED이면 shortformId가 채워진다.")
    @GetMapping("/{generationId}")
    public ApiResponse<GenerationStatusResponse> getStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long generationId) {
        return ApiResponse.onSuccess(generationService.getStatus(memberId, generationId));
    }

    @Operation(summary = "AI 게시물 생성 취소",
            description = "만드는 중(PENDING·PROCESSING)일 때만. 결과를 저장하지 않고 FAILED(사장님이 생성을 취소했어요)로 끝난다. 끝난 생성이면 GENERATION409_2")
    @PostMapping("/{generationId}/cancel")
    public ApiResponse<Void> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long generationId) {
        generationService.cancel(memberId, generationId);
        return ApiResponse.onSuccess(null);
    }
}
