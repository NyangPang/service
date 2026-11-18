package com.commicom.itda.domain.notification.controller;

import com.commicom.itda.domain.notification.dto.NotificationDtos.NotificationListResponse;
import com.commicom.itda.domain.notification.dto.NotificationDtos.UnreadCountResponse;
import com.commicom.itda.domain.notification.service.NotificationService;
import com.commicom.itda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "앱 안 알림 (사장님: 새 리뷰)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록", description = "최신순 최대 50개 + 안 읽은 수")
    @GetMapping
    public ApiResponse<NotificationListResponse> getMyNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(notificationService.getMyNotifications(memberId));
    }

    @Operation(summary = "안 읽은 알림 수", description = "상단 종 아이콘 배지용. 클라이언트가 주기적으로 조회")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(notificationService.getUnreadCount(memberId));
    }

    @Operation(summary = "알림 읽음 처리", description = "내 알림이 아니면 NOTIFICATION404")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId) {
        notificationService.markRead(memberId, notificationId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "알림 모두 읽음")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        notificationService.markAllRead(memberId);
        return ApiResponse.onSuccess(null);
    }
}
