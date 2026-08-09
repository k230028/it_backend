package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 공통알림기본 동적 쿼리 인터페이스 (QueryDSL 구현 위임). */
public interface CinfmmRepositoryCustom {

    /**
     * 수신자별 알림 목록 페이지 조회 (미삭제 + 최신 등록 순).
     *
     * @param rmsEno 수신자 사원번호 (RMS_ENO)
     * @param unreadOnly true=미조회만, false 또는 null=전체
     * @param pageable 페이지 정보 (정렬은 본 메서드가 FST_ENR_DTM DESC로 강제)
     * @return 알림 페이지
     */
    Page<Cinfmm> findInbox(String rmsEno, Boolean unreadOnly, Pageable pageable);

    /** 수신자별 알림 목록을 응답 최소 필드로 페이지 조회합니다. */
    Page<NotificationInboxRow> findInboxRows(String rmsEno, Boolean unreadOnly, Pageable pageable);

    /**
     * 수신자별 미조회(미읽음) 카운트.
     *
     * @param rmsEno 수신자 사원번호
     * @return 미조회 알림 건수 (미삭제 기준)
     */
    long countUnread(String rmsEno);

    /**
     * 수신자별 미조회 알림을 일괄 조회(읽음) 처리.
     *
     * @param rmsEno 수신자 사원번호
     * @return 갱신된 행 수
     */
    long markAllReadByRmsEno(String rmsEno);
}
