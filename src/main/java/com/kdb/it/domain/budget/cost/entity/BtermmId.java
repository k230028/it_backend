package com.kdb.it.domain.budget.cost.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단말기관리마스터(Btermm) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 복합키를 정의합니다.
 * {@link Btermm} 엔티티의 {@code @Id} 필드({@code tmnMngNo}, {@code tmnSno})와
 * 동일한 이름과 타입을 가져야 합니다.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BtermmId implements Serializable {

    /** 단말기관리번호: Btermm.tmnMngNo와 이름/타입 일치 필수 */
    @Column(name = "TMN_MNG_NO", comment = "단말기관리번호")
    private String tmnMngNo;

    /** 단말기일련번호: Btermm.tmnSno와 이름/타입 일치 필수 */
    @Column(name = "TMN_SNO", comment = "단말기일련번호")
    private Integer tmnSno;
}
