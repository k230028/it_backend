package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가위원(Bcmmtm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code eno})
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID
 *   <li>eno: 위원 사번
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BcmmtmId implements Serializable {

    /** 협의회ID: Bcmmtm.asctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 사번: Bcmmtm.eno와 이름/타입 일치 필수 */
    private String eno;
}
