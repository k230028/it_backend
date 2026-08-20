package com.kdb.it.common.mfa.domain;

/** MFA 거래의 처리 상태다. */
public enum MfaTransactionStatus {
    PENDING,
    VERIFIED,
    LOCKED,
    EXPIRED,
    /** 사용자가 진행 중 거래를 취소했다. JPA 저장소에서만 영속되며 메모리 저장소는 즉시 제거한다. */
    CANCELLED,
    /** 1회용 증표가 정확히 한 번 소비됐다. JPA 저장소에서만 영속되며 메모리 저장소는 즉시 제거한다. */
    CONSUMED
}
