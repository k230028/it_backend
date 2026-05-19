package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 협의회 일정(Bschdm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code asctId}, {@code eno}, {@code dsdDt}, {@code dsdTm})</p>
 *
 * <ul>
 *   <li>asctId: 협의회ID</li>
 *   <li>eno: 위원 사번</li>
 *   <li>dsdDt: 일정일자</li>
 *   <li>dsdTm: 일정시간 (10:00/14:00/15:00/16:00)</li>
 * </ul>
 *
 * <p>4컬럼 복합키로, 위원별 날짜×시간대 조합을 고유하게 식별합니다.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BschdmId implements Serializable {

    /** 협의회ID: Bschdm.asctId와 이름/타입 일치 필수 */
    private String asctId;

    /** 사번: Bschdm.eno와 이름/타입 일치 필수 */
    private String eno;

    /** 일정일자: Bschdm.dsdDt와 이름/타입 일치 필수 (DT 도메인 VARCHAR2(8) yyyyMMdd) */
    private String dsdDt;

    /** 일정시간: Bschdm.dsdTm와 이름/타입 일치 필수 */
    private String dsdTm;
}
