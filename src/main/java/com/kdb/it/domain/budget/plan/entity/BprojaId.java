package com.kdb.it.domain.budget.plan.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정보화사업 관계(BPROJA) 엔티티의 복합 기본키 클래스
 *
 * <p>
 * JPA의 {@code @IdClass} 방식으로 복합키를 정의합니다.
 * {@link Bproja} 엔티티의 {@code @Id} 필드({@code prjMngNo}, {@code bzMngNo})와
 * 동일한 이름과 타입을 가져야 합니다.
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BprojaId implements Serializable {

    /** 프로젝트관리번호 (예: PRJ-2026-0001) */
    @Column(name = "PRJ_MNG_NO", comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 업무관리번호 (예: PLN-2026-0001) */
    @Column(name = "BZ_MNG_NO", comment = "업무관리번호")
    private String bzMngNo;
}
