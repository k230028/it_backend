package com.kdb.it.domain.budget.plan.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정보기술부문계획 관계(BPLANA) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 복합키를 정의합니다. {@link Bplana} 엔티티의 {@code @Id} 필드({@code prjMngNo},
 * {@code reqDocNo})와 동일한 이름과 타입을 가져야 합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BplanaId implements Serializable {

    /** 프로젝트관리번호 (예: PRJ-2026-0001) */
    @Column(name = "ABUS_MNG_NO", comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 요청문서번호 (예: PLN-2026-0001) */
    @Column(name = "REQ_DOC_NO", comment = "요청문서번호")
    private String reqDocNo;

    /** 계획 개정 순번 */
    @Column(name = "SNO", comment = "계획일련번호")
    private Integer sno;
}
