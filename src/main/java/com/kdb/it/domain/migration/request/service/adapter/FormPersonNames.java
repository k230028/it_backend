package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 담당자 이름을 물리 컬럼에 맞춰 다듬습니다.
 *
 * <p>양식의 담당자 표기는 사번으로 확정하지 않고 이름 스냅샷 컬럼에 저장하므로 이름 컬럼 길이에 맞춥니다.
 */
final class FormPersonNames {

    private static final Pattern KOREAN_NAME_WITH_SUFFIX = Pattern.compile("^([가-힣]{2,})\\s+.+$");

    /** 담당자 이름 스냅샷 컬럼의 물리 길이. 문자 기준(CHAR semantics)입니다. */
    static final int LIMIT = 100;

    /**
     * 이름 뒤에 붙는 직책·직위 표기.
     *
     * <p>양식에는 `Luke Buckingham-Brown 과장`처럼 직책을 붙여 적습니다(런던 실측). 담당자 컬럼은 이름 자리이고 직책은 인사 정보라 원장에 담지
     * 않습니다 — 직책이 바뀌면 원장 값이 사실과 어긋나고 이름 컬럼 자리도 직책이 차지합니다.
     */
    private static final Set<String> TITLES =
            Set.of(
                    "행원", "사원", "주임", "계장", "대리", "과장", "차장", "부부장", "부장", "파트장", "팀장", "실장", "센터장",
                    "지점장", "본부장", "수석", "책임", "선임");

    private FormPersonNames() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /**
     * 이름 뒤에 붙은 직책을 떼어 냅니다.
     *
     * <p><b>공백으로 떨어진 마지막 토큰만</b> 봅니다. 붙여 쓴 표기(`김성원과장`)까지 잘라 내면 이름 끝 글자가 우연히 직책과 겹치는 경우에 진짜 이름이 조용히
     * 훼손됩니다 — 대조표에 없는 어휘를 추측해 매핑하지 않는다는 {@code FormLexicon}의 원칙과 같은 이유입니다.
     *
     * <p>{@code endsWith}까지 보는 것은 `IT팀장`·`수석부부장`처럼 직책 앞에 수식이 붙는 표기를 잡기 위함입니다. 남는 토큰이 없어지는 경우에는 떼지
     * 않습니다 — 직책만 적혀 있으면 그것이 우리가 가진 전부입니다.
     *
     * @param name 공백을 정리한 이름
     * @return 직책을 뗀 이름
     */
    private static String stripTitle(String name) {
        String current = name;
        while (true) {
            int lastSpace = current.lastIndexOf(' ');
            if (lastSpace < 0) return current;
            String tail = current.substring(lastSpace + 1);
            if (!isTitle(tail)) return current;
            current = current.substring(0, lastSpace).trim();
            if (current.isEmpty()) return name;
        }
    }

    private static boolean isTitle(String token) {
        for (String title : TITLES) {
            if (token.endsWith(title)) return true;
        }
        return false;
    }

    /**
     * 이름에서 직책을 떼고 컬럼 길이에 맞춥니다.
     *
     * <p>영문 성명이 이름 컬럼 길이를 넘으면 파일을 막는 대신 잘라 담고 잘린 사실을 알립니다 — 조용히 자르면 나중에 이름이 왜 끊겨 있는지 아무도 설명하지 못합니다.
     * 진단 문구에는 직책을 뗀 이름을 실어 화면에서 실제 저장 대상과 대조할 수 있게 합니다.
     *
     * @param name 양식에 적힌 이름. null·공백이거나 직책만 적혀 있으면 그대로 다룹니다
     * @param label 진단 문구에 쓸 항목 이름 (`확인자` 등)
     * @param sheet 진단 좌표로 쓸 시트
     * @param diagnostics 잘렸을 때 경고를 담을 목록
     * @return 직책을 뗀 이름 컬럼 길이 이내 이름. 이름이 없으면 null
     */
    static String fit(
            String name,
            String label,
            FormSheetKind sheet,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (name == null || name.isBlank()) return null;
        String trimmed = stripTitle(name.trim().replaceAll("\\s+", " "));
        Matcher koreanName = KOREAN_NAME_WITH_SUFFIX.matcher(trimmed);
        if (koreanName.matches()) trimmed = Objects.requireNonNull(koreanName.group(1));
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
