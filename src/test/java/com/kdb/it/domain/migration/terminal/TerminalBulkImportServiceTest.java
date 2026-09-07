package com.kdb.it.domain.migration.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalBulkImportServiceTest {

    @Mock private CostService costService;
    @Mock private CostRepository costRepository;
    @Mock private CodeService codeService;
    @Mock private OrgIdentityResolver orgIdentityResolver;

    @Test
    void 일괄업로드는_비목을_회선사용료로고정한다() {
        TerminalBulkImportService service = configuredService();
        when(costService.createCostForMigration(any(), anyInt()))
                .thenReturn("COST-2025-0001", "COST-2026-0001");

        service.commit(new TerminalBulkImportDto.Request(2026, List.of(row())), "999999");

        ArgumentCaptor<CostDto.CreateRequest> requestCaptor =
                ArgumentCaptor.forClass(CostDto.CreateRequest.class);
        verify(costService, times(2)).createCostForMigration(requestCaptor.capture(), anyInt());
        assertThat(requestCaptor.getAllValues())
                .extracting(CostDto.CreateRequest::getIoeC)
                .containsOnly("010");
    }

    @Test
    void 당해연도_신규원장은_이번반영에서채번한_전년도관리번호를연결한다() {
        TerminalBulkImportService service = configuredService();
        when(costService.createCostForMigration(any(), eq(2025))).thenReturn("COST-2025-0099");
        when(costService.createCostForMigration(any(), eq(2026))).thenReturn("COST-2026-0100");

        service.commit(new TerminalBulkImportDto.Request(2026, List.of(row())), "999999");

        ArgumentCaptor<CostDto.CreateRequest> currentRequest =
                ArgumentCaptor.forClass(CostDto.CreateRequest.class);
        verify(costService).createCostForMigration(currentRequest.capture(), eq(2026));
        assertThat(currentRequest.getValue().getCncdRfrNo()).isEqualTo("COST-2025-0099");
    }

    @Test
    void 확정반영한_모든원장을_업로드사용자의_수기등록신청서로표시한다() {
        TerminalBulkImportService service = configuredService();
        when(costService.createCostForMigration(any(), eq(2025))).thenReturn("COST-2025-0099");
        when(costService.createCostForMigration(any(), eq(2026))).thenReturn("COST-2026-0100");

        service.commit(new TerminalBulkImportDto.Request(2026, List.of(row())), "999999");

        verify(costService).stampManualMigration("COST-2025-0099", "999999");
        verify(costService).stampManualMigration("COST-2026-0100", "999999");
    }

    @Test
    void 미리보기와확정은_기준연도에따른_이전과당해그룹을각각검증하고반영한다() {
        TerminalBulkImportService service =
                new TerminalBulkImportService(
                        new TerminalBulkImportPlanner(),
                        costService,
                        costRepository,
                        codeService,
                        orgIdentityResolver);
        when(orgIdentityResolver.snapshot()).thenReturn(orgIndex());
        when(codeService.findCodeEntitiesByCIdWithoutCache(any()))
                .thenAnswer(invocation -> codes((String) invocation.getArgument(0)));

        TerminalBulkImportDto.Request request =
                new TerminalBulkImportDto.Request(2027, List.of(row()));

        TerminalBulkImportDto.Response preview = service.dryRun(request);
        TerminalBulkImportDto.Response committed = service.commit(request, "999999");

        assertThat(preview.groups()).hasSize(2);
        assertThat(preview.groups()).allMatch(group -> group.createNew());
        assertThat(committed.terminalCount()).isEqualTo(2);
        assertThat(committed.createCount()).isEqualTo(2);
        assertThat(committed.groups())
                .extracting(TerminalBulkImportDto.Group::bseYy)
                .containsExactly("2026", "2027");
    }

    @Test
    void 기존ID는_수정하고_외화금액과유지구분을해석한다() {
        TerminalBulkImportService service =
                new TerminalBulkImportService(
                        new TerminalBulkImportPlanner(),
                        costService,
                        costRepository,
                        codeService,
                        orgIdentityResolver);
        when(orgIdentityResolver.snapshot()).thenReturn(orgIndex());
        when(costRepository.findCurrentVersionsForUpdate("COST-2025-0002"))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2025-0002")
                                        .bgSno(1)
                                        .bseYy("2025")
                                        .build()));
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0002"))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2026-0002")
                                        .bgSno(1)
                                        .bseYy("2026")
                                        .build()));
        when(codeService.findCodeEntitiesByCIdWithoutCache(any()))
                .thenAnswer(invocation -> codes((String) invocation.getArgument(0)));
        when(costService.updateCostForMigration(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TerminalBulkImportDto.Row row =
                new TerminalBulkImportDto.Row(
                        4,
                        "COST-2025-0002",
                        "COST-2026-0002",
                        "부서",
                        "팀",
                        null,
                        "블룸버그",
                        "별도단말",
                        "리서치",
                        "Bloomberg Service",
                        "USD",
                        new BigDecimal("10"),
                        null,
                        new BigDecimal("1200"),
                        "유지(단가변동)",
                        null,
                        new BigDecimal("20"),
                        null,
                        new BigDecimal("2400"),
                        "연간",
                        null);

        TerminalBulkImportDto.Response result =
                service.commit(new TerminalBulkImportDto.Request(2026, List.of(row)), "999999");

        assertThat(result.updateCount()).isEqualTo(2);
        var ordered = org.mockito.Mockito.inOrder(costRepository, costService);
        ordered.verify(costRepository).findCurrentVersionsForUpdate("COST-2025-0002");
        ordered.verify(costRepository).findCurrentVersionsForUpdate("COST-2026-0002");
        ordered.verify(costService).updateCostForMigration(eq("COST-2025-0002"), any());
        assertThat(result.groups())
                .extracting(TerminalBulkImportDto.Group::costId)
                .containsExactly("COST-2025-0002", "COST-2026-0002");
        verify(costService).updateCostForMigration(eq("COST-2025-0002"), any());
        verify(costService).updateCostForMigration(eq("COST-2026-0002"), any());
    }

    @Test
    void 기존_당해연도원장도_회선사용료와_전년도관리번호를갱신한다() {
        TerminalBulkImportService service = configuredService();
        Bcostm previous =
                Bcostm.builder().costBgNo("COST-2025-0002").bgSno(1).bseYy("2025").build();
        Bcostm current = Bcostm.builder().costBgNo("COST-2026-0002").bgSno(1).bseYy("2026").build();
        when(costRepository.findCurrentVersionsForUpdate("COST-2025-0002"))
                .thenReturn(List.of(previous));
        when(costRepository.findCurrentVersionsForUpdate("COST-2026-0002"))
                .thenReturn(List.of(current));
        when(costService.updateCostForMigration(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TerminalBulkImportDto.Row existingRow =
                rowWithIds(row(), "COST-2025-0002", "COST-2026-0002");

        service.commit(new TerminalBulkImportDto.Request(2026, List.of(existingRow)), "999999");

        ArgumentCaptor<CostDto.UpdateRequest> currentRequest =
                ArgumentCaptor.forClass(CostDto.UpdateRequest.class);
        verify(costService).updateCostForMigration(eq("COST-2026-0002"), currentRequest.capture());
        assertThat(currentRequest.getValue().getIoeC()).isEqualTo("010");
        assertThat(currentRequest.getValue().getCncdRfrNo()).isEqualTo("COST-2025-0002");
    }

    @Test
    void 신규ID는_같은부서의블룸버그옵션을_연도별한건으로생성한다() {
        TerminalBulkImportService service = configuredService();
        TerminalBulkImportDto.Row first = rowFor("블룸버그", "KRW", "신규");
        TerminalBulkImportDto.Row second = rowWithTerminal(first, "블룸버그(***)");

        TerminalBulkImportDto.Response result =
                service.commit(
                        new TerminalBulkImportDto.Request(2026, List.of(first, second)), "999999");

        assertThat(result.createCount()).isEqualTo(2);
        assertThat(result.groups())
                .extracting(
                        TerminalBulkImportDto.Group::bseYy,
                        TerminalBulkImportDto.Group::terminalCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2025", 2),
                        org.assertj.core.groups.Tuple.tuple("2026", 2));
        verify(costService, times(2)).createCostForMigration(any(), anyInt());
    }

    @Test
    void 입력이비어있거나_코드가맞지않으면_저장전에거부한다() {
        TerminalBulkImportService service = configuredService();

        assertThatThrownBy(
                        () ->
                                service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026,
                                                Arrays.asList((TerminalBulkImportDto.Row) null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("빈 업로드 행");
        assertThatThrownBy(
                        () ->
                                service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowNoAmount()))))
                .hasMessageContaining("집행금액");
        assertThatThrownBy(
                        () ->
                                service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowFor("", "KRW", "신규")))))
                .hasMessageContaining("단말기명");
        assertThatThrownBy(
                        () ->
                                service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowFor("블룸버그", null, "신규")))))
                .hasMessageContaining("통화");
        assertThatThrownBy(
                        () ->
                                service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowFor("알 수 없는 단말기", "KRW", "신규")))))
                .hasMessageContaining("단말기명 공통코드");

        assertThat(
                        service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowFor("블룸버그", "KRW", null))))
                                .groups())
                .hasSize(2);
        assertThat(
                        service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowFor("블룸버그", "KRW", "해지"))))
                                .groups())
                .hasSize(1);
        assertThat(
                        service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowWithManager("없는 담당자"))))
                                .groups())
                .hasSize(2);

        assertThat(
                        service.dryRun(
                                        new TerminalBulkImportDto.Request(
                                                2026, List.of(rowWithTeam("유가증권운용전략팀"))))
                                .groups())
                .hasSize(2);
    }

    private static TerminalBulkImportDto.Row row() {
        return rowFor("블룸버그", "KRW", "신규");
    }

    private static TerminalBulkImportDto.Row rowFor(
            String terminalName, String currency, String currentKind) {
        return new TerminalBulkImportDto.Row(
                3,
                null,
                null,
                "부서",
                "팀",
                "강동현",
                terminalName,
                "Web접속",
                "리서치",
                "Bloomberg Service",
                currency,
                BigDecimal.ZERO,
                new BigDecimal("100"),
                new BigDecimal("1200"),
                currentKind,
                null,
                BigDecimal.ZERO,
                new BigDecimal("110"),
                new BigDecimal("1320"),
                "분기별",
                null);
    }

    private static TerminalBulkImportDto.Row rowWithTerminal(
            TerminalBulkImportDto.Row base, String terminalName) {
        return new TerminalBulkImportDto.Row(
                base.excelRow() + 1,
                base.previousCostId(),
                base.currentCostId(),
                base.department(),
                base.team(),
                base.managerName(),
                terminalName,
                base.usageMethod(),
                base.purpose(),
                base.service(),
                base.currency(),
                base.previousForeignMonthly(),
                base.previousKrwMonthly(),
                base.previousAnnual(),
                base.currentKind(),
                base.increaseRate(),
                base.currentForeignMonthly(),
                base.currentKrwMonthly(),
                base.currentAnnual(),
                base.paymentCycle(),
                base.note());
    }

    private static TerminalBulkImportDto.Row rowWithIds(
            TerminalBulkImportDto.Row base, String previousCostId, String currentCostId) {
        return new TerminalBulkImportDto.Row(
                base.excelRow(),
                previousCostId,
                currentCostId,
                base.department(),
                base.team(),
                base.managerName(),
                base.terminalName(),
                base.usageMethod(),
                base.purpose(),
                base.service(),
                base.currency(),
                base.previousForeignMonthly(),
                base.previousKrwMonthly(),
                base.previousAnnual(),
                base.currentKind(),
                base.increaseRate(),
                base.currentForeignMonthly(),
                base.currentKrwMonthly(),
                base.currentAnnual(),
                base.paymentCycle(),
                base.note());
    }

    private static TerminalBulkImportDto.Row rowNoAmount() {
        return new TerminalBulkImportDto.Row(
                3,
                null,
                null,
                "부서",
                "팀",
                "강동현",
                "블룸버그",
                "Web접속",
                "리서치",
                "Bloomberg Service",
                "KRW",
                null,
                null,
                null,
                "신규",
                null,
                null,
                null,
                null,
                "분기별",
                null);
    }

    private static TerminalBulkImportDto.Row rowWithManager(String managerName) {
        TerminalBulkImportDto.Row base = rowFor("블룸버그", "KRW", "신규");
        return new TerminalBulkImportDto.Row(
                base.excelRow(),
                base.previousCostId(),
                base.currentCostId(),
                base.department(),
                base.team(),
                managerName,
                base.terminalName(),
                base.usageMethod(),
                base.purpose(),
                base.service(),
                base.currency(),
                base.previousForeignMonthly(),
                base.previousKrwMonthly(),
                base.previousAnnual(),
                base.currentKind(),
                base.increaseRate(),
                base.currentForeignMonthly(),
                base.currentKrwMonthly(),
                base.currentAnnual(),
                base.paymentCycle(),
                base.note());
    }

    private static TerminalBulkImportDto.Row rowWithTeam(String team) {
        TerminalBulkImportDto.Row base = rowFor("블룸버그", "KRW", "신규");
        return new TerminalBulkImportDto.Row(
                base.excelRow(),
                base.previousCostId(),
                base.currentCostId(),
                base.department(),
                team,
                base.managerName(),
                base.terminalName(),
                base.usageMethod(),
                base.purpose(),
                base.service(),
                base.currency(),
                base.previousForeignMonthly(),
                base.previousKrwMonthly(),
                base.previousAnnual(),
                base.currentKind(),
                base.increaseRate(),
                base.currentForeignMonthly(),
                base.currentKrwMonthly(),
                base.currentAnnual(),
                base.paymentCycle(),
                base.note());
    }

    private TerminalBulkImportService configuredService() {
        when(orgIdentityResolver.snapshot()).thenReturn(orgIndex());
        when(codeService.findCodeEntitiesByCIdWithoutCache(any()))
                .thenAnswer(invocation -> codes((String) invocation.getArgument(0)));
        return new TerminalBulkImportService(
                new TerminalBulkImportPlanner(),
                costService,
                costRepository,
                codeService,
                orgIdentityResolver);
    }

    private static OrgIdentityResolver.Index orgIndex() {
        CorgnI department = CorgnI.builder().prlmOgzCCone("D001").bbrNm("부서").build();
        CuserI user =
                CuserI.builder()
                        .eno("E001")
                        .usrNm("강동현")
                        .bbrC("D001")
                        .temC("T001")
                        .temNm("팀")
                        .build();
        return OrgIdentityResolver.Index.of(List.of(department), List.of(user));
    }

    private static List<Ccodem> codes(String group) {
        return switch (group) {
            case "CUR_C" -> List.of(code(group, "KRW", "한국원"), code(group, "USD", "미국달러"));
            case "IT_PTL_TMN_SVC_TC" -> List.of(code(group, "05", "블룸버그"));
            case "IT_PTL_TMN_KD_TC" -> List.of(code(group, "01", "별도단말"), code(group, "02", "웹접속"));
            case "DFR_CLE_C" -> List.of(code(group, "Q", "분기"), code(group, "Y", "연간"));
            case "ABUS_TC" -> List.of(code(group, "10", "신규"), code(group, "20", "계속"));
            default -> List.of();
        };
    }

    private static Ccodem code(String group, String value, String name) {
        return Ccodem.builder().cId(group).cdva(value).cdvaNm(name).build();
    }
}
