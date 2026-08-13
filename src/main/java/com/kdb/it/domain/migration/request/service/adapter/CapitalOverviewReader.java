package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    /** `2026.02.28`·`202602` 등 완료기한 표기. */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})\\D?(\\d{1,2})\\D?(\\d{1,2})?");

    /** 요약표에서 `'26년도 합계` 열을 찾을 때 훑는 최대 열 수. */
    private static final int TOTAL_SCAN_WIDTH = 15;

    private final SheetAnchorScanner scanner;
    private final FormLabelReader labelReader;

    /**
     * 1-1 시트를 읽어 사업 생성 요청을 만듭니다.
     *
     * @param sheet 1-1 시트
     * @param context 어댑터 실행 맥락
     * @param exePttCodes 추진가능성 코드값명 별 코드
     * @param edrtCodes 전결권 자본예산 계열 코드값명 별 코드
     * @return 사업 요청, 진단, 1-1 요약표의 `'26년도 합계` 기재값
     */
    public Result read(
            Sheet sheet,
            FormAdapterContext context,
            Map<String, String> exePttCodes,
            Map<String, String> edrtCodes) {
        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();

        project.setBseYy(context.bseYy());
        project.setOdnYn("N");
        project.setAbusTc(CodeDefaults.NOT_APPLICABLE);
        project.setAbusNm(labelReader.value(sheet, "사업명"));
        project.setAbusCone(labelReader.value(sheet, "(개요)"));
        project.setCpnSafCone(labelReader.value(sheet, "(현황)"));
        project.setAbusNcsCone(labelReader.value(sheet, "(필요성)"));
        project.setDgogPpoCone(labelReader.value(sheet, "(기대효과)"));
        project.setPlmDes(labelReader.value(sheet, "(미추진시 문제점)"));
        project.setAbusRngCone(labelReader.multiRowValue(sheet, "사업 범위 (전산 요구사항)", MULTI_ROW_SPAN));
        project.setMnPrgCone(labelReader.multiRowValue(sheet, "추진경과", MULTI_ROW_SPAN));
        project.setHrfPlnCone(labelReader.multiRowValue(sheet, "향후계획", MULTI_ROW_SPAN));
        project.setPrlmHrkOgzCCone(labelReader.value(sheet, "주관부문/본부"));

        applyOptionalFields(sheet, project, exePttCodes, diagnostics);
        applyOrganization(sheet, context, project, diagnostics);
        applyPeople(sheet, context, project, diagnostics);
        applyPeriod(sheet, project, diagnostics);
        applyDelegation(sheet, context, project, edrtCodes, diagnostics);

        return new Result(project, List.copyOf(diagnostics), declaredYearTotal(sheet));
    }

    private void applyOptionalFields(
            Sheet sheet,
            ProjectDto.CreateRequest project,
            Map<String, String> exePttCodes,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Map<String, String> values = new LinkedHashMap<>();
        OPTIONAL_FIELDS.forEach(
                (field, label) -> values.put(field, labelReader.value(sheet, label)));
        values.forEach(
                (field, value) -> {
                    if (hasText(value)) return;
                    diagnostics.add(
                            RequestFormDto.FormDiagnostic.of(
                                    FormSheetKind.CAPITAL_OVERVIEW,
                                    null,
                                    field,
                                    RequestFormDiagnosticCode.OPTIONAL_MISSING,
                                    "`%s` 항목이 비어 있습니다. 반입 후 사업 상세 화면에서 채울 수 있습니다."
                                            .formatted(OPTIONAL_FIELDS.get(field)),
                                    List.of()));
                });

        // 공통코드 코드값명을 그대로 저장하는 항목들 (Bprojm 필드 주석 참고)
        project.setBzDttNm(values.get("bzDttNm"));
        project.setBzTpC(values.get("bzTpC"));
        project.setSklTpTc(values.get("sklTpTc"));
        project.setCstTpTc(values.get("cstTpTc"));
        if (hasText(values.get("dplYn"))) {
            project.setDplYn(FormLexicon.toYn(values.get("dplYn")).orElse(null));
        }
        project.setFlfFsgDt(toYyyyMmDd(values.get("flfFsgDt")));
        project.setRprStsTc(values.get("rprStsTc"));
        project.setExePttYn(lookup(exePttCodes, values.get("exePttYn")));
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

    private void applyOrganization(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        String raw = labelReader.value(sheet, "주관부서/팀");
        if (!hasText(raw)) {
            // 폼에 없으면 폴더명으로 확정한 부서를 쓴다
            project.setSvnDpmC(context.resolvedDeptCode());
            return;
        }
        String[] parts = raw.split("/", 2);
        project.setSvnDpmC(
                resolveOrg(context, parts[0].trim(), "svnDpmC", diagnostics)
                        .orElse(context.resolvedDeptCode()));
        if (parts.length == 2) {
            resolveOrg(context, parts[1].trim(), "svnTemC", diagnostics)
                    .ifPresent(project::setSvnTemC);
        }
    }

    private Optional<String> resolveOrg(
            FormAdapterContext context,
            String name,
            String field,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override = context.override(FormSheetKind.CAPITAL_OVERVIEW, null, field);
        if (override.isPresent()) return override;

        OrgIdentityResolver.Resolution resolution = context.orgIndex().resolveOrg(name);
        if (resolution.code() != null) return Optional.of(resolution.code());

        diagnostics.add(
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        field,
                        resolution.isAmbiguous()
                                ? RequestFormDiagnosticCode.ORG_AMBIGUOUS
                                : RequestFormDiagnosticCode.ORG_UNRESOLVED,
                        "조직 `%s`를 확정하지 못했습니다.".formatted(name),
                        resolution.candidates()));
        return Optional.empty();
    }

    private void applyPeople(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        String deptHint = project.getSvnDpmC();
        resolveUser(sheet, context, "팀장", "tlrUsid", deptHint, diagnostics)
                .ifPresent(project::setTlrUsid);
        resolveUser(sheet, context, "실무자(정/부)", "usid", deptHint, diagnostics)
                .ifPresent(project::setUsid);
        resolveUser(sheet, context, "IT팀장", "dvmTlrUsid", null, diagnostics)
                .ifPresent(project::setDvmTlrUsid);
        resolveUser(sheet, context, "IT실무자(정/부)", "dvmUsid", null, diagnostics)
                .ifPresent(project::setDvmUsid);
    }

    /**
     * 담당자 칸을 읽어 사번을 해석합니다.
     *
     * <p>`실무자(정/부)`는 `허진성/장준호`처럼 두 사람이 적힙니다. `BPROJM`에 부(뒤) 담당자를 담을 컬럼이 없어 정(앞)만 저장하고 미적재 경고를 남깁니다.
     * 조용히 버리면 나중에 담당자가 왜 한 명뿐인지 아무도 설명하지 못합니다.
     */
    private Optional<String> resolveUser(
            Sheet sheet,
            FormAdapterContext context,
            String label,
            String field,
            String deptHint,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override = context.override(FormSheetKind.CAPITAL_OVERVIEW, null, field);
        if (override.isPresent()) return override;

        String raw = labelReader.value(sheet, label);
        if (!hasText(raw)) return Optional.empty();

        String[] parts = raw.split("/");
        if (parts.length > 1) {
            diagnostics.add(
                    RequestFormDto.FormDiagnostic.of(
                            FormSheetKind.CAPITAL_OVERVIEW,
                            null,
                            field,
                            RequestFormDiagnosticCode.SUBSTITUTE_DROPPED,
                            "`%s`의 부담당자 `%s`는 담을 컬럼이 없어 반입하지 않습니다."
                                    .formatted(label, parts[1].trim()),
                            List.of()));
        }
        String primary = parts[0].trim();
        OrgIdentityResolver.Resolution resolution =
                context.orgIndex().resolveUser(primary, deptHint);
        if (resolution.code() != null) return Optional.of(resolution.code());

        diagnostics.add(
                RequestFormDto.FormDiagnostic.of(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        field,
                        resolution.isAmbiguous()
                                ? RequestFormDiagnosticCode.USER_AMBIGUOUS
                                : RequestFormDiagnosticCode.USER_UNRESOLVED,
                        "`%s`의 담당자 `%s`를 확정하지 못했습니다.".formatted(label, primary),
                        resolution.candidates()));
        return Optional.empty();
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
     */
    private void applyDelegation(
            Sheet sheet,
            FormAdapterContext context,
            ProjectDto.CreateRequest project,
            Map<String, String> edrtCodes,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.CAPITAL_OVERVIEW, null, "edrtTc");
        if (override.isPresent()) {
            project.setEdrtTc(override.get());
            return;
        }

        String name = labelReader.value(sheet, "전결권자");
        if (!hasText(name)) return;
        String code = lookup(edrtCodes, name);
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
                        "전결권자 `%s`에 해당하는 코드를 찾지 못했습니다.".formatted(name),
                        List.of()));
    }

    /**
     * 1-1 요약표의 `'26년도 합계` 기재값을 읽습니다.
     *
     * <p>적재하지 않고 1-2 품목 합계와 대사하는 데만 씁니다. 요약표를 읽지 못하면 null을 돌려주고 호출자가 대사를 건너뜁니다.
     */
    private BigDecimal declaredYearTotal(Sheet sheet) {
        Optional<Integer> totalRow = scanner.findLabelRow(sheet, new int[] {0, 2}, "총 계", "총계");
        if (totalRow.isEmpty()) return null;

        for (int rowIndex = 0; rowIndex < totalRow.get(); rowIndex++) {
            for (int colIndex = 0; colIndex <= TOTAL_SCAN_WIDTH; colIndex++) {
                String header =
                        SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, colIndex));
                if (!header.endsWith("년도합계")) continue;
                return parseAmount(scanner.text(sheet, totalRow.get(), colIndex));
            }
        }
        return null;
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

    /** `2026.02`·`20260228` 같은 표기를 `YYYYMMDD`로 폅니다. 해석 못 하면 null. */
    private String toYyyyMmDd(String raw) {
        if (!hasText(raw)) return null;
        Matcher matcher = DATE_PATTERN.matcher(raw.trim());
        if (!matcher.find()) return null;
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (month < 1 || month > 12) return null;
        int day =
                matcher.group(3) == null
                        ? YearMonth.of(year, month).lengthOfMonth()
                        : Integer.parseInt(matcher.group(3));
        if (day < 1 || day > YearMonth.of(year, month).lengthOfMonth()) return null;
        return LocalDate.of(year, month, day).format(DateTimeFormatter.BASIC_ISO_DATE);
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
     * @param project 사업 생성 요청 (품목 미포함 — 어댑터가 1-2에서 채웁니다)
     * @param diagnostics 해석 진단
     * @param declaredYearTotal 1-1 요약표의 `'26년도 합계` 기재값. 없으면 null
     */
    public record Result(
            ProjectDto.CreateRequest project,
            List<RequestFormDto.FormDiagnostic> diagnostics,
            BigDecimal declaredYearTotal) {}
}
