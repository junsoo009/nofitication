package com.notification.domain.enrollment.application.service;

import com.notification.domain.enrollment.application.dto.EnrollmentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicLong idGenerator = new AtomicLong(1);

    public EnrollmentService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Long enroll(Long userId, Long lectureId, String lectureName) {
        Long enrollmentId = idGenerator.getAndIncrement();

        log.info("수강신청 완료: enrollmentId={}, userId={}, lectureId={}, lectureName={}",
                enrollmentId, userId, lectureId, lectureName);

        eventPublisher.publishEvent(
                new EnrollmentCompletedEvent(enrollmentId, userId, lectureId, lectureName)
        );

        return enrollmentId;
    }
}
