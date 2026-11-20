package com.commicom.itda.domain.notification.entity;

import com.commicom.itda.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 앱 안 알림 (사장님: 우리 가게에 새 리뷰). 받는 사람·대상은 ID 로만 들고 있음 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "recipientId, isRead"))
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 회원 */
    @Column(nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String message;

    private Long storeId;

    private Long reviewId;

    @Column(nullable = false)
    private boolean isRead = false;

    @Builder
    private Notification(Long recipientId, NotificationType type, String title, String message, Long storeId, Long reviewId) {
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.storeId = storeId;
        this.reviewId = reviewId;
    }

    public void markRead() {
        this.isRead = true;
    }
}
