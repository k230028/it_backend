package com.kdb.it.domain.migration.request.support;

import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.List;

/**
 * 진단 목록을 필드 id로 거르는 테스트 헬퍼입니다.
 *
 * <p>같은 조건에서 서로 다른 두 진단이 같은 코드({@code AMOUNT_MISMATCH})로 나오는 자리가 있습니다 — 대사 경고({@code
 * field="declaredYearTotal"})와 산출 실패 경고({@code field="declaredAmounts"})가 그렇습니다. 코드만 assert하면 한쪽을
 * 지워도 다른 한쪽이 테스트를 통과시키므로, 두 어댑터 테스트가 이 헬퍼로 필드를 좁혀서 봅니다.
 */
public final class FormDiagnostics {

    private FormDiagnostics() {
        throw new UnsupportedOperationException("테스트 헬퍼 — 인스턴스화 금지");
    }

    /**
     * 지정한 필드 id의 진단만 골라냅니다.
     *
     * @param diagnostics 진단 목록
     * @param field 필드 id (예: `declaredYearTotal`·`declaredAmounts`)
     * @return 해당 필드의 진단 목록. 없으면 빈 목록
     */
    public static List<RequestFormDto.FormDiagnostic> byField(
            List<RequestFormDto.FormDiagnostic> diagnostics, String field) {
        return diagnostics.stream().filter(d -> field.equals(d.field())).toList();
    }

    /**
     * 지정한 필드 id의 첫 진단 문구를 반환합니다.
     *
     * @param diagnostics 진단 목록
     * @param field 필드 id
     * @return 첫 진단의 사용자 문구. 해당 필드의 진단이 없으면 빈 문자열
     */
    public static String messageOf(List<RequestFormDto.FormDiagnostic> diagnostics, String field) {
        return byField(diagnostics, field).stream()
                .map(RequestFormDto.FormDiagnostic::message)
                .findFirst()
                .orElse("");
    }
}
