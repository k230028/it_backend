# 신청서 상태와 작성완료 스탬프

## 상태 코드 (`IT_PTL_APF_PRG_STS_C`, `ApprovalStatus`)

| 코드 | 상수 | 의미 | 결재선 |
| --- | --- | --- | --- |
| `0` | `DRAFTED` | 작성완료. [저장]으로 만든 결재선 없는 신청서 | 없음 |
| `1` | `IN_PROGRESS` | 결재중 | 있음 |
| `2` | `COMPLETED` | 결재완료 | 있음 |
| `3` | `REJECTED` | 반려 | 있음 |
| `4` | `RECALLED` | 회수 | 있음 |
| `9` | `MANUAL` | 수기등록(편성요청서 반입). 등록자결재요청내용 `수기등록` 표식 | 없음 |

신청서가 없는 원천은 **임시저장**이다. 상신 대상은 최신 신청서가 `0`인 원천이며, 반려·회수 건은 다시 [저장]해야 상신 대상이 된다.

## 스탬프 규칙 (`ApprovalStamper`)

- `stamp(...)`: 반입 전용. 결재완료 또는 수기등록 상태의 받이를 만든다.
- `stampDrafted(...)`: `ProjectService`·`CostService`가 요청의 `complete=true`일 때 호출한다. 같은 `(원천, 관리번호, 순번)`의 최신 신청서가 `0`이면 제목만 갱신하고, `1`이면 `IllegalStateException`, 그 외에는 새 `0` 신청서를 만든다.
- 결재 상신(`ApplicationService.submit`)은 묶음 단위로 새 `1` 신청서를 만든다. `0` 행은 갱신하지 않고 최신 신청서번호 판정으로 자연히 덮인다.

## 목록·집계

- `BudgetListVersionScope`: `0`·`1`·`3`·`4` 스코프는 재상신 초안(`LST_YN='N'`)까지 노출한다.
- 결재함 목록(`GET /api/applications`)과 대시보드는 `0`을 제외한다.
- 사이드바 상신 대상 건수(`/pending-count`)는 `apfSts=0`으로 집계한다.
