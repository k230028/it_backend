package com.kdb.it.common.code.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 공통코드마스터(Ccodem) 엔티티의 복합 기본키 클래스
 *
 * <p>JPA의 {@code @IdClass} 방식으로 {@link Ccodem} 엔티티의 복합키를 정의합니다.</p>
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CcodemId implements Serializable {

    /** 코드ID: Ccodem.cdId와 이름/타입 일치 필수 */
    private String cdId;

    /** 시작일자: Ccodem.sttDt와 이름/타입 일치 필수 */
    private LocalDate sttDt;
}
