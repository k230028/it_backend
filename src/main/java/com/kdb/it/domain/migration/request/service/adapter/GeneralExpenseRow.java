package com.kdb.it.domain.migration.request.service.adapter;

import java.math.BigDecimal;

/**
 * 시트 ③ `전산 일반관리비 편성요청서`의 데이터 행 하나입니다.
 *
 * @param excelRow 엑셀 사용자 관점 행 번호(1-based). 진단 좌표로 씁니다
 * @param midCategory A열 비목명 (forward-fill 적용 후)
 * @param detailName B열 세부비목 (forward-fill 적용 후)
 * @param contractName C열 계약명 / 건명
 * @param currency D열 통화 구분
 * @param monthly E열 월간. 비어 있으면 null
 * @param annual F열 연간. 비어 있으면 null
 * @param counterparty G열 상대처
 * @param continued H열 계속 표시 원문
 * @param isNew I열 신규 표시 원문
 * @param infoSec J열 정보보호 관련여부 원문
 * @param remarks K열 비고
 */
public record GeneralExpenseRow(
        int excelRow,
        String midCategory,
        String detailName,
        String contractName,
        String currency,
        BigDecimal monthly,
        BigDecimal annual,
        String counterparty,
        String continued,
        String isNew,
        String infoSec,
        String remarks) {}
