package com.kdb.it.domain.deliberation.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BdelimL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 과업심의 기본(마스터) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BDELIM}. 정보화사업/전산업무비에 대한 과업심의위원회 신청을 관리한다.
 *
 * <p>대상구분 {@code IOE_C}: 100=정보화사업, 200=전산업무비. 상태 51→52→59.
 */
@LogTarget(entity = BdelimL.class)
@Entity
@Table(name = "TPRMPP_BDELIM", comment = "과업심의 기본")
@IdClass(BdelimId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bdelim extends BaseEntity {

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

    @Column(name = "TASK_DBR_TC", length = 2, comment = "과업심의구분코드")
    private String taskDbrTc;

    @Column(name = "TASK_DBR_RLT_TC", length = 2, comment = "과업심의결과구분코드")
    private String taskDbrRltTc;

    @Column(name = "TASK_DBR_DT", length = 8, comment = "과업심의일자")
    private String taskDbrDt;

    @Column(name = "TASK_DBR_TOD", length = 2, comment = "과업심의회차")
    private String taskDbrTod;

    @Column(name = "TASK_DBR_OMT_YN", length = 1, comment = "과업심의생략여부")
    private String taskDbrOmtYn;

    @Column(name = "TASK_DBR_OMT_RSN", length = 200, comment = "과업심의생략사유")
    private String taskDbrOmtRsn;

    @Column(name = "OPNN_CONE", length = 1000, comment = "의견내용")
    private String opnnCone;

    @Column(name = "APV_TRDN_RSN_CONE", length = 300, comment = "승인반려사유내용")
    private String apvTrdnRsnCone;

    /** 요청내용 수정 (작성중에서만 서비스가 호출) */
    public void updateRequest(String reqCone) {
        this.reqCone = reqCone;
    }

    /** 심의 결과 입력 (진행중에서만 서비스가 호출) */
    public void updateResult(
            String taskDbrTc,
            String taskDbrRltTc,
            String taskDbrDt,
            String taskDbrTod,
            String taskDbrOmtYn,
            String taskDbrOmtRsn,
            String opnnCone,
            String apvTrdnRsnCone) {
        this.taskDbrTc = taskDbrTc;
        this.taskDbrRltTc = taskDbrRltTc;
        this.taskDbrDt = taskDbrDt;
        this.taskDbrTod = taskDbrTod;
        this.taskDbrOmtYn = taskDbrOmtYn;
        this.taskDbrOmtRsn = taskDbrOmtRsn;
        this.opnnCone = opnnCone;
        this.apvTrdnRsnCone = apvTrdnRsnCone;
    }

    /** 상태 전이 (서비스의 changeStatus에서만 호출) */
    public void changeStatus(String stsTc) {
        this.stsTc = stsTc;
    }
}
