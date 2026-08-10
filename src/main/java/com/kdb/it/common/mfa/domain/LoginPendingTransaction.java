package com.kdb.it.common.mfa.domain;

import java.time.Instant;
import java.util.Objects;

/** MFA 완료 전 로그인 요청을 보관하는 불변 거래다. */
public record LoginPendingTransaction(String tokenHash, String eno, Instant expiresAt) {

    public LoginPendingTransaction {
        Objects.requireNonNull(tokenHash, "토큰 해시는 필수입니다.");
        Objects.requireNonNull(eno, "사원번호는 필수입니다.");
        Objects.requireNonNull(expiresAt, "만료 시각은 필수입니다.");
    }

    /** 지정 시각에 로그인 대기 거래가 만료됐는지 확인한다. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
