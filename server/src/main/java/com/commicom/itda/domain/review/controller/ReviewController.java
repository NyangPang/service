package com.commicom.itda.domain.review.controller;

import com.commicom.itda.domain.review.dto.ReviewDtos.CreateRequest;
import com.commicom.itda.domain.review.dto.ReviewDtos.CreateResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.DeleteResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.DrawResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.MyReviewListResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.ReviewResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.StoreReviewListResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.UpdateRequest;
import com.commicom.itda.domain.review.dto.ReviewDtos.VisibilityRequest;
import com.commicom.itda.domain.review.dto.ReviewDtos.WritableVisitResponse;
import com.commicom.itda.domain.review.service.ReviewService;
import com.commicom.itda.global.response.ApiResponse;
import com.commicom.itda.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Review", description = "가게 리뷰 (별점·사진/영상·본문·공개 여부). QR 방문 인증이 있어야 쓰고 여러 개 가능, 보상 뽑기 3회는 인증당 첫 리뷰에만")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "가게 리뷰 목록",
            description = "최신순. 로그인 없이 조회 가능. 숨긴·비공개 리뷰는 빠지고, 그 가게 사장님이 조회하면 함께 나온다")
    @GetMapping("/api/stores/{storeId}/reviews")
    public ApiResponse<StoreReviewListResponse> getStoreReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId) {
        return ApiResponse.onSuccess(reviewService.getStoreReviews(storeId, memberId));
    }

    @Operation(summary = "리뷰 숨기기·다시 보이기 (사장님)",
            description = "Body { \"hidden\": true|false }. 내 가게가 아니면 STORE403_2, 이 가게 리뷰가 아니면 REVIEW404")
    @PatchMapping("/api/stores/{storeId}/reviews/{reviewId}/visibility")
    public ApiResponse<ReviewResponse> changeVisibility(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long storeId,
            @PathVariable Long reviewId,
            @Valid @RequestBody VisibilityRequest request) {
        return ApiResponse.onSuccess(reviewService.changeVisibility(memberId, storeId, reviewId, request.hidden()));
    }

    @Operation(summary = "리뷰 작성",
            description = "multipart: data(JSON: visitId, rating 1~5, content 최대 200자, privateReview) + photos(선택) + video(선택, 1개). "
                    + "사진·영상 합쳐 최대 3개. 같은 인증으로 여러 개 쓸 수 있고, 그 인증으로 처음 쓰는 리뷰면 뽑기 3회 (drawsGranted)")
    @PostMapping(value = "/api/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestPart("data") CreateRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            @RequestPart(value = "video", required = false) MultipartFile video) {
        return ApiResponse.of(SuccessStatus.CREATED, reviewService.create(memberId, request, photos, video));
    }

    @Operation(summary = "내 리뷰 목록 (손님 마이페이지)", description = "최신순. drawsLeft = 남은 보상 뽑기")
    @GetMapping("/api/reviews/me")
    public ApiResponse<MyReviewListResponse> getMyReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(reviewService.getMyReviews(memberId));
    }

    @Operation(summary = "리뷰를 쓸 수 있는 방문 인증",
            description = "내 QR 방문 인증 전부 (최근 순). rewardAvailable=false 면 이 인증으로 이미 보상을 받아 더 써도 뽑기 없음. reviewCount = 이 인증으로 쓴 리뷰 수")
    @GetMapping("/api/reviews/writable-visits")
    public ApiResponse<List<WritableVisitResponse>> getWritableVisits(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(reviewService.getWritableVisits(memberId));
    }

    @Operation(summary = "리뷰 수정",
            description = "multipart: data(JSON: rating, content, privateReview, keepPhotoUrls, keepVideo) + photos(새 사진) + video(새 영상). "
                    + "합쳐서 최대 3개, 영상 1개")
    @PatchMapping(value = "/api/reviews/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long reviewId,
            @Valid @RequestPart("data") UpdateRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            @RequestPart(value = "video", required = false) MultipartFile video) {
        return ApiResponse.onSuccess(reviewService.update(memberId, reviewId, request, photos, video));
    }

    @Operation(summary = "리뷰 삭제", description = "남은 보상 뽑기는 사라진다 (revokedDraws). 이미 받은 보상은 그대로")
    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiResponse<DeleteResponse> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long reviewId) {
        return ApiResponse.onSuccess(reviewService.delete(memberId, reviewId));
    }

    @Operation(summary = "리뷰 보상 뽑기 1회",
            description = "카드 3장(먹이 1개 / 먹이 2개 / 쿠폰) 중 결과. 쿠폰 3% · 먹이 1개 70% · 먹이 2개 27%. "
                    + "먹이는 보유 먹이에 쌓임. 남은 뽑기가 없으면 REVIEW409_2")
    @PostMapping("/api/reviews/{reviewId}/draws")
    public ApiResponse<DrawResponse> draw(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long reviewId) {
        return ApiResponse.onSuccess(reviewService.draw(memberId, reviewId));
    }
}
