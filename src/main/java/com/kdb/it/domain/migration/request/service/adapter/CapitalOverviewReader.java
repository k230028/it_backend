package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDecisionKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 ① 1-1 `정보화사업 개요` 폼을 읽습니다.
 *
 * <p>항목이 20개가 넘어 어댑터에서 분리했습니다. 라벨 앵커로 각 항목을 찾고, 사람 이름은 조직 인덱스로 사번을 해석합니다.
 *
 * <p>상단의 `(확인자)`·`(작성자)`는 <b>쓰지 않습니다.</b> 담당자·팀장은 `관련 조직` 블록의 `팀장`·`실무자(정/부)`·`IT팀장`·`IT실무자(정/부)`가
 * 정본입니다. 실측 파일에서 두 곳이 어긋났습니다(확인자 `허인선 팀장` 대 관련 조직 팀장 `윤소정`) — 제출 담당자와 사업 주관자가 다른 경우입니다.
 */
@Component
@RequiredArgsConstructor
public class CapitalOverviewReader {

    /** 사업범위·추진경과처럼 여러 행에 걸치는 칸을 이어 붙일 최대 행 수. */
    private static final int MULTI_ROW_SPAN = 8;

    /** 공란이면 경고만 내고 null로 반입하는 선택 항목. 필드 id 별 폼 라벨. */
    private static final Map<String, String> OPTIONAL_FIELDS = optionalFields();

    /** `25/06` 형태의 연월 표기. */
    private static final Pattern YEAR_MONTH_PATTERN =
            Pattern.compile("(\\d{2})\\s*/\\s*(\\d{1,2})");

    /** 요약표에서 `'26년도 합계` 열을 찾을 때 훑는 최대 열 수. */
    private static final int TOTAL_SCAN_WIDTH = 15;

    /** 요약표 `'26년도 합계` 헤더의 정규화 접미사. 연도가 바뀌어도 맞도록 뒤 4글자만 봅니다. */
    private static final String YEAR_TOTAL_SUFFIX = "년도합계";

    /** 요약표 `'26년도 이후` 헤더의 정규화 접미사. */
    private static final String LATER_TOTAL_SUFFIX = "년도이후";

    /**
     * `총 사업금액(전체기간)` 칸의 단위 접미사. <b>긴 접미사를 먼저 본다</b> — `백만원`이 `원`으로 먼저 잡히면 100만배 틀린다.
     *
     * <p>여기 없는 접미사(`억원` 등)는 요약표 배수로 폴백하지 않고 해석 실패로 처리합니다. 폴백이 틀리면 조용히 자릿수가 어긋난 금액이 원장에 남습니다.
     */
    private static final List<Map.Entry<String, AmountUnit>> WHOLE_PERIOD_SUFFIXES =
            List.of(
                    Map.entry("백만원", AmountUnit.MILLION),
                    Map.entry("천원", AmountUnit.THOUSAND),
                    Map.entry("원", AmountUnit.WON));

    /** 법규상 완료시기 필드 id. 다른 선택 항목과 처리 방식이 달라 따로 가릅니다. */
    private static final String COMPLETION_DEADLINE_FIELD = "flfFsgDt";

    /** 법규상 완료시기의 "기한 없음" 선택지. 미기재가 아니라 확정된 답입니다. */
    private static final String NO_DEADLINE_OPTION = "별도없음";

    /** 보고상태 필드 id. 코드 카탈로그를 가르는 데 씁니다. */
    private static final String REPORT_STATUS_FIELD = "rprStsTc";

    private final SheetAnchorScanner scanner;
    private final FormLabelReader labelReader;
    private final FormCheckboxReader checkboxReader;

