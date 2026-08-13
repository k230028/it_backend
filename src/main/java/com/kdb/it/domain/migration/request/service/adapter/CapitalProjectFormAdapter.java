package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.AmountUnitResolver;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 시트 ① 1-1·1-2를 합쳐 정보화사업 생성 요청을 만듭니다.
 *
 * <p>두 시트가 같은 사업의 머리와 몸통이라 어댑터 하나가 둘을 함께 읽습니다. 1-2는 자본예산 블록과 일반관리비 블록이 위아래로 놓여 있어 소요자원 리더를 두 번
 * 호출하고, 두 블록의 품목을 <b>같은 사업의 {@code BITEMM}</b>으로 담습니다 — 양식 주석("정보화사업에 포함된 일반관리비는 1-1·1-2 시트에 작성")과
 * 실측 {@code BITEMM}에 일반관리비 비목이 들어 있는 사실이 이를 뒷받침합니다.
 *
 * <p>금액은 1-2가 원본입니다. 1-1 요약표는 단위가 부점마다 달라 적재하지 않고 배수를 역추정해 대사만 합니다.
 */
@Component
@RequiredArgsConstructor
public class CapitalProjectFormAdapter implements FormSheetAdapter {

    private final CapitalOverviewReader overviewReader;
    private final ResourceTableReader resourceTableReader;
    private final MigrationIoeCatalogReader catalogReader;

    @Override
    public FormSheetKind trigger() {
        return FormSheetKind.CAPITAL_OVERVIEW;
    }

    @Override
    public FormAdapterOutput adapt(FormAdapterContext context) {
        Sheet overview = context.sheets().get(FormSheetKind.CAPITAL_OVERVIEW);
        if (overview == null) return FormAdapterOutput.empty();

        CapitalOverviewReader.Result read =
                overviewReader.read(
                        overview,
                        context,
                        catalogReader.exePttCodeByName(),
                        catalogReader.edrtCapitalCodeByName());
        ProjectDto.CreateRequest project = read.project();
        if (project.getAbusNm() == null || project.getAbusNm().isBlank()) {
            // 1-1 시트가 빈 껍데기인 파일(경상사업·일반관리비만 낸 부점)이라 진단 없이 건너뛴다
            return FormAdapterOutput.empty();
        }

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>(read.diagnostics());
        List<ProjectDto.BitemmDto> items = readItems(context, diagnostics);
        project.setItems(items);

        reconcileTotals(read.declaredYearTotal(), items, diagnostics);

        return new FormAdapterOutput(List.of(project), List.of(), List.copyOf(diagnostics), null);
    }

    /** 1-2의 자본예산·일반관리비 두 블록을 순서대로 읽어 품목 목록을 만듭니다. */
    private List<ProjectDto.BitemmDto> readItems(
            FormAdapterContext context, List<RequestFormDto.FormDiagnostic> diagnostics) {
        Sheet resource = context.sheets().get(FormSheetKind.CAPITAL_RESOURCE);
        List<ProjectDto.BitemmDto> items = new ArrayList<>();
        if (resource == null) return items;

        int sno = 1;
        Optional<ResourceTableReader.Result> capital = resourceTableReader.read(resource, 0, false);
        if (capital.isPresent()) {
            for (ResourceRow row : capital.get().rows()) {
                items.add(toItem(row, context, sno++, diagnostics));
            }
        }
        int nextFrom = capital.map(result -> result.headerRow() + 1).orElse(0);
        Optional<ResourceTableReader.Result> general =
                resourceTableReader.read(resource, nextFrom, true);
        if (general.isPresent()) {
            for (ResourceRow row : general.get().rows()) {
                items.add(toItem(row, context, sno++, diagnostics));
            }
        }
        return items;
    }

    private ProjectDto.BitemmDto toItem(
            ResourceRow row,
            FormAdapterContext context,
            int sno,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        return ResourceTableReader.toItem(
                row, resolveIoe(row, context, diagnostics), sno, context.bseYy());
    }

    private String resolveIoe(
            ResourceRow row,
            FormAdapterContext context,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        Optional<String> override =
                context.override(FormSheetKind.CAPITAL_RESOURCE, row.excelRow(), "ioeC");
        if (override.isPresent() && context.ioeIndex().exists(override.get()))
            return override.get();

        boolean domestic = "KRW".equalsIgnoreCase(row.currency());
        IoeHierarchyIndex.Resolution resolution =
                context.ioeIndex().resolveByGroup(row.group(), domestic);

        if (resolution.code() == null) {
            diagnostics.add(
                    itemDiagnostic(
                            row,
                            resolution.isAmbiguous()
                                    ? RequestFormDiagnosticCode.CODE_AMBIGUOUS
                                    : RequestFormDiagnosticCode.CODE_UNRESOLVED,
                            "품목 구분 `%s`의 비목을 정하지 못했습니다.".formatted(row.group()),
                            resolution));
            return null;
        }
        if (!resolution.candidates().isEmpty()) {
            // 기본값으로 정했지만 대안이 있는 경우다. 그대로 반입하면 틀릴 수 있어 확인을 요청한다.
            diagnostics.add(
                    itemDiagnostic(
                            row,
                            RequestFormDiagnosticCode.CODE_AMBIGUOUS,
                            "품목 구분 `%s`는 `%s`로 기본 설정했습니다. 다른 비목이면 골라 주세요."
                                    .formatted(row.group(), resolution.code()),
                            resolution));
        }
        return resolution.code();
    }

    private RequestFormDto.FormDiagnostic itemDiagnostic(
            ResourceRow row,
            RequestFormDiagnosticCode code,
            String message,
            IoeHierarchyIndex.Resolution resolution) {
        return RequestFormDto.FormDiagnostic.of(
                FormSheetKind.CAPITAL_RESOURCE,
                row.excelRow(),
                "ioeC",
                code,
                message,
                resolution.candidates());
    }

    /**
     * 1-1 요약표와 1-2 품목 합계를 대사합니다.
     *
     * <p>어느 배수로도 맞지 않으면 단위 문제가 아니라 기재 오류이므로 경고를 냅니다. 요약표를 읽지 못했으면 대사할 수 없어 조용히 넘어갑니다 — 요약표는 적재 대상이
     * 아니라 검증 근거일 뿐입니다.
     */
    private void reconcileTotals(
            BigDecimal declaredYearTotal,
            List<ProjectDto.BitemmDto> items,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (declaredYearTotal == null || items.isEmpty()) return;

        BigDecimal actual = BigDecimal.ZERO;
        for (ProjectDto.BitemmDto item : items) {
            if (item.getAmt() != null) actual = actual.add(item.getAmt());
        }
        if (actual.signum() == 0) return;

        if (AmountUnitResolver.inferUnit(declaredYearTotal, actual).isEmpty()) {
            diagnostics.add(
                    RequestFormDto.FormDiagnostic.of(
                            FormSheetKind.CAPITAL_OVERVIEW,
                            null,
                            "declaredYearTotal",
                            RequestFormDiagnosticCode.AMOUNT_MISMATCH,
                            "1-1 요약표의 합계(%s)와 1-2 품목 합계(%s)가 어느 단위로도 맞지 않습니다."
                                    .formatted(
                                            declaredYearTotal.toPlainString(),
                                            actual.toPlainString()),
                            List.of()));
        }
    }
}
