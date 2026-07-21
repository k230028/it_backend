package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자체점검(Bchklm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code itPtlCkgItmTc})
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID
 *   <li>itPtlCkgItmTc: 점검항목코드 (CCODEM CKG_ITM_C 체계)
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BchklmId implements Serializable {

    /** 협의회ID: Bchklm.itPtlAsctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 점검항목코드: Bchklm.itPtlCkgItmTc와 이름/타입 일치 필수 */
    private String itPtlCkgItmTc;
}