    /**
     * 1-1 시트를 읽어 사업 생성 요청을 만듭니다.
     *
     * @param sheet 1-1 시트
     * @param context 어댑터 실행 맥락
     * @param catalogs 코드 해석에 쓰는 공통코드 묶음
     * @return 사업 요청, 진단, 1-1이 선언한 금액
     */
    public Result read(Sheet sheet, FormAdapterContext context, FormCatalogs catalogs) {
        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();

        project.setBseYy(context.bseYy());
        project.setOdnYn("N");
        project.setAbusTc(CodeDefaults.NOT_APPLICABLE);
        project.setAbusNm(FormText.singleLineName(labelReader.value(sheet, "사업명")));
        // 개요는 사업범위와 함께 Tiptap이 편집하는 HTML 필드다 — 나머지 서술 칸은 평문(Textarea)이라 개행을 그대로 둔다
        project.setAbusCone(FormText.multiLineRichText(labelReader.value(sheet, "(개요)")));
        project.setCpnSafCone(labelReader.value(sheet, "(현황)"));
        project.setAbusNcsCone(labelReader.value(sheet, "(필요성)"));
        project.setDgogPpoCone(labelReader.value(sheet, "(기대효과)"));
        project.setPlmDes(labelReader.value(sheet, "(미추진시 문제점)"));
        // 사업범위는 여러 행에 나뉘어 적히는 HTML(Tiptap) 필드다 — 행 사이 줄바꿈을 <br>로 옮겨야 화면에 그대로 보인다
        project.setAbusRngCone(
                FormText.multiLineRichText(
                        labelReader.multiRowValue(sheet, "사업 범위 (전산 요구사항)", MULTI_ROW_SPAN)));
        project.setMnPrgCone(labelReader.multiRowValue(sheet, "추진경과", MULTI_ROW_SPAN));
        project.setHrfPlnCone(labelReader.multiRowValue(sheet, "향후계획", MULTI_ROW_SPAN));

        applyOptionalFields(
                sheet, context, project, catalogs, checkboxReader.read(sheet), diagnostics);
        applyOrganization(sheet, context, project);
        applyPeople(sheet, context, project, diagnostics);
        applyPeriod(sheet, project, diagnostics);
        applyDelegation(sheet, context, project, catalogs, diagnostics);

        return new Result(
                project,
                stampProjectSubject(diagnostics, project.getAbusNm()),
                declaredAmounts(sheet));
    }

    /**
     * 1-1 진단에 사업명을 대상으로 달아 줍니다.
     *
     * <p>이 시트의 진단은 대부분 사업 단위(전결권자·기간·선택 항목)라 항목별로 대상을 넘기는 대신 마지막에 한 번 붙입니다. 한 배치가 파일 수십 건을 다루므로 어느
     * 사업의 이야기인지 없으면 결과 표에서 짚어낼 수 없습니다.
     */
    private static List<RequestFormDto.FormDiagnostic> stampProjectSubject(
            List<RequestFormDto.FormDiagnostic> diagnostics, String projectName) {
        if (projectName == null || projectName.isBlank()) return List.copyOf(diagnostics);
        List<RequestFormDto.FormDiagnostic> stamped = new ArrayList<>();
        for (RequestFormDto.FormDiagnostic diagnostic : diagnostics) {
            stamped.add(
                    diagnostic.subject() != null
                            ? diagnostic
                            // 결정 종류를 그대로 옮긴다 — 후보 유무로 다시 유추하면 후보 없는
                            // 입력(완료기한 날짜 등)이 `해소 불가`로 뒤집힌다
                            : RequestFormDto.FormDiagnostic.decide(
                                    diagnostic.sheet(),
                                    diagnostic.excelRow(),
                                    diagnostic.field(),
                                    projectName,
                                    diagnostic.code(),
                                    diagnostic.message(),
                                    diagnostic.candidates(),
                                    diagnostic.decision()));
        }
        return List.copyOf(stamped);
    }

    /**
     * 선택 항목 8개를 채웁니다. 이 항목들은 셀이 아니라 <b>양식 컨트롤 체크박스</b>로 표시됩니다.
     *
     * <p>체크박스가 없는 변형 양식을 위해 셀 값 폴백을 남겨 둡니다({@link #optionsOf}). 그래서 두 표기 중 무엇으로 오든 같은 결과가 됩니다.
     */
    private void applyOptionalFields(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            FormCatalogs catalogs,
            List<FormCheckbox> checkboxes,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        OPTIONAL_FIELDS.forEach(
                (field, label) -> values.put(field, optionsOf(sheet, checkboxes, label)));
        values.forEach(
                (field, options) -> {
                    // 보정값이 있으면 그 값이 최종값이므로 미기재 안내를 내지 않는다
                    if (decisionOf(context, field).isPresent()) return;
                    // 법규상 완료시기는 고른 값이 있어도 날짜가 아니라 따로 안내한다
                    if (COMPLETION_DEADLINE_FIELD.equals(field) || !options.isEmpty()) return;
                    diagnostics.add(
                            optionalDiagnostic(
                                    field,
                                    "`%s` 항목이 비어 있습니다. 미리보기에서 고르거나 반입 후 사업 상세 화면에서 채울 수 있습니다."
                                            .formatted(OPTIONAL_FIELDS.get(field)),
                                    catalogs.optionCandidates(field),
                                    RequestFormDecisionKind.SELECT));
                });

        // 공통코드 코드값명을 그대로 저장하는 항목들 (Bprojm 필드 주석 참고). 복수 선택은 쉼표로 잇는다
        project.setBzDttNm(decided(context, "bzDttNm", values));
        project.setBzTpC(decided(context, "bzTpC", values));
        project.setSklTpTc(decided(context, "sklTpTc", values));
        project.setCstTpTc(decided(context, "cstTpTc", values));
        project.setDplYn(
                decisionOf(context, "dplYn").orElseGet(() -> duplicateYn(values.get("dplYn"))));
        project.setFlfFsgDt(decisionOf(context, COMPLETION_DEADLINE_FIELD).orElse(null));
        applyCompletionDeadlineNotice(context, values.get(COMPLETION_DEADLINE_FIELD), diagnostics);
        project.setRprStsTc(firstMatchingCode(context, "rprStsTc", catalogs, values, diagnostics));
        project.setExePttYn(firstMatchingCode(context, "exePttYn", catalogs, values, diagnostics));
    }

