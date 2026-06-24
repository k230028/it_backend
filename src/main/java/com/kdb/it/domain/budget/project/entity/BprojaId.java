package com.kdb.it.domain.budget.project.entity;

import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정보화사업관계(BPROJA) 엔티티의 복합 기본키 클래스.
 *
 * <p>{@link Bproja}의 {@code @Id} 필드({@code abusMngNo}, {@code cncdRfrNo})와
 * 동일한 이름·타입을 가져야 합니다. {@link Serializable} 구현 + 기본 생성자 + equals/hashCode 필수.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BprojaId implements Serializable {

    /** 사업관리번호: Bproja.abusMngNo와 이름/타입 일치 필수 */
    @Column(name = "ABUS_MNG_NO", comment = "사업관리번호")
    private String abusMngNo;

    /** 관련참조번호(단계 원본문서 key): Bproja.cncdRfrNo와 이름/타입 일치 필수 */
    @Column(name = "CNCD_RFR_NO", comment = "관련참조번호")
    private String cncdRfrNo;
}
