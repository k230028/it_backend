package com.kdb.it.domain.migration.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostQueryService;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalBulkImportCostServiceTest {

    @Mock private CostRepository costRepository;
    @Mock private BtermmRepository btermmRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrgNameResolver orgNameResolver;
    @Mock private CodeService codeService;
    @Mock private XcrLookupService xcrLookupService;
    @Mock private CostQueryService costQueryService;
    @InjectMocks private CostService costService;

    @Test
    void 이관전용생성은_대상연도로채번하고_제출금액과외화를보존한다() {
        when(costRepository.getNextSequenceValue()).thenReturn(7L);
        when(costRepository.getNextSnoValue("COST-2025-0007")).thenReturn(1);
        when(btermmRepository.getNextSequenceValue()).thenReturn(3L);
        CostDto.TerminalDto terminal =
                CostDto.TerminalDto.builder()
                        .spfTmnNm("Bloomberg Service")
                        .curC("USD")
                        .cgprId("10001")
                        .termRqmBgAmt(new BigDecimal("1200"))
                        .fcAmt(new BigDecimal("10"))
                        .xcr(new BigDecimal("120"))
                        .build();
        CostDto.CreateRequest request =
                CostDto.CreateRequest.builder()
                        .bseYy("2025")
                        .curC("KRW")
                        .cgprId("10001")
                        .costTotXpAmt(new BigDecimal("1200"))
                        .terminals(List.of(terminal))
                        .build();

        String costId = costService.createCostForMigration(request, 2025);

        assertThat(costId).isEqualTo("COST-2025-0007");
        ArgumentCaptor<Bcostm> costCaptor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(costCaptor.capture());
        assertThat(costCaptor.getValue().getCgprId()).isNull();
        ArgumentCaptor<Btermm> terminalCaptor = ArgumentCaptor.forClass(Btermm.class);
        verify(btermmRepository).save(terminalCaptor.capture());
        assertThat(terminalCaptor.getValue().getTmnMngNo()).isEqualTo("TER-2025-0003");
        assertThat(terminalCaptor.getValue().getCgprId()).isNull();
        assertThat(terminalCaptor.getValue().getTermRqmBgAmt()).isEqualByComparingTo("1200");
        assertThat(terminalCaptor.getValue().getFcAmt()).isEqualByComparingTo("10");
    }

    @Test
    void 이관전용수정은_파일에없는기존단말기를삭제하지않고_같은단말기를재사용한다() {
        Bcostm cost = org.mockito.Mockito.mock(Bcostm.class);
        when(cost.getCostBgNo()).thenReturn("COST-2025-0007");
        when(cost.getBgSno()).thenReturn(1);
        when(cost.getCttNm()).thenReturn("원래 전산업무비 제목");
        when(cost.getCttOppNm()).thenReturn("원래 계약상대처");
        when(costRepository.findByCostBgNoAndDelYn("COST-2025-0007", "N"))
                .thenReturn(List.of(cost));

        Btermm existing = org.mockito.Mockito.mock(Btermm.class);
        when(existing.getTmnMngNo()).thenReturn("TER-2025-0003");
        when(existing.getSno()).thenReturn(1);
        when(existing.getSpfTmnNm()).thenReturn("Bloomberg Service");
        when(existing.getTmnKdTc()).thenReturn("02");
        when(existing.getTmnClsfC()).thenReturn("05");
        when(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-2025-0007", 1, "N"))
                .thenReturn(List.of(existing));

        CostDto.TerminalDto terminal =
                CostDto.TerminalDto.builder()
                        .spfTmnNm("Bloomberg Service")
                        .tmnKdTc("02")
                        .tmnClsfC("05")
                        .termRqmBgAmt(new BigDecimal("2400"))
                        .curC("KRW")
                        .build();
        CostDto.UpdateRequest request =
                CostDto.UpdateRequest.builder()
                        .bseYy("2025")
                        .cttNm("Bloomberg")
                        .terminals(List.of(terminal))
                        .build();

        assertThat(costService.updateCostForMigration("COST-2025-0007", request))
                .isEqualTo("COST-2025-0007");
        ArgumentCaptor<Bcostm.UpdateCommand> costCommandCaptor =
                ArgumentCaptor.forClass(Bcostm.UpdateCommand.class);
        verify(cost).update(costCommandCaptor.capture());
        assertThat(costCommandCaptor.getValue().cttNm()).isEqualTo("원래 전산업무비 제목");
        assertThat(costCommandCaptor.getValue().cttOppNm()).isEqualTo("원래 계약상대처");
        verify(existing).update(org.mockito.ArgumentMatchers.any(Btermm.UpdateCommand.class));
        verify(existing, never()).delete();
        verify(btermmRepository, never()).save(org.mockito.ArgumentMatchers.any(Btermm.class));
        verify(btermmRepository, never()).getNextSequenceValue();
    }
}
