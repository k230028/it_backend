package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조립된 생성 요청을 놓고 횡단 검증을 합니다.
 *
 * <p>어댑터는 셀 좌표를 아는 대신 DB 상태나 배치 전체를 보지 못합니다. 그래서 해석 실패 진단은 어댑터가 내고, 필수값·물리 길이·자연키 중복처럼 조립 결과를 놓고 봐야
 * 하는 것만 여기서 봅니다.
 *
 * <p>dry-run과 commit이 같은 검증기를 씁니다. 서버가 dry-run 결과를 보관하지 않으므로 commit이 클라이언트가 보낸 값을 신뢰하지 않고 전부 다시
 * 검증합니다.
 */
@Component
@RequiredArgsConstructor
public class RequestFormValidator {

    /** 물리 컬럼 길이 상한. 엔티티 `@Column(length=…)`과 같은 값입니다. */
    private static final int CONTRACT_NAME_LIMIT = 100;

    private static final int COUNTERPARTY_LIMIT = 100;
    private static final int INCREASE_REASON_LIMIT = 200;
    private static final int PROJECT_NAME_LIMIT = 100;
    private static final int ITEM_NAME_LIMIT = 100;

    /** 비목코드 필드 id. 어댑터가 이미 보고한 대상은 여기서 다시 보지 않습니다. */
    private static final String IOE_FIELD = "ioeC";

    /**
     * 자연키 구성요소 구분자.
     *
     * <p>제어문자(UNIT SEPARATOR)를 씁니다 — 계약명·상대처는 자유 입력이라 `|` 같은 흔한 문자를 쓰면 `A|B`와 `A`+`B`가 같은 키가 되어 서로
     * 다른 계약이 중복으로 오인됩니다.
     */
    private static final String KEY_DELIMITER = "\u001F";

    private final CostRepository costRepository;
    private final ProjectRepository projectRepository;

    /**
     * 생성 요청 전체를 검증합니다.
     *
     * @param output 어댑터가 조립한 생성 요청
     * @param bseYy 예산연도 4자리
     * @return 추가 진단 목록. 문제가 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<RequestFormDto.FormDiagnostic> validate(FormAdapterOutput output, String bseYy) {
        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        Set<String> alreadyReported = ioeSubjectsAlreadyReported(output);
        validateCosts(output.costs(), bseYy, alreadyReported, diagnostics);
        validateProjects(output.projects(), bseYy, alreadyReported, diagnostics);
        return List.copyOf(diagnostics);
    }

    /**
     * 어댑터가 이미 비목 진단을 낸 대상(품목명·계약명)을 모읍니다.
     *
     * <p>비목이 비어 있는 이유는 <b>어댑터가 해석하지 못했기 때문</b>이고, 어댑터는 그 사실을 행 좌표와 후보까지 붙여 이미 보고했습니다. 여기서 같은 사건을
     * 필수값 누락으로 한 번 더 내면 사용자에게는 <b>고칠 수 없는 차단</b>이 하나 더 생깁니다 — 행 진단에서 비목을 골라도 이 중복이 남아 파일이 계속 막힙니다
     * (실측: 자금운용실 품목 3·4·5).
     *
     * @param output 어댑터가 조립한 생성 요청
     * @return 어댑터가 짚은 대상 이름 집합. 이름이 없는 진단은 담지 않습니다
     */
    private static Set<String> ioeSubjectsAlreadyReported(FormAdapterOutput output) {
        Set<String> subjects = new HashSet<>();
        for (RequestFormDto.FormDiagnostic diagnostic : output.diagnostics()) {
            if (IOE_FIELD.equals(diagnostic.field()) && diagnostic.subject() != null) {
                subjects.add(diagnostic.subject());
            }
        }
        return subjects;
    }

