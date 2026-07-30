package com.kdb.it.common.system.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kdb.it.common.system.repository.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RefreshTokenRevoker 단위 테스트 (SEC-08 Phase A, Task 4).
 *
 * <p>{@code REQUIRES_NEW} 트랜잭션에서 {@code deleteByEno} 실행 후 {@code flush()}까지 호출되는지 순서까지 포함해 검증한다
 * ({@link com.kdb.it.domain.log.listener.AuditLogWriter}와 동일한 패턴).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRevokerTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks private RefreshTokenRevoker revoker;

    @BeforeEach
    void setUp() {
        // Mockito @InjectMocks는 생성자(RefreshTokenRepository)가 존재하면 생성자 주입만 수행하고
        // @PersistenceContext 필드(entityManager)는 채우지 않으므로 수동으로 주입한다.
        ReflectionTestUtils.setField(revoker, "entityManager", entityManager);
    }

    @Test
    @DisplayName("revokeByEno - deleteByEno 실행 후 flush()를 호출한다")
    void revokeByEno_삭제후flush호출() {
        // when
        revoker.revokeByEno("10001");

        // then: deleteByEno가 먼저, flush가 그 다음이어야 한다.
        InOrder order = inOrder(refreshTokenRepository, entityManager);
        order.verify(refreshTokenRepository).deleteByEno("10001");
        order.verify(entityManager).flush();
        verifyNoMoreInteractions(refreshTokenRepository, entityManager);
    }
}
