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
 * <p>DB 테이블: {@code TPRMPP_CFILEM}
 *
 * <p>시스템 전역에서 사용되는 첨부파일(이미지 포함)의 메타데이터를 관리합니다. 첨부파일종류명({@code APG_FL_KD_NM})과 첨부파일연결콘텐츠명({@code
 * APG_FL_LNK_CTZ_NM})으로 어느 도메인 데이터에 연결된 파일인지 식별합니다.
 *
 * <p>파일매핑ID 형식: {@code FL-{8자리 시퀀스}} (예: {@code FL-00000001}) — 최대 36자.
 *
 * <p>구분자는 2026-07-30에 {@code _}에서 {@code -}로 통일했습니다. 그 이전에 채번된 기존 행은 {@code FL_00000001} 형식을 그대로
 * 유지하므로 두 형식이 공존합니다. 이 값은 정확히 일치 조회로만 사용하고 접두어를 파싱하지 않습니다.
 *
 * <p>파일물리명 채번 규칙: {@code {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자}} (예: {@code
 * SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf}) → UUID 기반으로 1번·2번 서버 동시 운영 시에도 파일명 충돌
 * 완전 방지
 */
@Entity
@Table(name = "TPRMPP_CFILEM", comment = "공통첨부파일기본")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Cfilem extends BaseEntity {

    /** 파일매핑ID: 기본키 (형식: FL-{8자리 시퀀스}, 예: FL-00000001) */
    @Id
    @Column(name = "FL_MPN_ID", nullable = false, length = 36, comment = "파일매핑ID")
    private String flMpnId;

    /** 파일명: 사용자가 업로드한 실제 파일명 (예: 요구사항정의서_v1.0.pdf). DB는 NULL 허용(레거시)이며 업로드 플로우가 항상 값을 채운다. */
    @Column(name = "FL_NM", length = 100, comment = "파일명")
    private String flNm;

    /**
     * 파일물리명: 서버에 저장되는 고유 파일명 형식: {서버ID}_{yyyyMMddHHmmss}_{UUID}.{확장자} (예:
     * SVR1_20260315143022_550e8400e29b41d4a716446655440000.pdf)
     *
     * <p>DB는 NULL 허용(레거시)이며 업로드 플로우가 항상 값을 채운다.
     */
    @Column(name = "FL_PYS_NM", length = 120, comment = "파일물리명")
    private String flPysNm;

    /**
     * 파일저장경로: 서버 내 실제 저장 디렉토리 경로 (예: /data/files/요구사항정의서/2026/03). DB는 NULL 허용(레거시)이며 업로드 플로우가 항상
     * 값을 채운다.
     */
    @Column(name = "FL_KPN_PTH", length = 255, comment = "파일저장경로")
    private String flKpnPth;

    /** 파일유형내용: 파일 유형 구분 (예: '이미지' 또는 '첨부파일') */
    @Column(name = "FL_TP_CONE", length = 100, comment = "파일유형내용")
    private String flTpCone;

    /** 첨부파일크기: 업로드 시점의 바이트 크기이며 레거시 파일은 NULL일 수 있습니다. */
    @Column(name = "APG_FL_SZ", precision = 10, comment = "첨부파일크기")
    private Long apgFlSz;

    /** 첨부파일경로: 반입 원본 폴더 구조를 보존하는 선택 상대경로입니다. */
    @Column(name = "APG_FL_PTH", length = 255, comment = "첨부파일경로")
    private String apgFlPth;

    /** 첨부파일종류명: 파일이 연결된 도메인 종류 (예: 요구사항정의서, 정보화사업, 전산관리비) */
    @Column(name = "APG_FL_KD_NM", length = 100, comment = "첨부파일종류명")
    private String apgFlKdNm;

    /** 첨부파일연결콘텐츠명: 파일이 연결된 도메인 레코드의 기본키 값 (예: PRJ-2026-0001) */
    @Column(name = "APG_FL_LNK_CTZ_NM", length = 100, comment = "첨부파일연결콘텐츠명")
    private String apgFlLnkCtzNm;

    /**
     * 파일 메타데이터 수정 메서드
     *
     * <p>파일이 연결된 원본 도메인 정보를 변경합니다. 파일 자체(파일물리명, 저장경로)는 변경되지 않습니다.
     *
     * @param apgFlLnkCtzNm 변경할 첨부파일연결콘텐츠명
     * @param apgFlKdNm 변경할 첨부파일종류명
     */
    public void updateMeta(String apgFlLnkCtzNm, String apgFlKdNm) {
        if (apgFlLnkCtzNm != null) this.apgFlLnkCtzNm = apgFlLnkCtzNm;
        if (apgFlKdNm != null) this.apgFlKdNm = apgFlKdNm;
    }
}
