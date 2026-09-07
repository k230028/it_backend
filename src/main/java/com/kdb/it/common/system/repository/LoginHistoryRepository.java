package com.kdb.it.common.system.repository;

import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.util.NativeRowMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 공통로그인이력(Clognh) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 로그인이력 테이블(TPRMPP_CLOGNH)에 대한 CRUD 기능을 제공합니다.
 *
 * <p>기본키 타입: {@link Long} (lgnLogSno: Oracle 시퀀스 SQ_TPRMPP_CLOGNH_1)
 *
 * <p>로그인구분코드({@code IT_PTL_LGN_TC})는 공통코드 {@code C_ID='IT_PTL_LGN_TC'} 기반 1자리 값입니다. (1=성공, 2=실패,
 * 3=로그아웃)
 */
public interface LoginHistoryRepository extends JpaRepository<Clognh, Long> {

    /** 관리자 로그인 이력 응답에 필요한 프로젝션. */
    interface LoginHistoryView {
        String getEno();

        LocalDateTime getLgnDtm();

        String getItPtlLgnTc();

        String getIpAddr();

        String getLgnErrRsn();

        String getAgtVrsCone();

        LocalDateTime getFstEnrDtm();
    }

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
     * @param eno 조회할 사용자의 사번
     * @param startTime 조회 시작 시각 (포함)
     * @param endTime 조회 종료 시각 (포함)
     * @return 해당 기간의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByEnoAndLgnDtmBetweenOrderByLgnDtmDesc(
            String eno, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 로그인구분코드로 이력 조회 (최신순)
     *
     * @param itPtlLgnTc 조회할 로그인구분코드 ("1"=성공, "2"=실패, "3"=로그아웃)
     * @return 해당 구분코드의 로그인 이력 목록 (로그인일시 내림차순)
     */
    List<Clognh> findByItPtlLgnTcOrderByLgnDtmDesc(String itPtlLgnTc);

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
     * 전체 로그인 이력을 관리자 페이지 프로젝션으로 최신순 조회합니다.
     *
     * @param pageable 페이지 정보
     * @return 최신순 로그인 이력 프로젝션 페이지
     */
    Page<LoginHistoryView> findPageViewsByOrderByLgnDtmDesc(Pageable pageable);

    /**
     * 특정 사용자의 지정 시각 이후 로그인구분코드별 이력 건수 조회 — SEC-03 Brute-force 감지용
     *
     * <p>직전 N분 내 로그인 실패({@code IT_PTL_LGN_TC='2'}) 횟수를 집계하여 Brute-force 공격 여부를 판단합니다.
     *
     * @param eno 조회할 사용자의 사번
     * @param itPtlLgnTc 로그인구분코드 (예: "2"=로그인 실패)
     * @param after 집계 시작 시각 (이 시각 이후 이력만 카운트)
     * @return 해당 조건에 맞는 이력 건수
     */
    long countByEnoAndItPtlLgnTcAndLgnDtmAfter(String eno, String itPtlLgnTc, LocalDateTime after);

    /**
     * 최근 30일 일별 로그인 성공 건수·접속자 수 집계 (대시보드용)
     *
     * <p>TPRMPP_CLOGNH에서 {@code IT_PTL_LGN_TC='1'} 조건으로 최근 30일간의 날짜별 로그인 성공 건수(접속 횟수)와 행번(ENO) 중복을
     * 제거한 접속자 수를 함께 집계합니다. Oracle TRUNC 함수로 날짜 단위 그룹화.
     *
     * @return [날짜 문자열(YYYY-MM-DD), 접속 횟수, 접속자 수(행번 DISTINCT)] 배열 목록
     */
    @Query(
            value =
                    """
            SELECT TO_CHAR(TRUNC(LGN_DTM), 'YYYY-MM-DD') AS LGN_DATE,
                   COUNT(*) AS CNT,
                   COUNT(DISTINCT ENO) AS UNIQUE_CNT
            FROM TPRMPP_CLOGNH
            WHERE IT_PTL_LGN_TC = '1'
              AND LGN_DTM >= TRUNC(SYSDATE) - 30
            GROUP BY TRUNC(LGN_DTM)
            ORDER BY TRUNC(LGN_DTM)
            """,
            nativeQuery = true)
    List<Object[]> findDailyLoginStats();

    /**
     * 최근 30일 일별 로그인 성공 건수·접속자 수를 DTO로 봉인 반환한다(#6).
     *
     * @return (일자 YYYY-MM-DD, 접속 횟수, 접속자 수) DTO 목록
     */
    default List<DailyLoginStatRow> findDailyLoginStatRows() {
        return findDailyLoginStats().stream().map(DailyLoginStatRow::fromRow).toList();
    }

    /**
     * {@link #findDailyLoginStats()} 3컬럼 native 집계 결과 DTO.
     *
     * @param label 집계 일자(YYYY-MM-DD)
     * @param count 로그인 성공 건수(접속 횟수)
     * @param uniqueUserCount 행번(ENO) 중복을 제거한 접속자 수
     */
    record DailyLoginStatRow(String label, long count, long uniqueUserCount) {
        /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
        private static final int EXPECTED_COLUMNS = 3;

        /**
         * native {@code Object[]} 1행을 DTO로 매핑한다. 건수 컬럼이 null이면 0으로 폴백한다.
         *
         * @param r [0]=일자(VARCHAR), [1]=접속 횟수(NUMBER), [2]=접속자 수(NUMBER)
         * @return 매핑된 DTO
         * @throws IllegalStateException 컬럼 수가 3이 아니면(SQL/팩토리 불일치 조기 검출)
         */
        public static DailyLoginStatRow fromRow(Object[] r) {
            if (r == null || r.length != EXPECTED_COLUMNS) {
                throw new IllegalStateException(
                        "컬럼 수 불일치: 기대="
                                + EXPECTED_COLUMNS
                                + ", 실제="
                                + (r == null ? "null" : r.length));
            }
            return new DailyLoginStatRow(
                    NativeRowMapper.toStr(r[0]), zeroIfNull(r[1]), zeroIfNull(r[2]));
        }

        private static long zeroIfNull(Object v) {
            Long n = NativeRowMapper.toLong(v);
            return n == null ? 0L : n;
        }
    }
}
