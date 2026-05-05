package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BpovwmL;
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
 * 협의회 사업개요 엔티티 (타당성검토표의 사업개요 섹션)
 *
 * <p>DB 테이블: {@code TAAABB_BPOVWM}</p>
 *
 * <p>BASCTM과 1:1 관계이며, Step 1(타당성검토표 작성) 단계에서 입력됩니다.
 * 주요 필드는 TAAABB_BPROJM 스키마와 동일하게 맞춰 데이터 일관성을 유지합니다.</p>
 *
 * <p>KPN_TP(저장유형): TEMP(임시저장) / COMPLETE(작성완료)</p>
 */
@LogTarget(entity = BpovwmL.class)
@Entity
@Table(name = "TAAABB_BPOVWM", comment = "협의회 사업개요")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bpovwm extends BaseEntity {

    /** 협의회ID: BASCTM.ASCT_ID (FK, PK, 1:1) */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 사업명: BPROJM.PRJ_NM과 동일 필드 (수정 가능) */
    @Column(name = "PRJ_NM", length = 200, comment = "사업명")
    private String prjNm;

    /** 사업기간: 예) 2026.01 ~ 2026.12 */
    @Column(name = "PRJ_TRM", length = 100, comment = "사업기간")
    private String prjTrm;

    /** 필요성: BPROJM.NCS와 동일 스키마 (최대 1000자) */
    @Column(name = "NCS", length = 1000, comment = "필요성")
    private String ncs;

    /** 소요예산: BPROJM.PRJ_BG와 동일 (숫자형, 단위: 원) */
    @Column(name = "PRJ_BG", comment = "소요예산")
    private Long prjBg;

    /** 전결권자: BPROJM.EDRT와 동일 (부점장/본부장 등 코드 또는 직급명) */
    @Column(name = "EDRT", length = 32, comment = "전결권자")
    private String edrt;

    /** 사업내용: BPROJM.PRJ_DES와 동일 스키마 (최대 1000자) */
    @Column(name = "PRJ_DES", length = 1000, comment = "사업내용")
    private String prjDes;

    /** 법률규제대응여부: Y(해당) / N(해당없음), 기본값 N */
    @Column(name = "LGL_RGL_YN", length = 1, comment = "법률규제대응여부")
    private String lglRglYn;

    /** 관련법률규제명: LGL_RGL_YN='Y'인 경우 필수 입력 */
    @Column(name = "LGL_RGL_NM", length = 500, comment = "관련법률규제명")
    private String lglRglNm;

    /** 기대효과: BPROJM.XPT_EFF와 동일 스키마 (최대 1000자) */
    @Column(name = "XPT_EFF", length = 1000, comment = "기대효과")
    private String xptEff;

    /** 저장유형: TEMP(임시저장) / COMPLETE(작성완료), CCODEM KPN_TP 기준 */
    @Column(name = "KPN_TP", length = 10, comment = "저장유형")
    private String kpnTp;

    /** 첨부파일관리번호: TAAABB_CFILEM.FL_MNG_NO (FK, hwp/hwpx/pdf만 허용) */
    @Column(name = "FL_MNG_NO", length = 32, comment = "첨부파일관리번호")
    private String flMngNo;

    /**
     * 사업개요 정보 업데이트 (임시저장 / 작성완료 공통)
     *
     * @param prjNm     사업명
     * @param prjTrm    사업기간
     * @param ncs       필요성
     * @param prjBg     소요예산
     * @param edrt      전결권자
     * @param prjDes    사업내용
     * @param lglRglYn  법률규제대응여부
     * @param lglRglNm  관련법률규제명
     * @param xptEff    기대효과
     * @param kpnTp     저장유형 (TEMP/COMPLETE)
     * @param flMngNo   첨부파일관리번호
     */
    public void update(String prjNm, String prjTrm, String ncs, Long prjBg, String edrt,
                       String prjDes, String lglRglYn, String lglRglNm, String xptEff,
                       String kpnTp, String flMngNo) {
        this.prjNm = prjNm;
        this.prjTrm = prjTrm;
        this.ncs = ncs;
        this.prjBg = prjBg;
        this.edrt = edrt;
        this.prjDes = prjDes;
        this.lglRglYn = lglRglYn;
        this.lglRglNm = lglRglNm;
        this.xptEff = xptEff;
        this.kpnTp = kpnTp;
        this.flMngNo = flMngNo;
    }
}

