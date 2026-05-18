package com.kdb.it.common.system.repository;

import com.kdb.it.common.system.entity.Clognh;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 로그인이력(Clognh) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 로그인이력 테이블(TAAABB_CLOGNH)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link Long} (lgnSno: Oracle 시퀀스 SEQ_CLOGNH)</p>
 *
 * <p>보안 감사 목적의 이력 조회 메서드를 제공합니다.</p>
 */
public interface LoginHistoryRepository extends JpaRepository<Clognh, Long> {

    /**
     * 사번으로 로그인 이력 조회 (최신순)
     *
     * <p>특정 사용자의 전체 로그인 이력을 최신 순으로 반환합니다.
     * 최신순 정렬은 {@code LGN_DTM DESC}로 처리됩니다.</p>
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 사번과 날짜 범위로 로그인 이력 조회 (최신순)
     *
     * <p>특정 기간 동안의 사용자 로그인 이력을 조회합니다.
     * 이상 접근 탐지(특정 기간 내 과도한 로그인 실패 등)에 활용할 수 있습니다.</p>
     *
     * @param eno       조회할 사용자의 사번
     * @param startTime 조회 시작 시각 (포함)
     * @param endTime   조회 종료 시각 (포함)
     * @return 해당 기간의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByEnoAndLgnDtmBetweenOrderByLgnDtmDesc(
            String eno, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 로그인유형으로 이력 조회 (최신순)
     *
     * <p>특정 유형의 이력(예: 로그인 실패만)을 전체에서 조회합니다.
     * 관리자 보안 모니터링에 활용할 수 있습니다.</p>
     *
     * @param lgnTp 조회할 이력 유형 ("LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGOUT")
     * @return 해당 유형의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByLgnTpOrderByLgnDtmDesc(String lgnTp);

    /**
     * 특정 사용자의 최근 50개 이력 조회 (최신순)
     *
     * <p>로그인 이력 목록 조회 시 DB 레벨에서 50건으로 제한합니다.
     * {@code findTop50By} 접두사를 통해 Spring Data JPA가 자동으로 LIMIT 50 처리합니다.</p>
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 최근 50개 로그인 이력 (로그인일시 내림차순)
     */
    List<Clognh> findTop50ByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 특정 사용자의 최근 10개 이력 조회 (최신순)
     *
     * <p>사용자 대시보드에서 최근 접속 이력을 간략하게 표시하는 데 사용됩니다.
     * {@code findTop10By} 접두사를 통해 Spring Data JPA가 자동으로 LIMIT 10 처리합니다.</p>
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 최근 10개 로그인 이력 (로그인일시 내림차순)
     */
    List<Clognh> findTop10ByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 전체 로그인 이력 페이지네이션 조회 (최신순)
     *
     * <p>관리자 화면에서 전체 로그인 이력을 페이지 단위로 조회합니다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 페이지네이션된 로그인 이력
     */
    Page<Clognh> findAllByOrderByLgnDtmDesc(Pageable pageable);

    /**
     * 특정 사용자의 지정 시각 이후 로그인유형별 이력 건수 조회 — SEC-03 Brute-force 감지용
     *
     * <p>직전 N분 내 LOGIN_FAILURE 횟수를 집계하여 Brute-force 공격 여부를 판단합니다.
     * Spring Data JPA 파생 쿼리로 별도 SQL 작성 없이 처리됩니다.</p>
     *
     * @param eno    조회할 사용자의 사번
     * @param lgnTp  로그인 유형 (예: "LOGIN_FAILURE")
     * @param after  집계 시작 시각 (이 시각 이후 이력만 카운트)
     * @return 해당 조건에 맞는 이력 건수
     */
    long countByEnoAndLgnTpAndLgnDtmAfter(String eno, String lgnTp, LocalDateTime after);

    /**
     * 최근 30일 일별 로그인 건수 집계 (대시보드용)
     *
     * <p>TAAABB_CLOGNH에서 LGN_TP='LOGIN_SUCCESS' 조건으로 최근 30일간의
     * 날짜별 로그인 성공 건수를 집계합니다. Oracle TRUNC 함수로 날짜 단위 그룹화.</p>
     *
     * @return [날짜 문자열(YYYY-MM-DD), 건수] 쌍의 배열 목록
     */
    @Query(value = """
            SELECT TO_CHAR(TRUNC(LGN_DTM), 'YYYY-MM-DD') AS LGN_DATE,
                   COUNT(*) AS CNT
            FROM TAAABB_CLOGNH
            WHERE LGN_TP = 'LOGIN_SUCCESS'
              AND LGN_DTM >= TRUNC(SYSDATE) - 30
            GROUP BY TRUNC(LGN_DTM)
            ORDER BY TRUNC(LGN_DTM)
            """, nativeQuery = true)
    List<Object[]> findDailyLoginStats();
}
