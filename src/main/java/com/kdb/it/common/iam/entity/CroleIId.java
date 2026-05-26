package com.kdb.it.common.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 역할관리(TPRMPP_CROLEI) 복합키 클래스
 *
 * <p>
 * ATH_ID(권한ID) + ENO(사원번호)로 구성된 복합 기본키입니다.
 * 한 사용자(ENO)가 여러 자격등급(ATH_ID)을 가질 수 있는 구조를 지원합니다.
 * </p>
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CroleIId implements Serializable {

    /** 권한ID: 자격등급 식별자 (예: ITPZZ001, ITPZZ002, ITPAD001) */
    @Column(name = "ATH_ID", length = 32, comment = "권한ID")
    private String athId;

    /** 사원번호: 자격등급을 부여받은 사용자의 사번 */
    @Column(name = "ENO", length = 32, comment = "사원번호")
    private String eno;
}
