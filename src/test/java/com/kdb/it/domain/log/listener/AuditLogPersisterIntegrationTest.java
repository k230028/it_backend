package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@DisplayName("AuditLogPersister Oracle 통합 테스트")
@Import(AuditLogPersister.class)
class AuditLogPersisterIntegrationTest extends AbstractOracleRepositoryTest {

    @Autowired
    private AuditLogPersister auditLogPersister;

    @Test
    @DisplayName("감사 로그 저장 컴포넌트가 테스트 컨텍스트에 로드된다")
    void persisterContextLoads() {
        assertThat(auditLogPersister).isNotNull();
    }
}
