# 과제 C — 알림 발송 시스템

## 기술 스택

- Spring Boot 3.4.3 / Java 17
- H2 인메모리 DB / JPA
- Gradle (Groovy DSL)

---

## 1. 아키텍처

Hexagonal Architecture 기반으로 설계했다. 도메인 로직은 프레임워크에 의존하지 않고, 프레임워크 관심사(스케줄러, 이벤트 리스너)와 외부 인프라(발송 구현체)는 도메인 바깥으로 분리했다.

```
com/notification/
├── NotificationApplication.java                  @EnableAsync, @EnableScheduling
│
├── global/                                        프레임워크 의존 트리거 (도메인 밖)
│   ├── event/
│   │   ├── EnrollmentEventListener.java           수강신청 → 알림 생성 (크로스도메인)
│   │   └── NotificationCreatedEventListener.java  알림 생성 → 발송 처리
│   └── scheduler/
│       ├── RetryNotificationScheduler.java        실패 재시도 + PROCESSING 복구
│       └── ReserveNotificationScheduler.java      예약 발송
│
├── infrastructure/                                외부 시스템 발송 구현체 (채널별 독립 패키지)
│   ├── email/EmailSender.java
│   ├── inapp/InAppSender.java
│   ├── slack/SlackSender.java                     TODO
│   └── sms/SmsSender.java                         TODO
│
├── domain/enrollment/                             수강신청 도메인 (이벤트 발행 측)
│   ├── adapter/in/
│   │   ├── EnrollmentController.java
│   │   └── EnrollmentRequest.java
│   └── application/
│       ├── dto/EnrollmentCompletedEvent.java
│       └── service/EnrollmentService.java
│
└── domain/notification/                           알림 도메인
    ├── adapter/
    │   ├── in/                                    Controller, Request/Response DTO
    │   └── out/                                   JPA Entity, Repository, PersistenceAdapter
    ├── application/
    │   ├── dto/                                   NotificationData, ReserveData, Event
    │   ├── port/out/                              NotificationOutPort, NotificationSendPort, NotificationReserveOutPort
    │   └── service/                               순수 비즈니스 로직만
    └── domain/                                    Enum (Status, Channel, Type)
```

### 계층 분리 원칙

| 계층 | 역할 | 프레임워크 의존 |
|------|------|----------------|
| `domain/.../application/service` | 순수 비즈니스 로직 | X (Spring 트랜잭션만) |
| `domain/.../adapter/in` | REST 컨트롤러 | O |
| `domain/.../adapter/out` | JPA 영속성 | O |
| `global/event` | 이벤트 리스너 (`@TransactionalEventListener`) | O |
| `global/scheduler` | 스케줄러 (`@Scheduled`) | O |
| `infrastructure/*` | 외부 시스템 발송 구현체 | O |

### 통신 규약

- Controller ↔ Service: Request/Response DTO
- Service ↔ OutPort ↔ Adapter: NotificationData / ReserveData DTO
- JPA Entity는 adapter/out 내부에서만 사용 (package-private), 밖으로 유출하지 않는다
- 스케줄러/이벤트 리스너는 서비스의 public 메서드만 호출한다

---2

## 2. 비동기 처리 구조

### 전체 흐름

```
POST /api/enrollments (수강신청)
  │
  ├─ EnrollmentService.enroll()          @Transactional
  │   └─ EnrollmentCompletedEvent 발행
  │   └─ 응답 즉시 반환: "수강신청이 완료되었습니다."
  │
  ├─ [트랜잭션 커밋 후, 별도 스레드 task-1]
  │   EnrollmentEventListener.handleEnrollmentEmailNotification()
  │   └─ RequestNotificationService.requestNotification()
  │       └─ PENDING 저장 → NotificationCreatedEvent 발행
  │           └─ [별도 스레드 task-3]
  │              NotificationCreatedEventListener → ProcessNotificationService.processNotification()
  │
  └─ [트랜잭션 커밋 후, 별도 스레드 task-2]
      EnrollmentEventListener.handleEnrollmentInAppNotification()
      └─ RequestNotificationService.requestNotification()
          └─ PENDING 저장 → NotificationCreatedEvent 발행
              └─ [별도 스레드 task-4]
                 NotificationCreatedEventListener → ProcessNotificationService.processNotification()
```

