package com.notification.infrastructure.email;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationSendPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// TODO: 실제 이메일 발송 구현 (SMTP, AWS SES 등)
@Component("emailSender")
public class EmailSender implements NotificationSendPort {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    @Override
    public void send(NotificationData data) {
        // Mock: 랜덤 30% 실패
        if (ThreadLocalRandom.current().nextInt(10) < 3) {
            throw new RuntimeException("이메일 발송 실패 (Mock 랜덤 에러): receiver=" + data.getReceiverId());
        }
        log.info("이메일 발송 완료: receiver={}, eventId={}", data.getReceiverId(), data.getEventId());
    }
}
