package com.kdb.it.domain.payment.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BpaymmL;
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
 * 대금지급 기본(마스터) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BPAYMM}. 정보화사업/전산업무비에 대한 대금지급을 관리한다.</p>
 * <p>대상구분 {@code BG_PRN_TC}: 100=정보화사업, 200=전산업무비. 상태 71→72→79.</p>
 */
@LogTarget(entity = BpaymmL.class)
@Entity
@Table(name = "TPRMPP_BPAYMM", comment = "대금지급 기본")
@IdClass(BpaymmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bpaymm extends BaseEntity {

    @Id
    @Column(name = "DOC_MNG_NO", length = 20, nullable = false, comment = "문서관리번호")
    private String docMngNo;

    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "BG_PRN_TC", length = 3, nullable = false, comment = "예산성격구분코드(대상구분)")
    private String bgPrnTc;

    @Column(name = "CNCD_RFR_NO", length = 30, nullable = false, comment = "관련참조번호(대상관리번호)")
    private String cncdRfrNo;

    @Column(name = "IT_PTL_STS_TC", length = 2, nullable = false, comment = "IT포탈상태구분코드")
    private String stsTc;

    @Column(name = "REQ_CONE", length = 300, comment = "요청내용")
    private String reqCone;

    @Column(name = "CTT_NM", length = 100, comment = "계약명")
    private String cttNm;

    @Column(name = "CTT_AMT", precision = 18, scale = 3, comment = "계약금액")
    private BigDecimal cttAmt;

    /** 마스터 수정 (작성중에서만): 요청내용 + 계약 정보 */
    public void updateMaster(String reqCone, String cttNm, BigDecimal cttAmt) {
        this.reqCone = reqCone;
        this.cttNm = cttNm;
        this.cttAmt = cttAmt;
    }

    /** 상태 전이 (서비스의 changeStatus에서만 호출) */
    public void changeStatus(String stsTc) {
        this.stsTc = stsTc;
    }
}
