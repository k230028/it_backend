package com.kdb.it.common.approval.itbudget.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 전산예산 신청서에 보존하는 원장 전체 컬럼 스냅샷이다. */
public record ItBudgetLedgerSnapshot(String format, List<Aggregate> aggregates) {

    public ItBudgetLedgerSnapshot {
        if (!"IT_BUDGET_LEDGER_V1".equals(format)) {
            throw new IllegalArgumentException("ledger 형식이 올바르지 않습니다.");
        }
        aggregates = List.copyOf(aggregates);
    }

    public record Aggregate(String kind, String id, int revision, Row parent, List<Row> children) {

        public Aggregate {
            children = List.copyOf(children);
        }
    }

    public record Row(String table, Map<String, Object> columns) {

        public Row {
            columns = Collections.unmodifiableMap(new TreeMap<>(columns));
        }
    }
}
