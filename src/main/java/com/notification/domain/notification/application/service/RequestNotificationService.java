package com.notification.domain.notification.application.service;

import com.notification.domain.notification.application.dto.NotificationCreatedEvent;
import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationOutPort;
import com.notification.domain.notification.domain.NotificationStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RequestNotificationService {

    private final NotificationOutPort outPort;
    private final ApplicationEventPublisher eventPublisher;

    public RequestNotificationService(NotificationOutPort outPort, ApplicationEventPublisher eventPublisher) {
        this.outPort = outPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public NotificationData requestNotification(NotificationData data) {
        NotificationData saved = outPort.save(
                NotificationData.builder()
                        .eventId(data.getEventId())
                        .receiverId(data.getReceiverId())
                        .channel(data.getChannel())
                        .notificationType(data.getNotificationType())
                        .referenceId(data.getReferenceId())
                        .status(NotificationStatus.PENDING)
                        .retryCount(0)
                        .isRead(false)
                        .build()
        );

        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationData> findById(Long id) {
        return outPort.findById(id);
    }

    @Transactional(readOnly = true)
    public List<NotificationData> findByReceiverId(Long receiverId, Boolean isRead) {
        return outPort.findByReceiverId(receiverId, isRead);
    }

    @Transactional
    public void markAsRead(Long id) {
        outPort.markAsRead(id);
    }

    @Transactional
    public NotificationData retryDeadNotification(Long id) {
        NotificationData data = outPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));

        if (data.getStatus() != NotificationStatus.DEAD) {
            throw new IllegalStateException("DEAD 상태인 알림만 수동 재시도할 수 있습니다. 현재 상태: " + data.getStatus());
        }

        return outPort.updateStatus(id, NotificationStatus.FAILED, null);
    }
}
