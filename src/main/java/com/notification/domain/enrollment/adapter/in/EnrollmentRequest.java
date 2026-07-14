package com.notification.domain.enrollment.adapter.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
class EnrollmentRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long lectureId;

    @NotBlank
    private String lectureName;
}
