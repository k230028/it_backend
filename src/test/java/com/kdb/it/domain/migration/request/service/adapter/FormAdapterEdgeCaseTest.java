package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 어댑터 3종의 예외 경로를 모읍니다.
 *
 * <p>정상 흐름은 각 어댑터 테스트가 다루고, 여기서는 <b>양식이 깨졌거나 시트가 없을 때</b>의 동작만 봅니다 — 수백 건을 받는 기능에서 이 경로가 실제로 자주
 * 밟히므로, 조용히 빈 결과를 내는지 진단을 내는지가 사용자에게 그대로 드러납니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormAdapterEdgeCaseTest {

    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private MigrationIoeCatalogReader catalogReader;

    private final WorkbookReader workbookReader = new WorkbookReader(10_485_760L, 20, 5000);
    private final SheetAnchorScanner scanner = new SheetAnchorScanner();
    private final FormLabelReader labelReader = new FormLabelReader(scanner);
    private final ResourceTableReader resourceTableReader = new ResourceTableReader(scanner);

    /** 시트명만 양식과 같고 내용이 없는 워크북. 부점이 양식을 지우고 낸 경우를 재현합니다. */
    private static byte[] emptyShellWorkbook() {
        try (Workbook wb = new HSSFWorkbook()) {
            wb.createSheet("① (정보화사업) 1-1. 정보화사업 개요")
                    .createRow(0)
                    .createCell(0)
                    .setCellValue("빈 양식");
            wb.createSheet("① (정보화사업) 1-2. 소요자원 상세내용")
                    .createRow(0)
                    .createCell(0)
                    .setCellValue("빈 양식");
            wb.createSheet("② (경상사업) 2. 경상적인 사업").createRow(0).createCell(0).setCellValue("빈 양식");
            wb.createSheet("③ (일반관리비) 전산 일반관리비 편성요청서")
                    .createRow(0)
                    .createCell(0)
                    .setCellValue("빈 양식");
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FormAdapterContext contextOf(byte[] bytes) {
        Map<FormSheetKind, Sheet> sheets =
                workbookReader.classify(workbookReader.open(bytes, "픽스처.xls"));
        return new FormAdapterContext(
                sheets,
                "2026",
                new RequestFormDto.FileEntry("부서/파일.xls", "부서", null, null, null),
                "0210",
                "부서",
                orgIndex,
                TestIoeIndex.snapshot(),
                Map.of(),
                "12345678");
    }

    private FormAdapterContext emptySheets() {
        return new FormAdapterContext(
                new EnumMap<>(FormSheetKind.class),
                "2026",
                new RequestFormDto.FileEntry("부서/파일.xls", "부서", null, null, null),
                "0210",
                "부서",
                orgIndex,
                TestIoeIndex.snapshot(),
                Map.of(),
                "12345678");
    }

    @Test
    @DisplayName("시트 ③의 표 헤더를 못 찾으면 앵커 실패 진단을 낸다")
    void generalExpenseReportsMissingAnchor() {
        GeneralExpenseFormAdapter adapter =
                new GeneralExpenseFormAdapter(
                        scanner, catalogReader, new FormApproverReader(scanner));

        FormAdapterOutput output = adapter.adapt(contextOf(emptyShellWorkbook()));

        assertThat(output.costs()).isEmpty();
        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .containsExactly(RequestFormDiagnosticCode.ANCHOR_NOT_FOUND);
    }

    @Test
    @DisplayName("시트가 아예 없으면 어댑터 3종 모두 조용히 빈 결과를 낸다")
    void allAdaptersReturnEmptyWhenSheetAbsent() {
        FormAdapterContext context = emptySheets();
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of());

        assertThat(
                        new GeneralExpenseFormAdapter(
                                        scanner, catalogReader, new FormApproverReader(scanner))
                                .adapt(context)
                                .costs())
                .isEmpty();
        assertThat(
                        new RecurringProjectFormAdapter(
                                        labelReader,
                                        resourceTableReader,
                                        new FormApproverReader(scanner))
                                .adapt(context)
                                .projects())
                .isEmpty();
        assertThat(
                        new CapitalProjectFormAdapter(
                                        new CapitalOverviewReader(
                                                scanner, labelReader, new FormCheckboxReader()),
                                        resourceTableReader,
                                        catalogReader)
                                .adapt(context)
                                .projects())
                .isEmpty();
    }

    @Test
    @DisplayName("빈 껍데기 시트 ②는 진단 없이 건너뛴다")
    void recurringSkipsEmptyShellWithoutDiagnostics() {
        RecurringProjectFormAdapter adapter =
                new RecurringProjectFormAdapter(
                        labelReader, resourceTableReader, new FormApproverReader(scanner));

        FormAdapterOutput output = adapter.adapt(contextOf(emptyShellWorkbook()));

        assertThat(output.projects()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("빈 껍데기 시트 1-1은 진단 없이 건너뛴다")
    void capitalSkipsEmptyShellWithoutDiagnostics() {
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of());
        CapitalProjectFormAdapter adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(scanner, labelReader, new FormCheckboxReader()),
                        resourceTableReader,
                        catalogReader);

        FormAdapterOutput output = adapter.adapt(contextOf(emptyShellWorkbook()));

        assertThat(output.projects()).isEmpty();
        assertThat(output.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("기본값으로 정한 비목은 반영을 막지 않고 확인만 요청한다")
    void defaultedIoeIsWarningNotBlocker() {
        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of("수석부행장", "21"));
        when(orgIndex.resolveOrg(any()))
                .thenReturn(new OrgIdentityResolver.Resolution("0210", "부서", List.of(), false));
        when(orgIndex.resolveUser(any(), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("12345678", "담당자", List.of(), false));
        CapitalProjectFormAdapter adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(scanner, labelReader, new FormCheckboxReader()),
                        resourceTableReader,
                        catalogReader);

        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        // 기타무형자산(SW)+KRW는 106으로 기본 설정되고 107이 대안으로 남는다
        ProjectDto.CreateRequest project = output.projects().get(0);
        assertThat(project.getItems().get(0).getIoeC()).isEqualTo("106");
        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.CODE_DEFAULTED)
                .isNotEmpty()
                .allSatisfy(
                        d ->
                                assertThat(d.severity())
                                        .isEqualTo(
                                                com.kdb.it.domain.migration.dto.MigrationDto
                                                        .Severity.WARNING));
    }

    @Test
    @DisplayName("알 수 없는 중분류는 미해석으로 남고 품목 비목이 비어 검증에 걸린다")
    void unknownGroupLeavesIoeUnresolved() {
        IoeHierarchyIndex.Resolution resolution =
                TestIoeIndex.snapshot().resolveByGroup("존재하지 않는 중분류", true);

        assertThat(resolution.isUnresolved()).isTrue();
        assertThat(resolution.code()).isNull();
        assertThat(resolution.candidates()).isEmpty();
        assertThat(resolution.label()).isEqualTo("존재하지 않는 중분류");
    }
}
