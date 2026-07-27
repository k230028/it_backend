package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ItemRepresentativeSelector 단위 테스트 (BE-17 결정 #1: LST_YN='Y' 우선, 없으면 SNO 최대) */
class ItemRepresentativeSelectorTest {

    @Test
    @DisplayName("pick - LST_YN='Y' 행이 리스트 뒤에 있어도 우선 선택된다")
    void pick_lstYnY행_우선선택() {
        Bitemm oldVersion =
                Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("N").abusMngNo("PRJ-OLD").build();
        Bitemm latest =
                Bitemm.builder().gclMngNo("GCL-1").sno(2).lstYn("Y").abusMngNo("PRJ-NEW").build();

        Bitemm result = ItemRepresentativeSelector.pick(List.of(oldVersion, latest));

        assertThat(result.getAbusMngNo()).isEqualTo("PRJ-NEW");
    }

    @Test
    @DisplayName("pick - LST_YN='Y' 행이 없으면 SNO 최대 행으로 폴백한다")
    void pick_lstYnY없음_sno최대폴백() {
        Bitemm sno1 = Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("N").abusMngNo("PRJ-1").build();
        Bitemm sno3 = Bitemm.builder().gclMngNo("GCL-1").sno(3).lstYn("N").abusMngNo("PRJ-3").build();
        Bitemm sno2 = Bitemm.builder().gclMngNo("GCL-1").sno(2).lstYn("N").abusMngNo("PRJ-2").build();

        Bitemm result = ItemRepresentativeSelector.pick(List.of(sno1, sno3, sno2));

        assertThat(result.getAbusMngNo()).isEqualTo("PRJ-3");
    }

    @Test
    @DisplayName("pick - LST_YN='Y' 행이 2건이면 SNO 최대 행을 채택한다 (WARN 로그, 장애 없음)")
    void pick_lstYnY중복_sno최대채택() {
        Bitemm y1 = Bitemm.builder().gclMngNo("GCL-1").sno(1).lstYn("Y").abusMngNo("PRJ-1").build();
        Bitemm y2 = Bitemm.builder().gclMngNo("GCL-1").sno(2).lstYn("Y").abusMngNo("PRJ-2").build();

        Bitemm result = ItemRepresentativeSelector.pick(List.of(y1, y2));

        assertThat(result.getAbusMngNo()).isEqualTo("PRJ-2");
    }

    @Test
    @DisplayName("pick - 빈 목록이면 IllegalArgumentException")
    void pick_빈목록_예외() {
        assertThatThrownBy(() -> ItemRepresentativeSelector.pick(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
