package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.dto.CostTerminalDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CostTerminalUpdateServiceTest {

    private static final String COST_BG_NO = "COST-2026-001";
    private static final String STAMP = "a".repeat(64);

    @Mock private CodeService codeService;
    @Mock private CostWriteTargetLoader writeTargetLoader;
    @Mock private ApprovalWriteGuard approvalWriteGuard;
    @Mock private CostConcurrencyGuard concurrencyGuard;
    @Mock private CostTerminalSynchronizer terminalSynchronizer;
    @Mock private CostNameSnapshotResolver nameResolver;

    private CostTerminalUpdateService service;

    @BeforeEach
    void setUp() {
        service =
                new CostTerminalUpdateService(
                        codeService,
                        writeTargetLoader,
                        approvalWriteGuard,
                        concurrencyGuard,
                        terminalSynchronizer,
                        nameResolver);
        when(concurrencyGuard.runUserUpdate(any()))
                .thenAnswer(
                        invocation ->
                                invocation.<java.util.function.Supplier<String>>getArgument(0).get());
        CustomUserDetails admin =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("부모 개정본에 단말기를 치환하고 단말기 합계를 부모 예산에 반영한다")
    void replaceTerminals_updatesSameParentRevisionAndSummary() {
        Bcostm parent =
                Bcostm.builder()
                        .costBgNo(COST_BG_NO)
                        .bgSno(3)
                        .tmnYn("N")
                        .curC("USD")
                        .fcAmt(BigDecimal.TEN)
                        .costTotXpAmt(BigDecimal.ZERO)
                        .build();
        when(writeTargetLoader.loadForUpdate(COST_BG_NO, 3)).thenReturn(parent);
        CostTerminalDto.TerminalUpdateRequest request =
                CostTerminalDto.TerminalUpdateRequest.builder()
                        .concurrencyStamp(STAMP)
                        .terminals(
                                List.of(
                                        CostDto.TerminalDto.builder()
                                                .termRqmBgAmt(new BigDecimal("1000"))
                                                .build(),
                                        CostDto.TerminalDto.builder()
                                                .termRqmBgAmt(new BigDecimal("2500"))
                                                .build()))
                        .build();

        service.replaceTerminals(COST_BG_NO, 3, request);

        assertThat(parent.getTmnYn()).isEqualTo("Y");
        assertThat(parent.getCurC()).isEqualTo("KRW");
        assertThat(parent.getFcAmt()).isNull();
        assertThat(parent.getCostTotXpAmt()).isEqualByComparingTo("3500");

        ArgumentCaptor<CostDto.UpdateRequest> syncRequest =
                ArgumentCaptor.forClass(CostDto.UpdateRequest.class);
        verify(terminalSynchronizer).sync(eq(parent), syncRequest.capture(), eq(false));
        assertThat(syncRequest.getValue().getTerminals()).isSameAs(request.getTerminals());

        InOrder ordered =
                inOrder(
                        codeService,
                        writeTargetLoader,
                        approvalWriteGuard,
                        concurrencyGuard,
                        terminalSynchronizer);
        ordered.verify(codeService).validateBudgetPeriod();
        ordered.verify(writeTargetLoader).loadForUpdate(COST_BG_NO, 3);
        ordered.verify(approvalWriteGuard).verifyWritable("BCOSTM", COST_BG_NO, 3, "수정");
        ordered.verify(concurrencyGuard).verifyStamp(eq(STAMP), eq(parent), any());
        ordered.verify(terminalSynchronizer).sync(eq(parent), any(), eq(false));
    }
}
