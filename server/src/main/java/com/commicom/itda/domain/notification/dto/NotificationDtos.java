package com.commicom.itda.domain.notification.dto;

import com.commicom.itda.domain.notification.entity.Notification;
import com.commicom.itda.domain.notification.entity.NotificationType;
import com.commicom.itda.global.util.KstTime;

import java.time.OffsetDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long notificationId,
            NotificationType type,
            String title,
            String message,
            Long storeId,
            Long reviewId,
            boolean read,
            OffsetDateTime createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getStoreId(), n.getReviewId(), n.isRead(), KstTime.fromServerTime(n.getCreatedAt()));
        }
    }

    /** 최근 알림 (최대 50개) + 안 읽은 수 */
    public record NotificationListResponse(long unreadCount, List<NotificationResponse> notifications) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }
}
