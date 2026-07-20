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
 * <p>진행상태는 별도 컬럼 없이 확인일시(CNFM_DTM)/생략여부(PRTY_IVG_OMT_YN)/결재·협의회 상태로 파생합니다.</p>
 */
@LogTarget(entity = BaskpmL.class)
@Entity
@Table(name = "TPRMPP_BASKPM", comment = "프로젝트관리_협의회제외요청기본")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Baskpm extends BaseEntity {

    /** 협의회ID (PK, BASCTM FK) */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 담당자의견내용 (요청측 정보보호기획 의견) */
    @Column(name = "CGPR_OPNN_CONE", length = 4000, nullable = false, comment = "담당자의견내용")
    private String cgprOpnnCone;

    /** 첨부파일관리번호 (사업계획서, Cfilem.FL_MPN_ID) */
    @Column(name = "FL_MPN_ID", length = 36, nullable = false, comment = "파일매핑ID")
    private String flMpnId;

    /** 신청사용자ID (정보보호기획 요청자) */
    @Column(name = "RQS_USID", length = 14, nullable = false, comment = "신청사용자ID")
    private String rqsUsid;

    /** 신청일시 */
    @Column(name = "RQS_DTM", nullable = false, comment = "신청일시")
    private LocalDateTime rqsDtm;

    /** 타당성검토생략여부 (확인 결과 Y/N) */
    @Column(name = "PRTY_IVG_OMT_YN", length = 1, comment = "타당성검토생략여부")
    private String prtyIvgOmtYn;

    /** 담당자응답내용 (응답측 IT기획 응답) */
    @Column(name = "CGPR_RPD_CONE", length = 4000, comment = "담당자응답내용")
    private String cgprRpdCone;

    /** 확인사용자ID (IT기획) */
    @Column(name = "CNFM_USID", length = 14, comment = "확인사용자ID")
    private String cnfmUsid;

    /** 확인일시 */
    @Column(name = "CNFM_DTM", comment = "확인일시")
    private LocalDateTime cnfmDtm;

    /** 신청관리번호 (전자결재 연동) */
    @Column(name = "APF_DCM_NO", length = 64, comment = "신청서식별번호")
    private String apfMngNo;

    /**
     * 신규 생략판정요청 생성 (정보보호기획 요청 시점).
     *
     * <p>NOT NULL 컬럼은 감사로그 스냅샷 타이밍을 위해 생성 시점에 모두 채웁니다(it_backend §5.12.1.1).</p>
     *
     * @param itPtlAsctId 협의회ID
     * @param opnn        담당자의견내용(요청 설명)
     * @param flMpnId     사업계획서 첨부파일관리번호
     * @param rqsUsid     신청자(정보보호기획) 사용자ID
     */
    public static Baskpm create(String itPtlAsctId, String opnn, String flMpnId, String rqsUsid) {
        Baskpm b = new Baskpm();
        b.itPtlAsctId = itPtlAsctId;
        b.cgprOpnnCone = opnn;
        b.flMpnId = flMpnId;
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
     * @param rpd       담당자응답내용(판정 사유)
     * @param cnfmUsid  확인자(IT기획) 사용자ID
     * @param apfMngNo  전자결재 신청관리번호
     */
    public void submitForDecision(String omtYn, String rpd, String cnfmUsid, String apfMngNo) {
        this.prtyIvgOmtYn = omtYn;
        this.cgprRpdCone = rpd;
        this.cnfmUsid = cnfmUsid;
        this.cnfmDtm = LocalDateTime.now();
        this.apfMngNo = apfMngNo;
    }
}
