package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 신청서 마스터(TPRMPP_CAPPLM) 변경 로그 엔티티.
 *
 * <p>공통 컬럼(LOG_HIS_TGR_SNO, CHG_DTT_YN 등)은 {@link BaseLogEntity}에서 상속합니다.
 * 이 테이블 전용으로 {@code CHG_TP}(변경유형구분코드) 컬럼이 추가됩니다.</p>
 */
@Entity
@Table(name = "TPRMPP_CAPPLL", comment = "신청서 마스터 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CapplmL extends BaseLogEntity {

    @Column(name = "APF_DCM_NO", length = 64, comment = "신청서식별번호")
    private String apfMngNo;

    @Column(name = "APF_PRG_STS_C", length = 3, comment = "신청서진행상태코드")
    private String apfPrgStsC;

    @Column(name = "DCD_REQ_TTL", length = 255, comment = "결재요청제목")
    private String dcdReqTtl;

    @Lob
    @Column(name = "DCD_REQ_INF", comment = "결재요청정보")
    private String dcdReqInf;

    @Column(name = "DCD_REQ_USID", length = 14, comment = "결재요청사용자ID")
    private String dcdReqUsid;

    @Column(name = "DCD_REQ_DTM", comment = "결재요청일시")
    private LocalDate dcdReqDtm;

    @Column(name = "RGPR_DCD_REQ_CONE", length = 1000, comment = "등록자결재요청내용")
    private String rgprDcdReqCone;

}
