package com.kdb.it.common.approval.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 결재(Cdecim) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 {@link Cdecim} 엔티티의 복합키를 정의합니다. {@link Cdecim}의 {@code @Id}
 * 필드({@code dcdMngNo}, {@code dcdSqn})와 동일한 이름과 타입을 가져야 합니다.
 *
 * <p>JPA 복합키 클래스 요구사항:
 *
 * <ul>
 *   <li>{@link Serializable} 구현 필수
 *   <li>기본 생성자 필수
 *   <li>{@code equals()}, {@code hashCode()} 재정의 필수
 * </ul>
 */
@NoArgsConstructor // JPA 요구사항: 기본 생성자 필수
@AllArgsConstructor // 모든 필드를 받는 생성자 (직접 생성용)
@EqualsAndHashCode // equals(), hashCode() 자동 생성 (JPA 1차 캐시 동등성 비교에 필수)
public class CdecimId implements Serializable {

    /** 신청서식별번호: Cdecim.dcdMngNo와 이름/타입 일치 필수 */
    private String dcdMngNo;

    /** 결재자순서일련번호: Cdecim.dcrSqnSno와 이름/타입 일치 필수 */
    private Integer dcrSqnSno;
}
