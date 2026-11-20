package com.commicom.itda.domain.review.entity;

import com.commicom.itda.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 보상 뽑기권. QR 방문 인증 1건(visitId)당 평생 1장만 생김 → 리뷰를 지우고 다시 써도 새로 안 생김.
 * 리뷰를 쓰면 3회. 리뷰를 지우면 남은 횟수는 0 (이미 받은 보상은 그대로)
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDrawTicket extends BaseTimeEntity {

    public static final int DRAWS_PER_REVIEW = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    /** 보상을 받은 QR 방문 인증 (QuestVisit.id) */
    @Column(nullable = false, unique = true)
    private Long visitId;

    /** 보상을 준 리뷰. 리뷰를 지우면 null */
    @Column(unique = true)
    private Long reviewId;

    @Column(nullable = false)
    private int remaining;

    public ReviewDrawTicket(Long memberId, Long visitId, Long reviewId) {
        this.memberId = memberId;
        this.visitId = visitId;
        this.reviewId = reviewId;
        this.remaining = DRAWS_PER_REVIEW;
    }

    public void use() {
        this.remaining -= 1;
    }

    /** 리뷰 삭제: 남은 기회 박탈. 박탈한 횟수를 돌려줌 */
    public int revoke() {
        int revoked = remaining;
        this.remaining = 0;
        this.reviewId = null;
        return revoked;
    }
}
