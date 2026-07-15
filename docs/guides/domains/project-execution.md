# 정보화사업 집행 가이드

정보화사업 집행은 네 도메인으로 나뉩니다.

| 단계          | 도메인         | API                          | 기본·상세 엔티티   |
| ------------- | -------------- | ---------------------------- | ------------------ |
| 소요예산 산정 | `estimate`     | `/api/project/estimates`     | `Bestim`, `Besttm` |
| 과업심의      | `deliberation` | `/api/project/deliberations` | `Bdelim`           |
| 입찰·계약     | `contract`     | `/api/project/contracts`     | `Bcontm`           |
| 대금지급      | `payment`      | `/api/project/payments`      | `Bpaymm`, `Bpaymt` |

## 공통 계약

- 목록·상세·생성·수정·Soft Delete와 상태 전이 API를 제공합니다.
- 상태는 작성중 ↔ 진행중 ↔ 완료의 인접 단계만 전이합니다.
- 작성중 상태에서만 수정·삭제합니다.
- 동일 대상에 진행 중 문서가 중복 생성되지 않도록 검증합니다.
- 수정·삭제·상세 저장은 owner-or-admin, 상태 전이는 ADMIN을 검증합니다.
- 부서 목록 범위는 사업과 전산업무비의 담당 부서를 기준으로 Repository에서 필터링합니다.
- 변경 엔티티는 감사 로그 대상입니다.

`BITEMM.amt`는 이미 KRW 환산 금액이므로 환율을 다시 곱하지 않습니다. 원통화 표시는 `fcAmt`와 통화·환율 필드를 사용합니다.
