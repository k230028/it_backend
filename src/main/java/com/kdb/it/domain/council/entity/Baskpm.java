package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BaskpmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 정보화실무협의회 타당성검토 생략판정요청 엔티티 (PRD_c_20260620 #3)
 *
 * <p>DB 테이블: {@code TPRMPP_BASKPM} (협의회당 1건, PK=협의회ID)</p>
 *
 * <p>정보보호기획(ITPAD002)이 IT기획(ITPAD001) 앞으로 보내는 타당성검토 생략 판정 요청을 적재합니다.
 * IT기획이 생략여부를 판정한 뒤 전자결재(IT기획팀장→부장)를 거쳐 확정합니다.</p>
 *
 * <p>처리상태(PRC_STS_TC): 01 요청 → 02 판정·결재중 → (03 생략확정 / 04 개최확정 / 05 반려)</p>
 */
@LogTarget(entity = BaskpmL.class)
@Entity
@Table(name = "TPRMPP_BASKPM", comment = "정보화실무협의회 타당성검토 생략판정요청")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Baskpm extends BaseEntity {

    /** 협의회ID (PK, BASCTM FK) */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 타당성검토생략사유구분코드 (PRTY_IVG_OMT_RSN_TC 01~04) */
    @Column(name = "PRTY_IVG_OMT_RSN_TC", length = 2, nullable = false, comment = "타당성검토생략사유구분코드")
    private String prtyIvgOmtRsnTc;

    /** 타당성검토생략사유 (요청자 개별 설명) */
    @Column(name = "PRTY_IVG_OMT_RSN", length = 200, nullable = false, comment = "타당성검토생략사유")
    private String prtyIvgOmtRsn;

    /** 사업계획서 첨부파일관리번호 (Cfilem.FL_MPN_ID) */
    @Column(name = "ABUS_PDC_FL_MPN_ID", length = 36, nullable = false, comment = "사업계획서첨부파일관리번호")
    private String abusPdcFlMpnId;

    /** 타당성검토표 첨부파일관리번호 (Cfilem.FL_MPN_ID) */
    @Column(name = "PRTY_IVG_FL_MPN_ID", length = 36, nullable = false, comment = "타당성검토표첨부파일관리번호")
    private String prtyIvgFlMpnId;

    /** 신청사용자ID (정보보호기획 요청자) */
    @Column(name = "RQS_USID", length = 14, nullable = false, comment = "신청사용자ID")
    private String rqsUsid;

    /** 신청일시 */
    @Column(name = "RQS_DTM", nullable = false, comment = "신청일시")
    private LocalDateTime rqsDtm;

    /** 타당성검토생략여부 (확인 결과 Y/N) */
    @Column(name = "PRTY_IVG_OMT_YN", length = 1, comment = "타당성검토생략여부")
    private String prtyIvgOmtYn;

    /** 생략확인내용 (IT기획 확인사유) */
    @Column(name = "OMT_CNFM_CONE", length = 2000, comment = "생략확인내용")
    private String omtCnfmCone;

    /** 확인사용자ID (IT기획) */
    @Column(name = "CNFM_USID", length = 14, comment = "확인사용자ID")
    private String cnfmUsid;

    /** 확인일시 */
    @Column(name = "CNFM_DTM", comment = "확인일시")
    private LocalDateTime cnfmDtm;

    /** 신청관리번호 (전자결재 연동) */
    @Column(name = "APF_MNG_NO", length = 30, comment = "신청관리번호")
    private String apfMngNo;

    /**
     * 신규 생략판정요청 생성 (정보보호기획 요청 시점).
     *
     * <p>NOT NULL 컬럼은 감사로그 스냅샷 타이밍을 위해 생성 시점에 모두 채웁니다(it_backend §5.12.1.1).</p>
     *
     * @param itPtlAsctId       협의회ID
     * @param rsnTc             생략사유코드(4종)
     * @param rsn               생략사유 개별 설명
     * @param abusPdcFlMpnId    사업계획서 첨부파일관리번호
     * @param prtyIvgFlMpnId    타당성검토표 첨부파일관리번호
     * @param rqsUsid           신청자(정보보호기획) 사용자ID
     */
    public static Baskpm create(
            String itPtlAsctId, String rsnTc, String rsn,
            String abusPdcFlMpnId, String prtyIvgFlMpnId, String rqsUsid) {
        Baskpm b = new Baskpm();
        b.itPtlAsctId = itPtlAsctId;
        b.prtyIvgOmtRsnTc = rsnTc;
        b.prtyIvgOmtRsn = rsn;
        b.abusPdcFlMpnId = abusPdcFlMpnId;
        b.prtyIvgFlMpnId = prtyIvgFlMpnId;
        b.rqsUsid = rqsUsid;
        b.rqsDtm = LocalDateTime.now();
        return b;
    }

    /**
     * IT기획 생략여부 확인 기록 + 결재 상신.
     *
     * <p>확인 결과(생략여부)·확인자·확인일시·결재번호를 기록합니다. 회신 전(요청) 상태는
     * 확인일시(CNFM_DTM) null 여부로 판별하므로 별도 처리상태 컬럼을 두지 않습니다.</p>
     *
     * @param omtYn     생략여부(Y=생략 / N=개최)
     * @param cnfmCone  확인사유
     * @param cnfmUsid  확인자(IT기획) 사용자ID
     * @param apfMngNo  전자결재 신청관리번호
     */
    public void submitForDecision(String omtYn, String cnfmCone, String cnfmUsid, String apfMngNo) {
        this.prtyIvgOmtYn = omtYn;
        this.omtCnfmCone = cnfmCone;
        this.cnfmUsid = cnfmUsid;
        this.cnfmDtm = LocalDateTime.now();
        this.apfMngNo = apfMngNo;
    }
}
