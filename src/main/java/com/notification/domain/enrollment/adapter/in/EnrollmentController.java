package com.notification.domain.enrollment.adapter.in;

import com.notification.domain.enrollment.application.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> enroll(@RequestBody @Valid EnrollmentRequest request) {
        Long enrollmentId = enrollmentService.enroll(
                request.getUserId(),
                request.getLectureId(),
                request.getLectureName()
        );

        return ResponseEntity.ok(Map.of(
                "enrollmentId", enrollmentId,
                "message", "수강신청이 완료되었습니다."
        ));
    }
}
