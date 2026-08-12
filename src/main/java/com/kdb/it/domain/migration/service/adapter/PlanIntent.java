package com.kdb.it.domain.migration.service.adapter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 부문계획 조정 의도입니다.
 *
 * <p>조정액은 비율 곱이 아니라 확정 금액이므로 대상 사업의 {@code BITEMM}을 이 금액으로 버전 교체하고 편성률 100을 적용합니다(§5.4).
 *
 * @param normalizedProjectName 정규화 사업명 (동일성 판정 키)
 * @param devAmount 개발비 조정액 (원 단위). 없으면 null
 * @param hwAmount 기계장치 조정액 (원 단위). 없으면 null
 * @param swAmount 기타무형자산 조정액 (원 단위). 없으면 null
 * @param generalAmount 일반관리비 조정액 (원 단위). 계획 마스터의 {@code TOT_XP_AMT} 합계에 들어갑니다. 없으면 null
 * @param paymentYm 예상지급일정 6자리 (BITEMM.BSE_YM). 없으면 null
 * @param snapshotFields 원장에 넣지 않고 계획 스냅샷에만 남길 값 (집행 실적·사업진행·비고)
 */
public record PlanIntent(
        String normalizedProjectName,
        BigDecimal devAmount,
        BigDecimal hwAmount,
        BigDecimal swAmount,
        BigDecimal generalAmount,
        String paymentYm,
        Map<String, String> snapshotFields) {}
