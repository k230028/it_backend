package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
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
import org.mockito.ArgumentCaptor;
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
    @Mock private RequestFormSourceFileArchiver sourceFileArchiver;
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
        when(orgIndex.resolveOrgFolder(anyString()))
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
                sourceFileArchiver,
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
                                                key,
                                                key.split("/")[0],
                                                null,
                                                AmountUnit.WON,
                                                "571"))
                        .toList();
        return new RequestFormDto.ImportManifest("2026", entries, List.of());
    }

    private static RequestFormDto.FileResult applied(String fileKey) {
        return new RequestFormDto.FileResult(
                fileKey,
                "자금운용실",
                RequestFormDto.FileStatus.APPLIED,
                List.of(),
                List.of(
                        new RequestFormDto.CreatedRecord(
                                "BCOSTM", "COST-2026-0001", "계약", "APF-2026-00000001")),
                new RequestFormDto.RecordCounts(0, 0, 1),
                AmountUnit.WON);
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
        assertThat(response.summary().created().costs()).isEqualTo(1);
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
    @DisplayName("dry-run은 원본을 보관하지 않는다")
    void dryRun_doesNotArchive() {
        when(fileImporter.preview(any(), any(), anyString())).thenReturn(applied("자금운용실/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실/요청서.xls"),
                        "12345678",
                        true);

        org.mockito.Mockito.verify(sourceFileArchiver, org.mockito.Mockito.never()).archive(any());
    }

    @Test
    @DisplayName("commit은 원본을 보관한다")
    void commit_archives() {
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenReturn(applied("자금운용실/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실/요청서.xls"),
                        "12345678",
                        false);

        org.mockito.Mockito.verify(sourceFileArchiver).archive(any());
    }

    @Test
    @DisplayName("같은 폴더의 상충한 부서 보정값을 검증된 부서코드별 archive plan으로 전달한다")
    void commit_carriesEffectiveDepartmentCodesIntoArchivePlan() {
        List<RequestFormDto.FileEntry> entries =
                List.of(
                        new RequestFormDto.FileEntry(
                                "동일폴더/a.xls", "동일폴더", "D01", AmountUnit.WON, "571"),
                        new RequestFormDto.FileEntry(
                                "동일폴더/b.xls", "동일폴더", "D02", AmountUnit.WON, "571"));
        RequestFormDto.ImportManifest manifest =
                new RequestFormDto.ImportManifest("2026", entries, List.of());
        when(fileImporter.apply(any(), any(), anyString(), anyString()))
                .thenAnswer(
                        invocation -> {
                            RequestFormDto.FileEntry entry = invocation.getArgument(1);
                            return applied(entry.fileKey());
                        });

        service(50)
                .importBatch(
                        List.of(
                                file("a.xls", RequestFormFixtures.fullFormXls()),
                                file("b.xls", RequestFormFixtures.fullFormXls())),
                        manifest,
                        "12345678",
                        false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestFormSourceFileArchiver.ArchivePlanItem>> planCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sourceFileArchiver).archive(planCaptor.capture());
        assertThat(planCaptor.getValue())
                .extracting(RequestFormSourceFileArchiver.ArchivePlanItem::effectiveDeptCode)
                .containsExactly("D01", "D02");
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
    @DisplayName("인식할 시트가 없는 파일은 실패가 아니라 SKIPPED로 남긴다")
    void skipsFileWithoutRecognizableSheet() {
        RequestFormDto.ImportResponse response =
                service(50)
                        .importBatch(
                                List.of(file("참고자료.xls", RequestFormFixtures.unrelatedSheetXls())),
                                manifest("자금운용실(420)/팀1/사업1/참고자료.xls"),
                                "12345678",
                                true);

        RequestFormDto.FileResult result = response.files().get(0);
        assertThat(result.status()).isEqualTo(RequestFormDto.FileStatus.SKIPPED);
        assertThat(result.diagnostics())
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.SHEET_NOT_FOUND);
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.WARNING);
                        });
        // 건너뛴 파일은 차단도 반영도 아니다
        assertThat(response.summary().blockedFiles()).isZero();
        assertThat(response.summary().appliedFiles()).isZero();
    }

    @Test
    @DisplayName("폴더명 원문을 폴더 해석기에 그대로 넘겨 부서코드 병기를 살린다")
    void passesRawFolderNameToFolderResolver() {
        when(fileImporter.preview(any(), any(), anyString()))
                .thenReturn(applied("자금운용실(420)/요청서.xls"));

        service(50)
                .importBatch(
                        List.of(file("요청서.xls", RequestFormFixtures.fullFormXls())),
                        manifest("자금운용실(420)/요청서.xls"),
                        "12345678",
                        true);

        // 이름만 남기는 가공 없이 원문을 넘겨야 Index가 괄호 안 코드를 볼 수 있다
        org.mockito.Mockito.verify(orgIndex).resolveOrgFolder("자금운용실(420)");
        org.mockito.Mockito.verify(orgIndex, org.mockito.Mockito.never()).resolveOrg(anyString());
    }

    @Test
    @DisplayName("부서를 확정하지 못하면 차단하고 후보를 담아 돌려준다")
    void blocksWhenDepartmentUnresolved() {
        when(orgIndex.resolveOrgFolder(anyString()))
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
