package com.kdb.it.infra.file.entity;

import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 공통 첨부파일 관리 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_CFILEM}
 * </p>
 *
 * <p>
 * 시스템 전역에서 사용되는 첨부파일(이미지 포함)의 메타데이터를 관리합니다.
 * 원본구분(ORC_DTT)과 원본PK값(ORC_PK_VL)으로 어느 도메인 데이터에 연결된
 * 파일인지 식별합니다.
 * </p>
 *
 * <p>
 * 관리번호 형식: {@code FL_{8자리 시퀀스}} (예: {@code FL_00000001})
 * </p>
 *
 * <p>
 * 서버 파일명 채번 규칙: {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}}
 * (예: {@code SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf})
 * → UUID 기반으로 1번·2번 서버 동시 운영 시에도 파일명 충돌 완전 방지
 * </p>
 */
@Entity
@Table(name = "TPRMPP_CFILEM", comment = "공통 첨부파일 관리")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Cfilem extends BaseEntity {

    /** 파일관리번호: 기본키 (형식: FL_{8자리 시퀀스}, 예: FL_00000001) */
    @Id
    @Column(name = "FL_MNG_NO", nullable = false, length = 32, comment = "파일관리번호")
    private String flMngNo;

    /** 원본파일명: 사용자가 업로드한 실제 파일명 (예: 요구사항정의서_v1.0.pdf) */
    @Column(name = "ORC_FL_NM", nullable = false, length = 255, comment = "원본파일명")
    private String orcFlNm;

    /**
     * 서버파일명: 서버에 저장되는 고유 파일명
     * 형식: {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}
     * (예: SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf)
     */
    @Column(name = "SVR_FL_NM", nullable = false, length = 100, comment = "서버파일명")
    private String svrFlNm;

    /** 파일저장경로: 서버 내 실제 저장 디렉토리 경로 (예: /data/files/요구사항정의서/2026/03) */
    @Column(name = "FL_KPN_PTH", nullable = false, length = 255, comment = "파일저장경로")
    private String flKpnPth;

    /** 파일구분: 파일 유형 구분 ('이미지' 또는 '첨부파일') */
    @Column(name = "FL_DTT", nullable = false, length = 100, comment = "파일구분")
    private String flDtt;

    /** 원본PK값: 파일이 연결된 도메인 레코드의 기본키 값 (예: PRJ-2026-0001) */
    @Column(name = "ORC_PK_VL", length = 32, comment = "원본PK값")
    private String orcPkVl;

    /** 원본구분: 파일이 연결된 도메인 종류 (예: 요구사항정의서, 정보화사업, 전산관리비) */
    @Column(name = "ORC_DTT", nullable = false, length = 100, comment = "원본구분")
    private String orcDtt;

    /**
     * 파일 메타데이터 수정 메서드
     *
     * <p>
     * 파일이 연결된 원본 도메인 정보를 변경합니다.
     * 파일 자체(서버파일명, 저장경로)는 변경되지 않습니다.
     * </p>
     *
     * @param orcPkVl 변경할 원본PK값
     * @param orcDtt  변경할 원본구분
     */
    public void updateMeta(String orcPkVl, String orcDtt) {
        if (orcPkVl != null) this.orcPkVl = orcPkVl;
        if (orcDtt != null) this.orcDtt = orcDtt;
    }
}