### 비동기 분리가 적용된 지점

| 지점 | 방식 | 이유 |
|------|------|------|
| 수강신청 → 알림 생성 | `@Async @TransactionalEventListener` (global/event) | 비즈니스 트랜잭션과 알림 처리를 구조적으로 분리. 알림 실패가 수강신청에 영향을 주지 않는다. |
| 알림 생성 → 발송 처리 | `@Async @TransactionalEventListener` (global/event) | 알림 저장(PENDING)과 실제 발송을 분리. 저장은 동기적으로 확정하고, 발송은 별도 스레드에서 처리한다. |

### 채널별 독립 실행

같은 이벤트에 대해 EMAIL과 IN_APP 알림을 **별도 `@Async` 메서드**로 분리했다.

```java
// global/event/EnrollmentEventListener.java
@Async @TransactionalEventListener
public void handleEnrollmentEmailNotification(EnrollmentCompletedEvent event) { ... }

@Async @TransactionalEventListener
public void handleEnrollmentInAppNotification(EnrollmentCompletedEvent event) { ... }
```

하나의 메서드에서 두 채널을 순차 호출하면, 첫 번째 채널에서 예외가 발생할 때 두 번째 채널이 실행되지 않는다. 메서드를 분리하면 각각 독립된 스레드에서 실행되므로 한쪽 실패가 다른 쪽에 영향을 주지 않는다.

### 프로덕션 전환 시

현재는 Spring `ApplicationEventPublisher` + `@Async`로 구현했다. 프로덕션 환경에서는 이벤트 발행 지점을 SQS/Kafka로 교체하면 된다.

- **모듈 간 통신** (수강신청 → 알림): SQS + DLQ
- **모듈 내 통신** (알림 생성 → 발송): 현재 구조 유지 가능 (EventListener)

EventListener 구조에서 이벤트 유실이 발생할 수 있는 구간:
- 트랜잭션 커밋 후 ~ 이벤트 리스너 실행 사이에 서버가 죽으면 이벤트가 유실된다
- 이 경우 알림은 DB에 PENDING 상태로 남아있으므로, 스케줄러가 복구한다
- 완전한 보장이 필요하면 Transactional Outbox 패턴 또는 SQS로 전환한다

---

## 3. 상태 전이

```
PENDING → PROCESSING → SUCCESS
                     → FAILED → (스케줄러 재시도) → PROCESSING → SUCCESS
                                                              → FAILED (반복)
                              → DEAD (retry_count > 3, 수동 처리 대상)
```

| 상태 | 의미 |
|------|------|
| PENDING | 요청 접수됨, 아직 처리 안 됨 |
| PROCESSING | 발송 처리 중 |
| SUCCESS | 발송 성공 |
| FAILED | 발송 실패, 재시도 대상 |
| DEAD | 재시도 횟수 초과, 수동 처리 필요 |

---

## 4. 재시도 정책

### 2단계 재시도 전략

일시적 오류(네트워크 타임아웃 등)와 장기 장애(외부 서버 다운 등)를 구분하여 대응한다.

#### 1단계: 즉시 재시도 (NotificationSender)

발송 실패 시 1초 간격으로 최대 2회 즉시 재시도한다.

```
발송 시도 → 실패 → 1초 후 재시도 → 또 실패 → 1초 후 재시도 → 또 실패
→ FAILED 저장 (retry_count=0, next_retry_at = now + 5분)
→ 이 요청은 여기서 끝, 스케줄러에 위임
```

일시적 네트워크 오류는 대부분 여기서 해결된다.

#### 2단계: 스케줄러 지연 재시도 (RetryNotificationService)

