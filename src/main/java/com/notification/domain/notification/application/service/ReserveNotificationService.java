package com.notification.domain.notification.application.service;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.dto.ReserveData;
import com.notification.domain.notification.application.port.out.NotificationReserveOutPort;
import com.notification.domain.notification.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReserveNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReserveNotificationService.class);

    private final NotificationReserveOutPort reserveOutPort;
    private final RequestNotificationService requestNotificationService;

    public ReserveNotificationService(NotificationReserveOutPort reserveOutPort,
                                      RequestNotificationService requestNotificationService) {
        this.reserveOutPort = reserveOutPort;
        this.requestNotificationService = requestNotificationService;
    }

    @Transactional
    public ReserveData reserve(ReserveData data) {
        if (data.getReservedAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("예약 시각은 현재 시각 이후여야 합니다.");
        }
        return reserveOutPort.save(data);
    }

    @Transactional(readOnly = true)
    public Optional<ReserveData> findById(Long id) {
        return reserveOutPort.findById(id);
    }

    @Transactional
    public void cancel(Long id) {
        ReserveData data = reserveOutPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("예약 알림을 찾을 수 없습니다: " + id));

        if (!"RESERVED".equals(data.getStatus())) {
            throw new IllegalStateException("RESERVED 상태인 예약만 취소할 수 있습니다. 현재 상태: " + data.getStatus());
        }

        reserveOutPort.markCancelled(id);
    }

    @Transactional
    public void processReservedNotifications() {
        List<ReserveData> readyList = reserveOutPort.findReadyToSend(LocalDateTime.now());
        if (readyList.isEmpty()) return;

        log.info("예약 발송 대상 {}건 발견", readyList.size());

        for (ReserveData reserve : readyList) {
            NotificationData notificationData = NotificationData.builder()
                    .eventId(reserve.getEventId())
                    .receiverId(reserve.getReceiverId())
                    .channel(reserve.getChannel())
                    .notificationType(reserve.getNotificationType())
                    .referenceId(reserve.getReferenceId())
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .isRead(false)
                    .build();

            try {
                requestNotificationService.requestNotification(notificationData);
                reserveOutPort.markSent(reserve.getId());
                log.info("예약 알림 발송 처리 완료: reserveId={}, eventId={}", reserve.getId(), reserve.getEventId());
            } catch (Exception e) {
                log.error("예약 알림 발송 실패: reserveId={}, error={}", reserve.getId(), e.getMessage());
            }
        }
    }
}
