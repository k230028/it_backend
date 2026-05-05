package com.kdb.it.domain.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 코드 마스터(TAAABB_CCODEM) 변경 로그 엔티티.
 */
@Entity
@Table(name = "TAAABB_CCODEL", comment = "코드 마스터 변경 로그")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CcodemL extends BaseLogEntity {

    @Column(name = "C_ID", length = 32, comment = "코드ID")
    private String cId;

    @Column(name = "C_NM", length = 100, comment = "코드명")
    private String cNm;

    @Column(name = "CDVA", length = 100, comment = "코드값")
    private String cdva;

    @Column(name = "C_DES", length = 500, comment = "코드설명")
    private String cDes;

    @Column(name = "CTT_TP", length = 100, comment = "콘텐츠유형")
    private String cttTp;

    @Column(name = "CTT_TP_DES", length = 500, comment = "콘텐츠유형설명")
    private String cttTpDes;

    @Column(name = "C_SQN", comment = "코드순번")
    private Integer cSqn;

    @Column(name = "STT_DT", comment = "시작일자")
    private LocalDate sttDt;

    @Column(name = "END_DT", comment = "종료일자")
    private LocalDate endDt;
}
