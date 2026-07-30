package com.kdb.it.domain.budget.plan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bplana ORM 매핑이 물리 DDL(TPRMPP_BPLANA, VARCHAR2(30 CHAR))과 일치하는지 고정하는 계약 테스트. */
class BplanaColumnContractTest {

    @Test
    @DisplayName("복합키 컬럼 길이가 물리 DDL(30)과 일치한다")
    void 복합키_컬럼길이_물리DDL_일치() throws Exception {
        Column abus = Bplana.class.getDeclaredField("prjMngNo").getAnnotation(Column.class);
        Column req = Bplana.class.getDeclaredField("reqDocNo").getAnnotation(Column.class);

        assertThat(abus.length()).isEqualTo(30);
        assertThat(req.length()).isEqualTo(30);
    }
}