    /** 미리보기에서 고른 값을 읽습니다. 선택 항목의 보정값은 <b>그대로 저장값</b>입니다. */
    private Optional<String> decisionOf(FormAdapterContext context, String field) {
        return context.override(FormSheetKind.CAPITAL_OVERVIEW, null, field);
    }

    /** 보정값이 있으면 그 값을, 없으면 체크된 문구를 쉼표로 이어 씁니다. */
    private String decided(
            FormAdapterContext context, String field, Map<String, List<String>> values) {
        return decisionOf(context, field)
                .orElseGet(() -> CheckboxFieldReader.joined(values.get(field)));
    }

    /**
     * 항목 하나가 고른 값들을 읽습니다. 체크박스가 놓여 있으면 체크된 문구, 없으면 셀 값을 씁니다.
     *
     * <p>범위는 라벨 열부터 <b>같은 행 다음 라벨 직전까지</b>입니다. 한 행에 항목이 둘 놓이는 배치(`중복 여부 … 법규상 완료시기 …`)에서 앞 항목이 뒤
     * 항목의 체크박스까지 삼키지 않게 하는 경계입니다.
     */
    private List<String> optionsOf(Sheet sheet, List<FormCheckbox> checkboxes, String label) {
        Optional<FormLabelReader.Anchor> anchor = labelReader.findLabel(sheet, label);
        if (anchor.isEmpty()) return List.of();

        int row = anchor.get().rowIndex();
        int fromColumn = anchor.get().colIndex();
        int toColumn = labelReader.nextLabelColumn(sheet, row, fromColumn);
        if (CheckboxFieldReader.hasCheckbox(checkboxes, row, fromColumn, toColumn)) {
            return CheckboxFieldReader.checkedCaptions(checkboxes, row, fromColumn, toColumn);
        }
        String cellValue = labelReader.value(sheet, label);
        return hasText(cellValue) ? List.of(cellValue.trim()) : List.of();
    }

    /** 중복 여부는 `비중복(N)`·`중복(Y)` 표기라 괄호 안 문자를 우선 보고, 없으면 O/X 표기로 접습니다. */
    private static String duplicateYn(List<String> options) {
        String fromParenthesis = CheckboxFieldReader.toDuplicateYn(options);
        if (fromParenthesis != null) return fromParenthesis;
        return options.isEmpty() ? null : FormLexicon.toYn(options.get(0)).orElse(null);
    }

    /**
     * 법규상 완료시기 체크 결과를 안내로 남깁니다. <b>값은 반입하지 않습니다.</b>
     *
     * <p>물리 컬럼({@code FLF_FSG_DT})은 법규상 반드시 완료해야 하는 <b>날짜</b>인데 양식은 `2026년 이내`처럼 구간을 고르게 되어 있어 날짜로
     * 환산할 근거가 없습니다. 추정한 날짜를 법규 기한 칸에 넣으면 원장에 근거 없는 값이 남으므로, 사람이 상세 화면에서 채우도록 안내만 합니다.
     *
     * <p>`별도없음`은 "기한이 없다"는 <b>확정된 답</b>이므로 미기재 안내를 내지 않습니다.
     */
    private void applyCompletionDeadlineNotice(
            FormAdapterContext context,
            List<String> options,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (decisionOf(context, COMPLETION_DEADLINE_FIELD).isPresent()) return;
        if (options.isEmpty()) {
            diagnostics.add(
                    optionalDiagnostic(
                            COMPLETION_DEADLINE_FIELD,
                            "`법규상 완료시기` 항목이 비어 있습니다. 기한이 있으면 미리보기에서 연월일을 입력해 주세요.",
                            List.of(),
                            RequestFormDecisionKind.DATE));
            return;
        }
        if (options.stream().anyMatch(NO_DEADLINE_OPTION::equals)) return;
        diagnostics.add(
                optionalDiagnostic(
                        COMPLETION_DEADLINE_FIELD,
                        "`법규상 완료시기`가 `%s`로 체크되어 있습니다. 정확한 기한(연월일)을 입력해 주세요."
                                .formatted(String.join(", ", options)),
                        List.of(),
                        RequestFormDecisionKind.DATE));
    }

