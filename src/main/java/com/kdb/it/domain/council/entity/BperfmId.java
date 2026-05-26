package com.kdb.it.domain.council.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성과지표(Bperfm) 복합 기본키 클래스
 *
 * <p>복합키 구성: ({@code asctId}, {@code dtpSno})</p>
 *
 * <ul>
 *   <li>asctId: 협의회ID</li>
 *   <li>dtpSno: 지표순번 (1부터 시작, 담당자가 동적으로 추가)</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BperfmId implements Serializable {

    /** 협의회ID: Bperfm.asctId와 이름/타입 일치 필수 */
    private String asctId;

    /** 지표순번: Bperfm.dtpSno와 이름/타입 일치 필수 */
    private Integer dtpSno;
}
