package com.notification.domain.notification.application.dto;

import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationStatus;
import com.notification.domain.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationData {

    private Long id;
    private String eventId;
    private Long receiverId;
    private NotificationChannel channel;
    private NotificationType notificationType;
    private Long referenceId;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private String failureReason;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
