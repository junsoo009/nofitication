package com.notification.domain.notification.adapter.in;

import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
class NotificationRequest {

    @NotBlank
    private String eventId;

    @NotNull
    private Long receiverId;

    @NotNull
    private NotificationType notificationType;

    private Long referenceId;

    @NotNull
    private NotificationChannel channel;
}
