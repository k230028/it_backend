package com.kdb.it.common.code.repository;

import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("로컬 Oracle 통합 테스트 하네스 스모크")
class OracleHarnessSmokeTest extends AbstractOracleRepositoryTest {

    @Autowired
    CodeRepository codeRepository;

    @Test
    @DisplayName("실 스키마에 연결되어 JPQL 라운드트립이 동작한다")
    void connectsToRealSchema_andRunsJpql() {
        // CURRENT_SCHEMA=ITPOWN의 TPRMPP_CCODEM에 연결되어 카운트가 음수가 아니다.
        assertThat(codeRepository.count()).isNotNegative();
        // 존재하지 않는 복합키 조회는 데이터 변동과 무관하게 항상 false (결정적).
        assertThat(codeRepository.existsByCIdAndCdvaAndSttDt(
                "ZZ_NONEXIST", "ZZ_NONEXIST", "00000000")).isFalse();
    }
}
