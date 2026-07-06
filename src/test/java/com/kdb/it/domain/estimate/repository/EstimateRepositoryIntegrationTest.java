package com.kdb.it.domain.estimate.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("EstimateRepository Oracle 통합 테스트")
class EstimateRepositoryIntegrationTest extends AbstractOracleRepositoryTest {

    @Autowired
    private EstimateRepository estimateRepository;

    @Test
    @DisplayName("search는 Oracle 스키마에서 예외 없이 실행된다")
    void search_executesWithoutThrowing() {
        assertThatCode(() -> estimateRepository.search(null, null, null))
                .doesNotThrowAnyException();
    }
}
