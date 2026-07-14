package com.notification.global.scheduler;

import com.notification.domain.notification.application.service.RetryNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryNotificationScheduler {

    private final RetryNotificationService retryService;

    public RetryNotificationScheduler(RetryNotificationService retryService) {
        this.retryService = retryService;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryFailed() {
        retryService.retryFailedNotifications();
    }

    @Scheduled(fixedDelay = 300000)
    public void recoverStuck() {
        retryService.recoverStuckProcessing();
    }
}
