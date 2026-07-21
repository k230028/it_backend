package com.kdb.it.domain.budget.document.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 요구사항 정의서(Brdocm) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 {@link Brdocm} 엔티티의 복합키를 정의합니다. {@link Brdocm}의 {@code @Id}
 * 필드({@code docMngNo}, {@code docVrs})와 동일한 이름과 타입을 가져야 합니다.
 *
 * <p>JPA 복합키 클래스 요구사항:
 *
 * <ul>
 *   <li>{@link Serializable} 구현 필수
 *   <li>기본 생성자 필수
 *   <li>{@code equals()}, {@code hashCode()} 재정의 필수
 * </ul>
 *
 * <p>equals/hashCode 는 Lombok {@code @EqualsAndHashCode} 대신 수동 구현합니다. {@link
 * BigDecimal#equals(Object)} 는 스케일까지 비교하므로 {@code 0.01} 과 {@code 0.010} 을 서로 다른 값으로 판단하여 JPA 1차 캐시
 * 미스 등의 문제가 발생할 수 있습니다. 따라서 {@link BigDecimal#compareTo(BigDecimal)} 기반으로 값 동등성을 보장하며, {@code
 * hashCode()} 역시 {@link BigDecimal#stripTrailingZeros()} 로 스케일에 영향받지 않도록 합니다.
 */
@NoArgsConstructor // JPA 요구사항: 기본 생성자 필수
@AllArgsConstructor // 모든 필드를 받는 생성자 (직접 생성용)
public class BrdocmId implements Serializable {

    /** 문서관리번호: Brdocm.docMngNo와 이름/타입 일치 필수 */
    private String docMngNo;

    /** 문서버전: Brdocm.docVrsSno와 이름/타입 일치 필수 (Oracle NUMBER(4,2)) */
    private BigDecimal docVrsSno;

    /**
     * 값 동등성 비교 BigDecimal 필드는 {@code compareTo} 로 비교하여 스케일 차이({@code 0.01} vs {@code 0.010})를 동일한
     * 값으로 간주합니다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrdocmId)) return false;
        BrdocmId that = (BrdocmId) o;
        return Objects.equals(docMngNo, that.docMngNo)
                && (docVrsSno == null
                        ? that.docVrsSno == null
                        : (that.docVrsSno != null && docVrsSno.compareTo(that.docVrsSno) == 0));
    }

    /**
     * 해시코드 생성 BigDecimal 의 스케일이 해시에 영향을 주지 않도록 {@link BigDecimal#stripTrailingZeros()} 적용 후 해시를
     * 계산합니다.
     */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(docMngNo);
        result = 31 * result + (docVrsSno == null ? 0 : docVrsSno.stripTrailingZeros().hashCode());
        return result;
    }
}
