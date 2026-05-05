package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 협의회 결과서(TAAABB_BRSLTM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_BRSLTL", comment = "협의회 결과서 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BrsltmL extends BaseLogEntity {

    @Column(name = "ASCT_ID", length = 32, comment = "협의회ID")
    private String asctId;

    @Column(name = "SYN_OPNN", length = 4000, comment = "종합의견")
    private String synOpnn;

    @Column(name = "CKG_OPNN", length = 4000, comment = "타당성검토의견")
    private String ckgOpnn;

    @Column(name = "FL_MNG_NO", length = 32, comment = "관련자료 첨부파일관리번호")
    private String flMngNo;
}
