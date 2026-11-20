package com.commicom.itda.domain.review.dto;

import com.commicom.itda.domain.pigeon.dto.PigeonDtos.Reward;
import com.commicom.itda.domain.quest.entity.QuestVisit;
import com.commicom.itda.domain.review.entity.Review;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.global.util.KstTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** 리뷰 작성 multipart 의 data. 사진은 photos 파트, 영상은 video 파트 (합쳐서 최대 3개, 영상은 1개) */
    public record CreateRequest(
            @NotNull Long visitId,
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 200) String content,
            /** 없으면 공개 */
            Boolean privateReview
    ) {
        public boolean isPrivate() {
            return Boolean.TRUE.equals(privateReview);
        }
    }

    /**
     * 리뷰 수정 multipart 의 data. keepPhotoUrls = 남길 기존 사진 (순서대로), keepVideo = 기존 영상을 남길지.
     * 새 사진은 photos, 새 영상은 video 파트 (새 영상을 보내면 기존 영상은 바뀜)
     */
    public record UpdateRequest(
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 200) String content,
            /** 없으면 공개 */
            Boolean privateReview,
            List<String> keepPhotoUrls,
            /** 없으면 기존 영상을 지움 */
            Boolean keepVideo
    ) {
        public boolean isPrivate() {
            return Boolean.TRUE.equals(privateReview);
        }

        public boolean shouldKeepVideo() {
            return Boolean.TRUE.equals(keepVideo);
        }
    }

    /** drawsLeft: 작성자 본인에게만 (내 리뷰), 다른 사람에겐 null */
    public record ReviewResponse(
            Long reviewId,
            Long visitId,
            Long storeId,
            String storeName,
            String storeThumbnailUrl,
            Long memberId,
            String nickname,
            String profileImageUrl,
            int rating,
            String content,
            List<String> photoUrls,
            String videoUrl,
            boolean privateReview,
            boolean hidden,
            Integer drawsLeft,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static ReviewResponse from(Review r) {
            return from(r, null);
        }

        public static ReviewResponse from(Review r, Integer drawsLeft) {
            return new ReviewResponse(
                    r.getId(),
                    r.getVisitId(),
                    r.getStore().getId(),
                    r.getStore().getName(),
                    r.getStore().getThumbnailUrl(),
                    r.getMember().getId(),
                    r.getMember().getNickname(),
                    r.getMember().getProfileImageUrl(),
                    r.getRating(),
                    r.getContent(),
                    List.copyOf(r.getPhotoUrls()),
                    r.getVideoUrl(),
                    r.isPrivateReview(),
                    r.isHidden(),
                    drawsLeft,
                    KstTime.fromServerTime(r.getCreatedAt()),
                    KstTime.fromServerTime(r.getUpdatedAt()));
        }
    }

    /** 리뷰 작성 결과. drawsGranted: 이번에 받은 뽑기 횟수 (3, 이 인증으로 이미 받았으면 0) */
    public record CreateResponse(ReviewResponse review, int drawsGranted) {
    }

    /** 리뷰 삭제 결과. revokedDraws: 사라진 남은 뽑기 횟수 */
    public record DeleteResponse(int revokedDraws) {
    }

    public record MyReviewListResponse(int count, List<ReviewResponse> reviews) {
    }

    /**
     * 가게 리뷰 목록. count·averageRating 은 손님에게 보이는(숨김·비공개 아닌) 리뷰 기준
     * (averageRating 은 소수 첫째 자리, 없으면 0).
     * 그 가게 사장님이 조회하면 숨긴·비공개 리뷰도 reviews 에 들어가고 hiddenCount·privateCount 가 채워짐 (다른 사람에겐 0)
     */
    public record StoreReviewListResponse(int count, double averageRating, int hiddenCount, int privateCount,
                                          List<ReviewResponse> reviews) {
    }

    /** 사장님 리뷰 숨기기·다시 보이기 */
    public record VisibilityRequest(@NotNull Boolean hidden) {
    }

    /**
     * 리뷰를 쓸 수 있는 QR 방문 인증 (내 인증 전부 — 같은 인증으로 여러 개 쓸 수 있음).
     * rewardAvailable: 이 인증으로 리뷰를 쓰면 뽑기 3회를 받는지 (이미 보상을 받았으면 false).
     * reviewCount: 이 인증으로 쓴 (지금 남아 있는) 리뷰 수
     */
    public record WritableVisitResponse(Long visitId, Long storeId, String storeName, String storeThumbnailUrl,
                                        OffsetDateTime visitedAt, boolean rewardAvailable, int reviewCount) {
        public static WritableVisitResponse of(QuestVisit v, Store s, boolean rewardAvailable, int reviewCount) {
            return new WritableVisitResponse(v.getId(), s.getId(), s.getName(), s.getThumbnailUrl(),
                    KstTime.fromServerTime(v.getCreatedAt()), rewardAvailable, reviewCount);
        }
    }

    /** 리뷰 보상 뽑기 1회 결과. reward 는 레벨업 뽑기와 같은 모양 (FEED / COUPON) */
    public record DrawResponse(Reward reward, int drawsLeft, int feedBalance) {
    }
}
