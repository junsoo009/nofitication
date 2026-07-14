package com.notification.infrastructure.inapp;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationSendPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// TODO: 실제 인앱 알림 구현 (WebSocket, FCM 등)
@Component("inAppSender")
public class InAppSender implements NotificationSendPort {

    private static final Logger log = LoggerFactory.getLogger(InAppSender.class);

    @Override
    public void send(NotificationData data) {
        // Mock: 랜덤 20% 실패
        if (ThreadLocalRandom.current().nextInt(10) < 2) {
            throw new RuntimeException("인앱 알림 저장 실패 (Mock 랜덤 에러): receiver=" + data.getReceiverId());
        }
        log.info("인앱 알림 저장 완료: receiver={}, eventId={}, isRead=false", data.getReceiverId(), data.getEventId());
    }
}
