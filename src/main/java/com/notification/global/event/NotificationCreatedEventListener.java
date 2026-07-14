package com.notification.global.event;

import com.notification.domain.notification.application.dto.NotificationCreatedEvent;
import com.notification.domain.notification.application.service.ProcessNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationCreatedEventListener {

    private final ProcessNotificationService processService;

    public NotificationCreatedEventListener(ProcessNotificationService processService) {
        this.processService = processService;
    }

    @Async
    @TransactionalEventListener
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        processService.processNotification(event.getNotificationId());
    }
}
