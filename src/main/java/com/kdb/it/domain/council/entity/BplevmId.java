package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업별 평가의견(Bplevm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code itPtlAsctId}, {@code eno}, {@code abusMngNo})
 *
 * <ul>
 *   <li>itPtlAsctId: 협의회ID
 *   <li>eno: 평가위원 사번
 *   <li>abusMngNo: 사업관리번호 (심의 대상 정보화사업)
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BplevmId implements Serializable {

    /** 협의회ID: Bplevm.itPtlAsctId와 이름/타입 일치 필수 */
    private String itPtlAsctId;

    /** 사번: Bplevm.eno와 이름/타입 일치 필수 */
    private String eno;

    /** 사업관리번호: Bplevm.abusMngNo와 이름/타입 일치 필수 */
    private String abusMngNo;
}
