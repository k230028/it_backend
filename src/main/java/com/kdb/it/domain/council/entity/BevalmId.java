package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가의견(Bevalm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code eno}, {@code itPtlCkgItmTc})</p>
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID</li>
 *   <li>eno: 평가위원 사번</li>
 *   <li>itPtlCkgItmTc: 점검항목코드 (BCHKLC와 동일 코드체계)</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BevalmId implements Serializable {

    /** 협의회ID: Bevalm.asctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 사번: Bevalm.eno와 이름/타입 일치 필수 */
    private String eno;

    /** 점검항목코드: Bevalm.ckgItmC와 이름/타입 일치 필수 */
    private String itPtlCkgItmTc;
}
