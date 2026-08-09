package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가위원(Bcmmtm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code itPtlAsctMebTc}, {@code eno})
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID
 *   <li>itPtlAsctMebTc: 위원유형구분코드
 *   <li>eno: 위원 사번
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BcmmtmId implements Serializable {

    /** 협의회ID: Bcmmtm.itPtlAsctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 위원유형구분코드: Bcmmtm.itPtlAsctMebTc와 이름/타입 일치 필수 */
    private String itPtlAsctMebTc;

    /** 사번: Bcmmtm.eno와 이름/타입 일치 필수 */
    private String eno;
}
