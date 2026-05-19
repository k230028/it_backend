package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 알림 마스터 동적 쿼리 인터페이스 (QueryDSL 구현 위임).
 */
public interface CinfmmRepositoryCustom {

    /**
     * 수신자별 알림 목록 페이지 조회 (미삭제 + 최신 등록 순).
     *
     * @param rcvUsid    수신자 사번
     * @param unreadOnly true=미읽음만, false 또는 null=전체
     * @param pageable   페이지 정보 (정렬은 본 메서드가 FST_ENR_DTM DESC로 강제)
     * @return 알림 페이지
     */
    Page<Cinfmm> findInbox(String rcvUsid, Boolean unreadOnly, Pageable pageable);

    /**
     * 수신자별 미읽음 카운트.
     *
     * @param rcvUsid 수신자 사번
     * @return 미읽음 알림 건수 (미삭제 기준)
     */
    long countUnread(String rcvUsid);

    /**
     * 수신자별 미읽음 알림을 일괄 읽음 처리.
     *
     * @param rcvUsid 수신자 사번
     * @return 갱신된 행 수
     */
    long markAllReadByRcvUsid(String rcvUsid);
}
