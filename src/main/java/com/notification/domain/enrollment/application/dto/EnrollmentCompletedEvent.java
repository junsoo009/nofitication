package com.notification.domain.enrollment.application.dto;

import lombok.Getter;

@Getter
public class EnrollmentCompletedEvent {

    private final Long enrollmentId;
    private final Long userId;
    private final Long lectureId;
    private final String lectureName;

    public EnrollmentCompletedEvent(Long enrollmentId, Long userId, Long lectureId, String lectureName) {
        this.enrollmentId = enrollmentId;
        this.userId = userId;
        this.lectureId = lectureId;
        this.lectureName = lectureName;
    }
}
