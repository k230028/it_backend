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

## 정보화사업 금액 스냅샷

컬럼별 통화와 원금 의미는 [데이터 모델의 정보화사업 금액 계약](../persistence/data-model.md#정보화사업-금액-계약)을 따른다. `BITEMM.AMT`는 이미 당해 KRW 금액이므로 환율을 다시 곱하지 않으며, 원통화 당해 원금은 `FC_AMT`로 표시한다.

- 집계 대상은 `DEL_YN = 'N' AND LST_YN = 'Y'`인 활성 품목이다.
- 외화 예정금액은 `ROUND(NVL(MPL_AMT, 0) * XCR, 3)`으로 원화 환산하고, KRW/null 통화 품목의 예정금액은 `MPL_AMT`를 그대로 사용한다.
- 사업 스냅샷은 `BPROJM.MPL_AMT`에 원화 예정금액 합계를, `BPROJM.TOT_RQM_AMT`에 `당해 원화 합계 + 예정 원화 합계 + NVL(DFR_AMT, 0)`을 기록한다. `BPROJM`의 세 금액 컬럼은 모두 KRW다.

기존 업무 데이터의 마이그레이션·보정은 이 계약의 범위가 아니다. 운영 절차로 기존 데이터를 삭제한 후 새 계약으로 저장하며, 적용된 과거 Flyway는 수정하지 않는다.
