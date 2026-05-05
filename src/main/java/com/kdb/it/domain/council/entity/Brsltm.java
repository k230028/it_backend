package com.kdb.it.domain.council.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BrsltmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 협의회 결과서 엔티티
 *
 * <p>DB 테이블: {@code TAAABB_BRSLTM}</p>
 *
 * <p>BASCTM과 1:1 관계이며, Step 3(결과서 작성/검토/결재) 단계에서 작성됩니다.
 * IT관리자(ITPAD001)가 작성하고, 평가위원이 확인 후 결재가 진행됩니다.</p>
 *
 * <p>결과서는 2페이지 구조:</p>
 * <ul>
 *   <li>1page: 일정공지 내용 (사업명, 심의유형, 일시, 장소, 위원목록)</li>
 *   <li>2page: 평균점수 + 종합의견(SYN_OPNN) + 타당성검토의견(CKG_OPNN)</li>
 * </ul>
 */
@LogTarget(entity = BrsltmL.class)
@Entity
@Table(name = "TAAABB_BRSLTM", comment = "협의회 결과서")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Brsltm extends BaseEntity {

    /** 협의회ID: BASCTM.ASCT_ID (FK, PK, 1:1) */
    @Id
    @Column(name = "ASCT_ID", length = 32, nullable = false, comment = "협의회ID")
    private String asctId;

    /** 종합의견: IT관리자가 작성하는 전체 심의 결과 요약 (최대 4000자) */
    @Column(name = "SYN_OPNN", length = 4000, comment = "종합의견")
    private String synOpnn;

    /** 타당성검토의견: 각 항목별 검토 결과 종합 의견 (최대 4000자) */
    @Column(name = "CKG_OPNN", length = 4000, comment = "타당성검토의견")
    private String ckgOpnn;

    /** 관련자료 첨부파일관리번호: TAAABB_CFILEM.FL_MNG_NO FK */
    @Column(name = "FL_MNG_NO", length = 32, comment = "관련자료 첨부파일관리번호")
    private String flMngNo;

    /**
     * 결과서 내용 업데이트 (작성/수정 시 공통)
     *
     * @param synOpnn 종합의견
     * @param ckgOpnn 타당성검토의견
     * @param flMngNo 관련자료 첨부파일관리번호
     */
    public void update(String synOpnn, String ckgOpnn, String flMngNo) {
        this.synOpnn = synOpnn;
        this.ckgOpnn = ckgOpnn;
        this.flMngNo = flMngNo;
    }
}

