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
 * 공통 첨부파일 기본 엔티티
 *
 * <p>
 * DB 테이블: {@code TPRMPP_CFILEM}
 * </p>
 *
 * <p>
 * 시스템 전역에서 사용되는 첨부파일(이미지 포함)의 메타데이터를 관리합니다.
 * 주식별자컬럼명({@code PK_COL_NM})과 주식별자내용({@code PK_CONE})으로 어느 도메인 데이터에
 * 연결된 파일인지 식별합니다.
 * </p>
 *
 * <p>
 * 파일매핑ID 형식: {@code FL_{8자리 시퀀스}} (예: {@code FL_00000001}) — 최대 36자.
 * </p>
 *
 * <p>
 * 파일물리명 채번 규칙: {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}}
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

    /** 파일매핑ID: 기본키 (형식: FL_{8자리 시퀀스}, 예: FL_00000001) */
    @Id
    @Column(name = "FL_MPN_ID", nullable = false, length = 36, comment = "파일매핑ID")
    private String flMpnId;

    /** 파일명: 사용자가 업로드한 실제 파일명 (예: 요구사항정의서_v1.0.pdf) */
    @Column(name = "FL_NM", nullable = false, length = 100, comment = "파일명")
    private String flNm;

    /**
     * 파일물리명: 서버에 저장되는 고유 파일명
     * 형식: {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}
     * (예: SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf)
     */
    @Column(name = "FL_PYS_NM", nullable = false, length = 120, comment = "파일물리명")
    private String flPysNm;

    /** 파일저장경로: 서버 내 실제 저장 디렉토리 경로 (예: /data/files/요구사항정의서/2026/03) */
    @Column(name = "FL_KPN_PTH", nullable = false, length = 255, comment = "파일저장경로")
    private String flKpnPth;

    /** 파일유형내용: 파일 유형 구분 (예: '이미지' 또는 '첨부파일') */
    @Column(name = "FL_TP_CONE", length = 100, comment = "파일유형내용")
    private String flTpCone;

    /** 주식별자컬럼명: 파일이 연결된 도메인 종류 (예: 요구사항정의서, 정보화사업, 전산관리비) */
    @Column(name = "PK_COL_NM", length = 4000, comment = "주식별자컬럼명")
    private String pkColNm;

    /** 주식별자내용: 파일이 연결된 도메인 레코드의 기본키 값 (예: PRJ-2026-0001) */
    @Column(name = "PK_CONE", length = 4000, comment = "주식별자내용")
    private String pkCone;

    /**
     * 파일 메타데이터 수정 메서드
     *
     * <p>
     * 파일이 연결된 원본 도메인 정보를 변경합니다.
     * 파일 자체(파일물리명, 저장경로)는 변경되지 않습니다.
     * </p>
     *
     * @param pkCone   변경할 주식별자내용
     * @param pkColNm  변경할 주식별자컬럼명
     */
    public void updateMeta(String pkCone, String pkColNm) {
        if (pkCone != null) this.pkCone = pkCone;
        if (pkColNm != null) this.pkColNm = pkColNm;
    }
}
