package com.notification.domain.notification.application.service;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationOutPort;
import com.notification.domain.notification.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetryNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RetryNotificationService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final NotificationOutPort outPort;
    private final NotificationSender sender;

    public RetryNotificationService(NotificationOutPort outPort, NotificationSender sender) {
        this.outPort = outPort;
        this.sender = sender;
    }

    @Transactional
    public void retryFailedNotifications() {
        List<NotificationData> targets = outPort.findRetryTargets(LocalDateTime.now());
        if (targets.isEmpty()) return;

        log.info("재시도 대상 {}건 발견", targets.size());

        for (NotificationData data : targets) {
            processRetry(data);
        }
    }

    @Transactional
    public void recoverStuckProcessing() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<NotificationData> stuck = outPort.findStuckProcessing(threshold);
        if (stuck.isEmpty()) return;

        log.info("PROCESSING 상태 방치 {}건 복구", stuck.size());

        for (NotificationData data : stuck) {
            outPort.updateStatus(data.getId(), NotificationStatus.FAILED, "PROCESSING 상태 5분 초과 — 자동 복구");
            outPort.updateForRetry(data.getId(), data.getRetryCount(), LocalDateTime.now());
            log.info("PROCESSING → FAILED 복구: id={}", data.getId());
        }
    }

    private void processRetry(NotificationData data) {
        Long id = data.getId();
        int newRetryCount = data.getRetryCount() + 1;

        if (newRetryCount > MAX_RETRY_COUNT) {
            outPort.updateStatus(id, NotificationStatus.DEAD, "재시도 횟수 초과 (max=" + MAX_RETRY_COUNT + ")");
            log.warn("DEAD 상태 전환: id={}, retryCount={}", id, newRetryCount);
            return;
        }

        outPort.updateStatus(id, NotificationStatus.PROCESSING, null);

        try {
            sender.send(data);
            outPort.updateStatus(id, NotificationStatus.SUCCESS, null);
            log.info("재시도 발송 성공: id={}, retryCount={}", id, newRetryCount);
        } catch (Exception e) {
            outPort.saveFailureLog(id, newRetryCount, e.getMessage());
            LocalDateTime nextRetryAt = calculateNextRetry();
            outPort.updateForRetry(id, newRetryCount, nextRetryAt);
            outPort.updateStatus(id, NotificationStatus.FAILED, e.getMessage());
            log.warn("재시도 발송 실패: id={}, retryCount={}, nextRetryAt={}", id, newRetryCount, nextRetryAt);
        }
    }

    private LocalDateTime calculateNextRetry() {
        return LocalDateTime.now().plusMinutes(5);
    }
}
