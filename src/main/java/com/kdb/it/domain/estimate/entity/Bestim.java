package com.kdb.it.domain.estimate.entity;

import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BestimL;
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
 * 소요예산 산정 기본(마스터) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BESTIM}. 정보화화사업(BPROJM)에 대한 소요예산 산정 요청을 관리한다.</p>
 * <p>대상은 정보화사업으로 고정되며 {@code CNCD_RFR_NO}=사업 관리번호(ABUS_MNG_NO)입니다.</p>
 * <p>상태(IT_PTL_STS_TC): 41(작성중) → 42(진행중) → 49(완료).</p>
 */
@LogTarget(entity = BestimL.class)
@Entity
@Table(name = "TPRMPP_BESTIM", comment = "소요예산 산정 기본")
@IdClass(BestimId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bestim extends BaseEntity {

    @Id
    @Column(name = "RQM_BG_REQ_DOC_NO", length = 30, nullable = false, comment = "소요예산요청문서번호")
    private String rqmBgReqDocNo;

    @Id
    @Column(name = "DOC_VRS_SNO", nullable = false, comment = "문서버전일련번호")
    private Integer docVrsSno;

    @Column(name = "LST_YN", length = 1, comment = "최종여부")
    private String lstYn;

    @Column(name = "CNCD_RFR_NO", length = 30, nullable = false, comment = "관련참조번호(대상관리번호)")
    private String cncdRfrNo;

    @Column(name = "IT_PTL_STS_TC", length = 2, nullable = false, comment = "IT포탈상태구분코드")
    private String stsTc;

    @Column(name = "REQ_CONE", length = 300, comment = "요청내용")
    private String reqCone;

    /** 요청내용 수정 (작성중 상태에서만 서비스가 호출) */
    public void updateRequest(String reqCone) {
        this.reqCone = reqCone;
    }

    /** 상태 전이 (서비스의 changeStatus에서만 호출) */
    public void changeStatus(String stsTc) {
        this.stsTc = stsTc;
    }
}
