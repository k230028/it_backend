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
 * 추가인증(MFA) 거래 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CMFATM}
 *
 * <p>{@link com.kdb.it.common.mfa.domain.MfaTransaction}의 영속 표현이다. 상태 전이는 {@link
 * MfaTransactionJpaRepository}의 조건부 UPDATE({@code @Modifying})로만 수행하며, 이 엔티티는 조회 결과를 도메인 객체로 되돌리는
 * 매핑에만 쓰인다.
 */
@Entity
@Table(name = "TPRMPP_CMFATM", comment = "추가인증거래기본")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class MfaTransactionEntity extends BaseEntity {

    /** 상태코드: 대기 — {@link com.kdb.it.common.mfa.domain.MfaTransactionStatus#PENDING} */
    static final String STATUS_PENDING = "10";

    /** 상태코드: 검증완료 — {@link com.kdb.it.common.mfa.domain.MfaTransactionStatus#VERIFIED} */
    static final String STATUS_VERIFIED = "20";

    /** 상태코드: 잠금 — {@link com.kdb.it.common.mfa.domain.MfaTransactionStatus#LOCKED} */
    static final String STATUS_LOCKED = "30";

    /** 상태코드: 취소 — {@link com.kdb.it.common.mfa.domain.MfaTransactionStatus#CANCELLED} */
    static final String STATUS_CANCELLED = "40";

    /** 상태코드: 소비완료 — {@link com.kdb.it.common.mfa.domain.MfaTransactionStatus#CONSUMED} */
    static final String STATUS_CONSUMED = "50";

    @Id
    @Column(name = "APN_CER_TR_TOK_CONE", length = 300, comment = "추가인증거래토큰내용")
    private String tokenHash;

    @Column(name = "ENO", nullable = false, length = 32, comment = "사원번호")
    private String eno;

    @Column(name = "APN_CER_USG_TC", nullable = false, length = 2, comment = "IT포탈추가인증용도구분코드")
    private String purposeCode;

    @Column(name = "APN_CER_MNS_TC", nullable = false, length = 2, comment = "IT포탈추가인증수단구분코드")
    private String methodCode;

    @Column(name = "APN_CER_STS_TC", nullable = false, length = 2, comment = "IT포탈추가인증상태구분코드")
    private String statusCode;

    @Column(name = "END_DTM", nullable = false, comment = "종료일시")
    private LocalDateTime endDtm;

    @Column(name = "VRF_DTM", comment = "검증일시")
    private LocalDateTime vrfDtm;

    @Column(name = "FLUR_NOT", nullable = false, comment = "실패횟수")
    private Integer failureCount;

    @Column(name = "APN_CER_TRY_TOK_CONE", length = 300, comment = "추가인증시도토큰내용")
    private String tryTokenHash;

    @Column(name = "APN_CER_CRTT_TOK_CONE", length = 300, comment = "추가인증증표토큰내용")
    private String proofTokenHash;

    @Column(name = "APN_CER_SVC_TR_NO", length = 20, comment = "추가인증서비스거래번호")
    private String svcTrNo;

    /**
     * 신규 대기 거래를 생성한다.
     *
     * <p>로그인/MFA 시작 시점은 {@code AuditorAware}가 개입할 수 없는 비인증 흐름이 대부분이므로, 거래 소유자 사번을 최초·최종 감사자로 명시적으로
     * 기록한다({@code Crtokm.create}와 같은 방식).
     *
     * @param tokenHash 거래 토큰 해시(PK)
     * @param eno 거래 소유자 사번(감사자로도 함께 기록됨)
     * @param purposeCode IT포탈추가인증용도구분코드
     * @param methodCode IT포탈추가인증수단구분코드
     * @param endDtm 만료 시각
     * @param tryTokenHash 공급자 challenge 해시. 없으면 null
     * @return 소유자 사번이 감사자로 기록된 PENDING 상태 신규 엔티티
     */
    public static MfaTransactionEntity create(
            String tokenHash,
            String eno,
            String purposeCode,
            String methodCode,
            LocalDateTime endDtm,
            String tryTokenHash) {
        MfaTransactionEntity entity =
                MfaTransactionEntity.builder()
                        .tokenHash(tokenHash)
                        .eno(eno)
                        .purposeCode(purposeCode)
                        .methodCode(methodCode)
                        .statusCode(STATUS_PENDING)
                        .endDtm(endDtm)
                        .failureCount(0)
                        .tryTokenHash(tryTokenHash)
                        .build();
        entity.initializeAuditActors(eno);
        return entity;
    }
}
