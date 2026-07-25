package com.kdb.it.common.system.service;

import com.kdb.it.common.system.repository.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 패밀리 폐기 전용 트랜잭션 컴포넌트 (SEC-08 Phase A, Task 4).
 *
 * <p>{@link RefreshTokenRotator#rotate(String)}가 재사용·만료를 감지하면 삭제 없이 마커 예외만 던지고 롤백된다(비관적 쓰기 잠금 해제).
 * 이 컴포넌트는 그 트랜잭션이 완전히 끝난 뒤에만 호출되어야 하며({@link
 * com.kdb.it.common.system.service.AuthService#refreshAccessToken(String)}이 오케스트레이션을 담당한다), {@code
 * REQUIRES_NEW}로 독립된 새 트랜잭션을 열어 패밀리를 삭제한다. {@link
 * com.kdb.it.domain.log.listener.AuditLogWriter#writeInNewTransaction}와 동일하게 {@code flush()}까지 강제해
 * DELETE 오류를 이 트랜잭션 경계 안에서 즉시 확정한다.
 *
 * <p>REQUIRES_NEW는 Spring 프록시(AOP)를 통한 호출에서만 적용되므로, 별도 빈으로 분리되어 있다 — 같은 빈 내부 메서드 호출로는 프록시를 우회해 전파
 * 속성이 적용되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    @PersistenceContext private EntityManager entityManager;

    /**
     * 사번으로 Refresh Token 패밀리를 별도 트랜잭션에서 삭제한다.
     *
     * @param eno 패밀리를 폐기할 토큰 소유자 사번
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeByEno(String eno) {
        refreshTokenRepository.deleteByEno(eno);
        // DELETE 오류를 이 트랜잭션 경계 안에서 확정해 호출자가 즉시 포착하도록 강제한다.
        entityManager.flush();
    }
}
