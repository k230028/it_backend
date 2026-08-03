package com.kdb.it.domain.budget.project.entity;

import com.kdb.it.domain.entity.BaseEntity;
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
 * 정보화사업관계(BPROJA) 엔티티.
 *
 * <p>DB 테이블: {@code TPRMPP_BPROJA}. 프로젝트({@code ABUS_MNG_NO})와 각 단계 원본문서 ({@code CNCD_RFR_NO}=단계 자기
 * key)의 IT포탈 상태({@code IT_PTL_STS_TC})를 정규화해 관리합니다. 한 프로젝트당 단계별 다건이 존재합니다.
 *
 * <p>사업 자신의 상태는 {@code CNCD_RFR_NO = ABUS_MNG_NO}인 행입니다. 나머지 행은 상위 계획({@code PLN-...})·사업계획({@code
 * BIZ-...}) 등 다른 문서의 상태이므로, 사업 상태를 읽을 때 {@code MAX(IT_PTL_STS_TC)} 같은 집계를 쓰면 다른 단계 코드가 사업 상태를 가립니다.
 *
 * <p>감사 로그 미적용({@code @LogTarget} 부착하지 않음). 적재(단계 서비스 upsert)는 2차 범위.
 */
@Entity
@Table(name = "TPRMPP_BPROJA", comment = "정보화사업관계")
@IdClass(BprojaId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bproja extends BaseEntity {

    /** 사업관리번호: 복합키 1 (프로젝트 관리번호) */
    @Id
    @Column(name = "ABUS_MNG_NO", nullable = false, length = 30, comment = "사업관리번호")
    private String abusMngNo;

    /** 관련참조번호: 복합키 2 (해당 단계 원본문서의 key) */
    @Id
    @Column(name = "CNCD_RFR_NO", nullable = false, length = 30, comment = "관련참조번호(단계 원본문서 key)")
    private String cncdRfrNo;

    /** IT포탈상태구분코드: 해당 단계의 상태 (2자리 코드) */
    @Column(name = "IT_PTL_STS_TC", length = 2, comment = "IT포탈상태구분코드")
    private String stsTc;

    /**
     * 상태 변경 (2차 단계 서비스 upsert에서 사용).
     *
     * @param stsTc 새 상태 코드
     */
    public void changeStatus(String stsTc) {
        this.stsTc = stsTc;
    }
}
