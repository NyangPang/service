package com.commicom.itda.domain.review.service;

import com.commicom.itda.domain.member.entity.Member;
import com.commicom.itda.domain.member.repository.MemberRepository;
import com.commicom.itda.domain.notification.service.NotificationService;
import com.commicom.itda.domain.pigeon.dto.PigeonDtos.Reward;
import com.commicom.itda.domain.pigeon.dto.PigeonDtos.RewardCoupon;
import com.commicom.itda.domain.pigeon.service.PigeonRewardCouponIssuer;
import com.commicom.itda.domain.pigeon.service.PigeonService;
import com.commicom.itda.domain.quest.entity.QuestVisit;
import com.commicom.itda.domain.quest.repository.QuestVisitRepository;
import com.commicom.itda.domain.review.dto.ReviewDtos.CreateRequest;
import com.commicom.itda.domain.review.dto.ReviewDtos.CreateResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.DeleteResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.DrawResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.MyReviewListResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.ReviewResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.StoreReviewListResponse;
import com.commicom.itda.domain.review.dto.ReviewDtos.UpdateRequest;
import com.commicom.itda.domain.review.dto.ReviewDtos.WritableVisitResponse;
import com.commicom.itda.domain.review.entity.Review;
import com.commicom.itda.domain.review.entity.ReviewDraw;
import com.commicom.itda.domain.review.entity.ReviewDrawTicket;
import com.commicom.itda.domain.review.repository.ReviewDrawRepository;
import com.commicom.itda.domain.review.repository.ReviewDrawTicketRepository;
import com.commicom.itda.domain.review.repository.ReviewRepository;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.domain.store.repository.StoreRepository;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import com.commicom.itda.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 가게 리뷰: 손님 마이페이지 > 내 리뷰 (작성·수정·삭제·보상 뽑기), 사장님 가게 프로필 > 리뷰 (조회·숨기기).
 * <p>
 * 기준은 QR 방문 인증 1건: 인증 1건당 리뷰는 평생 1개, 쓰면 보상 뽑기 3회.
 * 리뷰를 지우면 남은 뽑기는 사라지고, 그 인증으로는 다시 쓸 수 없음 (뽑기권이 남아 있어서).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 리뷰 보상 뽑기 확률 (잇둘기 레벨과 무관하게 고정). 나머지는 먹이 2개 */
    static final double COUPON_RATE = 0.03;
    static final double FEED1_RATE = 0.70;

    /** 영상 1개 최대 크기 (30초 영상 기준. 길이는 클라이언트에서 확인) */
    private static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;

    private final ReviewRepository reviewRepository;
    private final ReviewDrawTicketRepository ticketRepository;
    private final ReviewDrawRepository drawRepository;
    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final QuestVisitRepository visitRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final PigeonService pigeonService;
    private final PigeonRewardCouponIssuer rewardCouponIssuer;

    @Transactional
    public CreateResponse create(Long memberId, CreateRequest request, List<MultipartFile> photos, MultipartFile video) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        QuestVisit visit = visitRepository.findById(request.visitId())
                .filter(v -> v.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_VISIT_NOT_FOUND));
        Store store = storeRepository.findById(visit.getStoreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        // 인증 1건당 리뷰 1개. 지운 리뷰도 포함 (뽑기권은 지워도 남음). 예전 데이터엔 뽑기권 없는 리뷰도 있어서 둘 다 봄
        if (ticketRepository.existsByVisitId(visit.getId()) || reviewRepository.existsByVisitId(visit.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
        }

        List<MultipartFile> photoFiles = nonEmpty(photos);
        MultipartFile videoFile = isPresent(video) ? video : null;
        validateMedia(photoFiles.size(), videoFile);

        Review review = reviewRepository.save(Review.builder()
                .member(member)
                .store(store)
                .visitId(visit.getId())
                .rating(request.rating())
                .content(request.content().trim())
                .privateReview(request.isPrivate())
                .photoUrls(uploadPhotos(photoFiles, store.getId()))
                .videoUrl(videoFile == null ? null : storageService.upload(videoFile, "reviews/" + store.getId()))
                .build());

        // 뽑기 3회. visitId 가 unique 라 동시에 두 번 써도 하나는 실패하고 리뷰까지 롤백됨
        ticketRepository.saveAndFlush(new ReviewDrawTicket(memberId, visit.getId(), review.getId()));
        int granted = ReviewDrawTicket.DRAWS_PER_REVIEW;
        notificationService.notifyReviewCreated(review);
        return new CreateResponse(ReviewResponse.from(review, granted), granted);
    }

    @Transactional
    public ReviewResponse update(Long memberId, Long reviewId, UpdateRequest request, List<MultipartFile> photos, MultipartFile video) {
        Review review = findMine(memberId, reviewId);

        // 원래 있던 사진만 남길 수 있음
        List<String> keep = request.keepPhotoUrls() == null ? List.of()
                : request.keepPhotoUrls().stream().filter(review.getPhotoUrls()::contains).distinct().toList();
        List<MultipartFile> photoFiles = nonEmpty(photos);
        MultipartFile videoFile = isPresent(video) ? video : null;
        boolean keepVideo = request.shouldKeepVideo() && review.getVideoUrl() != null && videoFile == null;
        if (videoFile != null) validateVideo(videoFile);
        int mediaCount = keep.size() + photoFiles.size() + (keepVideo || videoFile != null ? 1 : 0);
        if (mediaCount > Review.MAX_MEDIA) {
            throw new BusinessException(ErrorCode.REVIEW_TOO_MANY_MEDIA);
        }

        Long storeId = review.getStore().getId();
        List<String> photoUrls = new ArrayList<>(keep);
        photoUrls.addAll(uploadPhotos(photoFiles, storeId));
        String videoUrl = videoFile != null ? storageService.upload(videoFile, "reviews/" + storeId)
                : keepVideo ? review.getVideoUrl() : null;
        review.update(request.rating(), request.content().trim(), request.isPrivate(), photoUrls, videoUrl);
        // 응답의 updatedAt 이 바뀐 값이 되도록 먼저 반영
        Review saved = reviewRepository.saveAndFlush(review);
        return ReviewResponse.from(saved, drawsLeftOf(saved.getId()));
    }

    /** 리뷰 삭제: 남은 뽑기는 사라짐 (이미 받은 보상은 그대로) */
    @Transactional
    public DeleteResponse delete(Long memberId, Long reviewId) {
        Review review = findMine(memberId, reviewId);
        int revoked = ticketRepository.findByReviewId(reviewId).map(ReviewDrawTicket::revoke).orElse(0);
        reviewRepository.delete(review);
        return new DeleteResponse(revoked);
    }

    /** 리뷰 보상 뽑기 1회: 카드 3장(먹이 1개 / 먹이 2개 / 쿠폰) 중 결과를 서버가 정함 */
    @Transactional
    public DrawResponse draw(Long memberId, Long reviewId) {
        findMine(memberId, reviewId);
        ReviewDrawTicket ticket = ticketRepository.findByReviewIdForUpdate(reviewId)
                .filter(t -> t.getRemaining() > 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NO_DRAWS_LEFT));
        ticket.use();

        double r = ThreadLocalRandom.current().nextDouble();
        Reward reward;
        Long userCouponId = null;
        if (r < COUPON_RATE) {
            RewardCoupon coupon = rewardCouponIssuer.issue(memberId).orElse(null);
            // 쿠폰 풀이 비었으면 먹이 2개로 대체 (레벨업 뽑기와 같음)
            reward = coupon != null ? new Reward("COUPON", 0, coupon) : new Reward("FEED", 2, null);
            userCouponId = coupon == null ? null : coupon.userCouponId();
        } else {
            reward = new Reward("FEED", r < COUPON_RATE + FEED1_RATE ? 1 : 2, null);
        }
        drawRepository.save(new ReviewDraw(memberId, ticket.getId(), reward.type(), reward.feedAmount(), userCouponId));
        int balance = reward.feedAmount() > 0
                ? pigeonService.addDrawFeed(memberId, reward.feedAmount())
                : pigeonService.feedBalance(memberId);
        return new DrawResponse(reward, ticket.getRemaining(), balance);
    }

    @Transactional(readOnly = true)
    public MyReviewListResponse getMyReviews(Long memberId) {
        List<Review> reviews = reviewRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        Map<Long, Integer> left = ticketRepository.findAllByReviewIdIn(reviews.stream().map(Review::getId).toList()).stream()
                .collect(Collectors.toMap(ReviewDrawTicket::getReviewId, ReviewDrawTicket::getRemaining));
        List<ReviewResponse> list = reviews.stream().map(r -> ReviewResponse.from(r, left.getOrDefault(r.getId(), 0))).toList();
        return new MyReviewListResponse(list.size(), list);
    }

    /** viewerId: 로그인했으면 회원 ID (없으면 null). 그 가게 사장님이면 숨긴·비공개 리뷰도 함께 */
    @Transactional(readOnly = true)
    public StoreReviewListResponse getStoreReviews(Long storeId, Long viewerId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        boolean owner = viewerId != null && store.isOwnedBy(viewerId);

        List<Review> all = reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId);
        List<Review> visible = all.stream().filter(Review::isPublic).toList();
        double average = visible.stream().mapToInt(Review::getRating).average().orElse(0);
        List<ReviewResponse> reviews = (owner ? all : visible).stream().map(ReviewResponse::from).toList();
        return new StoreReviewListResponse(visible.size(), Math.round(average * 10) / 10.0,
                owner ? (int) all.stream().filter(Review::isHidden).count() : 0,
                owner ? (int) all.stream().filter(Review::isPrivateReview).count() : 0,
                reviews);
    }

    /** 사장님: 우리 가게 리뷰 숨기기·다시 보이기 (손님의 남은 뽑기는 그대로) */
    @Transactional
    public ReviewResponse changeVisibility(Long memberId, Long storeId, Long reviewId, boolean hidden) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.STORE_FORBIDDEN);
        }
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getStore().getId().equals(storeId))
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        review.changeHidden(hidden);
        return ReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    /** 리뷰를 쓸 수 있는 QR 방문 인증: 아직 리뷰를 안 쓴 내 인증만 (최근 인증 순). 리뷰를 지운 인증도 빠짐 */
    @Transactional(readOnly = true)
    public List<WritableVisitResponse> getWritableVisits(Long memberId) {
        List<QuestVisit> visits = visitRepository.findAllByMemberId(memberId).stream()
                .sorted(Comparator.comparing(QuestVisit::getId).reversed())
                .toList();
        Set<Long> used = ticketRepository.findAllByVisitIdIn(visits.stream().map(QuestVisit::getId).toList()).stream()
                .map(ReviewDrawTicket::getVisitId).collect(Collectors.toCollection(HashSet::new));
        reviewRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(Review::getVisitId).filter(id -> id != null).forEach(used::add);
        List<QuestVisit> writable = visits.stream().filter(v -> !used.contains(v.getId())).toList();
        Map<Long, Store> stores = storeRepository.findAllById(writable.stream().map(QuestVisit::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        return writable.stream()
                .filter(v -> stores.containsKey(v.getStoreId()))
                .map(v -> WritableVisitResponse.of(v, stores.get(v.getStoreId()), true, 0))
                .toList();
    }

    /** 회원 탈퇴 시 */
    @Transactional
    public void deleteAllOf(Member member) {
        reviewRepository.deleteAll(reviewRepository.findAllByMember(member));
        drawRepository.deleteByMemberId(member.getId());
        ticketRepository.deleteByMemberId(member.getId());
    }

    private Integer drawsLeftOf(Long reviewId) {
        return ticketRepository.findByReviewId(reviewId).map(ReviewDrawTicket::getRemaining).orElse(0);
    }

    private Review findMine(Long memberId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.REVIEW_FORBIDDEN);
        }
        return review;
    }

    private void validateMedia(int photoCount, MultipartFile video) {
        if (video != null) validateVideo(video);
        if (photoCount + (video != null ? 1 : 0) > Review.MAX_MEDIA) {
            throw new BusinessException(ErrorCode.REVIEW_TOO_MANY_MEDIA);
        }
    }

    private void validateVideo(MultipartFile video) {
        String type = video.getContentType();
        if (type == null || !type.startsWith("video/") || video.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.REVIEW_INVALID_VIDEO);
        }
    }

    private List<String> uploadPhotos(List<MultipartFile> files, Long storeId) {
        return files.stream().map(f -> storageService.upload(f, "reviews/" + storeId)).toList();
    }

    private static boolean isPresent(MultipartFile f) {
        return f != null && !f.isEmpty();
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> photos) {
        return photos == null ? List.of() : photos.stream().filter(ReviewService::isPresent).toList();
    }
}
