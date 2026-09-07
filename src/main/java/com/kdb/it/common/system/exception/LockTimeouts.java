package com.kdb.it.common.system.exception;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 원인 사슬에서 Oracle 잠금 대기 초과를 판정한다. */
public final class LockTimeouts {

    private LockTimeouts() {}

    /**
     * 예외 사슬 어딘가에 잠금 대기 초과가 있는지 확인한다.
     *
     * <p>순환 참조가 있는 사슬에서도 종료되도록 방문한 예외를 식별자 기준으로 기억한다.
     *
     * @param failure 검사할 예외. null이면 false를 반환한다.
     * @return JPA·Spring 잠금 예외이거나 ORA-30006·ORA-00054이면 true
     */
    public static boolean isLockTimeout(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof jakarta.persistence.LockTimeoutException
                    || cause instanceof org.springframework.dao.CannotAcquireLockException
                    || cause instanceof java.sql.SQLException sql
                            && (sql.getErrorCode() == 30006 || sql.getErrorCode() == 54))
                return true;
        }
        return false;
    }
}
