package com.kdb.it.domain.budget.document.entity;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BrdocmL;
import com.kdb.it.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 요구사항 정의서 엔티티
 *
 * <p>
 * DB 테이블: {@code TAAABB_BRDOCM}
 * </p>
 *
 * <p>
 * IT 프로젝트의 요구사항 정의서를 관리합니다.
 * </p>
 *
 * <p>
 * 관리번호 형식: {@code DOC-{연도}-{4자리 시퀀스}} (예: {@code DOC-2026-0001})
 * </p>
 */
@LogTarget(entity = BrdocmL.class)
@Entity
@Table(name = "TAAABB_BRDOCM", comment = "요구사항 정의서")
@IdClass(BrdocmId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class Brdocm extends BaseEntity {

    /** 문서관리번호: 복합 기본키의 첫 번째 컬럼 (예: DOC-2026-0001) */
    @Id
    @Column(name = "DOC_MNG_NO", nullable = false, length = 32, comment = "문서관리번호")
    private String docMngNo;

    /** 문서버전: 복합 기본키의 두 번째 컬럼 (Oracle NUMBER(4,2), 예: 1.00, 1.01, 2.00) */
    @Id
    @Column(name = "DOC_VRS", nullable = false, precision = 4, scale = 2, comment = "문서버전")
    private BigDecimal docVrs;

    /** 요구사항명: 요구사항의 제목 (최대 200자) */
    @Column(name = "REQ_NM", length = 200, comment = "요구사항명")
    private String reqNm;

    /** 요구사항내용: 요구사항 상세 내용 (BLOB, HTML 포함 가능) */
    @Lob
    @Column(name = "REQ_CONE", comment = "요구사항내용")
    private byte[] reqCone;

    /** 요구사항구분: 요구사항 분류 코드 (최대 32자) */
    @Column(name = "REQ_DTT", length = 32, comment = "요구사항구분")
    private String reqDtt;

    /** 업무구분: 업무 영역 분류 코드 (최대 32자) */
    @Column(name = "BZ_DTT", length = 32, comment = "업무구분")
    private String bzDtt;

    /** 완료기한: 요구사항 처리 완료 기한 */
    @Column(name = "FSG_TLM", comment = "완료기한")
    private LocalDate fsgTlm;

    /**
     * 요구사항 정의서 정보 업데이트 메서드
     *
     * <p>
     * JPA Dirty Checking을 활용하여 트랜잭션 내에서 필드를 변경합니다.
     * </p>
     *
     * @param reqNm  요구사항명
     * @param reqCone 요구사항내용 (BLOB)
     * @param reqDtt 요구사항구분
     * @param bzDtt  업무구분
     * @param fsgTlm 완료기한
     */
    public void update(String reqNm, byte[] reqCone, String reqDtt, String bzDtt, LocalDate fsgTlm) {
        this.reqNm = reqNm;
        this.reqCone = reqCone;
        this.reqDtt = reqDtt;
        this.bzDtt = bzDtt;
        this.fsgTlm = fsgTlm;
    }

    /**
     * 새 버전 엔티티 생성 메서드
     *
     * <p>
     * 현재 엔티티의 업무 필드({@code reqNm}, {@code reqCone}, {@code reqDtt},
     * {@code bzDtt}, {@code fsgTlm})를 복제하여 지정된 버전({@code nextVrs})의
     * 새 {@link Brdocm} 인스턴스를 반환합니다.
     * </p>
     *
     * <p>
     * {@link com.kdb.it.domain.entity.BaseEntity#prePersist()} 가 {@code delYn},
     * {@code guid}, {@code guidPrgSno} 및 JPA Auditing 필드({@code fstEnrDtm},
     * {@code fstEnrUsid} 등)를 INSERT 시점에 자동 설정하므로, 이 메서드에서는
     * 해당 필드들을 별도로 지정하지 않습니다.
     * </p>
     *
     * @param nextVrs 새로 생성할 문서버전 (예: 1.01, 2.00)
     * @return 새 버전의 {@link Brdocm} 인스턴스 (영속화 전 상태)
     */
    public Brdocm newVersion(BigDecimal nextVrs) {
        return Brdocm.builder()
            .docMngNo(this.docMngNo)
            .docVrs(nextVrs)
            .reqNm(this.reqNm)
            .reqCone(this.reqCone)
            .reqDtt(this.reqDtt)
            .bzDtt(this.bzDtt)
            .fsgTlm(this.fsgTlm)
            .build();
    }
}
