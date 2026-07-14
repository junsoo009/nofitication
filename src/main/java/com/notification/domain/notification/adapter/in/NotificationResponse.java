package com.notification.domain.notification.adapter.in;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationStatus;
import com.notification.domain.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
class NotificationResponse {

    private Long id;
    private String eventId;
    private Long receiverId;
    private NotificationChannel channel;
    private NotificationType notificationType;
    private Long referenceId;
    private NotificationStatus status;
    private int retryCount;
    private String failureReason;
    private boolean isRead;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    static NotificationResponse from(NotificationData data, String message) {
        return NotificationResponse.builder()
                .id(data.getId())
                .eventId(data.getEventId())
                .receiverId(data.getReceiverId())
                .channel(data.getChannel())
                .notificationType(data.getNotificationType())
                .referenceId(data.getReferenceId())
                .status(data.getStatus())
                .retryCount(data.getRetryCount())
                .failureReason(data.getFailureReason())
                .isRead(data.isRead())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .message(message)
                .build();
    }

    static NotificationResponse duplicate(String eventId) {
        return NotificationResponse.builder()
                .eventId(eventId)
                .message("이미 처리된 알림 요청입니다.")
                .build();
    }
}
