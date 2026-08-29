package com.kdb.it.domain.log.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BitemmL ORM 매핑이 물리 DDL의 사업관리번호 길이와 일치하는지 고정한다. */
class BitemmLColumnContractTest {

    @Test
    @DisplayName("사업관리번호 컬럼 길이가 30이다")
    void 사업관리번호_컬럼길이_30() throws Exception {
        Column abusMngNo = BitemmL.class.getDeclaredField("abusMngNo").getAnnotation(Column.class);

        assertThat(abusMngNo.length()).isEqualTo(30);
    }
}
