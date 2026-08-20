package com.kdb.it.common.mfa.store;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 로그인 대기(MFA 이전) 거래 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CMFADM}
 *
 * <p>{@link com.kdb.it.common.mfa.domain.LoginPendingTransaction}의 영속 표현이다. 상태 컬럼이 없어 1회
 * 소비는 {@link LoginPendingTransactionJpaRepository}의 조건부 물리 삭제로 표현한다.
 */
@Entity
@Table(name = "TPRMPP_CMFADM", comment = "추가인증대기기본")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class LoginPendingTransactionEntity extends BaseEntity {

    @Id
    @Column(name = "APN_CER_TR_TOK_CONE", length = 300, comment = "추가인증거래토큰내용")
    private String tokenHash;

    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    @Column(name = "END_DTM", nullable = false, comment = "종료일시")
    private LocalDateTime endDtm;

    /**
     * 신규 로그인 대기 거래를 생성한다. 로그인 1단계는 비인증 흐름이므로 소유자 사번을 감사자로
     * 명시적으로 기록한다.
     *
     * @param tokenHash 거래 토큰 해시(PK)
     * @param eno 거래 소유자 사번(감사자로도 함께 기록됨)
     * @param endDtm 만료 시각
     * @return 소유자 사번이 감사자로 기록된 신규 엔티티
     */
    public static LoginPendingTransactionEntity create(String tokenHash, String eno, LocalDateTime endDtm) {
        LoginPendingTransactionEntity entity =
                LoginPendingTransactionEntity.builder().tokenHash(tokenHash).eno(eno).endDtm(endDtm).build();
        entity.initializeAuditActors(eno);
        return entity;
    }
}
