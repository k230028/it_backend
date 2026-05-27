package com.kdb.it.common.notification.entity;

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

import java.time.LocalDateTime;

/**
 * 공통알림기본 엔티티 — {@code TPRMPP_CINFMM}
 *
 * <p>
 * 결재요청·결재결과·게시판 멘션·시스템 알림 등 사내 알림을 1행 = 1수신자 구조로 보관한다.
 * BaseEntity 상속으로 공통 컬럼(DEL_YN, GUID, FST_ENR_*, LST_CHG_*)을 자동 포함한다.
 * </p>
 *
 * <p>채번 규칙: {@code INF-{YYYY}-{8자리 시퀀스}} (예: {@code INF-2026-00000001})</p>
 *
 * <p>알림서비스구분({@code INFM_SVC_TC})은 공통코드 {@code C_ID='INFM_SVC'} 2자리 값.
 * 발송구분({@code SD_TC})은 공통코드 {@code C_ID='SD'} 2자리 값.</p>
 */
@Entity
@Table(name = "TPRMPP_CINFMM", comment = "공통알림기본")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cinfmm extends BaseEntity {

    /** 알림메시지번호: 기본키. 형식 {@code INF-{YYYY}-{NEXTVAL:08}} */
    @Id
    @Column(name = "INFM_MSG_NO", length = 30, nullable = false, comment = "알림메시지번호")
    private String infmMsgNo;

    /** 알림서비스구분코드 — 공통코드 {@code C_ID='INFM_SVC'} (01=시스템, 02=결재요청, 03=결재결과, 04=게시물멘션, 05=댓글멘션, 06=결재회수) */
    @Column(name = "INFM_SVC_TC", length = 2, nullable = false, comment = "알림서비스구분코드")
    private String infmSvcTc;

    /** 제목 (최대 100자) */
    @Column(name = "TTL", length = 100, comment = "제목")
    private String ttl;

    /** 알림메시지내용: 알림 본문 (최대 4000자) */
    @Column(name = "INFM_MSG_CONE", length = 4000, comment = "알림메시지내용")
    private String infmMsgCone;

    /** 알림추천URL: 클릭 시 이동할 앱 내부 라우트 (최대 300자) */
    @Column(name = "INFM_RCD_URL", length = 300, comment = "알림추천URL")
    private String infmRcdUrl;

    /** 수신자사원번호 (1행 = 1수신자) */
    @Column(name = "RMS_ENO", length = 14, nullable = false, comment = "수신자사원번호")
    private String rmsEno;

    /** 조회여부: 'N' 미조회(기본), 'Y' 조회(=읽음) */
    @Column(name = "INQ_YN", length = 1, nullable = false, comment = "조회여부")
    private String inqYn;

    /** 조회일시: 알림을 처음 조회(=읽음 처리)한 시각 (미조회 상태에서는 null) */
    @Column(name = "INQ_DTM", comment = "조회일시")
    private LocalDateTime inqDtm;

    /** 발송구분코드 — 공통코드 {@code C_ID='SD'} (01=인앱, 02=알림톡, 03=SMS, 04=이메일) */
    @Column(name = "SD_TC", length = 2, comment = "발송구분코드")
    private String sdTc;

    /** 발송일시 (null=미발송) */
    @Column(name = "SD_DTM", comment = "발송일시")
    private LocalDateTime sdDtm;

    /** 발송문서내용: 외부 발송 페이로드 (JSON 권장, 최대 4000자) */
    @Column(name = "SD_DOC_CONE", length = 4000, comment = "발송문서내용")
    private String sdDocCone;

    // ── 비즈니스 메서드 ─────────────────────────────────────────────────────

    /** 조회(읽음) 처리 — INQ_YN='Y', INQ_DTM=now (이미 조회 상태면 변경 없음) */
    public void markRead() {
        if ("Y".equals(this.inqYn)) {
            return;
        }
        this.inqYn = "Y";
        this.inqDtm = LocalDateTime.now();
    }

    /**
     * 발송 완료 메타 기록.
     *
     * @param sdTc     발송 채널 코드 (공통코드 SD; 01=인앱, 02=알림톡, 03=SMS, 04=이메일)
     * @param payload  외부 발송 페이로드 (JSON 또는 null)
     */
    public void markDispatched(String sdTc, String payload) {
        this.sdTc = sdTc;
        this.sdDtm = LocalDateTime.now();
        this.sdDocCone = payload;
    }
}
