package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BevalmL;
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
 * 평가위원 평가의견 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BEVALM}</p>
 *
 * <p>협의회 당일 또는 이후 각 평가위원이 6개 점검항목에 대해
 * 점수(1~5)와 의견을 작성합니다.
 * 1~2점 입력 시 의견(CKG_OPNN) 작성이 필수입니다.</p>
 *
 * <p>점검항목 코드는 BCHKLC와 동일 체계(CCODEM CKG_ITM)를 사용합니다.</p>
 *
 * <p>복합키: ({@code ASCT_ID}, {@code ENO}, {@code CKG_ITM_C})</p>
 */
@LogTarget(entity = BevalmL.class)
@Entity
@Table(name = "TAAABB_BEVALM", comment = "평가위원 평가의견")
@IdClass(BevalmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bevalm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 사번: 복합키 두 번째 컬럼 (TAAABB_CUSERI.ENO FK, 평가위원) */
    @Id
    @Column(name = "ENO", length = 32, nullable = false, comment = "사번")
    private String eno;

    /** 점검항목코드: 복합키 세 번째 컬럼 (MGMT_STR/FIN_EFC/RISK_IMP/REP_IMP/DUP_SYS/ETC) */
    @Id
    @Column(name = "CKG_ITM_C", length = 20, nullable = false, comment = "점검항목코드")
    private String ckgItmC;

    /** 점검점수: 1~5점 척도 */
    @Column(name = "CKG_RCRD", comment = "점검점수")
    private Integer ckgRcrd;

    /** 점검의견: 1~2점 입력 시 필수, 부정적 평가 사유 기술 (최대 2000자) */
    @Column(name = "CKG_OPNN", length = 2000, comment = "점검의견")
    private String ckgOpnn;

    /**
     * 평가의견 업데이트 (위원이 수정 시 재호출)
     *
     * @param ckgRcrd 점검점수 (1~5)
     * @param ckgOpnn 점검의견 (1~2점 시 필수)
     */
    public void update(Integer ckgRcrd, String ckgOpnn) {
        this.ckgRcrd = ckgRcrd;
        this.ckgOpnn = ckgOpnn;
    }
}

