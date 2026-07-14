package com.notification.global.scheduler;

import com.notification.domain.notification.application.service.ReserveNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReserveNotificationScheduler {

    private final ReserveNotificationService reserveService;

    public ReserveNotificationScheduler(ReserveNotificationService reserveService) {
        this.reserveService = reserveService;
    }

    @Scheduled(fixedDelay = 60000)
    public void processReserved() {
        reserveService.processReservedNotifications();
    }
}
