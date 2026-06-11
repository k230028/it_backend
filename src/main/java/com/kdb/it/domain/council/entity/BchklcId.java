package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 타당성 자체점검(Bchklc) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code itPtlCkgItmTc})</p>
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID</li>
 *   <li>itPtlCkgItmTc: 점검항목코드 (MGMT_STR/FIN_EFC/RISK_IMP/REP_IMP/DUP_SYS/ETC)</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BchklcId implements Serializable {

    /** 협의회ID: Bchklc.asctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 점검항목코드: Bchklc.ckgItmC와 이름/타입 일치 필수 */
    private String itPtlCkgItmTc;
}