    /**
     * 고른 문구들 중 코드표에 맞는 <b>첫 값</b>의 코드를 돌려줍니다.
     *
     * <p>첫 문구가 아니라 첫 <b>매칭</b>을 쓰는 이유는 양식이 한 항목 안에 보조 체크박스를 끼워 넣기 때문입니다(추진가능성의 `유관부서검토 여부 : Y /
     * N`). 그 문구들은 코드표에 없으므로 자연히 건너뛰어집니다.
     */
    private String firstMatchingCode(
            FormAdapterContext context,
            String field,
            FormCatalogs catalogs,
            Map<String, List<String>> values,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> decision = decisionOf(context, field);
        if (decision.isPresent()) return decision.get();

        Map<String, String> catalog =
                REPORT_STATUS_FIELD.equals(field)
                        ? catalogs.reportStatusCodeByName()
                        : catalogs.exePttCodeByName();
        List<String> options = values.get(field);
        for (String option : options) {
            String code = catalog.get(FormLexicon.canonicalOptionName(option));
            if (code != null) return code;
        }
        if (!options.isEmpty()) {
            diagnostics.add(
                    optionalDiagnostic(
                            field,
                            "`%s`의 선택값 `%s`를 코드로 해석하지 못했습니다. 미리보기에서 골라 주세요."
                                    .formatted(
                                            OPTIONAL_FIELDS.get(field), String.join(", ", options)),
                            catalogs.optionCandidates(field),
                            RequestFormDecisionKind.SELECT));
        }
        return null;
    }

    private RequestFormDto.FormDiagnostic optionalDiagnostic(
            String field,
            String message,
            List<MigrationDto.Candidate> candidates,
            RequestFormDecisionKind decision) {
        return RequestFormDto.FormDiagnostic.decide(
                FormSheetKind.CAPITAL_OVERVIEW,
                null,
                field,
                null,
                RequestFormDiagnosticCode.OPTIONAL_MISSING,
                message,
                candidates,
                decision);
    }

    /**
     * 코드 카탈로그에서 이름으로 코드를 찾습니다.
     *
     * <p>키가 null이면 조회하지 않고 null을 돌려줍니다 — 카탈로그는 {@code Map.copyOf}로 만든 불변 맵이고 불변 맵은 null 키 조회에
     * {@code NullPointerException}을 던집니다. 양식의 해당 항목이 공란인 경우가 흔해서(실측 8항목 전부 공란) 이 방어가 없으면 파일 대부분이
     * 예외로 실패합니다.
     *
     * @param catalog 코드값명 별 코드
     * @param name 찾을 이름. null·공백이면 조회하지 않습니다
     * @return 코드. 이름이 없거나 카탈로그에 없으면 null
     */
    private static String lookup(Map<String, String> catalog, String name) {
        return hasText(name) ? catalog.get(name.trim()) : null;
    }

    /**
     * 주관 조직을 채웁니다.
     *
     * <p><b>부서와 부문/본부는 폴더명에 병기된 부서코드가 기준</b>입니다. 시트의 `주관부서/팀` 기재값은 조직 개편으로 낡거나(`IT인프라팀`) 상·하위 조직이
     * 함께 걸려 중의적이 되는 일이 잦아, 이름으로 조직을 찾는 대신 이미 확정된 부서코드에서 끌어옵니다. 부문/본부는 그 부서의 상위조직명입니다.
     *
     * <p><b>팀은 코드로 해석하지 않고 이름만</b> 담습니다. 팀코드(`SVN_TEM_C`)는 `CORGNI`에 없어 이름으로 찾을 수 없고, 팀명 컬럼
     * (`SVN_TEM_NM`)이 따로 있어 기재값을 그대로 보관하는 편이 손실이 없습니다.
     */
    private void applyOrganization(
            Sheet sheet, FormAdapterContext context, ProjectDto.CreateRequest project) {
        String deptCode = context.resolvedDeptCode();
        project.setSvnDpmC(deptCode);
        project.setPrlmHrkOgzCCone(context.orgIndex().parentOrgNameOf(deptCode));
        project.setSvnTemNm(teamNameOf(sheet));
    }

