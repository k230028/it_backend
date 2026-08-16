package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.List;

/**
 * 담당자 이름을 물리 컬럼에 맞춰 다듬습니다.
 *
 * <p>담당자 컬럼(`USID`·`TLR_USID`·`CGPR_ID`)은 사번과 이름을 모두 받는 14자 자리입니다. 부점이 적어 내는 이름은 인사 표기와 어긋나거나
 * 동명이인이라 사번을 확정하지 못하는 경우가 많아 <b>이름을 그대로</b> 담고, 사번은 반입 후 상세 화면에서 맞춥니다.
 */
final class FormPersonNames {

    /** 담당자 컬럼의 물리 길이. 문자 기준(CHAR semantics)입니다. */
    static final int LIMIT = 14;

    private FormPersonNames() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /**
     * 이름을 컬럼 길이에 맞춥니다.
     *
     * <p>영문 성명은 14자를 넘길 수 있습니다. 파일을 막는 대신 잘라 담고 잘린 사실을 알립니다 — 조용히 자르면 나중에 이름이 왜 끊겨 있는지 아무도 설명하지
     * 못합니다.
     *
     * @param name 양식에 적힌 이름. null·공백이면 null을 돌려줍니다
     * @param label 진단 문구에 쓸 항목 이름 (`확인자` 등)
     * @param sheet 진단 좌표로 쓸 시트
     * @param diagnostics 잘렸을 때 경고를 담을 목록
     * @return 14자 이내 이름. 이름이 없으면 null
     */
    static String fit(
            String name,
            String label,
            FormSheetKind sheet,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        if (trimmed.length() <= LIMIT) return trimmed;

        diagnostics.add(
                RequestFormDto.FormDiagnostic.of(
                        sheet,
                        null,
                        null,
                        RequestFormDiagnosticCode.SUBSTITUTE_DROPPED,
                        "`%s`의 이름 `%s`가 %d자를 넘어 앞 %d자만 반입합니다."
                                .formatted(label, trimmed, LIMIT, LIMIT),
                        List.of()));
        return trimmed.substring(0, LIMIT);
    }
}
