package com.kdb.it.common.admin.realtime.repository;

import com.kdb.it.common.admin.realtime.dto.RealtimeLogDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V_ITPAPP_LOG_FEED 기반 실시간 로그 조회 Repository.
 *
 * <p>QueryDSL 메타모델을 사용하지 않고 EntityManager 네이티브 쿼리로 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class RealtimeLogRepository {

    private static final String BASE_COLS = """
        LOG_TBL, LOG_KEY, LOG_HIS_TGR_SNO, CHG_DTT_YN, CHG_DTM,
        CHG_USID, GUID, DEL_YN
        """;

    private static final String ORDER_AND_LIMIT = """
        ORDER BY CHG_DTM DESC, LOG_TBL DESC, LOG_HIS_TGR_SNO DESC
        FETCH FIRST :limit ROWS ONLY
        """;

    private final EntityManager entityManager;

    /**
     * 조건에 맞는 최신 로그를 최대 {@code limit}건 반환한다.
     *
     * <p>{@code since}가 null이면 초기 스냅샷, 비-null이면 복합 커서 기반 증분 조회.</p>
     */
    public List<RealtimeLogDto.FeedRow> findFeed(RealtimeLogDto.QueryCondition cond) {
        StringBuilder sql = new StringBuilder("SELECT ").append(BASE_COLS)
                .append(" FROM V_ITPAPP_LOG_FEED WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        params.put("limit", cond.limit());

        if (cond.since() != null) {
            sql.append(" AND ( CHG_DTM > :since")
               .append("       OR (CHG_DTM = :since AND LOG_TBL > :cursorLogTbl)")
               .append("       OR (CHG_DTM = :since AND LOG_TBL = :cursorLogTbl AND LOG_HIS_TGR_SNO > :cursorLogSno) ) ");
            params.put("since", Timestamp.valueOf(cond.since()));
            params.put("cursorLogTbl", cond.cursorLogTbl() == null ? "" : cond.cursorLogTbl());
            params.put("cursorLogSno", cond.cursorLogSno() == null ? 0L : cond.cursorLogSno());
        }
        if (cond.tableKeys() != null && !cond.tableKeys().isEmpty()) {
            sql.append(" AND LOG_KEY IN (:tableKeys) ");
            params.put("tableKeys", cond.tableKeys());
        }
        if (cond.chgTypes() != null && !cond.chgTypes().isEmpty()) {
            sql.append(" AND CHG_DTT_YN IN (:chgTypes) ");
            params.put("chgTypes", cond.chgTypes());
        }
        sql.append(ORDER_AND_LIMIT);

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<RealtimeLogDto.FeedRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new RealtimeLogDto.FeedRow(
                    toStr(r[0]),
                    toStr(r[1]),
                    ((Number) r[2]).longValue(),
                    toStr(r[3]),                    // CHG_DTT_YN — VARCHAR2(1), Oracle JDBC가 Character 반환 가능
                    toLdt(r[4]),                    // CHG_DTM — TIMESTAMP, Hibernate 6/Spring Boot 4가 LocalDateTime로 매핑하기도 함
                    toStr(r[5]),
                    toStr(r[6]),
                    toStr(r[7])                     // DEL_YN — VARCHAR2(1), Oracle JDBC가 Character 반환 가능
            ));
        }
        return out;
    }

    /**
     * Oracle JDBC가 VARCHAR2(1) 컬럼을 {@code Character}로 반환하는 경우가 있어
     * 직접 {@code (String)} 캐스트 시 ClassCastException이 발생한다.
     * 모든 텍스트 컬럼을 안전하게 String으로 변환한다.
     */
    private static String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    /**
     * Oracle TIMESTAMP 컬럼은 환경에 따라 {@link Timestamp} 또는 {@link LocalDateTime}로
     * 반환된다. 어느 쪽이든 안전하게 LocalDateTime으로 변환한다.
     */
    private static LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalStateException("지원하지 않는 시각 타입: " + v.getClass());
    }

    /**
     * 최근 5분간 LOG_KEY별 발생량.
     */
    public Map<String, Long> countByTableSince(LocalDateTime since) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT LOG_KEY, COUNT(*)
                  FROM V_ITPAPP_LOG_FEED
                 WHERE CHG_DTM > :since
                 GROUP BY LOG_KEY
                """)
                .setParameter("since", Timestamp.valueOf(since))
                .getResultList();
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] r : rows) {
            out.put(toStr(r[0]), ((Number) r[1]).longValue());
        }
        return out;
    }

    /**
     * 최근 30분간 분단위 발생량(최신이 끝). 데이터가 없는 분은 0으로 채워 30개 원소를 반환한다.
     */
    public List<Long> perMinuteSince(LocalDateTime since30MinAgo, LocalDateTime serverTime) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT TRUNC(CHG_DTM, 'MI') AS BUCKET, COUNT(*)
                  FROM V_ITPAPP_LOG_FEED
                 WHERE CHG_DTM > :since
                 GROUP BY TRUNC(CHG_DTM, 'MI')
                """)
                .setParameter("since", Timestamp.valueOf(since30MinAgo))
                .getResultList();

        Map<LocalDateTime, Long> byBucket = new HashMap<>();
        for (Object[] r : rows) {
            byBucket.put(toLdt(r[0]), ((Number) r[1]).longValue());
        }

        List<Long> out = new ArrayList<>(30);
        LocalDateTime start = serverTime.withSecond(0).withNano(0).minusMinutes(29);
        for (int i = 0; i < 30; i++) {
            out.add(byBucket.getOrDefault(start.plusMinutes(i), 0L));
        }
        return out;
    }
}