    /** `주관부서/팀` 기재값에서 `/` 뒤의 팀명만 떼어냅니다. 팀이 없으면 null. */
    private String teamNameOf(Sheet sheet) {
        String raw = labelReader.value(sheet, "주관부서/팀");
        if (!hasText(raw)) return null;
        String[] parts = raw.split("/", 2);
        if (parts.length < 2) return null;
        String team = parts[1].trim();
        return team.isEmpty() ? null : team;
    }

    private void applyPeople(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        project.setTlrUsid(personName(sheet, context, "팀장", "tlrUsid", diagnostics));
        project.setUsid(personName(sheet, context, "실무자(정/부)", "usid", diagnostics));
        project.setDvmTlrUsid(personName(sheet, context, "IT팀장", "dvmTlrUsid", diagnostics));
        project.setDvmUsid(personName(sheet, context, "IT실무자(정/부)", "dvmUsid", diagnostics));
    }

    /**
     * 담당자 칸에서 <b>이름만</b> 읽습니다. 사번으로 해석하지 않습니다.
     *
     * <p>담당자 컬럼(`USID`·`TLR_USID` 등)은 사번과 이름을 모두 받는 자리입니다. 부점이 적어 내는 이름은 인사 시스템의 표기와 어긋나거나 동명이인이라
     * 사번을 확정하지 못하는 경우가 많은데, 그때마다 파일을 차단하면 사람 이름 하나 때문에 사업 전체가 반입되지 못합니다. 이름을 그대로 담고 사번은 반입 후 상세
     * 화면에서 맞춥니다.
     *
     * <p>`실무자(정/부)`는 `허진성/장준호`처럼 둘이 적히며 <b>정(앞)만</b> 담습니다. 부담당자를 담을 컬럼이 없습니다.
     */
    private String personName(
            Sheet sheet,
            FormAdapterContext context,
            String label,
            String field,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override = context.override(FormSheetKind.CAPITAL_OVERVIEW, null, field);
        String raw = override.orElseGet(() -> labelReader.value(sheet, label));
        if (!hasText(raw)) return null;

        return FormPersonNames.fit(
                raw.split("/")[0], label, FormSheetKind.CAPITAL_OVERVIEW, diagnostics);
    }

    private void applyPeriod(
            Sheet sheet,
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        parseYearMonth(labelReader.value(sheet, "시작일자 (YY/MM)"), "sttDtm", diagnostics)
                .ifPresent(ym -> project.setSttDtm(ym.atDay(1)));
        parseYearMonth(labelReader.value(sheet, "종료일자 (YY/MM)"), "endDtm", diagnostics)
                .ifPresent(ym -> project.setEndDtm(ym.atEndOfMonth()));
    }

    /**
     * 전결권자 이름을 자본예산 계열 코드로 바꿉니다.
     *
     * <p>보정값을 먼저 봅니다 — 이름이 코드표에 없을 때 사람이 고를 길이 없으면 그 파일은 영구히 차단됩니다.
     *
     * <p>미매칭이면 후보를 실어 화면의 결정 열에서 바로 고르게 합니다. 차단(`BLOCKER`)은 유지합니다 — 전결권자를 아예 적지 않은 파일은 진단 없이 통과하지만,
     * <b>적어 냈는데</b> 코드표에 없는 것은 제출자가 특정 전결권자를 지정했다는 뜻이라 빈 값으로 넘기면 원장에 제출 의사와 다른 값이 남습니다.
     *
     * @param catalogs 코드 맵과 선택 후보를 담은 공통코드 묶음
     */
    private void applyDelegation(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            FormCatalogs catalogs,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.CAPITAL_OVERVIEW, null, "edrtTc");
        if (override.isPresent()) {
            project.setEdrtTc(override.get());
            return;
        }

