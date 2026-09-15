package com.kdb.it.common.approval.itbudget.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    public record Aggregate(
            String kind,
            String id,
            int revision,
            Row parent,
            List<Row> children,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Row> attachments) {

        public Aggregate {
            children = List.copyOf(children);
            attachments = attachments == null ? null : List.copyOf(attachments);
        }

        /** 첨부 필드 도입 전 생성된 v3 스냅샷은 빈 첨부 목록으로 해석한다. */
        public List<Row> attachmentRows() {
            return attachments == null ? List.of() : attachments;
        }

        public Aggregate(String kind, String id, int revision, Row parent, List<Row> children) {
            this(kind, id, revision, parent, children, null);
        }
    }

    public record Row(String table, Map<String, Object> columns) {

        public Row {
            columns = Collections.unmodifiableMap(new TreeMap<>(columns));
        }
    }
}
