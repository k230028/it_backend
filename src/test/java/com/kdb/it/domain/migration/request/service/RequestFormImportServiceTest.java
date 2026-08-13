package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.CapitalProjectFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.GeneralExpenseFormAdapter;
import com.kdb.it.domain.migration.request.service.adapter.RecurringProjectFormAdapter;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestFormImportServiceTest {

    @Mock private OrgIdentityResolver orgIdentityResolver;
    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private IoeHierarchyIndex ioeHierarchyIndex;
    @Mock private RequestFormFileImporter fileImporter;
    @Mock private CapitalProjectFormAdapter capitalAdapter;
    @Mock private RecurringProjectFormAdapter recurringAdapter;
    @Mock private GeneralExpenseFormAdapter generalAdapter;

    @BeforeEach
    void setUp() {
        // TestIoeIndex.snapshot()이 내부에서 Mockito를 쓰므로 when(...) 인자 안에서 부르면
        // 바깥 스터빙이 미완료 상태로 깨진다(UnfinishedStubbingException). 먼저 만들어 둔다.
        IoeHierarchyIndex.Snapshot ioeSnapshot = TestIoeIndex.snapshot();

        when(orgIdentityResolver.snapshot()).thenReturn(orgIndex);
        when(ioeHierarchyIndex.snapshot()).thenReturn(ioeSnapshot);
        when(orgIndex.resolveOrg(anyString()))
                .thenReturn(new OrgIdentityResolver.Resolution("0210", "자금운용실", List.of(), false));

        when(capitalAdapter.trigger()).thenReturn(FormSheetKind.CAPITAL_OVERVIEW);
        when(recurringAdapter.trigger()).thenReturn(FormSheetKind.RECURRING);
        when(generalAdapter.trigger()).thenReturn(FormSheetKind.GENERAL_EXPENSE);
        when(capitalAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
        when(recurringAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
        when(generalAdapter.adapt(any())).thenReturn(FormAdapterOutput.empty());
    }

    private RequestFormImportService service(int maxFilesPerBatch) {
        return new RequestFormImportService(
                new WorkbookReader(10_485_760L, 20, 5000),
                orgIdentityResolver,
                ioeHierarchyIndex,
                fileImporter,
                List.of(capitalAdapter, recurringAdapter, generalAdapter),
                maxFilesPerBatch);
    }

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "application/vnd.ms-excel", bytes);
    }

    private static RequestFormDto.ImportManifest manifest(String... fileKeys) {
        List<RequestFormDto.FileEntry> entries =
                Arrays.stream(fileKeys)
                        .map(
                                key ->
                                        new RequestFormDto.FileEntry(
                                                key, key.split("/")[0], null, 1L, "571"))
                        .toList();
        return new RequestFormDto.ImportManifest("2026", entries, List.of());
    }

    private static RequestFormDto.FileResult applied(String fileKey) {
        return new RequestFormDto.FileResult(
                fileKey,
                "자금운용실",
                RequestFormDto.FileStatus.APPLIED,
                List.of(),
                List.of(new RequestFormDto.CreatedRecord("BCOSTM", "COST-2026-0001", "계약")),
                1L);
    }

    @Test
    @DisplayName("파일마다 어댑터를 태우고 결과를 모아 요약한다")
    void aggregatesPerFileResults() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/요청서.xls"),
                                "12345678",
                                false);

        assertThat(response.dryRun()).isFalse();
        assertThat(response.summary().totalFiles()).isEqualTo(1);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
        assertThat(response.summary().createdCosts()).isEqualTo(1);
    }

    @Test
    @DisplayName("사전검증은 preview 경로로 보낸다")
    void dryRunUsesPreview() {
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/요청서.xls"),
                                "12345678",
                                true);

        assertThat(response.dryRun()).isTrue();
        org.mockito.Mockito.verify(fileImporter, org.mockito.Mockito.never())
                .apply(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("열지 못한 파일은 FAILED로 남기고 배치를 계속한다")
    void keepsGoingWhenOneFileIsUnreadable() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("런던지점/요청서.xls"));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(
                                        file("깨진.xlsx", "엑셀 아님".getBytes(StandardCharsets.UTF_8)),
                                        file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("자금운용실/깨진.xlsx", "런던지점/요청서.xls"),
                                "12345678",
                                false);

        assertThat(response.files()).hasSize(2);
        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.FAILED);
        assertThat(response.files().get(0).diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.FILE_UNREADABLE);
        assertThat(response.files().get(1).status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(response.summary().appliedFiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("부서를 확정하지 못하면 차단하고 후보를 담아 돌려준다")
    void blocksWhenDepartmentUnresolved() {
        when(orgIndex.resolveOrg(anyString()))
                .thenReturn(new OrgIdentityResolver.Resolution(null, "미등록부서", List.of(), false));

        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                                manifest("미등록부서/요청서.xls"),
                                "12345678",
                                true);

        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.BLOCKED);
        assertThat(response.files().get(0).diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.ORG_UNRESOLVED);
    }

    @Test
    @DisplayName("배치 파일 수 상한을 넘으면 거부한다")
    void rejectsOversizeBatch() {
        assertThatThrownBy(
                        () ->
                                service(1)
                                        .importBatch(
                                                List.of(
                                                        file(
                                                                "a.xls",
                                                                RequestFormFixtures.fullFormXls()),
                                                        file(
                                                                "b.xls",
                                                                RequestFormFixtures.fullFormXls())),
                                                manifest("d/a.xls", "d/b.xls"),
                                                "12345678",
                                                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일은");
    }

    @Test
    @DisplayName("파일 수와 manifest 항목 수가 다르면 거부한다")
    void rejectsWhenManifestCountDiffers() {
        assertThatThrownBy(
                        () ->
                                service(50)
                                        .importBatch(
                                                List.of(
                                                        file(
                                                                "요청서.xls",
                                                                RequestFormFixtures.fullFormXls())),
                                                manifest("자금운용실/요청서.xls", "자금운용실/빠진파일.xls"),
                                                "12345678",
                                                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifest");
    }
}
