package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDecisionKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 ② `2. 경상적인 사업`을 경상사업 생성 요청으로 바꿉니다.
 *
 * <p>부서 열이 시트에 없어 폴더명에서 온 부서코드를 씁니다. 기간은 예산연도 전체(`1월 1일 ~ 12월 31일`)로 둡니다 — 경상사업은 연중 상시 집행이라 양식에
 * 시작·종료일 칸이 없습니다.
 *
 * <p>런던 제출본처럼 사업명이 공란인 파일이 실제로 있습니다. 그때는 사업을 만들지 않고 `REQUIRED_MISSING`을 내 미리보기에서 채우게 합니다.
 */
@Component
@RequiredArgsConstructor
public class RecurringProjectFormAdapter implements FormSheetAdapter {

    private final FormLabelReader labelReader;
    private final ResourceTableReader resourceTableReader;
    private final FormApproverReader approverReader;

    @Override
    public FormSheetKind trigger() {
        return FormSheetKind.RECURRING;
    }

    @Override
    public FormAdapterOutput adapt(FormAdapterContext context) {
        Sheet sheet = context.sheets().get(FormSheetKind.RECURRING);
        if (sheet == null) return FormAdapterOutput.empty();

        Optional<ResourceTableReader.Result> table = resourceTableReader.readRecurring(sheet);
        boolean hasResources = table.isPresent() && !table.get().rows().isEmpty();
        String projectName = resolveProjectName(sheet, context);

        // 사업명도 없고 소요자원도 없으면 부점이 이 시트를 쓰지 않은 것이다. 진단 없이 건너뛴다.
        if (projectName == null && !hasResources) return FormAdapterOutput.empty();

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        if (projectName == null) {
            diagnostics.add(
                    RequestFormDto.FormDiagnostic.decide(
                            FormSheetKind.RECURRING,
                            null,
                            "abusNm",
                            null,
                            RequestFormDiagnosticCode.REQUIRED_MISSING,
                            "경상사업의 사업명이 비어 있습니다. 미리보기에서 입력해 주세요.",
                            List.of(),
                            RequestFormDecisionKind.TEXT));
            return new FormAdapterOutput(List.of(), List.of(), List.copyOf(diagnostics), null);
        }

        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm(projectName);
        project.setBseYy(context.bseYy());
        project.setOdnYn("Y");
        project.setAbusTc(CodeDefaults.NOT_APPLICABLE);
        project.setSvnDpmC(context.resolvedDeptCode());
        project.setSttDtm(LocalDate.of(Integer.parseInt(context.bseYy()), 1, 1));
        project.setEndDtm(LocalDate.of(Integer.parseInt(context.bseYy()), 12, 31));
        project.setAbusCone(labelReader.value(sheet, "(개요)"));
        project.setCpnSafCone(labelReader.value(sheet, "(현황)"));
        project.setAbusRngCone(labelReader.value(sheet, "(추진내용)"));
        project.setPlmDes(labelReader.value(sheet, "(미추진시 문제점)"));
        // 이 시트에는 `관련 조직` 블록이 없다. 상단 머리말의 확인자·작성자가 주관팀장·담당자다
        project.setTlrUsid(
                FormPersonNames.fit(
                        approverReader.confirmer(sheet),
                        "확인자",
                        FormSheetKind.RECURRING,
                        diagnostics));
        project.setUsid(
                FormPersonNames.fit(
                        approverReader.author(sheet), "작성자", FormSheetKind.RECURRING, diagnostics));

        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        if (hasResources) {
            int sno = 1;
            for (ResourceRow row : table.get().rows()) {
                ProjectDto.BitemmDto item =
                        ResourceTableReader.toItem(
                                row,
                                resolveIoe(row, context, diagnostics),
                                sno++,
                                context.bseYy());
                item.setCncdFdtnCone(row.remarks());
                items.add(item);
            }
        }
        project.setItems(items);

        return new FormAdapterOutput(List.of(project), List.of(), List.copyOf(diagnostics), null);
    }

    private String resolveIoe(
            ResourceRow row,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.RECURRING, row.excelRow(), "ioeC");
        if (override.isPresent() && context.ioeIndex().exists(override.get()))
            return override.get();

        boolean domestic = !context.foreignBranch();
        IoeHierarchyIndex.Resolution resolution =
                context.ioeIndex().resolveByGroup(row.group(), domestic);
        if (resolution.code() != null) {
            if (!resolution.candidates().isEmpty()) {
                // 기본값으로 정했지만 대안이 있다. 반영은 막지 않고 확인만 요청한다.
                diagnostics.add(
                        RequestFormDto.FormDiagnostic.about(
                                FormSheetKind.RECURRING,
                                row.excelRow(),
                                "ioeC",
                                row.itemName(),
                                RequestFormDiagnosticCode.CODE_DEFAULTED,
                                "품목 `%s`(구분 `%s`)의 비목을 `%s`로 기본 설정했습니다. 다른 비목이면 골라 주세요."
                                        .formatted(row.itemName(), row.group(), resolution.label()),
                                IoeCandidates.orAll(resolution, context)));
            }
            return resolution.code();
        }

        diagnostics.add(
                RequestFormDto.FormDiagnostic.about(
                        FormSheetKind.RECURRING,
                        row.excelRow(),
                        "ioeC",
                        row.itemName(),
                        resolution.isAmbiguous()
                                ? RequestFormDiagnosticCode.CODE_AMBIGUOUS
                                : RequestFormDiagnosticCode.CODE_UNRESOLVED,
                        "구분 `%s`의 비목을 정하지 못했습니다.".formatted(row.group()),
                        IoeCandidates.orAll(resolution, context)));
        return null;
    }

    /** 보정값을 우선하고, 없으면 시트의 사업명을 씁니다. 둘 다 없으면 null. */
    private String resolveProjectName(Sheet sheet, FormAdapterContext context) {
        Optional<String> override = context.override(FormSheetKind.RECURRING, null, "abusNm");
        if (override.isPresent()) return FormText.singleLineName(override.get());

        String fromSheet = labelReader.value(sheet, "사업명");
        return fromSheet == null || fromSheet.isBlank()
                ? null
                : FormText.singleLineName(fromSheet);
    }
}
