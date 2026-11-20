package com.commicom.itda.domain.notification.service;

import com.commicom.itda.domain.notification.dto.NotificationDtos.NotificationListResponse;
import com.commicom.itda.domain.notification.dto.NotificationDtos.NotificationResponse;
import com.commicom.itda.domain.notification.dto.NotificationDtos.UnreadCountResponse;
import com.commicom.itda.domain.notification.entity.Notification;
import com.commicom.itda.domain.notification.entity.NotificationType;
import com.commicom.itda.domain.notification.repository.NotificationRepository;
import com.commicom.itda.domain.review.entity.Review;
import com.commicom.itda.domain.store.entity.Store;
import com.commicom.itda.global.exception.BusinessException;
import com.commicom.itda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 앱 안 알림. 클라이언트는 안 읽은 수를 주기적으로 물어봄 (폰 푸시는 아직 없음) */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int LIST_LIMIT = 50;
    private static final int PREVIEW_LENGTH = 40;

    private final NotificationRepository notificationRepository;

    /** 리뷰가 등록되면 그 가게 사장님에게 (사장님이 없거나 본인 가게에 쓴 리뷰면 보내지 않음) */
    @Transactional
    public void notifyReviewCreated(Review review) {
        Store store = review.getStore();
        if (store.getOwner() == null || store.isOwnedBy(review.getMember().getId())) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .recipientId(store.getOwner().getId())
                .type(NotificationType.REVIEW_CREATED)
                .title("새 리뷰가 등록됐어요")
                .message(review.getMember().getNickname() + "님 ★" + review.getRating() + " · " + preview(review.getContent()))
                .storeId(store.getId())
                .reviewId(review.getId())
                .build());
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getMyNotifications(Long memberId) {
        List<NotificationResponse> list = notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(memberId, PageRequest.of(0, LIST_LIMIT))
                .stream().map(NotificationResponse::from).toList();
        return new NotificationListResponse(notificationRepository.countByRecipientIdAndIsReadFalse(memberId), list);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long memberId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndIsReadFalse(memberId));
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId);
    }

    /** 회원 탈퇴 시 */
    @Transactional
    public void deleteAllOf(Long memberId) {
        notificationRepository.deleteByRecipientId(memberId);
    }

    private static String preview(String content) {
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
