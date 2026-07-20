package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BchklmL;
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
 * 타당성 자체점검 엔티티
 *
 * <p>DB 테이블: {@code TPRMPP_BCHKLM}</p>
 *
 * <p>타당성검토표 작성 단계(Step 1)에서 소관부서 담당자가 6개 점검항목에 대해
 * 자체 점수(1~5)와 점검의견을 작성합니다. 평가위원 평가({@link Bevalm}, BEVALM)와
 * 동일한 점검항목 체계(CKG_ITM_C 01~06)를 쓰되, 위원별(사번 포함)이 아니라
 * 협의회 단위로 항목당 1행만 남깁니다.</p>
 *
 * <p>복합키: ({@code IT_PTL_ASCT_ID}, {@code IT_PTL_CKG_ITM_TC})</p>
 */
@LogTarget(entity = BchklmL.class)
@Entity
@Table(name = "TPRMPP_BCHKLM", comment = "타당성 자체점검")
@IdClass(BchklmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Bchklm extends BaseEntity {

    /** 협의회ID: 복합키 첫 번째 컬럼 */
    @Id
    @Column(name = "IT_PTL_ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String itPtlAsctId;

    /** 점검항목코드: 복합키 두 번째 컬럼 (01~06, BEVALM과 동일 체계) */
    @Id
    @Column(name = "IT_PTL_CKG_ITM_TC", length = 2, nullable = false, comment = "점검항목코드")
    private String itPtlCkgItmTc;

    /** 문항점수: 1~5점 척도 */
    @Column(name = "QUEL_RCRD", comment = "문항점수")
    private Integer quelRcrd;

    /** 점검의견내용: 1~2점 입력 시 필수 (최대 1000자) */
    @Column(name = "CKG_OPNN_CONE", length = 1000, comment = "점검의견내용")
    private String ckgOpnn;

    /**
     * 자체점검 업데이트 (담당자 수정 시 재호출)
     *
     * @param quelRcrd 문항점수 (1~5)
     * @param ckgOpnn  점검의견 (1~2점 시 필수)
     */
    public void update(Integer quelRcrd, String ckgOpnn) {
        this.quelRcrd = quelRcrd;
        this.ckgOpnn = ckgOpnn;
    }
}
