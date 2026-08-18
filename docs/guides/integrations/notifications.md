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

## 외부 메일 본문 (SD_DOC_CONE)

업무 도메인이 완성한 메일 제목·본문을 `NotificationEvent.sdPayload`에 실으면
`Cinfmm.SD_DOC_CONE`에 저장되어 발송 계층까지 전달됩니다. 계약은
`MailPayload(subject, html)` JSON(`com.kdb.it.common.notification.dispatcher.MailPayload`)입니다.

- 페이로드가 없거나 해석에 실패하면 `NotificationDispatcherRouter`가 알림의 `ttl`·`infmMsgCone`으로
  기본 본문을 만듭니다. 메일 서식 문제로 알림이 유실되지 않습니다.
- 본문 HTML은 GWE 전문 `CONTENTS` 폭(UTF-8 4000바이트, `ApprovalMailRenderer.CONTENTS_BUDGET_BYTES`)
  안에 들어가야 합니다. `ApprovalMailRenderer`가 신청서 개요와 총괄표 합계를 먼저 담고, 그래도 조립한
  본문이 예산을 넘으면 목록을 잘라내는 대신 페이로드 생성 자체를 포기하고 `renderPayloadJson`이
  `null`을 돌려줍니다 — 이 경우 발송 계층은 기본 본문으로 폴백합니다. 개요·총괄표만으로 예산을 넘기지
  않으면 목록은 남는 예산만큼만 싣고, 다 못 실은 항목 수는 "외 N건 · 전체 보기" 안내로 대체합니다.
- 신청 사업 목록은 정보화사업·전산업무비·경상사업을 표 하나로 합칩니다(구분별로 표를 나누면 머리글이
  세 번 반복되어 예산 대부분을 머리글이 먹습니다). 목록 표의 구분 열에는 총 예산만 싣고, 자본예산·
  일반관리비 내역은 목록 위 총괄표(구분별 건수·총 예산·자본예산·일반관리비 4열 합계)가 이미 보여줍니다.
- 메일 클라이언트가 `<style>` 블록을 제거하므로 서식은 기본적으로 인라인 `style` 속성으로 넣습니다.
  다만 표 테두리·셀 여백은 예외입니다. 셀마다 인라인 스타일을 반복하면 필수 항목(개요+총괄표)만으로도
  예산을 넘기므로(`MailHtml`), `<table border cellpadding cellspacing>` 같은 표 수준 표현 속성으로
  대신합니다.
- `ttl`·`infmMsgCone`은 인앱 알림 표시에 그대로 쓰이므로 HTML을 넣지 않습니다.
- 결재요청 메일 페이로드 조립 경로는 `ApprovalRequestNotifier.notifyApprovalRequest`(신청서 상신·중간
  결재 승인 두 지점에서 `ApplicationService`의 `@Transactional` 메서드가 호출) → `ApprovalMailPayloadProvider`
  → `ApprovalMailDataLoader` → `ApprovalMailRenderer` 순입니다. 렌더링에 필요한 신청자명·작성부서명 조회는
  `ApprovalMailDataLoader`가 `@Transactional(REQUIRES_NEW, readOnly = true)`로 분리된 별도 트랜잭션에서
  수행합니다. 호출자인 `ApprovalMailPayloadProvider`는 `@Transactional`이 아닌 상태로 로더를 호출하고 그
  결과(정상 반환 또는 예외)를 이 경계 밖에서 받습니다. 조회 실패를 신청서 상신 트랜잭션 **안에서** 잡으면
  이미 rollback-only로 표시된 트랜잭션이 커밋 시점에 `UnexpectedRollbackException`으로 다시 터지므로, 이
  트랜잭션 경계 분리가 메일 렌더링 실패로부터 상신 자체를 지키는 유일한 방어선입니다.
