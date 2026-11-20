package com.commicom.itda.domain.review.entity;

import com.commicom.itda.domain.member.entity.Member;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 가게 리뷰 (별점 1~5 · 사진/영상 · 본문 · 공개/비공개).
 * QR 방문 인증(visitId)이 있어야 쓸 수 있고, 같은 인증으로 여러 개 쓸 수 있음 (보상 뽑기는 인증당 첫 리뷰에만). 사장님은 숨길 수 있음
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    /** 사진 + 영상 합쳐서 */
    public static final int MAX_MEDIA = 3;
    public static final int MAX_VIDEOS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Store store;

    /** 이 리뷰를 쓰게 해 준 QR 방문 인증 (QuestVisit.id). 로컬 샘플 리뷰만 null */
    private Long visitId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 비공개 리뷰: 사장님과 작성자 본인에게만 보임 */
    @Column(nullable = false)
    private boolean privateReview = false;

    /** 사장님이 숨긴 리뷰: 가게 리뷰 목록에서 빠짐 (사장님·작성자 본인에게만 보임) */
    @Column(nullable = false)
    private boolean hidden = false;

    @ElementCollection
    @CollectionTable(name = "review_photo", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "url", nullable = false)
    private List<String> photoUrls = new ArrayList<>();

    /** 영상 1개 (선택) */
    private String videoUrl;

    @Builder
    private Review(Member member, Store store, Long visitId, int rating, String content, boolean privateReview,
                   List<String> photoUrls, String videoUrl) {
        this.member = member;
        this.store = store;
        this.visitId = visitId;
        this.rating = rating;
        this.content = content;
        this.privateReview = privateReview;
        if (photoUrls != null) this.photoUrls.addAll(photoUrls);
        this.videoUrl = videoUrl;
    }

    public boolean isWrittenBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /** 다른 손님에게 보이는 리뷰인지 (숨김·비공개가 아님) */
    public boolean isPublic() {
        return !hidden && !privateReview;
    }

    public void changeHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void update(int rating, String content, boolean privateReview, List<String> photoUrls, String videoUrl) {
        this.rating = rating;
        this.content = content;
        this.privateReview = privateReview;
        this.photoUrls.clear();
        this.photoUrls.addAll(photoUrls);
        this.videoUrl = videoUrl;
    }
}