    private void validateCosts(
            List<CostDto.CreateRequest> costs,
            String bseYy,
            Set<String> ioeAlreadyReported,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (costs.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        for (Bcostm cost : costRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            existing.add(
                    naturalKey(
                            cost.getCostSvnDpmC(),
                            cost.getIoeC(),
                            cost.getCttOppNm(),
                            cost.getCttNm()));
        }
        Set<String> withinBatch = new HashSet<>();

        for (CostDto.CreateRequest cost : costs) {
            String subject = subjectOf(cost.getCttNm(), "계약명 미기재");
            if (!ioeAlreadyReported.contains(nullSafe(cost.getCttNm()))) {
                requireText(
                        cost.getIoeC(),
                        FormSheetKind.GENERAL_EXPENSE,
                        IOE_FIELD,
                        subject,
                        "비목코드",
                        diagnostics);
            }
            requireText(
                    cost.getCttNm(),
                    FormSheetKind.GENERAL_EXPENSE,
                    "cttNm",
                    subject,
                    "계약명",
                    diagnostics);
            requireText(
                    cost.getCurC(),
                    FormSheetKind.GENERAL_EXPENSE,
                    "curC",
                    subject,
                    "통화",
                    diagnostics);
            if (cost.getCostTotXpAmt() == null && cost.getFcAmt() == null) {
                diagnostics.add(
                        blocker(
                                FormSheetKind.GENERAL_EXPENSE,
                                "amt",
                                subject,
                                RequestFormDiagnosticCode.REQUIRED_MISSING,
                                "금액이 비어 있습니다."));
            }
            limit(
                    cost.getCttNm(),
                    CONTRACT_NAME_LIMIT,
                    FormSheetKind.GENERAL_EXPENSE,
                    "cttNm",
                    subject,
                    "계약명",
                    diagnostics);
            limit(
                    cost.getCttOppNm(),
                    COUNTERPARTY_LIMIT,
                    FormSheetKind.GENERAL_EXPENSE,
                    "cttOppNm",
                    subject,
                    "상대처",
                    diagnostics);
            limit(
                    cost.getIndRsn(),
                    INCREASE_REASON_LIMIT,
                    FormSheetKind.GENERAL_EXPENSE,
                    "indRsn",
                    subject,
                    "비고",
                    diagnostics);

            String key =
                    naturalKey(
                            cost.getCostSvnDpmC(),
                            cost.getIoeC(),
                            cost.getCttOppNm(),
                            cost.getCttNm());
            if (existing.contains(key) || !withinBatch.add(key)) {
                diagnostics.add(
                        blocker(
                                FormSheetKind.GENERAL_EXPENSE,
                                "cttNm",
                                subject,
                                RequestFormDiagnosticCode.DUPLICATE_EXISTS,
                                "이미 반입된 전산업무비입니다. 덮어쓰지 않고 건너뜁니다."));
            }
        }
    }

    private void validateProjects(
            List<ProjectDto.CreateRequest> projects,
            String bseYy,
            Set<String> ioeAlreadyReported,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (projects.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        for (Bprojm project : projectRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            existing.add(normalizeProjectName(project.getAbusNm()));
        }
        Set<String> withinBatch = new HashSet<>();

        for (ProjectDto.CreateRequest project : projects) {
            FormSheetKind sheet =
                    "Y".equals(project.getOdnYn())
                            ? FormSheetKind.RECURRING
                            : FormSheetKind.CAPITAL_OVERVIEW;
            String subject = subjectOf(project.getAbusNm(), "사업명 미기재");
            requireText(project.getAbusNm(), sheet, "abusNm", subject, "사업명", diagnostics);
            limit(
                    project.getAbusNm(),
                    PROJECT_NAME_LIMIT,
                    sheet,
                    "abusNm",
                    subject,
                    "사업명",
                    diagnostics);
            validateItems(project, sheet, subject, ioeAlreadyReported, diagnostics);

            String key = normalizeProjectName(project.getAbusNm());
            if (key.isEmpty()) continue;
            if (existing.contains(key) || !withinBatch.add(key)) {
                diagnostics.add(
                        blocker(
                                sheet,
                                "abusNm",
                                subject,
                                RequestFormDiagnosticCode.DUPLICATE_EXISTS,
                                "이미 반입된 사업입니다. 덮어쓰지 않고 건너뜁니다."));
            }
        }
    }

    /**
     * 품목을 검증합니다. 대상 표기는 `사업명 · 품목 순번 품목명` 형태로 만듭니다.
     *
     * <p>한 사업이 품목을 수십 건 담기 때문에 사업명만으로는 어느 줄을 고쳐야 하는지 알 수 없습니다. 품목명이 비어 있는 행도 순번으로 짚을 수 있게 순번을 앞에
     * 붙입니다.
     */
    private void validateItems(
            ProjectDto.CreateRequest project,
            FormSheetKind sheet,
            String projectSubject,
            Set<String> ioeAlreadyReported,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (project.getItems() == null) return;
        for (ProjectDto.BitemmDto item : project.getItems()) {
            String subject =
                    "%s · 품목 %d %s"
                            .formatted(
                                    projectSubject,
                                    item.getSno() == null ? 0 : item.getSno(),
                                    subjectOf(item.getGclNm(), "품목명 미기재"));
            if (!ioeAlreadyReported.contains(nullSafe(item.getGclNm()))) {
                requireText(item.getIoeC(), sheet, IOE_FIELD, subject, "비목코드", diagnostics);
            }
            limit(item.getGclNm(), ITEM_NAME_LIMIT, sheet, "gclNm", subject, "품목명", diagnostics);
            if (item.getAmt() == null && item.getFcAmt() == null) {
                diagnostics.add(
                        blocker(
                                sheet,
                                "amt",
                                subject,
                                RequestFormDiagnosticCode.REQUIRED_MISSING,
                                "금액이 비어 있습니다."));
            }
        }
    }

    /**
     * BLOCKER가 하나라도 있는지 판정합니다.
     *
     * @param diagnostics 진단 목록
     * @return BLOCKER가 있으면 true
     */
    public static boolean hasBlocker(List<RequestFormDto.FormDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(d -> d.severity() == MigrationDto.Severity.BLOCKER);
    }

    /**
     * 사업명을 자연키 비교용으로 정규화합니다. 공백을 전부 제거합니다.
     *
     * @param name 사업명. null이면 빈 문자열
     * @return 정규화된 사업명
     */
    public static String normalizeProjectName(String name) {
        return name == null ? "" : name.replaceAll("[\\s\\u00A0\\u3000]+", "");
    }

    /**
     * 전산업무비 자연키를 만듭니다.
     *
     * <p>부서코드를 키에 넣습니다 — 부점별 제출이라 같은 계약명이 여러 부점에 나올 수 있고, 이 양식에는 기존 이관이 쓰던 사업코드 열이 없습니다.
     */
    private static String naturalKey(
            String deptCode, String ioeCode, String counterparty, String contractName) {
        return String.join(
                KEY_DELIMITER,
                nullSafe(deptCode),
                nullSafe(ioeCode),
                nullSafe(counterparty),
                nullSafe(contractName));
    }

    private void requireText(
            String value,
            FormSheetKind sheet,
            String field,
            String subject,
            String label,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (value != null && !value.isBlank()) return;
        diagnostics.add(
                blocker(
                        sheet,
                        field,
                        subject,
                        RequestFormDiagnosticCode.REQUIRED_MISSING,
                        "%s이(가) 비어 있습니다.".formatted(label)));
    }

    private void limit(
            String value,
            int max,
            FormSheetKind sheet,
            String field,
            String subject,
            String label,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (value == null || value.length() <= max) return;
        diagnostics.add(
                blocker(
                        sheet,
                        field,
                        subject,
                        RequestFormDiagnosticCode.LENGTH_EXCEEDED,
                        "%s이(가) %d자를 넘습니다(%d자).".formatted(label, max, value.length())));
    }

    private RequestFormDto.FormDiagnostic blocker(
            FormSheetKind sheet,
            String field,
            String subject,
            RequestFormDiagnosticCode code,
            String message) {
        return RequestFormDto.FormDiagnostic.about(
                sheet, null, field, subject, code, message, List.of());
    }

    /** 대상 표기를 만듭니다. 이름이 비어 있으면 자리를 짚을 수 있는 대체 문구를 씁니다. */
    private static String subjectOf(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