        String name = labelReader.value(sheet, "전결권자");
        if (!hasText(name)) return;
        // 부점은 `수석부행장` 같은 통칭을 쓰고 코드표는 직명(`전무이사`)을 쓴다
        String canonicalName =
                SheetAnchorScanner.normalize(name).contains("본부장")
                        ? "지역본부장"
                        : FormLexicon.canonicalOptionName(name);
        String code = lookup(catalogs.edrtCapitalCodeByName(), canonicalName);
        if (code != null) {
            project.setEdrtTc(code);
            return;
        }
        diagnostics.add(
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        "edrtTc",
                        RequestFormDiagnosticCode.CODE_UNRESOLVED,
                        "전결권자 `%s`에 해당하는 코드를 찾지 못했습니다. 공통코드에서 골라 주세요.".formatted(name),
                        catalogs.optionCandidates("edrtTc")));
    }

    /**
     * 1-1이 선언한 금액 3종을 읽습니다.
     *
     * <p>요약표를 못 찾으면 두 컬럼이 null이 되고, 호출자가 그것을 "산출 불가" 신호로 씁니다.
     *
     * @param sheet 1-1 시트
     * @return 선언 금액. 어느 값도 못 읽으면 필드가 모두 비어 있습니다
     */
    private DeclaredAmounts declaredAmounts(Sheet sheet) {
        Optional<Integer> totalRow =
                scanner.findLabelRow(sheet, new int[] {0, 2}, "총 계", "총계", "계");
        BigDecimal yearTotal =
                totalRow.map(row -> summaryColumn(sheet, row, YEAR_TOTAL_SUFFIX)).orElse(null);
        BigDecimal laterTotal =
                totalRow.map(row -> summaryColumn(sheet, row, LATER_TOTAL_SUFFIX)).orElse(null);
        SummaryTable summary =
                totalRow.map(row -> summaryTable(sheet, row)).orElseGet(SummaryTable::empty);
        return wholePeriod(sheet, yearTotal, laterTotal, summary.unit(), summary.items());
    }

    /** 1-1 요약표의 비목별 행과 표에 명시된 금액 단위를 읽습니다. */
    private SummaryTable summaryTable(Sheet sheet, int totalRow) {
        HeaderCell yearHeader = findHeader(sheet, totalRow, YEAR_TOTAL_SUFFIX);
        if (yearHeader == null) return SummaryTable.empty();

        HeaderCell laterHeader = findHeader(sheet, totalRow, LATER_TOTAL_SUFFIX);
        int groupColumn = findGroupColumn(sheet, yearHeader.row());
        AmountUnit unit = findSummaryUnit(sheet, yearHeader.row());
        List<SummaryItem> items = new ArrayList<>();
        for (int rowIndex = yearHeader.row() + 1; rowIndex < totalRow; rowIndex++) {
            String group = scanner.text(sheet, rowIndex, groupColumn);
            if (!hasText(group)) continue;
            String normalizedGroup = SheetAnchorScanner.normalize(group);
            if ("총계".equals(normalizedGroup)
                    || "소계".equals(normalizedGroup)
                    || "계".equals(normalizedGroup)) continue;

            BigDecimal amount = parseAmount(scanner.text(sheet, rowIndex, yearHeader.column()));
            BigDecimal later =
                    laterHeader == null
                            ? null
                            : parseAmount(scanner.text(sheet, rowIndex, laterHeader.column()));
            BigDecimal currentValue = amount == null ? BigDecimal.ZERO : amount;
            BigDecimal laterValue = later == null ? BigDecimal.ZERO : later;
            if (currentValue.signum() == 0 && laterValue.signum() == 0) continue;
            items.add(new SummaryItem(rowIndex + 1, group.trim(), currentValue, laterValue));
        }
        return new SummaryTable(unit, List.copyOf(items));
    }

    /** 연도 합계·연도 이후 헤더의 좌표를 찾습니다. */
    private HeaderCell findHeader(Sheet sheet, int totalRow, String suffix) {
        for (int rowIndex = 0; rowIndex < totalRow; rowIndex++) {
            for (int colIndex = 0; colIndex <= TOTAL_SCAN_WIDTH; colIndex++) {
                String header =
                        SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, colIndex));
                if (header.endsWith(suffix)) return new HeaderCell(rowIndex, colIndex);
            }
        }
        return null;
    }

    /** 같은 헤더 행에서 `비목` 열을 찾고, 변형 양식이면 통상 위치인 B열을 사용합니다. */
    private int findGroupColumn(Sheet sheet, int headerRow) {
        for (int colIndex = 0; colIndex <= TOTAL_SCAN_WIDTH; colIndex++) {
            String header = SheetAnchorScanner.normalize(scanner.text(sheet, headerRow, colIndex));
            if ("비목".equals(header)) return colIndex;
        }
        return 1;
    }

    /** 요약표 제목·헤더에 명시된 원/천원/백만원 단위를 읽습니다. */
    private AmountUnit findSummaryUnit(Sheet sheet, int headerRow) {
        // 요약표 자체의 표기를 우선하고, 없을 때만 바로 위의 총액·제목 행까지 넓힙니다.
        for (int offset = 0; offset <= 2; offset++) {
            int rowIndex = headerRow - offset;
            if (rowIndex < 0) break;
            for (int colIndex = 0; colIndex <= TOTAL_SCAN_WIDTH; colIndex++) {
                String text = scanner.text(sheet, rowIndex, colIndex);
                if (!hasText(text)) continue;
                if (text.contains("백만원")) return AmountUnit.MILLION;
                if (text.contains("천원")) return AmountUnit.THOUSAND;
                String normalized = SheetAnchorScanner.normalize(text);
                if ("원".equals(normalized) || normalized.contains("단위원")) {
                    return AmountUnit.WON;
                }
            }
        }
        return null;
    }

    /**
     * 요약표 컬럼 하나를 읽습니다.
     *
     * <p>`총 계` 행이 제출자가 확정한 값이므로 먼저 봅니다. 그 칸이 비어 있으면 헤더 아래 데이터 행을 더해 보완합니다 — 실측 제출본에 `'26년도 이후`만 총 계
     * 행이 빈 파일이 있어, 총 계 행만 보면 그 값을 통째로 놓칩니다.
     *
     * @param sheet 1-1 시트
     * @param totalRow `총 계` 행의 0-based 행 번호
     * @param headerSuffix 찾을 헤더의 정규화 접미사
     * @return 기재값. 헤더를 못 찾거나 총 계 행·데이터 행에 숫자가 하나도 없으면 null
     */
    private BigDecimal summaryColumn(Sheet sheet, int totalRow, String headerSuffix) {
        for (int rowIndex = 0; rowIndex < totalRow; rowIndex++) {
            for (int colIndex = 0; colIndex <= TOTAL_SCAN_WIDTH; colIndex++) {
                String header =
                        SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, colIndex));
                if (!header.endsWith(headerSuffix)) continue;
                BigDecimal declared = parseAmount(scanner.text(sheet, totalRow, colIndex));
                return declared != null
                        ? declared
                        : sumDataRows(sheet, rowIndex, totalRow, colIndex);
            }
        }
        return null;
    }

    /**
     * 헤더 다음 행부터 총 계 행 직전까지 같은 열을 더합니다.
     *
     * @param sheet 1-1 시트
     * @param headerRow 헤더 행 번호
     * @param totalRow `총 계` 행 번호
     * @param colIndex 더할 열 번호
     * @return 합계. 숫자가 하나도 없으면 null (0과 구분해야 "기재 없음"을 판정할 수 있습니다)
     */
    private BigDecimal sumDataRows(Sheet sheet, int headerRow, int totalRow, int colIndex) {
        BigDecimal sum = null;
        for (int rowIndex = headerRow + 1; rowIndex < totalRow; rowIndex++) {
            BigDecimal value = parseAmount(scanner.text(sheet, rowIndex, colIndex));
            if (value == null) continue;
            sum = sum == null ? value : sum.add(value);
        }
        return sum;
    }

    /**
     * `총 사업금액(전체기간)` 칸을 읽어 선언 금액을 완성합니다.
     *
     * <p>숫자 뒤에 붙은 단위 접미사를 인식하면 그 자리에서 원 단위로 폅니다. 접미사가 없으면 숫자만 남겨 호출자가 요약표 배수를 적용하게 하고, 모르는 접미사거나
     * 숫자가 아니면 폴백을 금지하는 신호를 세웁니다.
     *
     * @param sheet 1-1 시트
     * @param yearTotal 요약표 `'26년도 합계` 기재값
     * @param laterTotal 요약표 `'26년도 이후` 기재값
     * @return 선언 금액
     */
    private DeclaredAmounts wholePeriod(
            Sheet sheet,
            BigDecimal yearTotal,
            BigDecimal laterTotal,
            AmountUnit summaryUnit,
            List<SummaryItem> summaryItems) {
        String raw = labelReader.value(sheet, "총 사업금액(전체기간)");
        if (!hasText(raw)) {
            return new DeclaredAmounts(
                    null, null, false, yearTotal, laterTotal, summaryUnit, summaryItems);
        }
        String trimmed = raw.trim();
        for (Map.Entry<String, AmountUnit> suffix : WHOLE_PERIOD_SUFFIXES) {
            if (!trimmed.endsWith(suffix.getKey())) continue;
            BigDecimal number =
                    parseAmount(trimmed.substring(0, trimmed.length() - suffix.getKey().length()));
            return number == null
                    ? new DeclaredAmounts(
                            null, null, true, yearTotal, laterTotal, summaryUnit, summaryItems)
                    : new DeclaredAmounts(
                            suffix.getValue().toWon(number),
                            null,
                            false,
                            yearTotal,
                            laterTotal,
                            summaryUnit,
                            summaryItems);
        }
        BigDecimal number = parseAmount(trimmed);
        return number == null
                ? new DeclaredAmounts(
                        null, null, true, yearTotal, laterTotal, summaryUnit, summaryItems)
                : new DeclaredAmounts(
                        null, number, false, yearTotal, laterTotal, summaryUnit, summaryItems);
    }

    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw == null ? "" : raw.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Optional<YearMonth> parseYearMonth(
            String raw, String field, List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (!hasText(raw)) return Optional.empty();
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(raw.trim());
        if (!matcher.find()) {
            diagnostics.add(
                    RequestFormDto.FormDiagnostic.of(
                            FormSheetKind.CAPITAL_OVERVIEW,
                            null,
                            field,
                            RequestFormDiagnosticCode.DATE_UNPARSEABLE,
                            "`%s` 표기를 날짜로 해석하지 못했습니다.".formatted(raw),
                            List.of()));
            return Optional.empty();
        }
        int year = 2000 + Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (month < 1 || month > 12) return Optional.empty();
        return Optional.of(YearMonth.of(year, month));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, String> optionalFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bzDttNm", "업무구분");
        fields.put("bzTpC", "사업유형");
        fields.put("sklTpTc", "디지털 기술 유형");
        fields.put("cstTpTc", "주 사용자");
        fields.put("dplYn", "중복 여부");
        fields.put("flfFsgDt", "법규상 완료시기");
        fields.put("rprStsTc", "최종보고");
        fields.put("exePttYn", "추진가능성");
        return Map.copyOf(fields);
    }

    /**
     * 1-1 읽기 결과입니다.
     *
     * @param project 사업 생성 요청 (품목 미포함 — 어댑터가 1-2 또는 1-1 요약표에서 채웁니다)
     * @param diagnostics 해석 진단
     * @param amounts 1-1이 선언한 금액. 단위가 확정되지 않은 값이 섞여 있습니다
     */
    public record Result(
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics,
            DeclaredAmounts amounts) {}

    /**
     * 1-1이 선언한 금액입니다. <b>요약표 두 값은 단위가 확정되지 않은 기재값 그대로</b>입니다.
     *
     * <p>요약표 기재값은 raw로 넘기고, 표 제목의 명시 단위도 별도로 보존합니다. 1-2가 있으면 어댑터가 품목 합계 대사를 우선하고, 1-2가 없으면 명시 단위로
     * 비목별 행을 원 단위로 환산합니다.
     *
     * <p>반면 `총 사업금액(전체기간)`은 표 헤더에 딸린 칸이 아니라 제출자가 숫자와 단위를 함께 적는 자유 텍스트라, 적힌 접미사가 그 칸의 유일한 근거입니다. 그래서
     * 여기서 원 단위로 확정합니다.
     *
     * @param wholePeriodWon 접미사로 원 단위가 확정된 총 사업금액. 접미사가 없거나 해석에 실패하면 null
     * @param wholePeriodRaw 접미사가 없을 때의 기재 숫자. 호출자가 요약표 배수를 곱해 씁니다
     * @param wholePeriodUnknownUnit 값은 있으나 숫자·단위로 해석하지 못했으면 true. <b>배수 폴백을 금지하는 신호</b>입니다
     * @param yearTotalRaw 요약표 `'26년도 합계` 기재값 (단위 미확정). 요약표를 못 찾으면 null
     * @param laterTotalRaw 요약표 `'26년도 이후` 기재값 (단위 미확정). 기재가 없으면 null
     * @param summaryUnit 요약표 제목에 명시된 단위. 없으면 null
     * @param summaryItems 요약표의 비목별 금액 행
     */
    public record DeclaredAmounts(
            BigDecimal wholePeriodWon,
            BigDecimal wholePeriodRaw,
            boolean wholePeriodUnknownUnit,
            BigDecimal yearTotalRaw,
            BigDecimal laterTotalRaw,
            AmountUnit summaryUnit,
            List<SummaryItem> summaryItems) {}

    /** 1-1 요약표의 비목별 금액 행입니다. 금액은 아직 표 기재 단위입니다. */
    public record SummaryItem(
            int excelRow, String group, BigDecimal amountRaw, BigDecimal laterAmountRaw) {}

    /** 요약표 내부 파싱 결과입니다. */
    private record SummaryTable(AmountUnit unit, List<SummaryItem> items) {
        private static SummaryTable empty() {
            return new SummaryTable(null, List.of());
        }
    }

    /** 요약표 헤더 셀 좌표입니다. */
    private record HeaderCell(int row, int column) {}
}
