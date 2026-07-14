package com.notification.domain.notification.adapter.in;

import com.notification.domain.notification.application.dto.ReserveData;
import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
class ReserveResponse {

    private Long id;
    private String eventId;
    private Long receiverId;
    private NotificationChannel channel;
    private NotificationType notificationType;
    private Long referenceId;
    private LocalDateTime reservedAt;
    private String status;
    private String message;
    private LocalDateTime createdAt;

    static ReserveResponse from(ReserveData data, String message) {
        return ReserveResponse.builder()
                .id(data.getId())
                .eventId(data.getEventId())
                .receiverId(data.getReceiverId())
                .channel(data.getChannel())
                .notificationType(data.getNotificationType())
                .referenceId(data.getReferenceId())
                .reservedAt(data.getReservedAt())
                .status(data.getStatus())
                .createdAt(data.getCreatedAt())
                .message(message)
                .build();
    }
}
