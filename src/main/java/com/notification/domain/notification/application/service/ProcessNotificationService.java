package com.notification.domain.notification.application.service;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationOutPort;
import com.notification.domain.notification.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ProcessNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ProcessNotificationService.class);

    private final NotificationOutPort outPort;
    private final NotificationSender sender;

    public ProcessNotificationService(NotificationOutPort outPort, NotificationSender sender) {
        this.outPort = outPort;
        this.sender = sender;
    }

    @Transactional
    public void processNotification(Long notificationId) {
        NotificationData data = outPort.findById(notificationId).orElse(null);
        if (data == null) {
            log.warn("알림을 찾을 수 없습니다: id={}", notificationId);
            return;
        }

        outPort.updateStatus(notificationId, NotificationStatus.PROCESSING, null);

        try {
            sender.send(data);
            outPort.updateStatus(notificationId, NotificationStatus.SUCCESS, null);
            log.info("알림 발송 성공: id={}, channel={}", notificationId, data.getChannel());
        } catch (Exception e) {
            outPort.saveFailureLog(notificationId, 0, e.getMessage());
            outPort.updateStatus(notificationId, NotificationStatus.FAILED, e.getMessage());
            outPort.updateForRetry(notificationId, 0, LocalDateTime.now().plusMinutes(5));
            log.info("즉시 재시도 모두 실패, 스케줄러 재시도 대기: id={}", notificationId);
        }
    }
}
