package com.notification.domain.notification.application.dto;

import lombok.Getter;

@Getter
public class NotificationCreatedEvent {

    private final Long notificationId;

    public NotificationCreatedEvent(Long notificationId) {
        this.notificationId = notificationId;
    }
}
