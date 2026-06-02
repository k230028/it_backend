package com.kdb.it.domain.budget.cost.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전산관리비(Bcostm) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 복합키를 정의합니다.
 * {@link Bcostm} 엔티티의 {@code @Id} 필드({@code itMngcNo}, {@code itMngcSno})와
 * 동일한 이름과 타입을 가져야 합니다.</p>
 *
 * <p>JPA 복합키 클래스 요구사항:</p>
 * <ul>
 *   <li>{@link Serializable} 구현 필수</li>
 *   <li>기본 생성자({@code @NoArgsConstructor}) 필수</li>
 *   <li>{@code equals()} 및 {@code hashCode()} 재정의 필수 ({@code @EqualsAndHashCode})</li>
 * </ul>
 */
@Getter             // getter 자동 생성 (Lombok)
@NoArgsConstructor  // JPA 요구사항: 기본 생성자 필수
@AllArgsConstructor // 모든 필드를 받는 생성자 (테스트 및 직접 생성용)
@EqualsAndHashCode  // equals(), hashCode() 자동 생성 (JPA 요구사항: 동등성 비교)
public class BcostmId implements Serializable {

    /** 전산업무비코드(IT관리비관리번호): Bcostm.itMngcNo와 이름/타입 일치 필수 */
    @Column(name = "BG_NO", comment = "전산업무비코드")
    private String itMngcNo;

    /** 전산업무비일련번호(IT관리비일련번호): Bcostm.itMngcSno와 이름/타입 일치 필수 */
    @Column(name = "SNO", comment = "전산업무비일련번호")
    private Integer itMngcSno;
}
