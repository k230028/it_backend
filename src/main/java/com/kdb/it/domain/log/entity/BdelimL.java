package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 과업심의 기본(TPRMPP_BDELIM) 변경 로그. */
@Entity
@Table(name = "TPRMPP_BDELIL", comment = "과업심의 기본 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BdelimL extends BaseLogEntity {
    @Column(name = "DOC_MNG_NO", length = 20, comment = "문서관리번호") private String docMngNo;
    @Column(name = "DOC_VRS_SNO", comment = "문서버전일련번호") private Integer docVrsSno;
    @Column(name = "LST_YN", length = 1, comment = "최종여부") private String lstYn;
    @Column(name = "IOE_C", length = 7, comment = "IT포탈예산성격구분코드") private String ioeC;
    @Column(name = "CNCD_RFR_NO", length = 30, comment = "관련참조번호(대상관리번호)") private String cncdRfrNo;
    @Column(name = "IT_PTL_STS_TC", length = 2, comment = "IT포탈상태구분코드") private String stsTc;
    @Column(name = "REQ_CONE", length = 300, comment = "요청내용") private String reqCone;
    @Column(name = "TASK_DBR_TC", length = 2, comment = "과업심의구분코드") private String taskDbrTc;
    @Column(name = "TASK_DBR_RLT_TC", length = 2, comment = "과업심의결과구분코드") private String taskDbrRltTc;
    @Column(name = "TASK_DBR_DT", length = 8, comment = "과업심의일자") private String taskDbrDt;
    @Column(name = "TASK_DBR_TOD", length = 2, comment = "과업심의회차") private String taskDbrTod;
    @Column(name = "TASK_DBR_OMT_YN", length = 1, comment = "과업심의생략여부") private String taskDbrOmtYn;
    @Column(name = "TASK_DBR_OMT_RSN", length = 200, comment = "과업심의생략사유") private String taskDbrOmtRsn;
    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용") private String opnnCone;
    @Column(name = "APV_TRDN_RSN_CONE", length = 300, comment = "승인반려사유내용") private String apvTrdnRsnCone;
}
