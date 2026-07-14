package com.notification.global.event;

import com.notification.domain.enrollment.application.dto.EnrollmentCompletedEvent;
import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.service.RequestNotificationService;
import com.notification.domain.notification.domain.NotificationChannel;
import com.notification.domain.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EnrollmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentEventListener.class);
    private final RequestNotificationService requestService;

    public EnrollmentEventListener(RequestNotificationService requestService) {
        this.requestService = requestService;
    }

    @Async
    @TransactionalEventListener
    public void handleEnrollmentEmailNotification(EnrollmentCompletedEvent event) {
        String eventId = "ENROLLMENT_" + event.getUserId() + "_" + event.getLectureId();
        log.info("수강신청 이메일 알림 처리: eventId={}", eventId);
        createNotification(eventId, event.getUserId(), NotificationType.ENROLLMENT,
                event.getLectureId(), NotificationChannel.EMAIL);
    }

    @Async
    @TransactionalEventListener
    public void handleEnrollmentInAppNotification(EnrollmentCompletedEvent event) {
        String eventId = "ENROLLMENT_" + event.getUserId() + "_" + event.getLectureId();
        log.info("수강신청 인앱 알림 처리: eventId={}", eventId);
        createNotification(eventId, event.getUserId(), NotificationType.ENROLLMENT,
                event.getLectureId(), NotificationChannel.IN_APP);
    }

    private void createNotification(String eventId, Long receiverId, NotificationType type,
                                    Long referenceId, NotificationChannel channel) {
        try {
            NotificationData data = NotificationData.builder()
                    .eventId(eventId)
                    .receiverId(receiverId)
                    .notificationType(type)
                    .referenceId(referenceId)
                    .channel(channel)
                    .build();

            requestService.requestNotification(data);
            log.info("알림 생성 완료: eventId={}, channel={}", eventId, channel);
        } catch (DataIntegrityViolationException e) {
            log.info("중복 알림 요청 무시: eventId={}, channel={}", eventId, channel);
        }
    }
}
