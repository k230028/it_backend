package com.kdb.it.common.approval.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 프로젝트관리 공통신청서관계 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CAPPLA}
 *
 * <p>신청서({@link Capplm})와 신청 대상 원천 데이터(프로젝트, 전산관리비 등)를 연결하는 관계 테이블입니다.
 *
 * <p>사용 예시:
 *
 * <ul>
 *   <li>정보화사업(PRJ_MNG_NO) 신청서 → {@code FNT_TB_NM='BPROJM'}, {@code PK_COL_NM=PRJ_MNG_NO}
 * </ul>
 */
@Entity
@Table(name = "TPRMPP_CAPPLA", comment = "프로젝트관리 공통신청서관계")
@IdClass(CapplaId.class)
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cappla extends BaseEntity {

    /**
     * 신청서일련번호: 신청서(APF_DCM_NO) 안에서 원천 연결 행에 1부터 매기는 순번입니다. APF_DCM_NO와 함께 물리 복합 PK를 구성하며, 한 신청서에 몇
     * 건의 원천(사업·전산업무비)이 묶였는지 이 값으로 파악합니다.
     *
     * <p>시퀀스로 채번하지 않고 생성 지점({@code ApplicationPersistenceService}, {@code ApprovalStamper})이 같은
     * 트랜잭션 안에서 직접 할당합니다. 과거 시퀀스 채번 행(전역 일련번호)은 그대로 두며 PK 유일성에는 영향이 없습니다.
     */
    @Id
    @Column(name = "APF_SNO", nullable = false, comment = "신청서일련번호")
    private Long apfSno;

    /** 신청서식별번호: 연결된 신청서의 식별번호 (Capplm.apfMngNo 참조) 형식: APF-{연도}-{8자리 시퀀스} (예: APF-2026-00000001) */
    @Id
    @Column(name = "APF_DCM_NO", length = 64, nullable = false, comment = "신청서식별번호")
    private String apfDcmNo;

    /** 원천테이블명: 신청 대상이 속한 테이블 명칭 (예: 'BPROJM'=정보화사업, 'BCOSTM'=전산관리비) */
    @Column(name = "FNT_TB_NM", length = 120, comment = "원천테이블명")
    private String fntTbNm;

    /** 주식별자컬럼명: 신청 대상 레코드의 기본키 컬럼명 (예: 'PRJ_MNG_NO', 'IT_MNGC_NO') */
    @Column(name = "PK_COL_NM", length = 4000, comment = "주식별자컬럼명")
    private String pkColNm;

    /** 원천테이블적재일련번호: 신청 대상 레코드의 적재 일련번호 */
    @Column(name = "FNT_TB_CRY_SNO", comment = "원천테이블적재일련번호")
    private Integer fntTbCrySno;
}
