package com.kdb.it.common.iam.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.kdb.it.domain.entity.BaseEntity;

/**
 * 자격등급 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_CAUTHI}
 * </p>
 *
 * <p>
 * 시스템에서 정의한 자격등급(권한 그룹)을 관리합니다.
 * 주요 자격등급:
 * </p>
 * <ul>
 * <li>{@code ITPZZ001}: 일반사용자 - 소속 부서 조회, 본인 작성 수정</li>
 * <li>{@code ITPZZ002}: 기획통할담당자 - 소속 부서 조회/수정/삭제</li>
 * <li>{@code ITPAD001}: 시스템관리자 - 전체 조회/수정/삭제, 관리자 메뉴 접근</li>
 * </ul>
 */
@Entity
@Table(name = "TAAABB_CAUTHI", comment = "자격등급")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class CauthI extends BaseEntity {

    /**
     * 권한ID: 자격등급의 고유 식별자
     * 예: ITPZZ001(일반사용자), ITPZZ002(기획통할담당자), ITPAD001(시스템관리자)
     */
    @Id
    @Column(name = "ATH_ID", nullable = false, length = 32, comment = "권한ID")
    private String athId;

    /** 자격등급명: 자격등급의 한글 명칭 (예: 일반사용자, 기획통할담당자, 시스템관리자) */
    @Column(name = "QLF_GR_NM", length = 200, comment = "자격등급명")
    private String qlfGrNm;

    /** 자격등급사항: 자격등급의 상세 설명 및 권한 내용 (최대 600자) */
    @Column(name = "QLF_GR_MAT", length = 600, comment = "자격등급사항")
    private String qlfGrMat;

    /** 사용여부: 'Y'=사용, 'N'=미사용 (기본값: 'Y') */
    @Column(name = "USE_YN", length = 1, comment = "사용여부")
    private String useYn;

    // BaseEntity: DEL_YN, GUID, GUID_PRG_SNO, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID 제공

    /**
     * 자격등급 정보 업데이트 (Dirty Checking 활용)
     *
     * @param qlfGrNm  자격등급명
     * @param qlfGrMat 자격등급사항
     * @param useYn    사용여부
     */
    public void update(String qlfGrNm, String qlfGrMat, String useYn) {
        this.qlfGrNm  = qlfGrNm;
        this.qlfGrMat = qlfGrMat;
        this.useYn    = useYn;
    }
}

