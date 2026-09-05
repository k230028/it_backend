package com.kdb.it.common.approval.itbudget.service;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.entity.BaseEntity;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 삭제 상태를 포함해 정확한 원장 버전과 자식을 일괄 적재한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItBudgetSourceLoader {
    private final ProjectRepository projects;
    private final ProjectItemRepository items;
    private final CostRepository costs;
    private final BtermmRepository terminals;

    public record SourceKey(SourceKind kind, String id, int revision) {}

    public record Audit(String modifierUserId, LocalDateTime modifiedAt) {}

    public record SourceAggregate(
            SourceRef ref, BaseEntity parent, List<BaseEntity> children, Audit latestAudit) {
        public SourceAggregate {
            children = List.copyOf(children);
        }

        public SourceKey key() {
            return new SourceKey(ref.kind(), ref.id(), ref.revision());
        }
    }

    /** 1~500개 참조의 정확한 버전을 읽는다. 중복·잘못된 입력은 400, 버전 누락은 SOURCE_NOT_FOUND 404다. 삭제행은 보존한다. */
    public List<SourceAggregate> load(List<SourceRef> refs) {
        return read(refs, false);
    }

    /** 상신 트랜잭션 안에서 부모를 kind→ID→개정 순서로 잠근 뒤 자식을 읽는다. 잠금 실패는 호출 경계에서 변환한다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<SourceAggregate> loadForUpdate(List<SourceRef> refs) {
        return read(refs, true);
    }

    /** 상신 비교를 위해 없는 부모는 결과에서 누락한다. 호출자가 서명된 참조와 대조해 삭제 충돌로 응답한다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<SourceAggregate> loadForSubmission(List<SourceRef> refs) {
        return read(refs, true, true);
    }

    private List<SourceAggregate> read(List<SourceRef> refs, boolean lock) {
        return read(refs, lock, false);
    }

    private List<SourceAggregate> read(List<SourceRef> refs, boolean lock, boolean allowMissing) {
        validateRefs(refs);
        Map<SourceKey, SourceRef> requested = new HashMap<>();
        refs.forEach(r -> requested.put(new SourceKey(r.kind(), r.id(), r.revision()), r));
        Map<SourceKey, BaseEntity> parents = new HashMap<>();
        Map<SourceKey, List<BaseEntity>> children = new HashMap<>();
        // 두 종류의 부모를 모두 잠근 후 자식을 읽는다. IN 원소 수는 요청 상한 500 이하다.
        for (SourceKind kind : SourceKind.values()) {
            var selected = refs.stream().filter(r -> r.kind() == kind).toList();
            if (selected.isEmpty()) continue;
            var ids = selected.stream().map(SourceRef::id).distinct().sorted().toList();
            var revisions = selected.stream().map(SourceRef::revision).distinct().sorted().toList();
            List<? extends BaseEntity> rows =
                    kind == SourceKind.PROJECT
                            ? (lock
                                    ? projects.findVersionsForUpdate(ids, revisions)
                                    : projects.findVersions(ids, revisions))
                            : (lock
                                    ? costs.findVersionsForUpdate(ids, revisions)
                                    : costs.findVersions(ids, revisions));
            for (var row : rows) {
                SourceKey key = parentKey(row);
                if (requested.containsKey(key) && parents.putIfAbsent(key, row) != null)
                    throw invalid("원장 버전이 중복되었습니다.");
            }
        }
        if (!allowMissing && parents.size() != refs.size()) throw notFound();
        for (SourceKind kind : SourceKind.values()) {
            var selected = refs.stream().filter(r -> r.kind() == kind).toList();
            if (selected.isEmpty()) continue;
            var ids = selected.stream().map(SourceRef::id).distinct().sorted().toList();
            var revisions = selected.stream().map(SourceRef::revision).distinct().sorted().toList();
            List<? extends BaseEntity> rows =
                    kind == SourceKind.PROJECT
                            ? items.findSourceVersions(ids, revisions)
                            : terminals.findSourceVersions(ids, revisions);
            for (var row : rows) {
                SourceKey key = childParentKey(row);
                if (requested.containsKey(key))
                    children.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        }
        return refs.stream()
                .filter(r -> parents.containsKey(new SourceKey(r.kind(), r.id(), r.revision())))
                .sorted(
                        Comparator.comparing(SourceRef::order)
                                .thenComparing(r -> r.kind().name())
                                .thenComparing(SourceRef::id)
                                .thenComparing(SourceRef::revision))
                .map(
                        r -> {
                            var key = new SourceKey(r.kind(), r.id(), r.revision());
                            return aggregate(
                                    r, parents.get(key), children.getOrDefault(key, List.of()));
                        })
                .toList();
    }

    static void validateRefs(List<SourceRef> refs) {
        if (refs == null || refs.isEmpty() || refs.size() > 500)
            throw invalid("신청 대상은 1~500개여야 합니다.");
        Set<SourceKey> keys = new HashSet<>();
        for (var r : refs) {
            if (r == null
                    || r.kind() == null
                    || r.id() == null
                    || r.id().isBlank()
                    || r.id().length() > 30
                    || r.revision() == null
                    || r.revision() < 1
                    || r.order() == null
                    || r.order() < 1) throw invalid("신청 대상 참조가 올바르지 않습니다.");
            if (!keys.add(new SourceKey(r.kind(), r.id(), r.revision())))
                throw invalid("신청 대상이 중복되었습니다.");
        }
    }

    static SourceKey parentKey(BaseEntity row) {
        if (row instanceof Bprojm p)
            return new SourceKey(SourceKind.PROJECT, p.getAbusMngNo(), p.getSno());
        var c = (Bcostm) row;
        return new SourceKey(SourceKind.COST, c.getCostBgNo(), c.getBgSno());
    }

    static SourceKey childParentKey(BaseEntity row) {
        if (row instanceof Bitemm i)
            return new SourceKey(SourceKind.PROJECT, i.getAbusMngNo(), i.getFntTbCrySno());
        var t = (Btermm) row;
        return new SourceKey(SourceKind.COST, t.getTermBgNo(), t.getTermBgSno());
    }

    static String childId(BaseEntity row) {
        return row instanceof Bitemm i ? i.getGclMngNo() : ((Btermm) row).getTmnMngNo();
    }

    static int childSequence(BaseEntity row) {
        return row instanceof Bitemm i ? i.getSno() : ((Btermm) row).getSno();
    }

    static SourceAggregate aggregate(
            SourceRef ref, BaseEntity parent, List<? extends BaseEntity> children) {
        List<BaseEntity> ordered = new ArrayList<>(children);
        ordered.sort(
                Comparator.comparing(ItBudgetSourceLoader::childId)
                        .thenComparingInt(ItBudgetSourceLoader::childSequence));
        BaseEntity latest = parent;
        for (var child :
                ordered.stream()
                        .sorted(
                                Comparator.comparingInt(ItBudgetSourceLoader::childSequence)
                                        .thenComparing(ItBudgetSourceLoader::childId))
                        .toList()) {
            if (child.getLstChgDtm() != null
                    && (latest.getLstChgDtm() == null
                            || !child.getLstChgDtm().isBefore(latest.getLstChgDtm())))
                latest = child;
        }
        return new SourceAggregate(
                ref, parent, ordered, new Audit(latest.getLstChgUsid(), latest.getLstChgDtm()));
    }

    static ItBudgetApprovalException invalid(String message) {
        return new ItBudgetApprovalException(
                HttpStatus.BAD_REQUEST, "IT_BUDGET_PREVIEW_INVALID", message, List.of());
    }

    static ItBudgetApprovalException notFound() {
        return new ItBudgetApprovalException(
                HttpStatus.NOT_FOUND,
                "IT_BUDGET_SOURCE_NOT_FOUND",
                "신청 대상의 정확한 원장 개정본을 찾을 수 없습니다.",
                List.of());
    }
}
