package com.kdb.it.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.kdb.it.config.JpaAuditConfig;
import com.kdb.it.domain.log.listener.ChangeLogEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 공통 감사(Audit) 정보를 담는 추상 기반 엔티티 클래스
 *
 * <p>
 * 모든 업무 엔티티({@link Bprojm}, {@link Bcostm}, {@link Capplm} 등)가
 * 상속하는 공통 필드를 정의합니다.
 * </p>
 *
 * <p>
 * 자동 관리 필드:
 * </p>
 * <ul>
 * <li>{@code DEL_YN}: 논리 삭제 여부 (기본값: 'N', 삭제 시 'Y')</li>
 * <li>{@code GUID}: UUID 기반 고유 식별자 (자동 생성)</li>
 * <li>{@code FST_ENR_DTM}: 최초 등록일시 (JPA Auditing 자동 기록)</li>
 * <li>{@code FST_ENR_USID}: 최초 등록자 사번 (JPA Auditing 자동 기록)</li>
 * <li>{@code LST_CHG_DTM}: 최종 변경일시 (JPA Auditing 자동 기록)</li>
 * <li>{@code LST_CHG_USID}: 최종 변경자 사번 (JPA Auditing 자동 기록)</li>
 * </ul>
 *
 * <p>
 * Soft Delete 패턴: {@link #delete()} 메서드로 {@code DEL_YN}을 'Y'로 변경하여
 * 물리적 삭제 대신 논리 삭제를 수행합니다.
 * </p>
 *
 * @see JpaAuditConfig JPA Auditing 설정
 */
@MappedSuperclass // 이 클래스의 필드를 자식 엔티티 테이블에 매핑 (별도 테이블 없음)
@Getter // 모든 필드의 getter 자동 생성 (Lombok)
@SuperBuilder // 부모-자식 상속 구조에서 Builder 패턴 지원 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자를 protected로 제한 (외부 직접 생성 방지)
@AllArgsConstructor // 모든 필드를 받는 생성자 자동 생성 (Lombok)
@EntityListeners({AuditingEntityListener.class, ChangeLogEntityListener.class}) // JPA Auditing + 변경 로그 리스너
public abstract class BaseEntity {

    /** 삭제여부: 'N'=미삭제(기본값), 'Y'=삭제 (Soft Delete용 플래그) */
    @Column(name = "DEL_YN", length = 1, comment = "삭제여부")
    private String delYn;

    /**
     * 일련번호: UUID v4 기반 전역 고유 식별자 (자동 생성, 형식: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
     */
    @Column(name = "GUID", length = 38, comment = "전역고유식별자")
    private String guid;

    /** 일련번호2: 기본값 1 (버전 관리 또는 이력 구분용) */
    @Column(name = "GUID_PRG_SNO", comment = "진행일련번호")
    private Integer guidPrgSno;

    /**
     * 최초생성시간: 엔티티가 처음 저장될 때 자동으로 현재 시각이 기록됩니다.
     * {@code updatable = false}로 설정하여 이후 업데이트 시 변경되지 않습니다.
     */
    @CreatedDate
    @Column(name = "FST_ENR_DTM", updatable = false, comment = "최초등록일시")
    private LocalDateTime fstEnrDtm;

    /**
     * 최초생성자: 엔티티가 처음 저장될 때 현재 로그인한 사용자의 사번이 자동으로 기록됩니다.
     * {@link JpaAuditConfig#auditorProvider()}에서 현재 인증 사용자를 제공합니다.
     * {@code updatable = false}로 설정하여 이후 업데이트 시 변경되지 않습니다.
     */
    @CreatedBy
    @Column(name = "FST_ENR_USID", length = 14, updatable = false, comment = "최초등록자사번")
    private String fstEnrUsid;

    /**
     * 마지막수정시간: 엔티티가 수정될 때마다 현재 시각이 자동으로 업데이트됩니다.
     */
    @LastModifiedDate
    @Column(name = "LST_CHG_DTM", comment = "최종변경일시")
    private LocalDateTime lstChgDtm;

    /**
     * 마지막수정자: 엔티티가 수정될 때마다 현재 로그인한 사용자의 사번이 자동으로 업데이트됩니다.
     */
    @LastModifiedBy
    @Column(name = "LST_CHG_USID", length = 14, comment = "최종변경자사번")
    private String lstChgUsid;

    /**
     * JPA 엔티티 최초 저장(INSERT) 전 자동 실행 콜백 메서드
     *
     * <p>
     * DB에 INSERT되기 직전에 호출되어 기본값이 없는 필드를 초기화합니다.
     * </p>
     *
     * <p>
     * 초기화 항목:
     * </p>
     * <ul>
     * <li>{@code delYn}: null인 경우 'N'으로 설정 (미삭제 상태)</li>
     * <li>{@code guid}: null인 경우 UUID v4 랜덤 값으로 자동 생성</li>
     * <li>{@code guidPrgSno}: null인 경우 1로 설정</li>
     * </ul>
     */
    @PrePersist
    public void prePersist() {
        // 삭제여부 기본값 설정: null이면 'N'(미삭제)으로 초기화
        if (this.delYn == null) {
            this.delYn = "N";
        }
        // GUID 자동 생성: null이면 UUID v4 랜덤 문자열로 초기화
        if (this.guid == null) {
            this.guid = UUID.randomUUID().toString();
        }
        // GUID 일련번호 기본값 설정: null이면 1로 초기화
        if (this.guidPrgSno == null) {
            this.guidPrgSno = 1;
        }
    }

    /**
     * 논리 삭제(Soft Delete) 처리 메서드
     *
     * <p>
     * 실제 DB 레코드를 삭제하지 않고 {@code DEL_YN}을 'Y'로 변경합니다.
     * 데이터 이력 보존 및 복구 가능성을 위해 사용합니다.
     * </p>
     *
     * <p>
     * 사용 예:
     * </p>
     * 
     * <pre>{@code
     * project.delete(); // DEL_YN = 'Y'로 변경
     * projectRepository.save(project); // 변경 사항 DB 반영
     * }</pre>
     */
    public void delete() {
        this.delYn = "Y";
    }
}
