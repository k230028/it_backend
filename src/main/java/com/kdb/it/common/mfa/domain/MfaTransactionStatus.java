package com.kdb.it.common.mfa.domain;

/** MFA 거래의 처리 상태다. */
public enum MfaTransactionStatus {
    PENDING,
    VERIFIED,
    LOCKED,
    EXPIRED
}
