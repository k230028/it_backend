package com.kdb.it.domain.budget.status.repository;

import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Oracle 집계 쿼리에서 제외해야 하는 정보화사업 CLOB 본문을 별도로 조회합니다. */
@Repository
@RequiredArgsConstructor
class ProjectDescriptionQuery {

    private static final int QUERY_BATCH_SIZE = 200;

    private final JPAQueryFactory queryFactory;

    /** 집계 쿼리가 읽은 프로젝트 개정본의 CLOB 사업내용을 복합키로 반환합니다. */
    Map<ProjectRevisionKey, String> findByAggregateRows(List<Tuple> aggregateRows) {
        QBprojm p = QBprojm.bprojm;
        Set<ProjectRevisionKey> revisionKeys = new HashSet<>();
        for (Tuple row : aggregateRows) {
            revisionKeys.add(new ProjectRevisionKey(row.get(p.abusMngNo), row.get(p.sno)));
        }
        return findByRevisionKeys(revisionKeys);
    }

    /** 지정된 프로젝트 개정본만 조회하며 최신본 여부를 다시 평가하지 않습니다. */
    Map<ProjectRevisionKey, String> findByRevisionKeys(Set<ProjectRevisionKey> revisionKeys) {
        if (revisionKeys.isEmpty()) {
            return Map.of();
        }

        List<ProjectRevisionKey> keys = List.copyOf(revisionKeys);
        Map<ProjectRevisionKey, String> descriptions = new HashMap<>();
        for (int from = 0; from < keys.size(); from += QUERY_BATCH_SIZE) {
            int to = Math.min(from + QUERY_BATCH_SIZE, keys.size());
            loadBatch(Set.copyOf(keys.subList(from, to)), descriptions);
        }
        return descriptions;
    }

    private void loadBatch(
            Set<ProjectRevisionKey> revisionKeys, Map<ProjectRevisionKey, String> descriptions) {
        QBprojm p = QBprojm.bprojm;
        BooleanBuilder exactRevisionCondition = new BooleanBuilder();
        for (ProjectRevisionKey key : revisionKeys) {
            exactRevisionCondition.or(p.abusMngNo.eq(key.abusMngNo()).and(p.sno.eq(key.sno())));
        }

        List<Tuple> rows =
                queryFactory
                        .select(p.abusMngNo, p.sno, p.abusPulConeInf)
                        .from(p)
                        .where(exactRevisionCondition)
                        .fetch();

        for (Tuple row : rows) {
            ProjectRevisionKey key = new ProjectRevisionKey(row.get(p.abusMngNo), row.get(p.sno));
            if (revisionKeys.contains(key)) {
                descriptions.put(key, row.get(p.abusPulConeInf));
            }
        }
    }

    /** 정보화사업 관리번호와 재상신 순번으로 CLOB 본문을 정확한 개정본에 연결합니다. */
    record ProjectRevisionKey(String abusMngNo, Integer sno) {}
}