5분 주기(`@Scheduled(fixedDelay = 300000)`)로 FAILED 건을 조회하여 재시도한다. 5분 고정 간격을 적용한다.

| retry_count | next_retry_at |
|-------------|---------------|
| 1 | now + 5분 |
| 2 | now + 5분 |
| 3 | now + 5분 |
| > 3 | DEAD 상태 전환 |

DEAD 상태에 도달한 건은 수동 재시도 API(`POST /api/notifications/{id}/retry`)로 처리한다.

#### 실패 이력 기록 (notification_log)

스케줄러 재시도가 실패할 때마다 `notification_log` 테이블에 실패 사유를 기록한다. 어떤 시점에 어떤 이유로 몇 번째 시도가 실패했는지 추적할 수 있다.

---

## 5. 중복 발송 방지

### DB Unique Key 기반

```sql
CONSTRAINT uk_event_receiver_channel UNIQUE (event_id, receiver_id, channel)
```

- SELECT 없이 바로 INSERT 시도 → 성공이면 처리, UK 위반 예외면 중복으로 판단
- SELECT → INSERT 방식은 동시 요청 시 레이스 컨디션이 발생한다. 두 요청이 동시에 "없음"을 확인하고 둘 다 INSERT를 시도하는 경우다. DB 제약조건은 원자적으로 막아준다.

### eventId 생성 규칙

비즈니스 키 조합으로 eventId를 생성한다. 시퀀스 ID가 아닌 비즈니스 키를 사용해야 같은 이벤트를 식별할 수 있다.

```java
String eventId = "ENROLLMENT_" + event.getUserId() + "_" + event.getLectureId();
// 예: "ENROLLMENT_1_100" (유저1 + 강의100)
```

같은 유저가 같은 강의를 두 번 수강신청해도, eventId가 동일하므로 UK 위반으로 중복 알림이 차단된다.

---

## 6. 운영 시나리오 대응

### 서버 재시작 복구

스케줄러가 PROCESSING 상태로 5분 이상 방치된 건을 FAILED로 전환한다.

```java
// global/scheduler/RetryNotificationScheduler → RetryNotificationService.recoverStuckProcessing()
@Scheduled(fixedDelay = 300000)
public void recoverStuck() {
    // status = PROCESSING AND updated_at < now - 5분 → FAILED로 전환
}
```

서버가 발송 중에 죽으면 PROCESSING 상태로 남게 되는데, 재시작 후 스케줄러가 이를 감지하여 재시도 대상으로 복구한다.

### 다중 인스턴스 중복 방지

