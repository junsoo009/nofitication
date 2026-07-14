package com.notification.domain.notification.application.service;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationSendPort;
import com.notification.domain.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);
    private static final int MAX_IMMEDIATE_RETRY = 2;

    private final NotificationSendPort emailSender;
    private final NotificationSendPort inAppSender;

    public NotificationSender(
            @Qualifier("emailSender") NotificationSendPort emailSender,
            @Qualifier("inAppSender") NotificationSendPort inAppSender) {
        this.emailSender = emailSender;
        this.inAppSender = inAppSender;
    }

    public void send(NotificationData data) {
        NotificationSendPort sender = resolveSender(data.getChannel());

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_IMMEDIATE_RETRY; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(1000);
                    log.info("즉시 재시도 {}/{}: id={}", attempt, MAX_IMMEDIATE_RETRY, data.getId());
                }
                sender.send(data);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("발송 중 인터럽트 발생: id=" + data.getId(), e);
            } catch (Exception e) {
                lastException = e;
                log.warn("알림 발송 실패 (시도 {}): id={}, error={}", attempt + 1, data.getId(), e.getMessage());
            }
        }

        throw new RuntimeException("즉시 재시도 모두 실패: id=" + data.getId(), lastException);
    }

    private NotificationSendPort resolveSender(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailSender;
            case IN_APP -> inAppSender;
        };
    }
}
