# 알림 가이드

## 발송 흐름

```text
업무 서비스 NotificationEvent 발행
→ NotificationEventListener(AFTER_COMMIT)
→ NotificationOutboxService.enqueue(REQUIRES_NEW)
→ CINFMM PENDING 저장·커밋
→ NotificationDispatchService.dispatch(REQUIRES_NEW)
→ NotificationDispatcherRouter 발송
→ CINFMM SENT 또는 FAILED 저장·커밋
```

- 이벤트 수신자는 비어 있지 않아야 합니다.
- 알림 종류는 `NotificationEvent.TYPE_*` 상수를 사용합니다.
- outbox 적재와 외부 채널 발송은 별도 `REQUIRES_NEW` 트랜잭션으로 분리합니다.
- outbox 적재 실패는 원 업무를 롤백하지 않습니다. 이 유실 경로는 `notification.persist.failure` 메트릭과 오류 로그로만 탐지하므로 운영 알람을 연결해야 합니다.
- 예상 밖 발송 처리 오류는 `notification.dispatch.unexpected` 메트릭으로 탐지합니다.

## 발송 상태와 재시도

| 코드 | 상태      | 의미                         |
| ---- | --------- | ---------------------------- |
| `01` | `PENDING` | outbox 적재 후 발송 대기     |
| `02` | `SENT`    | 외부 채널 발송 성공 또는 스킵 |
| `03` | `FAILED`  | 외부 채널 발송 실패          |

- 스케줄러는 기본 60초마다 `FAILED`와 60초 이상 정체된 `PENDING`을 조회합니다.
- 한 번에 최대 50건을 처리하며 건별 최대 5회까지만 시도합니다.
- 설정 키는 `notification.retry.enabled`, `fixed-delay-ms`, `batch-size`, `max-attempts`입니다.

## 종류

| 코드 | 종류        |
| ---- | ----------- |
| `01` | 시스템      |
| `02` | 결재 요청   |
| `03` | 결재 결과   |
| `04` | 게시물 멘션 |
| `05` | 댓글 멘션   |
| `06` | 결재 회수   |

## 사용자 API

- 목록과 미읽음 수 조회
- 단건·일괄 읽음
- 단건 Soft Delete

모든 작업은 현재 사용자 자신의 알림만 허용합니다. 채널 코드는 `NotificationDispatcherRouter.CHANNEL_*` 상수를 사용하며 기본값은 인앱입니다.
