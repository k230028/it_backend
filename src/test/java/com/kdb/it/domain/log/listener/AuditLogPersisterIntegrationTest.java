package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AuditLogPersister Oracle 통합 테스트")
@Import(AuditLogPersister.class)
class AuditLogPersisterIntegrationTest extends AbstractOracleRepositoryTest {

    @Autowired
    private AuditLogPersister auditLogPersister;

    // ERR-06 이후 AuditLogPersister는 생성자로 아래 협력 빈을 주입받는다. @DataJpaTest 슬라이스에는
    // 두 @Component가 포함되지 않으므로, 빈 로드 스모크 검증을 위해 목으로 대체한다.
    @MockitoBean
    private AuditLogWriter auditLogWriter;

    @MockitoBean
    private AuditFailureRecorder auditFailureRecorder;

    @Test
    @DisplayName("감사 로그 저장 컴포넌트가 테스트 컨텍스트에 로드된다")
    void persisterContextLoads() {
        assertThat(auditLogPersister).isNotNull();
    }
}
