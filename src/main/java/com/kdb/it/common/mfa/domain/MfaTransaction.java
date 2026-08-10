package com.kdb.it.common.mfa.domain;

import java.time.Instant;
import java.util.Objects;

/** 상태 전이를 새 인스턴스로 반환하는 불변 MFA 거래다. */
public final class MfaTransaction {

    private final String tokenHash;
    private final String eno;
    private final MfaPurpose purpose;
    private final MfaMethod method;
    private final Instant expiresAt;
    private final MfaTransactionStatus status;
    private final Instant verifiedAt;
    private final int failureCount;

    private MfaTransaction(
            String tokenHash,
            String eno,
            MfaPurpose purpose,
            MfaMethod method,
            Instant expiresAt,
            MfaTransactionStatus status,
            Instant verifiedAt,
            int failureCount) {
        Objects.requireNonNull(tokenHash, "토큰 해시는 필수입니다.");
        Objects.requireNonNull(eno, "사원번호는 필수입니다.");
        Objects.requireNonNull(purpose, "MFA 목적은 필수입니다.");
        Objects.requireNonNull(method, "MFA 수단은 필수입니다.");
        Objects.requireNonNull(expiresAt, "만료 시각은 필수입니다.");
        Objects.requireNonNull(status, "MFA 상태는 필수입니다.");
        if (failureCount < 0) {
            throw new IllegalArgumentException("실패 횟수는 음수일 수 없습니다.");
        }
        if (status == MfaTransactionStatus.VERIFIED && verifiedAt == null) {
            throw new IllegalArgumentException("검증 완료 거래에는 검증 시각이 필요합니다.");
        }
        if ((status == MfaTransactionStatus.PENDING || status == MfaTransactionStatus.LOCKED)
                && verifiedAt != null) {
            throw new IllegalArgumentException("대기 또는 잠금 거래에는 검증 시각이 있을 수 없습니다.");
        }
        this.tokenHash = tokenHash;
        this.eno = eno;
        this.purpose = purpose;
        this.method = method;
        this.expiresAt = expiresAt;
        this.status = status;
        this.verifiedAt = verifiedAt;
        this.failureCount = failureCount;
    }

    /** 대기 상태의 MFA 거래를 생성한다. */
    public static MfaTransaction pending(
            String tokenHash, String eno, MfaPurpose purpose, MfaMethod method, Instant expiresAt) {
        return new MfaTransaction(
                tokenHash, eno, purpose, method, expiresAt, MfaTransactionStatus.PENDING, null, 0);
    }

    /** 만료되지 않은 대기 거래를 검증 완료 상태로 전이한다. */
    public MfaTransaction verify(Instant now) {
        if (isExpiredAt(now)) {
            return withStatus(MfaTransactionStatus.EXPIRED, verifiedAt, failureCount);
        }
        if (status != MfaTransactionStatus.PENDING) {
            return this;
        }
        return withStatus(MfaTransactionStatus.VERIFIED, now, failureCount);
    }

    /** 실패 횟수를 올리고 최대 횟수에 도달하면 거래를 잠근다. */
    public MfaTransaction fail(Instant now, int maxFailures) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("최대 실패 횟수는 1 이상이어야 합니다.");
        }
        if (isExpiredAt(now)) {
            return withStatus(MfaTransactionStatus.EXPIRED, verifiedAt, failureCount);
        }
        if (status != MfaTransactionStatus.PENDING) {
            return this;
        }
        int nextFailureCount = failureCount + 1;
        MfaTransactionStatus nextStatus =
                nextFailureCount >= maxFailures
                        ? MfaTransactionStatus.LOCKED
                        : MfaTransactionStatus.PENDING;
        return withStatus(nextStatus, null, nextFailureCount);
    }

    /** 지정 시각에 거래가 만료됐는지 확인한다. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private MfaTransaction withStatus(
            MfaTransactionStatus nextStatus, Instant nextVerifiedAt, int nextFailureCount) {
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                nextStatus,
                nextVerifiedAt,
                nextFailureCount);
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String eno() {
        return eno;
    }

    public MfaPurpose purpose() {
        return purpose;
    }

    public MfaMethod method() {
        return method;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public MfaTransactionStatus status() {
        return status;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public int failureCount() {
        return failureCount;
    }
}