스케줄러가 FAILED 건을 재처리할 때 비관적 락(`SELECT FOR UPDATE`)으로 조회한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT n FROM NotificationJpaEntity n WHERE n.status = :status AND n.nextRetryAt <= :now")
List<NotificationJpaEntity> findRetryTargets(...);
```

서버A가 잡으면 서버B는 대기한다. 전체 로직(조회 + 발송 + 상태 변경)이 하나의 `@Transactional` 안에서 실행되므로, 서버B가 락을 획득했을 때는 이미 상태가 변경되어 있어 중복 처리가 방지된다. 알림 발송은 비동기 백그라운드 처리이므로 DB 락 대기 비용이 문제가 되지 않는다.

---

## 7. API

```
POST   /api/enrollments                                  → 수강신청 (알림 자동 발행)
POST   /api/notifications                                → 알림 발송 요청 (직접 호출)
GET    /api/notifications/{id}                            → 특정 알림 상태 조회
GET    /api/users/{userId}/notifications?isRead=false     → 사용자 알림 목록
PATCH  /api/notifications/{id}/read                       → 읽음 처리
POST   /api/notifications/{id}/retry                      → DEAD 알림 수동 재시도
POST   /api/notifications/reserve                         → 예약 발송 등록
GET    /api/notifications/reserve/{id}                    → 예약 조회
DELETE /api/notifications/reserve/{id}                    → 예약 취소
```

### POST /api/enrollments

수강신청 완료 시 EMAIL + IN_APP 알림이 자동으로 생성된다.

요청:
```json
{
    "userId": 1,
    "lectureId": 100,
    "lectureName": "Spring Boot 마스터"
}
```

응답:
```json
{
    "enrollmentId": 1,
    "message": "수강신청이 완료되었습니다."
}
```

### POST /api/notifications

요청:
```json
{
    "eventId": "PAYMENT_1_100",
    "receiverId": 1,
    "notificationType": "PAYMENT",
    "referenceId": 100,
    "channel": "EMAIL"
}
```

정상 응답:
```json
{
    "id": 1,
    "eventId": "PAYMENT_1_100",
    "status": "PENDING",
    "message": "알림 요청이 접수되었습니다."
}
```

중복 응답:
```json
{
    "eventId": "PAYMENT_1_100",
    "message": "이미 처리된 알림 요청입니다."
}
```

### POST /api/notifications/reserve

특정 시각에 알림 발송을 예약한다. 스케줄러가 1분 주기로 예약 시각이 도래한 건을 조회하여 기존 알림 발송 플로우로 전달한다.

요청:
```json
{
    "eventId": "PROMOTION_1_200",
    "receiverId": 1,
    "notificationType": "REMINDER",
    "referenceId": 200,
    "channel": "EMAIL",
    "reservedAt": "2025-01-15T09:00:00"
}
```

응답:
```json
{
    "id": 1,
    "eventId": "PROMOTION_1_200",
    "status": "RESERVED",
    "reservedAt": "2025-01-15T09:00:00",
    "message": "알림 예약이 등록되었습니다."
}
```

### DELETE /api/notifications/reserve/{id}

RESERVED 상태인 예약만 취소할 수 있다. 이미 발송된(SENT) 예약은 취소 불가.

---

## 8. 테이블 설계

### notification

```sql
CREATE TABLE notification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL,
    receiver_id     BIGINT NOT NULL,
    channel         VARCHAR(20) NOT NULL,          -- EMAIL / IN_APP
    notification_type VARCHAR(30) NOT NULL,         -- ENROLLMENT / PAYMENT / REMINDER / CANCEL
    reference_id    BIGINT,
    status          VARCHAR(20) NOT NULL,           -- PENDING / PROCESSING / SUCCESS / FAILED / DEAD
    retry_count     INT NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP,
    failure_reason  VARCHAR(500),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,

    CONSTRAINT uk_event_receiver_channel UNIQUE (event_id, receiver_id, channel)
);
```

### notification_log

재시도 실패 시마다 이력을 기록한다.

```sql
CREATE TABLE notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    attempt_count   INT NOT NULL,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP NOT NULL
);
```

### notification_reserve

예약 발송 정보를 별도 테이블로 관리한다.

```sql
CREATE TABLE notification_reserve (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          VARCHAR(255) NOT NULL,
    receiver_id       BIGINT NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    reference_id      BIGINT,
    reserved_at       TIMESTAMP NOT NULL,
    status            VARCHAR(20) NOT NULL,        -- RESERVED / SENT / CANCELLED
    created_at        TIMESTAMP NOT NULL
);
```

---

## 9. 선택 구현

### 읽음 처리

`PATCH /api/notifications/{id}/read`로 `is_read = true`로 변경한다. 여러 기기에서 동시에 읽음 요청이 오면, 이미 true인 것을 다시 true로 변경하는 것이므로 멱등하게 동작한다.

### 수동 재시도

`POST /api/notifications/{id}/retry`로 DEAD 상태인 알림을 재시도한다. 기존 retry_count를 초기화하지 않고, status만 FAILED로 변경하여 스케줄러가 다시 처리하도록 한다. retry_count를 유지하는 이유는 해당 알림이 총 몇 회 실패했는지 이력을 보존하기 위해서다.

### 예약 발송

`POST /api/notifications/reserve`로 특정 시각에 알림 발송을 예약한다. `notification_reserve` 테이블에 RESERVED 상태로 저장되고, 스케줄러(`ReserveNotificationScheduler`)가 1분 주기로 `reserved_at <= now`인 건을 SELECT FOR UPDATE로 조회하여 기존 `RequestNotificationService.requestNotification()`을 호출한다. 이후 기존 발송 플로우(이벤트 → 비동기 발송 → 재시도)를 그대로 탄다. 발송 처리 후 상태를 SENT로 변경한다. RESERVED 상태인 예약만 `DELETE /api/notifications/reserve/{id}`로 취소할 수 있다.

### 알림 템플릿 관리

타입별 메시지 템플릿을 관리하는 기능. 템플릿에 변수 플레이스홀더(`{{userName}}`, `{{lectureName}}` 등)를 정의하고, 발송 시점에 실제 값으로 치환하는 방식이다. 템플릿 테이블에 채널별·타입별 템플릿을 저장하고, `NotificationSender`에서 발송 전에 템플릿을 조회하여 메시지를 조립한다. 구현 방향이 확정되지 않아 내용 정리만 해 둔다.

---

## 10. 설계 판단 근거

1. **헥사고날 계층 분리**: 스케줄러(`@Scheduled`)와 이벤트 리스너(`@TransactionalEventListener`)는 프레임워크 의존이 있는 인프라 관심사이므로 `global/` 패키지로 분리했다. 발송 구현체(이메일, 인앱, 슬랙, SMS)는 각각 독립된 도메인이므로 `infrastructure/` 아래 채널별 패키지로 분리했다. `application/service`에는 순수 비즈니스 로직만 남긴다.

2. **비동기 분리**: `@Async @TransactionalEventListener`로 비즈니스 트랜잭션과 알림 처리를 구조적으로 분리했다. 예외를 무시(try-catch 후 삼키기)하는 것이 아니라, 애초에 다른 스레드에서 실행되므로 비즈니스 로직에 영향을 줄 수 없는 구조다. 프로덕션에서는 모듈 간 SQS / 모듈 내 EventListener로 분리한다.

3. **채널별 독립 실행**: EMAIL과 IN_APP 알림을 하나의 메서드에서 순차 실행하면, 첫 번째 채널 실패 시 두 번째 채널이 실행되지 않는다. 채널별로 별도 `@Async` 메서드로 분리하여 각각 독립된 스레드에서 실행되도록 했다.

4. **중복 방지 — DB Unique Key**: INSERT 시도 시 UK 위반이면 중복으로 판단한다. INSERT 대상이라 잠글 행이 없으므로 락이 아닌 DB 제약조건으로 처리했다. SELECT → INSERT 방식의 레이스 컨디션 문제를 원천 차단한다.

5. **eventId 생성 — 비즈니스 키 조합**: 시퀀스 ID가 아닌 `userId + lectureId` 등 비즈니스 키를 조합하여 eventId를 만든다. 같은 비즈니스 이벤트가 여러 번 발행되어도 동일한 eventId가 생성되어 중복이 차단된다.

6. **재시도 전략 — 2단계**: 즉시 1~2회(일시적 에러 대응) + 스케줄러 5분 고정 간격 재시도(장기 장애 대응). 서버가 재시작해도 DB에 상태가 남아있으므로 유실이 없다. 재시도마다 `notification_log`에 실패 이력을 기록한다.

7. **스케줄러/서비스 분리**: `@Scheduled`는 트리거 역할만 하고(`global/scheduler`), 비즈니스 로직은 별도 서비스(`RetryNotificationService`, `ReserveNotificationService`)에서 `@Transactional`로 처리한다. 스케줄러에 트랜잭션과 비즈니스 로직이 섞이는 것을 방지한다.

8. **다중 인스턴스 — DB 비관적 락**: 알림은 비동기 백그라운드 처리이므로 응답 속도가 크리티컬하지 않다. DB 락 대기 비용이 문제가 되지 않는 구간이라 별도 인프라(Redis) 없이 해결했다.

