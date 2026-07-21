package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BpovwmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 협의회 사업개요 엔티티 (타당성검토표의 사업개요 섹션)
 *
 * <p>DB 테이블: {@code TPRMPP_BPOVWM}
 *
 * <p>BASCTM과 1:1 관계이며, Step 1(타당성검토표 작성) 단계에서 입력됩니다. 주요 필드는 TPRMPP_BPROJM 스키마와 동일하게 맞춰 데이터 일관성을
 * 유지합니다.
 *
 * <p>KPN_TP_TC(저장유형구분코드): 10(임시저장) / 20(저장완료)
 */
@LogTarget(entity = BpovwmL.class)
@Entity
@Table(name = "TPRMPP_BPOVWM", comment = "협의회 사업개요")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bpovwm extends BaseEntity {

    /** 협의회ID: BASCTM.ASCT_ID (FK, PK, 1:1) */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 사업명: BPROJM.ABUS_NM과 동일 필드 (수정 가능) */
    @Column(name = "ABUS_NM", length = 100, comment = "사업명")
    private String abusNm;

    /** 사업기간내용: 예) 2026.01 ~ 2026.12 */
    @Column(name = "ABUS_TRM_CONE", length = 300, comment = "사업기간내용")
    private String abusTrmCone;

    /** 필요성내용: BPROJM.NCS와 동일 스키마 (최대 1000자) */
    @Column(name = "ABUS_NCS_CONE", length = 300, comment = "필요성내용")
    private String abusNcsCone;

    /** 소요예산금액: BPROJM.PRJ_BG와 동일 (BG 도메인 NUMBER(18,3)) */
    @Column(name = "RQM_BG_AMT", precision = 18, comment = "소요예산금액")
    private BigDecimal rqmBgAmt;

    /** 전결권자명: BPROJM.EDRT와 동일 (부점장/본부장 등 직급명) */
    @Column(name = "IT_PTL_EDRT_TC", length = 2, comment = "전결권자명")
    private String itPtlEdrtTc;

    /** 사업내용: BPROJM.PRJ_DES와 동일 스키마 (최대 1000자) */
    @Column(name = "ABUS_CONE", length = 1000, comment = "사업내용")
    private String abusCone;

    /** 법률규제대응여부: Y(해당) / N(해당없음), 기본값 N */
    @Column(name = "LW_RGL_YN", length = 1, comment = "법률규제대응여부")
    private String lwRglYn;

    /** 관련법률규제명: LGL_RGL_YN='Y'인 경우 필수 입력 */
    @Column(name = "LW_FDTN", length = 300, comment = "관련법률규제명")
    private String lwFdtn;

    /** 기대효과내용: BPROJM.XPT_EFF와 동일 스키마 (최대 1000자) */
    @Column(name = "DGOG_PPO_CONE", length = 4000, comment = "기대효과내용")
    private String dgogPpoCone;

    /** 저장유형구분코드: 10(임시저장) / 20(저장완료), CCODEM KPN_TP_TC 기준 */
    @Column(name = "KPN_TP_TC", length = 2, comment = "저장유형구분코드")
    private String kpnTpTc;

    /** 첨부파일관리번호: TPRMPP_CFILEM.FL_MNG_NO (FK, hwp/hwpx/pdf만 허용) */
    @Column(name = "FL_MPN_ID", length = 36, comment = "첨부파일관리번호")
    private String flMpnId;

    /**
     * 사업개요 정보 업데이트 (임시저장 / 작성완료 공통)
     *
     * @param abusNm 사업명
     * @param abusTrmCone 사업기간
     * @param abusNcsCone 필요성
     * @param rqmBgAmt 소요예산
     * @param itPtlEdrtTc 전결권자
     * @param abusCone 사업내용
     * @param lwRglYn 법률규제대응여부
     * @param lwFdtn 관련법률규제명
     * @param dgogPpoCone 기대효과
     * @param kpnTpTc 저장유형구분코드 (10:임시저장 / 20:저장완료)
     * @param flMpnId 첨부파일관리번호
     */
    public void update(
            String abusNm,
            String abusTrmCone,
            String abusNcsCone,
            BigDecimal rqmBgAmt,
            String itPtlEdrtTc,
            String abusCone,
            String lwRglYn,
            String lwFdtn,
            String dgogPpoCone,
            String kpnTpTc,
            String flMpnId) {
        this.abusNm = abusNm;
        this.abusTrmCone = abusTrmCone;
        this.abusNcsCone = abusNcsCone;
        this.rqmBgAmt = rqmBgAmt;
        this.itPtlEdrtTc = itPtlEdrtTc;
        this.abusCone = abusCone;
        this.lwRglYn = lwRglYn;
        this.lwFdtn = lwFdtn;
        this.dgogPpoCone = dgogPpoCone;
        this.kpnTpTc = kpnTpTc;
        this.flMpnId = flMpnId;
    }
}
