package com.kdb.it.domain.contract.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BcontmL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 입찰계약 기본(마스터) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BCONTM}. 정보화사업/전산업무비에 대한 입찰/계약을 관리한다.
 *
 * <p>대상구분 {@code IOE_C}: 100=정보화사업, 200=전산업무비. 상태 61→62→69.
 */
@LogTarget(entity = BcontmL.class)
@Entity
@Table(name = "TPRMPP_BCONTM", comment = "입찰계약 기본")
@IdClass(BcontmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bcontm extends BaseEntity {

    @Id
    @Column(name = "DOC_MNG_NO", length = 20, nullable = false, comment = "문서관리번호")
    private String docMngNo;

    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "IOE_C", length = 7, nullable = false, comment = "IT포탈예산성격구분코드")
    private String ioeC;

    @Column(name = "CNCD_RFR_NO", length = 30, nullable = false, comment = "관련참조번호(대상관리번호)")
    private String cncdRfrNo;

    @Column(name = "IT_PTL_STS_TC", length = 2, nullable = false, comment = "IT포탈상태구분코드")
    private String stsTc;

    @Column(name = "REQ_CONE", length = 300, comment = "요청내용")
    private String reqCone;

    @Column(name = "IT_PTL_CTT_MANR_C", length = 2, comment = "IT포탈계약방법코드")
    private String itPtlCttManrC;

    @Column(name = "CTT_MANR_RSN", length = 1000, comment = "계약방법사유")
    private String cttManrRsn;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "CTT_AMT", precision = 18, scale = 3, comment = "계약금액")
    private BigDecimal cttAmt;

    @Column(name = "CTT_OPP_NM", length = 100, comment = "계약상대처명")
    private String cttOppNm;

    @Column(name = "CTT_DT", length = 8, comment = "계약일자")
    private String cttDt;

    /** 요청내용 수정 (작성중에서만 서비스가 호출) */
    public void updateRequest(String reqCone) {
        this.reqCone = reqCone;
    }

    /** 계약 정보 입력 (진행중에서만 서비스가 호출) */
    public void updateContract(
            String itPtlCttManrC,
            String cttManrRsn,
            String cttNm,
            BigDecimal cttAmt,
            String cttOppNm,
            String cttDt) {
        this.itPtlCttManrC = itPtlCttManrC;
        this.cttManrRsn = cttManrRsn;
        this.cttNm = cttNm;
        this.cttAmt = cttAmt;
        this.cttOppNm = cttOppNm;
        this.cttDt = cttDt;
    }

    /** 상태 전이 (서비스의 changeStatus에서만 호출) */
    public void changeStatus(String stsTc) {
        this.stsTc = stsTc;
    }
}
