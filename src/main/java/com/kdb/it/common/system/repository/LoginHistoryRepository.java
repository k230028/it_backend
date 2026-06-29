package com.kdb.it.common.system.repository;

import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.util.LabeledCountRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공통로그인이력(Clognh) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 로그인이력 테이블(TPRMPP_CLOGNH)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link Long} (lgnLogSno: Oracle 시퀀스 SEQ_CLOGNH)</p>
 *
 * <p>로그인구분코드({@code LGN_TC})는 공통코드 {@code C_ID='LGN_TC'} 기반 1자리 값입니다.
 * (1=성공, 2=실패, 3=로그아웃)</p>
 */
public interface LoginHistoryRepository extends JpaRepository<Clognh, Long> {

    /**
     * 사번으로 로그인 이력 조회 (최신순)
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 사번과 날짜 범위로 로그인 이력 조회 (최신순)
     *
     * @param eno       조회할 사용자의 사번
     * @param startTime 조회 시작 시각 (포함)
     * @param endTime   조회 종료 시각 (포함)
     * @return 해당 기간의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByEnoAndLgnDtmBetweenOrderByLgnDtmDesc(
            String eno, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 로그인구분코드로 이력 조회 (최신순)
     *
     * @param lgnTc 조회할 로그인구분코드 ("1"=성공, "2"=실패, "3"=로그아웃)
     * @return 해당 구분코드의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByLgnTcOrderByLgnDtmDesc(String lgnTc);

    /**
     * 특정 사용자의 최근 50개 이력 조회 (최신순)
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 최근 50개 로그인 이력 (로그인일시 내림차순)
     */
    List<Clognh> findTop50ByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 특정 사용자의 최근 10개 이력 조회 (최신순)
     *
     * @param eno 조회할 사용자의 사번
     * @return 해당 사용자의 최근 10개 로그인 이력 (로그인일시 내림차순)
     */
    List<Clognh> findTop10ByEnoOrderByLgnDtmDesc(String eno);

    /**
     * 전체 로그인 이력 페이지네이션 조회 (최신순)
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 페이지네이션된 로그인 이력
     */
    Page<Clognh> findAllByOrderByLgnDtmDesc(Pageable pageable);

    /**
     * 특정 사용자의 지정 시각 이후 로그인구분코드별 이력 건수 조회 — SEC-03 Brute-force 감지용
     *
     * <p>직전 N분 내 로그인 실패({@code LGN_TC='2'}) 횟수를 집계하여 Brute-force 공격 여부를 판단합니다.</p>
     *
     * @param eno    조회할 사용자의 사번
     * @param lgnTc  로그인구분코드 (예: "2"=로그인 실패)
     * @param after  집계 시작 시각 (이 시각 이후 이력만 카운트)
     * @return 해당 조건에 맞는 이력 건수
     */
    long countByEnoAndLgnTcAndLgnDtmAfter(String eno, String lgnTc, LocalDateTime after);

    /**
     * 최근 30일 일별 로그인 성공 건수 집계 (대시보드용)
     *
     * <p>TPRMPP_CLOGNH에서 {@code LGN_TC='1'} 조건으로 최근 30일간의
     * 날짜별 로그인 성공 건수를 집계합니다. Oracle TRUNC 함수로 날짜 단위 그룹화.</p>
     *
     * @return [날짜 문자열(YYYY-MM-DD), 건수] 쌍의 배열 목록
     */
    @Query(value = """
            SELECT TO_CHAR(TRUNC(LGN_DTM), 'YYYY-MM-DD') AS LGN_DATE,
                   COUNT(*) AS CNT
            FROM TPRMPP_CLOGNH
            WHERE LGN_TC = '1'
              AND LGN_DTM >= TRUNC(SYSDATE) - 30
            GROUP BY TRUNC(LGN_DTM)
            ORDER BY TRUNC(LGN_DTM)
            """, nativeQuery = true)
    List<Object[]> findDailyLoginStats();

    /**
     * 최근 30일 일별 로그인 성공 건수를 DTO로 봉인 반환한다(#6).
     *
     * @return (일자 YYYY-MM-DD, 건수) DTO 목록
     */
    default List<LabeledCountRow> findDailyLoginStatRows() {
        return findDailyLoginStats().stream()
                .map(LabeledCountRow::fromRow)
                .toList();
    }
}
