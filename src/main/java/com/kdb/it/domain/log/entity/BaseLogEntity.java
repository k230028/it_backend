package com.kdb.it.domain.log.entity;

import com.kdb.it.domain.log.id.AuditLogId;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 변경 로그 엔티티의 공통 필드를 정의하는 추상 기반 클래스.
 *
 * <p>모든 로그 엔티티({@code BprojmL}, {@code BitemlL} 등)가 상속한다.</p>
 *
 * <p>PK({@code LOG_SNO})는 {@link AuditLogIdGenerator}가
 * {@code SEQ_{Postfix}.NEXTVAL}을 조회하여 Long 값으로 생성한다.</p>
 *
 * <p>BaseEntity 스냅샷 필드(DEL_YN, GUID, FST_ENR_DTM 등)는
 * INSERT 시점 원본 엔티티의 값을 리플렉션으로 복사하여 저장한다.</p>
 */
@MappedSuperclass
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public abstract class BaseLogEntity {

    /** 로그일련번호: PK. Oracle 시퀀스 SEQ_{테이블Postfix}에서 발급 */
    @Id
    @AuditLogId
    @Column(name = "LOG_SNO", nullable = false, updatable = false, comment = "로그일련번호")
    private Long logSno;

    /**
     * 변경유형구분코드: C(생성) / U(수정) / D(논리삭제).
     *
     * <p>DB 실제 컬럼은 {@code CHG_TP} (VARCHAR2(4), 대부분 *L 테이블에서 NOT NULL).
     * 과거 매핑은 {@code CHG_TC}로 잘못 지정되어 있었으며, 이로 인해 INSERT 시
     * {@code CHG_TP}에 값이 들어가지 않아 ORA-01400이 발생했습니다 (예: CCODEL update).
     * {@link com.kdb.it.domain.log.listener.AuditLogPersister#persist}가
     * {@code setField(logEntity, "chgTp", chgTp)}로 'C'/'U'/'D'를 채우므로
     * 본 매핑 변경만으로 모든 *L 테이블의 NOT NULL 제약을 통과합니다.</p>
     */
    @Column(name = "CHG_TP", length = 1, comment = "변경유형구분코드")
    private String chgTp;

    /** 변경일시: 로그 INSERT 시각 */
    @Column(name = "CHG_DTM", nullable = false, comment = "변경일시")
    private LocalDateTime chgDtm;

    /** 변경자사번: SecurityContext에서 추출한 현재 사용자 사번 */
    @Column(name = "CHG_USID", length = 14, comment = "변경자사번")
    private String chgUsid;

    /* ── BaseEntity 스냅샷 필드 ── */

    /** 삭제여부 스냅샷 */
    @Column(name = "DEL_YN", length = 1, comment = "삭제여부")
    private String delYn;

    /** 전역고유식별자 스냅샷 */
    @Column(name = "GUID", length = 38, comment = "전역고유식별자")
    private String guid;

    /** 진행일련번호 스냅샷 */
    @Column(name = "GUID_PRG_SNO", comment = "진행일련번호")
    private Integer guidPrgSno;

    /** 최초등록일시 스냅샷 */
    @Column(name = "FST_ENR_DTM", comment = "최초등록일시")
    private LocalDateTime fstEnrDtm;

    /** 최초등록자사번 스냅샷 */
    @Column(name = "FST_ENR_USID", length = 14, comment = "최초등록자사번")
    private String fstEnrUsid;

    /** 최종변경일시 스냅샷 */
    @Column(name = "LST_CHG_DTM", comment = "최종변경일시")
    private LocalDateTime lstChgDtm;

    /** 최종변경자사번 스냅샷 */
    @Column(name = "LST_CHG_USID", length = 14, comment = "최종변경자사번")
    private String lstChgUsid;
}
