package com.kdb.it.common.approval.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Cappla 복합 PK 클래스
 *
 * <p>JPA {@code @IdClass} 규약에 따라 {@link Cappla} 엔티티의 복합 기본키를 표현합니다.</p>
 * <ul>
 *   <li>{@code apfMngNo} — 신청서관리번호 (APF_MNG_NO)</li>
 *   <li>{@code apfRelSno} — 신청서관계일련번호 (APF_REL_SNO, 신청서 단위 1~N)</li>
 * </ul>
 *
 * <p>기본 생성자 및 {@code equals/hashCode} 구현은 JPA 명세 요구사항입니다.</p>
 */
public class CapplaId implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 신청서관리번호 */
    private String apfMngNo;

    /** 신청서관계일련번호 (신청서 단위 1~N) */
    private Long apfRelSno;

    public CapplaId() {
    }

    public CapplaId(String apfMngNo, Long apfRelSno) {
        this.apfMngNo = apfMngNo;
        this.apfRelSno = apfRelSno;
    }

    public String getApfMngNo() {
        return apfMngNo;
    }

    public Long getApfRelSno() {
        return apfRelSno;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapplaId that)) return false;
        return Objects.equals(apfMngNo, that.apfMngNo)
                && Objects.equals(apfRelSno, that.apfRelSno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apfMngNo, apfRelSno);
    }
}
