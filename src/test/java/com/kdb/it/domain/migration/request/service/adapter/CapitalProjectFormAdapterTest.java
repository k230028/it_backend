package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import com.kdb.it.domain.migration.request.service.WorkbookReader;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapitalProjectFormAdapterTest {

    @Mock private OrgIdentityResolver.Index orgIndex;
    @Mock private MigrationIoeCatalogReader catalogReader;

    private final WorkbookReader reader = new WorkbookReader(10_485_760L, 20, 5000);
    private final SheetAnchorScanner scanner = new SheetAnchorScanner();

    private CapitalProjectFormAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
                new CapitalProjectFormAdapter(
                        new CapitalOverviewReader(scanner, new FormLabelReader(scanner)),
                        new ResourceTableReader(scanner),
                        catalogReader);

        when(catalogReader.exePttCodeByName()).thenReturn(Map.of());
        when(catalogReader.edrtCapitalCodeByName()).thenReturn(Map.of("수석부행장", "21"));
        when(orgIndex.resolveOrg("자금운용실"))
                .thenReturn(new OrgIdentityResolver.Resolution("0210", "자금운용실", List.of(), false));
        when(orgIndex.resolveOrg("원화유가증권팀"))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("02101", "원화유가증권팀", List.of(), false));
        when(orgIndex.resolveUser(eq("윤소정"), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("11111111", "윤소정", List.of(), false));
        when(orgIndex.resolveUser(eq("허진성"), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("22222222", "허진성", List.of(), false));
        when(orgIndex.resolveUser(eq("공현순"), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("33333333", "공현순", List.of(), false));
        when(orgIndex.resolveUser(eq("최현식"), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution("44444444", "최현식", List.of(), false));
    }

    private FormAdapterContext contextOf(byte[] bytes) {
        Map<FormSheetKind, Sheet> sheets = reader.classify(reader.open(bytes, "픽스처.xls"));
        return new FormAdapterContext(
                sheets,
                "2026",
                new RequestFormDto.FileEntry("자금운용실/요청서.xls", "자금운용실", null, null, null),
                "0210",
                "자금운용실",
                orgIndex,
                TestIoeIndex.snapshot(),
                Map.of(),
                "12345678");
    }

    @Test
    @DisplayName("1-1과 1-2를 합쳐 사업 1건과 품목 3건을 만든다")
    void combinesOverviewAndResourceSheets() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.projects()).hasSize(1);
        ProjectDto.CreateRequest project = output.projects().get(0);
        assertThat(project.getAbusNm()).isEqualTo("국채전문유통시장 접속인프라 도입");
        assertThat(project.getOdnYn()).isEqualTo("N");
        // 자본예산 블록 2건 + 일반관리비 블록 1건
        assertThat(project.getItems()).hasSize(3);
        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getSno)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("사업 설명 6개 항목을 각 컬럼으로 옮긴다")
    void mapsNarrativeFields() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getAbusCone()).isEqualTo("접속방식 변경");
        assertThat(project.getCpnSafCone()).isEqualTo("Exture3.0 운영 중");
        assertThat(project.getAbusNcsCone()).isEqualTo("접속체계 전환 대응");
        assertThat(project.getDgogPpoCone()).isEqualTo("PD 자격 유지");
        assertThat(project.getPlmDes()).isEqualTo("PD 업무 수행 불가");
        assertThat(project.getAbusRngCone()).contains("전용망 거래 기능").contains("추가 요구사항 3");
    }

    @Test
    @DisplayName("주관부서/팀을 슬래시로 갈라 각각 해석한다")
    void splitsDepartmentAndTeam() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getSvnDpmC()).isEqualTo("0210");
        assertThat(project.getSvnTemC()).isEqualTo("02101");
    }

    @Test
    @DisplayName("확인자·작성자가 아니라 관련 조직의 팀장·실무자를 정본으로 삼는다")
    void usesRelatedOrganizationNotConfirmer() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        // 픽스처의 확인자는 `허인선 팀장`, 관련 조직의 팀장은 `윤소정`
        assertThat(project.getTlrUsid()).isEqualTo("11111111");
        assertThat(project.getUsid()).isEqualTo("22222222");
        assertThat(project.getDvmTlrUsid()).isEqualTo("33333333");
        assertThat(project.getDvmUsid()).isEqualTo("44444444");
    }

    @Test
    @DisplayName("실무자(정/부)에서 정만 담고 부는 미적재 경고를 낸다")
    void keepsPrimaryStaffOnly() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.SUBSTITUTE_DROPPED);
    }

    @Test
    @DisplayName("YY/MM 표기를 시작 1일·종료 말일로 바꾼다")
    void parsesPeriod() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("전결권자 이름을 자본예산 계열 코드로 바꾼다")
    void mapsDelegationCode() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getEdrtTc()).isEqualTo("21");
    }

    @Test
    @DisplayName("공란인 사업구분·편성기준 8항목마다 선택항목 누락 경고를 낸다")
    void warnsOnEmptyOptionalFields() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.OPTIONAL_MISSING)
                .extracting(RequestFormDto.FormDiagnostic::field)
                .containsExactlyInAnyOrder(
                        "bzDttNm",
                        "bzTpC",
                        "sklTpTc",
                        "cstTpTc",
                        "dplYn",
                        "flfFsgDt",
                        "rprStsTc",
                        "exePttYn");
    }

    @Test
    @DisplayName("일반관리비 블록의 품목도 같은 사업의 BITEMM으로 담는다")
    void putsGeneralExpenseItemsIntoSameProject() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getItems())
                .extracting(ProjectDto.BitemmDto::getGclNm)
                .contains("전용망 회선 이용료");
    }

    @Test
    @DisplayName("자본예산 품목은 국내 원화 기준으로 비목을 정한다")
    void resolvesCapitalItemIoe() {
        ProjectDto.CreateRequest project =
                adapter.adapt(contextOf(RequestFormFixtures.fullFormXls())).projects().get(0);

        assertThat(project.getItems().get(0).getIoeC()).isEqualTo("106");
        assertThat(project.getItems().get(1).getIoeC()).isEqualTo("101");
        assertThat(project.getItems().get(2).getIoeC()).isEqualTo("001");
    }

    @Test
    @DisplayName("1-1 요약표와 1-2 합계가 맞으면 불일치 경고를 내지 않는다")
    void staysQuietWhenTotalsReconcile() {
        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("사람 이름이 있는데 해석되지 않으면 차단 진단을 낸다")
    void blocksWhenNamedUserUnresolved() {
        when(orgIndex.resolveUser(eq("윤소정"), any()))
                .thenReturn(new OrgIdentityResolver.Resolution(null, "윤소정", List.of(), false));

        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.USER_UNRESOLVED);
    }

    @Test
    @DisplayName("동명이인이면 후보를 담아 중의 진단을 낸다")
    void reportsAmbiguousUserWithCandidates() {
        when(orgIndex.resolveUser(eq("허진성"), any()))
                .thenReturn(
                        new OrgIdentityResolver.Resolution(
                                null,
                                "허진성",
                                List.of(
                                        new MigrationDto.Candidate("22222222", "허진성 과장"),
                                        new MigrationDto.Candidate("55555555", "허진성 대리")),
                                true));

        FormAdapterOutput output = adapter.adapt(contextOf(RequestFormFixtures.fullFormXls()));

        assertThat(output.diagnostics())
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.USER_AMBIGUOUS)
                .singleElement()
                .satisfies(d -> assertThat(d.candidates()).hasSize(2));
    }

    @Test
    @DisplayName("시트 1-1이 없으면 빈 결과를 돌려준다")
    void returnsEmptyWhenOverviewAbsent() {
        Map<FormSheetKind, Sheet> sheets =
                reader.classify(reader.open(RequestFormFixtures.englishFormXls(), "런던.xls"));
        FormAdapterContext context =
                new FormAdapterContext(
                        sheets,
                        "2026",
                        new RequestFormDto.FileEntry("런던지점/붙임.xls", "런던지점", null, null, null),
                        "0930",
                        "런던지점",
                        orgIndex,
                        TestIoeIndex.snapshot(),
                        Map.of(),
                        "12345678");

        assertThat(adapter.adapt(context).projects()).isEmpty();
    }
}
