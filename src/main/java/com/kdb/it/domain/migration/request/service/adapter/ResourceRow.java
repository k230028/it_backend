package com.kdb.it.domain.migration.request.service.adapter;

import java.math.BigDecimal;

/**
 * 소요자원 표의 데이터 행 하나입니다. 시트 ②와 시트 1-2가 같은 모양의 표를 씁니다.
 *
 * @param excelRow 엑셀 사용자 관점 행 번호(1-based). 진단 좌표로 씁니다
 * @param group 구분·중분류 (`기계장치(HW)`, `전산제비` 등). 병합이면 위 행에서 이어받은 값
 * @param itemName 항목명
 * @param qty 수량. 비어 있으면 null
 * @param unitPrice 단가. 적재하지 않고 `수량 × 단가 = 소요예산` 대사에만 씁니다
 * @param currency 통화
 * @param amount 소요예산 (원 단위 또는 통화 기본 단위)
 * @param basis 산정근거
 * @param timing 도입시기 또는 대금지급주기
 * @param infoSec 정보보호여부 원문
 * @param infra 인프라 통합관리 여부 원문
 * @param remarks 비고
 */
public record ResourceRow(
        int excelRow,
        String group,
        String itemName,
        BigDecimal qty,
        BigDecimal unitPrice,
        String currency,
        BigDecimal amount,
        String basis,
        String timing,
        String infoSec,
        String infra,
        String remarks) {}
