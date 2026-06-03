package com.kdb.it.domain.budget.project.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 품목(Bitemm) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 {@link Bitemm} 엔티티의 복합키를 정의합니다.
 * {@link Bitemm}의 {@code @Id} 필드({@code gclMngNo}, {@code gclSno})와
 * 동일한 이름과 타입을 가져야 합니다.</p>
 *
 * <p>JPA 복합키 클래스 요구사항:</p>
 * <ul>
 *   <li>{@link Serializable} 구현 필수</li>
 *   <li>기본 생성자 필수</li>
 *   <li>{@code equals()}, {@code hashCode()} 재정의 필수</li>
 * </ul>
 */
@Getter             // getter 자동 생성 (Lombok)
@NoArgsConstructor  // JPA 요구사항: 기본 생성자 필수
@AllArgsConstructor // 모든 필드를 받는 생성자 (직접 생성용)
@EqualsAndHashCode  // equals(), hashCode() 자동 생성 (JPA 요구사항)
public class BitemmId implements Serializable {

    /** 품목관리번호: Bitemm.gclMngNo와 이름/타입 일치 필수 */
    private String gclMngNo;

    /** 품목일련번호: Bitemm.sno와 이름/타입 일치 필수 */
    private Integer sno;
}
