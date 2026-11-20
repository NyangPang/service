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

/** 리뷰 보상 뽑기 기록 (뽑기 1회 = 1행). type: FEED / COUPON */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDraw extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = 10)
    private String rewardType;

    @Column(nullable = false)
    private int feedAmount;

    private Long userCouponId;

    public ReviewDraw(Long memberId, Long ticketId, String rewardType, int feedAmount, Long userCouponId) {
        this.memberId = memberId;
        this.ticketId = ticketId;
        this.rewardType = rewardType;
        this.feedAmount = feedAmount;
        this.userCouponId = userCouponId;
    }
}
