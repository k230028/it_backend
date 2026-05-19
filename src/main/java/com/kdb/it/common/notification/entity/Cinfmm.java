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
 * 알림 마스터 엔티티 — {@code TAAABB_CINFMM}
 *
 * <p>
 * 결재요청·결재결과·게시판 멘션 등 사내 알림을 1행 = 1수신자 구조로 보관한다.
 * BaseEntity 상속을 통해 공통 컬럼(DEL_YN, GUID, FST_ENR_*, LST_CHG_*)을 자동 포함한다.
 * </p>
 *
 * <p>채번 규칙: {@code INF-{YYYY}-{8자리 시퀀스}} (예: {@code INF-2026-00000001})</p>
 *
 * <p>EAI 컬럼은 외부 시스템 발송 채널·이력을 표현한다. 본 페이즈에서는 인앱(INAPP)만
 * 적재 시점에 동기 기록되고, 나머지 채널(EMAIL/SMS/TALK)은 Phase 2에서 활성화된다.</p>
 */
@Entity
@Table(name = "TAAABB_CINFMM", comment = "알림 마스터")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cinfmm extends BaseEntity {

    /** 알림관리번호: 기본키. 형식 {@code INF-{YYYY}-{NEXTVAL:08}} */
    @Id
    @Column(name = "INF_MNG_NO", length = 32, nullable = false, comment = "알림관리번호")
    private String infMngNo;

    /** 알림종류구분코드 — Ccodem cId=CINF_TP */
    @Column(name = "INF_TP_C", length = 3, nullable = false, comment = "알림종류구분코드")
    private String infTpC;

    /** 알림 제목 */
    @Column(name = "INF_TTL", length = 100, comment = "알림제목")
    private String infTtl;

    /** 알림 내용 미리보기 */
    @Column(name = "INF_CONE", length = 300, comment = "알림내용")
    private String infCone;

    /** 클릭 시 이동할 앱 내부 라우트 */
    @Column(name = "INF_LNK_URL", length = 300, comment = "알림연결URL")
    private String infLnkUrl;

    /** 수신자 사번 (1행 = 1수신자) */
    @Column(name = "RCV_USID", length = 14, nullable = false, comment = "수신자사번")
    private String rcvUsid;

    /** 읽음여부: 'N' 미읽음(기본), 'Y' 읽음 */
    @Column(name = "RDD_YN", length = 1, nullable = false, comment = "읽음여부")
    private String rddYn;

    /** 읽음일시 (미읽음 상태에서는 null) */
    @Column(name = "RDD_DTM", comment = "읽음일시")
    private LocalDateTime rddDtm;

    /** EAI 발송구분코드 — Ccodem cId=CEAI_SD_TP (INAPP/EMAIL/SMS/TALK) */
    @Column(name = "EAI_SD_TP_C", length = 3, comment = "EAI발송구분코드")
    private String eaiSdTpC;

    /** EAI 발송일시 (null=미발송) */
    @Column(name = "EAI_SD_DTM", comment = "EAI발송일시")
    private LocalDateTime eaiSdDtm;

    /** EAI 발송내용 페이로드 (JSON 권장) */
    @Column(name = "EAI_SD_CONE", length = 4000, comment = "EAI발송내용")
    private String eaiSdCone;

    // ── 비즈니스 메서드 ─────────────────────────────────────────────────────

    /** 읽음 처리 — RDD_YN='Y', RDD_DTM=now (이미 읽음 상태면 변경 없음) */
    public void markRead() {
        if ("Y".equals(this.rddYn)) {
            return;
        }
        this.rddYn = "Y";
        this.rddDtm = LocalDateTime.now();
    }

    /**
     * EAI 발송 완료 메타 기록.
     *
     * @param eaiSdTpC 발송 채널 코드 (INAPP/EMAIL/SMS/TALK)
     * @param payload  외부 발송 페이로드 (JSON 또는 null)
     */
    public void markDispatched(String eaiSdTpC, String payload) {
        this.eaiSdTpC = eaiSdTpC;
        this.eaiSdDtm = LocalDateTime.now();
        this.eaiSdCone = payload;
    }
}
