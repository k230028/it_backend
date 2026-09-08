package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CostTerminalLinkService 단위 테스트
 *
 * <p>연결 단말기 등록 표시가 부모의 {@code TMN_YN}만 바꾸는지, 행을 잠근 뒤 결재 상태를 확인하는지 검증합니다. 전체 치환 수정 경로를 쓰면 부모의 업무 필드가
 * null이 되고 단말기 행이 모두 논리 삭제되므로, 그 경로를 타지 않는다는 사실 자체가 이 테스트의 핵심입니다. DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostTerminalLinkServiceTest {

    private static final String COST_BG_NO = "COST-LINK";

    @Mock private CostRepository costRepository;
    @Mock private ApplicationMapRepository capplaRepository;

    private CostTerminalLinkService terminalLinkService;

    @BeforeEach
    void setUp() {
        terminalLinkService =
                new CostTerminalLinkService(
                        costRepository, new ApprovalWriteGuard(capplaRepository));
    }

    private Bcostm locked() {
        Bcostm cost = mock(Bcostm.class);
        given(cost.getBgSno()).willReturn(3);
        given(costRepository.findCurrentVersionForUpdate(COST_BG_NO)).willReturn(Optional.of(cost));
        return cost;
    }

    @Test
    @DisplayName("단말 연결 표시는 TMN_YN만 바꾸고 전체 치환 수정 경로를 타지 않는다")
    void marksOnlyTheFlag() {
        Bcostm cost = locked();

        terminalLinkService.markTerminalLinked(COST_BG_NO);

        verify(cost).markTerminalLinked();
        verify(cost, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("행을 잠근 뒤에 결재 상태를 확인한다")
    void locksBeforeApprovalGuard() {
        locked();

        terminalLinkService.markTerminalLinked(COST_BG_NO);

        var ordered = org.mockito.Mockito.inOrder(costRepository, capplaRepository);
        ordered.verify(costRepository).findCurrentVersionForUpdate(COST_BG_NO);
        ordered.verify(capplaRepository)
                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        eq("BCOSTM"), eq(COST_BG_NO), eq(3), anyList());
    }

    @Test
    @DisplayName("결재가 진행 중이면 표시를 막는다")
    void respectsApprovalGuard() {
        Bcostm cost = locked();
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BCOSTM"), eq(COST_BG_NO), eq(3), anyList()))
                .willReturn(true);

        assertThatThrownBy(() -> terminalLinkService.markTerminalLinked(COST_BG_NO))
                .isInstanceOf(IllegalStateException.class);
        verify(cost, never()).markTerminalLinked();
    }

    @Test
    @DisplayName("대상 전산업무비가 없으면 실패한다")
    void requiresExistingCost() {
        given(costRepository.findCurrentVersionForUpdate("COST-NONE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> terminalLinkService.markTerminalLinked("COST-NONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COST-NONE");
    }

    @Test
    @DisplayName("표시는 TMN_YN을 'Y'로 만든다")
    void flagBecomesY() {
        Bcostm cost = Bcostm.builder().costBgNo(COST_BG_NO).bgSno(3).tmnYn("N").build();
        given(costRepository.findCurrentVersionForUpdate(COST_BG_NO)).willReturn(Optional.of(cost));

        terminalLinkService.markTerminalLinked(COST_BG_NO);

        assertThat(cost.getTmnYn()).isEqualTo("Y");
    }
}
