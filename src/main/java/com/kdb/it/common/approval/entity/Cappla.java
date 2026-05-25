package com.kdb.it.common.approval.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 신청서-원본 데이터 관계 관리 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_CAPPLA}</p>
 *
 * <p>신청서({@link Capplm})와 신청 대상 원본 데이터(프로젝트, 전산관리비 등)를
 * 연결하는 관계 테이블입니다.</p>
 *
 * <p>사용 예시:</p>
 * <ul>
 *   <li>프로젝트(PRJ_MNG_NO)에 대한 신청서 → {@code ORC_TB_CD='BPROJM'}, {@code ORC_PK_VL=PRJ_MNG_NO}</li>
 * </ul>
 *
 * <p>이 테이블을 통해 하나의 신청서가 어떤 원본 데이터를 대상으로 하는지,
 * 또는 특정 원본 데이터에 대해 어떤 신청서가 발행되었는지를 조회할 수 있습니다.</p>
 */
@Entity                                              // JPA 엔티티로 등록
@Table(name = "TPRMPP_CAPPLA", comment = "신청서-원본 데이터 관계")                       // 매핑할 DB 테이블명
@Getter                                              // 모든 필드의 getter 자동 생성 (Lombok)
@SuperBuilder                                        // 상속 구조에서 Builder 패턴 지원
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // protected 기본 생성자 (JPA 요구사항)
@AllArgsConstructor                                  // 전체 필드 생성자 자동 생성
public class Cappla extends BaseEntity {

    /** 신청서관계일련번호: 기본키. 서비스 계층에서 신청서 단위 1~N 명시 채번 */
    @Id
    @Column(name = "APF_REL_SNO", nullable = false, comment = "신청서관계일련번호 (신청서 단위 1~N)")
    private Long apfRelSno;

    /**
     * 신청서관리번호: 연결된 신청서의 관리번호 (Capplm.apfMngNo 참조)
     * 형식: APF_{연도}{8자리 시퀀스} (예: APF_202600000001)
     */
    @Column(name = "APF_MNG_NO", length = 32, nullable = false, comment = "신청서관리번호")
    private String apfMngNo;

    /**
     * 원본테이블코드: 신청 대상이 속한 테이블 코드
     * (예: 'BPROJM'=정보화사업, 'BCOSTM'=전산관리비)
     */
    @Column(name = "ORC_TB_CD", length = 10, comment = "원본테이블코드")
    private String orcTbCd;

    /**
     * 원본PK값: 신청 대상 레코드의 기본키 값
     * (예: 프로젝트 관리번호 'PRJ-2026-0001')
     */
    @Column(name = "ORC_PK_VL", length = 32, comment = "원본PK값")
    private String orcPkVl;

    /**
     * 원본일련번호값: 신청 대상 레코드의 일련번호
     * (복합키 테이블의 경우 두 번째 키 값, 예: PRJ_SNO)
     */
    @Column(name = "ORC_SNO_VL", comment = "원본일련번호값")
    private Integer orcSnoVl;
}
