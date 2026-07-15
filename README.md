## 프로젝트 개요

수강신청 이벤트 기반의 알림 발송 시스템. 수강신청이 완료되면 이메일/인앱 알림을 비동기로 발송하고, 실패 시 자동 재시도하며, 특정 시각에 예약 발송할 수 있다.

---

## 기술 스택

- Spring Boot 3.4.3 / Java 17
- H2 인메모리 DB / JPA
- Gradle (Groovy DSL)

---

## 실행 방법

```bash
# 로컬 실행
./gradlew bootRun

# 빌드 후 실행
./gradlew build
java -jar build/libs/notification-0.0.1-SNAPSHOT.jar
```

H2 인메모리 DB를 사용하므로 별도 DB 설치가 필요 없다. 실행 후 `http://localhost:8080`으로 접근한다.

---

## API 목록 및 예시

```
POST   /api/enrollments                         → 수강신청 (알림 자동 발행)
POST   /api/notifications                       → 알림 발송 요청 (직접 호출)
GET    /api/notifications/{id}                   → 특정 알림 상태 조회
GET    /api/users/{userId}/notifications?isRead= → 사용자 알림 목록
PATCH  /api/notifications/{id}/read              → 읽음 처리
POST   /api/notifications/{id}/retry             → DEAD 알림 수동 재시도
POST   /api/notifications/reserve                → 예약 발송 등록
GET    /api/notifications/reserve/{id}           → 예약 조회
DELETE /api/notifications/reserve/{id}           → 예약 취소
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

## 데이터 모델 설명

### notification

알림 본체. 상태 전이와 재시도 관리의 핵심 테이블.

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

재시도 실패 시마다 이력을 기록한다. 어떤 시점에 몇 번째 시도가 어떤 이유로 실패했는지 추적할 수 있다.

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

예약 발송 정보를 별도 테이블로 관리한다. 예약 시각이 도래하면 notification 테이블로 이관되어 기존 발송 플로우를 탄다.

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

### 상태 전이

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

## 요구사항 해석 및 가정

1. **수강신청은 항상 성공한다고 가정**: 수강신청 자체의 검증(정원 초과, 중복 수강 등)은 범위 밖이다. 수강신청 완료 후 알림 발행에 집중한다.
2. **발송 채널은 Mock으로 구현**: 이메일은 SMTP/SES, 인앱은 WebSocket/FCM을 사용해야 하지만, 과제 범위에서는 로그 출력 + 랜덤 실패(이메일 30%, 인앱 20%)로 발송을 시뮬레이션한다.
3. **H2 인메모리 DB 사용**: 서버 재시작 시 데이터가 초기화된다. `SELECT FOR UPDATE` 비관적 락은 H2에서도 동작한다.
4. **단일 인스턴스 기준 개발, 다중 인스턴스 대비 설계**: `SELECT FOR UPDATE`로 다중 인스턴스 환경에서도 중복 처리를 방지하는 구조를 갖추었다.
5. **"알림 처리 실패가 비즈니스 트랜잭션에 영향을 주어서는 안 됩니다. 단, 예외를 단순히 무시하는 방식으로 이를 달성해서는 안 됩니다."** → `@Async @TransactionalEventListener`로 아예 별도 스레드에서 실행되도록 구조적으로 분리했다. try-catch로 예외를 삼키는 것이 아니라, 비동기 스레드 분리를 통해 영향 자체가 전파될 수 없는 구조다.

### 개선 의견

1. **발송 채널 확장 시 이벤트 리스너 수동 추가 문제**: 현재 채널별로 `@Async` 메서드를 수동으로 추가해야 한다. 채널 목록을 DB나 설정에서 읽어 동적으로 알림을 생성하는 방식으로 전환하면 채널 추가 시 코드 변경이 불필요해진다.
2. **즉시 재시도 중 Thread.sleep**: `NotificationSender`에서 즉시 재시도 시 `Thread.sleep(1000)`으로 대기하는데, 이 구간에서 스레드를 점유한다. 프로덕션에서는 `ScheduledExecutorService`나 비동기 딜레이로 교체하는 것이 바람직하다.
3. **알림 타입별 발송 채널 자동 매핑**: 현재는 호출 측에서 채널을 직접 지정하지만, 알림 타입에 따라 기본 발송 채널을 자동 매핑하는 정책 테이블이 있으면 호출 측의 부담이 줄어든다.

---

## 설계 결정과 이유

### 아키텍처

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

#### 계층 분리 원칙

| 계층 | 역할 | 프레임워크 의존 |
|------|------|----------------|
| `domain/.../application/service` | 순수 비즈니스 로직 | X (Spring 트랜잭션만) |
| `domain/.../adapter/in` | REST 컨트롤러 | O |
| `domain/.../adapter/out` | JPA 영속성 | O |
| `global/event` | 이벤트 리스너 (`@TransactionalEventListener`) | O |
| `global/scheduler` | 스케줄러 (`@Scheduled`) | O |
| `infrastructure/*` | 외부 시스템 발송 구현체 | O |

#### 통신 규약

- Controller ↔ Service: Request/Response DTO
- Service ↔ OutPort ↔ Adapter: NotificationData / ReserveData DTO
- JPA Entity는 adapter/out 내부에서만 사용 (package-private), 밖으로 유출하지 않는다
- 스케줄러/이벤트 리스너는 서비스의 public 메서드만 호출한다

### 비동기 처리 구조

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

| 지점 | 방식 | 이유 |
|------|------|------|
| 수강신청 → 알림 생성 | `@Async @TransactionalEventListener` (global/event) | 비즈니스 트랜잭션과 알림 처리를 구조적으로 분리. 알림 실패가 수강신청에 영향을 주지 않는다. |
| 알림 생성 → 발송 처리 | `@Async @TransactionalEventListener` (global/event) | 알림 저장(PENDING)과 실제 발송을 분리. 저장은 동기적으로 확정하고, 발송은 별도 스레드에서 처리한다. |

#### 채널별 독립 실행

같은 이벤트에 대해 EMAIL과 IN_APP 알림을 **별도 `@Async` 메서드**로 분리했다.

```java
// global/event/EnrollmentEventListener.java
@Async @TransactionalEventListener
public void handleEnrollmentEmailNotification(EnrollmentCompletedEvent event) { ... }

@Async @TransactionalEventListener
public void handleEnrollmentInAppNotification(EnrollmentCompletedEvent event) { ... }
```

하나의 메서드에서 두 채널을 순차 호출하면, 첫 번째 채널에서 예외가 발생할 때 두 번째 채널이 실행되지 않는다. 메서드를 분리하면 각각 독립된 스레드에서 실행되므로 한쪽 실패가 다른 쪽에 영향을 주지 않는다.

### 중복 발송 방지

#### DB Unique Key 기반

```sql
CONSTRAINT uk_event_receiver_channel UNIQUE (event_id, receiver_id, channel)
```

- SELECT 없이 바로 INSERT 시도 → 성공이면 처리, UK 위반 예외면 중복으로 판단
- SELECT → INSERT 방식은 동시 요청 시 레이스 컨디션이 발생한다. 두 요청이 동시에 "없음"을 확인하고 둘 다 INSERT를 시도하는 경우다. DB 제약조건은 원자적으로 막아준다.

#### eventId 생성 규칙

비즈니스 키 조합으로 eventId를 생성한다. 시퀀스 ID가 아닌 비즈니스 키를 사용해야 같은 이벤트를 식별할 수 있다.

```java
String eventId = "ENROLLMENT_" + event.getUserId() + "_" + event.getLectureId();
// 예: "ENROLLMENT_1_100" (유저1 + 강의100)
```

같은 유저가 같은 강의를 두 번 수강신청해도, eventId가 동일하므로 UK 위반으로 중복 알림이 차단된다.

### 재시도 정책

일시적 오류(네트워크 타임아웃 등)와 장기 장애(외부 서버 다운 등)를 구분하여 2단계로 대응한다.

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

DEAD 상태에 도달한 건은 수동 재시도 API(`POST /api/notifications/{id}/retry`)로 처리한다. 스케줄러 재시도가 실패할 때마다 `notification_log` 테이블에 실패 사유를 기록한다.

### 운영 시나리오 대응

#### 서버 재시작 복구

스케줄러가 PROCESSING 상태로 5분 이상 방치된 건을 FAILED로 전환한다. 서버가 발송 중에 죽으면 PROCESSING 상태로 남게 되는데, 재시작 후 스케줄러가 이를 감지하여 재시도 대상으로 복구한다.

#### 다중 인스턴스 중복 방지

스케줄러가 FAILED 건을 재처리할 때 비관적 락(`SELECT FOR UPDATE`)으로 조회한다. 전체 로직(조회 + 발송 + 상태 변경)이 하나의 `@Transactional` 안에서 실행되므로, 서버B가 락을 획득했을 때는 이미 상태가 변경되어 있어 중복 처리가 방지된다. 알림 발송은 비동기 백그라운드 처리이므로 DB 락 대기 비용이 문제가 되지 않는다.

### 설계 판단 요약

1. **헥사고날 계층 분리**: 스케줄러/이벤트 리스너는 프레임워크 의존이 있는 인프라 관심사이므로 `global/`로 분리. 발송 구현체는 채널별 독립 도메인이므로 `infrastructure/` 아래 채널별 패키지로 분리. `application/service`에는 순수 비즈니스 로직만 남긴다.
2. **비동기 분리**: 예외를 무시(try-catch 후 삼키기)하는 것이 아니라, 애초에 다른 스레드에서 실행되므로 비즈니스 로직에 영향을 줄 수 없는 구조다.
3. **스케줄러/서비스 분리**: `@Scheduled`는 트리거 역할만 하고(`global/scheduler`), 비즈니스 로직은 별도 서비스에서 `@Transactional`로 처리한다.
4. **프로덕션 전환**: 현재는 Spring `ApplicationEventPublisher` + `@Async`로 구현. 이벤트 유실 방지가 필요하면 Transactional Outbox 패턴 또는 SQS로 전환한다.
5. **오버 엔지니어링 배제**: 현재 규모에 맞는 선택을 하고, 확장이 필요한 시점에 Redis/SQS/Kafka를 검토한다.

---

## 테스트 실행 방법

```bash
./gradlew test
```

---

## 미구현 / 제약사항

### 구현됨

- **읽음 처리**: `PATCH /api/notifications/{id}/read`로 `is_read = true`로 변경한다. 여러 기기에서 동시에 읽음 처리 요청이 오더라도, 이미 `true`인 것을 다시 `true`로 변경하는 것이므로 멱등하게 동작한다. 별도 락 없이 안전하다. 만약 "누가 어느 기기에서 읽었는지" 이력을 남겨야 한다면 읽음 히스토리 테이블을 추가하고 비관적 락으로 특정화해야 하지만, 현재 요구사항에서는 읽음 여부(boolean)만 관리하면 충분하다.
- **수동 재시도**: `POST /api/notifications/{id}/retry`로 DEAD 상태인 알림을 재시도한다. retry_count를 초기화하지 않고 status만 FAILED로 변경하여 스케줄러가 다시 처리하도록 한다. retry_count를 유지하는 이유는 해당 알림이 총 몇 회 실패했는지 이력을 보존하기 위해서다. 초기화하면 "원래 3번 실패 → 수동 재시도 → 또 3번 실패"를 구분할 수 없다. `notification_log` 테이블과 함께 전체 실패 히스토리를 추적할 수 있다.
- **예약 발송**: `POST /api/notifications/reserve`로 특정 시각에 알림 발송을 예약한다. `notification_reserve` 테이블에 RESERVED 상태로 저장되고, 스케줄러가 1분 주기로 `reserved_at <= now`인 건을 SELECT FOR UPDATE로 조회하여 기존 발송 플로우로 전달한다. RESERVED 상태인 예약만 취소(`DELETE`)할 수 있다.

### 미구현

- **알림 템플릿 관리**: 타입별 메시지 템플릿에 변수 플레이스홀더(`{{userName}}`, `{{lectureName}}` 등)를 정의하고, 발송 시점에 실제 값으로 치환하는 방식. 구현 방향이 확정되지 않아 내용 정리만 해 둠.
- **Slack / SMS 발송**: `infrastructure/slack`, `infrastructure/sms` 패키지에 클래스 구조만 잡아 둠. 실제 API 연동은 미구현.

### 제약사항

- **H2 한계**: 인메모리 DB라 서버 재시작 시 데이터 초기화. 프로덕션에서는 MySQL/PostgreSQL로 교체 필요.
- **이벤트 유실 가능성**: `@TransactionalEventListener`는 트랜잭션 커밋 후 이벤트를 발행하므로, 커밋 직후 서버가 죽으면 이벤트가 유실될 수 있다. DB에 PENDING 상태로 남아있어 스케줄러가 복구하지만, 완전한 보장이 필요하면 Transactional Outbox 패턴 적용이 필요하다.

---

## AI 활용 범위

- Claude Code를 사용하여 코드 구현 및 README 작성을 수행함
- 아키텍처 설계, 재시도 정책, 중복 방지 전략 등 핵심 설계 결정은 직접 판단하고 Claude에 구현을 지시하는 방식으로 진행
- 코드 리뷰 및 헥사고날 아키텍처 적용 검증에 활용
