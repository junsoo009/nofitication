package com.notification.domain.notification.adapter.in;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.dto.ReserveData;
import com.notification.domain.notification.application.service.RequestNotificationService;
import com.notification.domain.notification.application.service.ReserveNotificationService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final RequestNotificationService requestService;
    private final ReserveNotificationService reserveService;

    public NotificationController(RequestNotificationService requestService,
                                  ReserveNotificationService reserveService) {
        this.requestService = requestService;
        this.reserveService = reserveService;
    }

    @PostMapping("/notifications")
    public ResponseEntity<NotificationResponse> createNotification(@RequestBody @Valid NotificationRequest request) {
        try {
            NotificationData data = NotificationData.builder()
                    .eventId(request.getEventId())
                    .receiverId(request.getReceiverId())
                    .channel(request.getChannel())
                    .notificationType(request.getNotificationType())
                    .referenceId(request.getReferenceId())
                    .build();

            NotificationData saved = requestService.requestNotification(data);
            return ResponseEntity.ok(NotificationResponse.from(saved, "알림 요청이 접수되었습니다."));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(NotificationResponse.duplicate(request.getEventId()));
        }
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable Long id) {
        return requestService.findById(id)
                .map(data -> ResponseEntity.ok(NotificationResponse.from(data, null)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean isRead) {
        List<NotificationResponse> responses = requestService.findByReceiverId(userId, isRead)
                .stream()
                .map(data -> NotificationResponse.from(data, null))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        requestService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notifications/{id}/retry")
    public ResponseEntity<NotificationResponse> retryNotification(@PathVariable Long id) {
        try {
            NotificationData data = requestService.retryDeadNotification(id);
            return ResponseEntity.ok(NotificationResponse.from(data, "수동 재시도가 요청되었습니다. 스케줄러가 처리합니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    NotificationResponse.builder().message(e.getMessage()).build()
            );
        }
    }

    @PostMapping("/notifications/reserve")
    public ResponseEntity<ReserveResponse> reserveNotification(@RequestBody @Valid ReserveRequest request) {
        ReserveData data = ReserveData.builder()
                .eventId(request.getEventId())
                .receiverId(request.getReceiverId())
                .channel(request.getChannel())
                .notificationType(request.getNotificationType())
                .referenceId(request.getReferenceId())
                .reservedAt(request.getReservedAt())
                .build();

        ReserveData saved = reserveService.reserve(data);
        return ResponseEntity.ok(ReserveResponse.from(saved, "알림 예약이 등록되었습니다."));
    }

    @GetMapping("/notifications/reserve/{id}")
    public ResponseEntity<ReserveResponse> getReserve(@PathVariable Long id) {
        return reserveService.findById(id)
                .map(data -> ResponseEntity.ok(ReserveResponse.from(data, null)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/notifications/reserve/{id}")
    public ResponseEntity<ReserveResponse> cancelReserve(@PathVariable Long id) {
        try {
            reserveService.cancel(id);
            return reserveService.findById(id)
                    .map(data -> ResponseEntity.ok(ReserveResponse.from(data, "예약이 취소되었습니다.")))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ReserveResponse.builder().message(e.getMessage()).build()
            );
        }
    }
}
