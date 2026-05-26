package com.kdb.it.common.iam.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.kdb.it.domain.entity.BaseEntity;

/**
 * 조직(부점) 정보 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_CORGNI}
 * </p>
 *
 * <p>
 * 회사 내 부점(부서/팀)의 조직 정보를 관리합니다.
 * 계층형 구조로 상위 조직과의 관계를 {@code PRLM_HRK_OGZ_C_CONE}으로 표현합니다.
 * </p>
 *
 * <p>
 * 연관 관계:
 * </p>
 * <ul>
 * <li>{@link CuserI}와 ManyToOne 관계 ({@code BBR_C} →
 * {@code PRLM_OGZ_C_CONE})</li>
 * </ul>
 *
 * <p>
 * 조직 코드는 {@link CuserI#bbrC} 필드와 조인하여 사용자의 소속 부점명을 조회합니다.
 * </p>
 */
@Entity // JPA 엔티티로 등록
@Table(name = "TPRMPP_CORGNI", comment = "조직(부점) 정보") // 매핑할 DB 테이블명
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor // 전체 필드 생성자 자동 생성
@SuperBuilder // 상속 구조에서 Builder 패턴 지원
public class CorgnI extends BaseEntity {

    /**
     * 조직코드: 기본키. 조직을 고유하게 식별하는 코드
     * (예: "001", "HR001" 등 최대 100자)
     * {@link CuserI}의 {@code BBR_C} 컬럼과 조인 대상
     */
    @Id
    @Column(name = "PRLM_OGZ_C_CONE", nullable = false, length = 100, comment = "인사조직코드내용")
    private String prlmOgzCCone;

    /** 항목순서일련번호: 동일 레벨 조직 간의 표시 순서 */
    @Column(name = "ITM_SQN_SNO", comment = "항목순서일련번호")
    private Integer itmSqnSno;

    /**
     * 인사상위조직코드내용: 이 조직의 부모 조직 코드
     * 최상위 조직의 경우 null이거나 자기 자신을 가리킴
     * 조직 트리(계층 구조) 구성에 사용
     */
    @Column(name = "PRLM_HRK_OGZ_C_CONE", length = 100, comment = "인사상위조직코드내용")
    private String prlmHrkOgzCCone;

    /** 부점영문명: 조직의 영문 명칭 (예: "IT Department") */
    @Column(name = "BBR_WREN_NM", length = 100, comment = "부점영문명")
    private String bbrWrenNm;

    /** 부점명: 조직의 한글 명칭 (예: "IT본부", "인사팀") */
    @Column(name = "BBR_NM", length = 100, comment = "부점명")
    private String bbrNm;

    /**
     * 상위조직 엔티티: PRLM_HRK_OGZ_C_CONE으로 자기 참조하여 상위 조직 정보 접근
     *
     * <p>
     * {@code insertable=false, updatable=false}: 이 컬럼을 직접 INSERT/UPDATE하지 않고
     * {@code prlmHrkOgzCCone} 필드를 통해서만 관리 (읽기 전용 조인)
     * </p>
     * <p>
     * {@code FetchType.LAZY}: 실제 접근 시에만 조회 (EntityGraph로 즉시 로딩 가능)
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRLM_HRK_OGZ_C_CONE", referencedColumnName = "PRLM_OGZ_C_CONE", insertable = false, updatable = false)
    private CorgnI parentOrganization;

    /**
     * 조직 정보 업데이트 (Dirty Checking 활용)
     *
     * @param bbrNm           부점명
     * @param bbrWrenNm       부점영문명
     * @param itmSqnSno       순서
     * @param prlmHrkOgzCCone 상위조직코드
     */
    public void update(String bbrNm, String bbrWrenNm, Integer itmSqnSno, String prlmHrkOgzCCone) {
        this.bbrNm           = bbrNm;
        this.bbrWrenNm       = bbrWrenNm;
        this.itmSqnSno       = itmSqnSno;
        this.prlmHrkOgzCCone = prlmHrkOgzCCone;
    }
}
