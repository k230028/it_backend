package com.kdb.it.domain.budget.project.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정보화사업(BPROJM) 엔티티의 복합 기본키 클래스
 *
 * <p>
 * JPA의 {@code @IdClass} 방식으로 복합키를 정의합니다.
 * {@link Bprojm} 엔티티의 {@code @Id} 필드({@code prjMngNo}, {@code prjSno})와
 * 동일한 이름과 타입을 가져야 합니다.
 * </p>
 *
 * <p>
 * JPA 복합키 클래스 요구사항:
 * </p>
 * <ul>
 * <li>{@link Serializable} 구현 필수</li>
 * <li>기본 생성자({@code @NoArgsConstructor}) 필수</li>
 * <li>{@code equals()} 및 {@code hashCode()} 재정의 필수
 * ({@code @EqualsAndHashCode})</li>
 * </ul>
 */
@Getter // getter 자동 생성 (Lombok)
@NoArgsConstructor // JPA 요구사항: 기본 생성자 필수
@AllArgsConstructor // 모든 필드를 받는 생성자 (테스트 및 직접 생성용)
@EqualsAndHashCode // equals(), hashCode() 자동 생성 (JPA 요구사항: 동등성 비교)
public class BprojmId implements Serializable {

    /** 프로젝트관리번호 (예: PRJ-2026-0001): Bprojm.prjMngNo와 이름/타입 일치 필수 */
    @Column(name = "ABUS_MNG_NO", comment = "프로젝트관리번호")
    private String prjMngNo;

    /** 프로젝트순번 (동일 관리번호 내 버전 구분, 1부터 시작): Bprojm.prjSno와 이름/타입 일치 필수 */
    @Column(name = "SNO", comment = "프로젝트순번")
    private Integer prjSno;
}
