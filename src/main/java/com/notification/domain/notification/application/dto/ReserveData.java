package com.notification.domain.notification.application.dto;

import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReserveData {

    private Long id;
    private String eventId;
    private Long receiverId;
    private NotificationChannel channel;
    private NotificationType notificationType;
    private Long referenceId;
    private LocalDateTime reservedAt;
    private String status;
    private LocalDateTime createdAt;
}
