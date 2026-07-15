# 알림 가이드

## 발송 흐름

```text
업무 서비스 NotificationEvent 발행
→ NotificationEventListener(AFTER_COMMIT)
→ NotificationService.send(REQUIRES_NEW)
→ Cinfmm 저장
→ NotificationDispatcherRouter
```

- 이벤트 수신자는 비어 있지 않아야 합니다.
- 알림 종류는 `NotificationEvent.TYPE_*` 상수를 사용합니다.
- 커밋 이후 DB 적재는 `REQUIRES_NEW`와 즉시 flush로 실패 지점을 명확히 합니다.
- 외부 채널 실패는 warn과 발송 결과로 남기되 원 업무를 롤백하지 않습니다.

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
