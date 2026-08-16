package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BudgetReadView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BudgetRepresentativeSelector 단위 테스트 (BE-17 결정 #2: 최신 편성 실행 행 기준) */
class BudgetRepresentativeSelectorTest {

    @Test
    @DisplayName("pick - bgNo가 가장 큰(최신 편성 실행) 행을 선택한다")
    void pick_최신bgNo행선택() {
        Bbugtm older = Bbugtm.builder().bgNo("BG-2026-0001").sno(9).asgRt(new BigDecimal("80")).build();
        Bbugtm newer = Bbugtm.builder().bgNo("BG-2026-0002").sno(1).asgRt(new BigDecimal("50")).build();

        Bbugtm result = BudgetRepresentativeSelector.pick(List.of(older, newer));

        assertThat(result.getAsgRt()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("pick - bgNo 동률이면 sno가 큰 행을 선택한다")
    void pick_bgNo동률_sno최대행선택() {
        Bbugtm first = Bbugtm.builder().bgNo("BG-2026-0001").sno(1).asgRt(new BigDecimal("80")).build();
        Bbugtm second = Bbugtm.builder().bgNo("BG-2026-0001").sno(2).asgRt(new BigDecimal("50")).build();

        Bbugtm result = BudgetRepresentativeSelector.pick(List.of(second, first));

        assertThat(result.getAsgRt()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("pick - bgNo null 행은 후순위로 밀린다")
    void pick_bgNoNull_후순위() {
        Bbugtm nullBgNo = Bbugtm.builder().sno(1).asgRt(new BigDecimal("80")).build();
        Bbugtm withBgNo = Bbugtm.builder().bgNo("BG-2026-0001").sno(1).asgRt(new BigDecimal("50")).build();

        Bbugtm result = BudgetRepresentativeSelector.pick(List.of(nullBgNo, withBgNo));

        assertThat(result.getAsgRt()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("pick - 빈 목록이면 IllegalArgumentException")
    void pick_빈목록_예외() {
        assertThatThrownBy(() -> BudgetRepresentativeSelector.pick(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pickView - 엔티티 선택기와 동일하게 bgNo, sno 내림차순 대표행을 고른다")
    void pickView_엔티티규칙동일() {
        BudgetReadView older =
                ReadProjectionStubs.budget(
                        Bbugtm.builder().bgNo("BG-2026-0001").sno(9).asgRt(new BigDecimal("80")).build());
        BudgetReadView newer =
                ReadProjectionStubs.budget(
                        Bbugtm.builder().bgNo("BG-2026-0002").sno(1).asgRt(new BigDecimal("50")).build());

        assertThat(BudgetRepresentativeSelector.pickView(List.of(newer, older)).getAsgRt())
                .isEqualByComparingTo("50");
        assertThatThrownBy(() -> BudgetRepresentativeSelector.pickView(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
