package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.invalid;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.DocumentRequest;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceRef;
import com.kdb.it.common.approval.itbudget.model.ItBudgetLedgerSnapshot;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshotV3.*;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.SourceAggregate;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.SourceKey;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.IoeCategories;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.service.ProjectAmountCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 원장 집계를 서버 전용 불변 문서와 다이제스트로 변환한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItBudgetSnapshotBuilder {
    private final ItBudgetCanonicalJson canonical;
    private final ItBudgetLedgerCapture ledgerCapture;
    private final ProjectAmountCalculator amountCalculator;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final CodeRepository codes;

    public record BuiltDocument(
            String clientDocumentKey, Payload payload, String payloadDigest, List<Source> sources) {
        public BuiltDocument {
            sources = List.copyOf(sources);
        }
    }

    /** 삭제 원장도 변경 검출할 수 있도록 표시정보 없이 업무 다이제스트를 계산한다. 정밀도 오류는 PREVIEW_INVALID다. */
    public String sourceDigest(SourceAggregate aggregate) {
        try {
            var sourceData = new ItBudgetSourceData(canonical);
            return canonical.digest(
                    new SourceDigestData(
                            sourceData.capture(aggregate.parent()),
                            aggregate.children().stream().map(sourceData::capture).toList()));
        } catch (IllegalArgumentException ex) {
            throw invalid("원장 수치가 스냅샷 정밀도를 충족하지 않습니다.");
        }
    }

    /** 문서 전체의 사용자·코드를 일괄 해석한다. 중복 참조, 삭제 대상, 필수 사용자 누락, 잘못된 수치는 PREVIEW_INVALID다. */
    public List<BuiltDocument> buildDocuments(
            List<DocumentRequest> documents, List<SourceAggregate> aggregates) {
        validateDocuments(documents);
        Map<SourceKey, SourceAggregate> byKey = new HashMap<>();
        for (var a : aggregates) {
            if (byKey.putIfAbsent(a.key(), a) != null
                    || !a.key().equals(ItBudgetSourceLoader.parentKey(a.parent())))
                throw invalid("원장 집계의 식별자가 올바르지 않습니다.");
        }
        var refs = documents.stream().flatMap(d -> d.sourceRefs().stream()).toList();
        if (byKey.size() != refs.size() || refs.stream().anyMatch(r -> !byKey.containsKey(key(r))))
            throw invalid("요청과 원장 집계가 일치하지 않습니다.");
        try {
            Map<SourceKey, String> digests = new HashMap<>();
            for (var a : aggregates) {
                if (!"N".equals(a.parent().getDelYn()))
                    throw invalid("삭제되었거나 비활성인 원장은 미리볼 수 없습니다.");
                digests.put(a.key(), sourceDigest(a));
            }
            DisplayData display = loadDisplay(aggregates);
            List<BuiltDocument> result = new ArrayList<>();
            for (var d : documents) {
                List<Project> projectRows = new ArrayList<>();
                List<Cost> costRows = new ArrayList<>();
                List<Source> sources = new ArrayList<>();
                List<SourceAggregate> documentAggregates = new ArrayList<>();
                for (var r :
                        d.sourceRefs().stream()
                                .sorted(Comparator.comparing(SourceRef::order))
                                .toList()) {
                    var a = byKey.get(key(r));
                    documentAggregates.add(a);
                    if (r.kind() == SourceKind.PROJECT) projectRows.add(project(a, display));
                    else costRows.add(cost(a, display));
                    sources.add(
                            new Source(
                                    r.kind().name(),
                                    r.id(),
                                    r.revision(),
                                    r.order(),
                                    digests.get(a.key())));
                }
                BigDecimal asset = BigDecimal.ZERO;
                BigDecimal expense = BigDecimal.ZERO;
                BigDecimal currentRequests = BigDecimal.ZERO;
                for (var p : projectRows) {
                    currentRequests = currentRequests.add(p.currentRequestAmount());
                    asset = asset.add(p.assetBudget());
                    expense = expense.add(p.costBudget());
                }
                for (var c : costRows) {
                    currentRequests = currentRequests.add(orZero(c.totalAmount()));
                    asset = asset.add(c.assetBudget());
                    expense = expense.add(c.costBudget());
                }
                var payload =
                        new Payload(
                                projectRows,
                                costRows,
                                new Summary(
                                        canonical.money(currentRequests),
                                        canonical.money(asset),
                                        canonical.money(expense)),
                                ledgerCapture.capture(documentAggregates));
                validateLedger(payload);
                result.add(
                        new BuiltDocument(
                                d.clientDocumentKey(),
                                payload,
                                canonical.digest(payload),
                                sources));
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException ex) {
            throw invalid("원장 데이터가 스냅샷 정밀도 또는 값 계약을 충족하지 않습니다: " + ex.getMessage());
        }
    }

    private static SourceKey key(SourceRef ref) {
        return new SourceKey(ref.kind(), ref.id(), ref.revision());
    }

    private void validateDocuments(List<DocumentRequest> documents) {
        if (documents == null || documents.isEmpty() || documents.size() > 100)
            throw invalid("문서는 1~100개여야 합니다.");
        Set<String> keys = new HashSet<>();
        List<SourceRef> refs = new ArrayList<>();
        for (var d : documents) {
            if (d == null
                    || d.clientDocumentKey() == null
                    || d.clientDocumentKey().isBlank()
                    || d.clientDocumentKey().length() > 64
                    || !keys.add(d.clientDocumentKey())) throw invalid("문서 식별자가 올바르지 않습니다.");
            ItBudgetSourceLoader.validateRefs(d.sourceRefs());
            Set<Integer> orders = new HashSet<>();
            for (var r : d.sourceRefs())
                if (!orders.add(r.order())) throw invalid("문서 안의 대상 순서가 중복되었습니다.");
            refs.addAll(d.sourceRefs());
        }
        ItBudgetSourceLoader.validateRefs(refs);
    }

    private DisplayData loadDisplay(List<SourceAggregate> aggregates) {
        Set<String> enos = new TreeSet<>();
        Set<String> orgCodes = new TreeSet<>();
        for (var a : aggregates) {
            if (a.parent() instanceof Bprojm p) {
                add(enos, p.getUsid(), p.getTlrUsid(), p.getDvmUsid(), p.getDvmTlrUsid());
                add(orgCodes, p.getSvnDpmC(), p.getDvmDpmC());
            } else {
                var c = (Bcostm) a.parent();
                add(enos, c.getCgprId());
                add(orgCodes, c.getCostSvnDpmC());
            }
        }
        Map<String, CuserI> people = new HashMap<>();
        for (var batch : batches(enos))
            for (var u : users.findByEnoIn(batch))
                if ("N".equals(u.getDelYn())) people.put(u.getEno(), u);
        Map<String, String> orgNames = new HashMap<>();
        for (var batch : batches(orgCodes))
            for (var o : organizations.findNameViewsByPrlmOgzCConeIn(batch))
                orgNames.put(o.getPrlmOgzCCone(), o.getBbrNm());
        Set<String> groups =
                Set.of(
                        CommonCodeGroups.IOE,
                        CommonCodeGroups.EDRT,
                        CommonCodeGroups.REPORT_STS,
                        CommonCodeGroups.EXE_POSSIBLE,
                        CommonCodeGroups.ABUS,
                        CommonCodeGroups.DFR_CLE,
                        CommonCodeGroups.TERM_SERVICE,
                        CommonCodeGroups.TERM_KIND);
        Map<CodeKey, Ccodem> catalog = new HashMap<>();
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (var c : codes.findAllByCIdIn(groups)) {
            if (!"N".equals(c.getDelYn())
                    || c.getSttDt() != null && c.getSttDt().compareTo(today) > 0
                    || c.getEndDt() != null && c.getEndDt().compareTo(today) < 0) continue;
            if (catalog.putIfAbsent(new CodeKey(c.getCId(), c.getCdva()), c) != null)
                throw invalid("유효한 공통코드 기간이 중복되었습니다.");
        }
        return new DisplayData(people, orgNames, catalog);
    }

    private Project project(SourceAggregate aggregate, DisplayData display) {
        var p = (Bprojm) aggregate.parent();
        var active =
                aggregate.children().stream()
                        .filter(c -> "N".equals(c.getDelYn()))
                        .map(Bitemm.class::cast)
                        .toList();
        // 저장 수치의 scale 검증은 sourceData에서 먼저 끝낸다. 기존 계산기는 환산·범위 SoT다.
        var calculated = amountCalculator.calculate(active, p.getDfrAmt());
        BigDecimal total =
                canonical.money(
                        p.getTotRqmAmt() != null
                                ? p.getTotRqmAmt()
                                : calculated.totalRequiredAmt());
        BigDecimal currentRequestAmount =
                canonical.money(
                        p.getTotRqmAmt() == null
                                ? calculated.currentRequestAmt()
                                : amountCalculator.restoreCurrentRequestAmount(
                                        p.getTotRqmAmt(), p.getMplAmt(), p.getDfrAmt()));
        BigDecimal asset = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (var i : active) {
            var code = display.catalog().get(new CodeKey(CommonCodeGroups.IOE, i.getIoeC()));
            if (code != null && IoeCategories.isCapitalCTp(code.getCTp()))
                asset = asset.add(orZero(i.getAmt()));
            else if (code != null && code.getCTp() != null && COST_TYPES.contains(code.getCTp()))
                expense = expense.add(orZero(i.getAmt()));
        }
        var itemRows =
                active.stream()
                        .map(
                                i ->
                                        new ProjectItem(
                                                i.getGclMngNo(),
                                                i.getFntTbCrySno(),
                                                i.getSno(),
                                                label(display, CommonCodeGroups.IOE, i.getIoeC()),
                                                i.getGclNm(),
                                                canonical.quantity(i.getQty()),
                                                i.getCurC(),
                                                canonical.money(i.getAmt()),
                                                canonical.money(i.getFcAmt()),
                                                i.getCncdFdtnCone()))
                        .toList();
        return new Project(
                p.getAbusMngNo(),
                p.getSno(),
                p.getOdnYn(),
                p.getAbusNm(),
                p.getBseYy(),
                total,
                currentRequestAmount,
                label(display, CommonCodeGroups.EDRT, p.getEdrtTc()),
                label(display, CommonCodeGroups.REPORT_STS, p.getRprStsTc()),
                p.getSttDtm(),
                p.getEndDtm(),
                date(p.getFlfFsgDt()),
                p.getAbusPulConeInf(),
                p.getAbusPulDrcnInf(),
                p.getCpnSafCone(),
                p.getPlmDes(),
                p.getAbusPulNcsInf(),
                p.getAbusXptEffInf(),
                p.getMnPrgCone(),
                p.getHrfPlnCone(),
                new Organization(null, p.getPrlmHrkOgzCCone()),
                organization(display, p.getSvnDpmC()),
                person(display, p.getUsid(), true),
                person(display, p.getTlrUsid(), false),
                organization(display, p.getDvmDpmC()),
                person(display, p.getDvmUsid(), false),
                person(display, p.getDvmTlrUsid(), false),
                storedLabel(p.getBzTpC()),
                storedLabel(p.getBzDttNm()),
                storedLabel(p.getCstTpTc()),
                storedLabel(p.getSklTpTc()),
                label(display, CommonCodeGroups.EXE_POSSIBLE, p.getExePttYn()),
                p.getDplYn(),
                canonical.money(asset),
                canonical.money(expense),
                itemRows);
    }

    private Cost cost(SourceAggregate aggregate, DisplayData display) {
        var c = (Bcostm) aggregate.parent();
        BigDecimal total = canonical.money(c.getCostTotXpAmt());
        var code = display.catalog().get(new CodeKey(CommonCodeGroups.IOE, c.getIoeC()));
        boolean capital = code != null && IoeCategories.isCapitalCTp(code.getCTp());
        BigDecimal normalizedTotal = orZero(total);
        BigDecimal asset = canonical.money(capital ? normalizedTotal : BigDecimal.ZERO);
        BigDecimal expense = canonical.money(capital ? BigDecimal.ZERO : normalizedTotal);
        var terminalRows =
                aggregate.children().stream()
                        .filter(t -> "N".equals(t.getDelYn()))
                        .map(Btermm.class::cast)
                        .map(
                                t ->
                                        new Terminal(
                                                t.getTmnMngNo(),
                                                t.getTermBgSno(),
                                                t.getSno(),
                                                label(
                                                        display,
                                                        CommonCodeGroups.TERM_SERVICE,
                                                        t.getTmnClsfC()),
                                                label(
                                                        display,
                                                        CommonCodeGroups.TERM_KIND,
                                                        t.getTmnKdTc()),
                                                t.getNsfUsgCone(),
                                                t.getSpfTmnNm(),
                                                t.getCurC(),
                                                canonical.exchangeRate(t.getXcr()),
                                                canonical.money(t.getFcAmt()),
                                                canonical.money(t.getTermRqmBgAmt())))
                        .toList();
        return new Cost(
                c.getCostBgNo(),
                c.getBgSno(),
                c.getBseYy(),
                c.getCttNm(),
                c.getCttOppNm(),
                label(display, CommonCodeGroups.ABUS, c.getAbusTc()),
                label(display, CommonCodeGroups.IOE, c.getIoeC()),
                total,
                c.getCurC(),
                canonical.exchangeRate(c.getXcr()),
                date(c.getXcrBseDt()),
                label(display, CommonCodeGroups.DFR_CLE, c.getDfrCleC()),
                date(c.getFstDfrDt()),
                c.getIndRsn(),
                organization(display, c.getCostSvnDpmC()),
                person(display, c.getCgprId(), true),
                c.getSectSysUtzYn(),
                asset,
                expense,
                terminalRows);
    }

    private static void validateLedger(Payload payload) {
        var ledger = payload.ledger();
        Set<AggregateIdentity> expected = new HashSet<>();
        payload.projects().stream()
                .map(project -> new AggregateIdentity("PROJECT", project.id(), project.revision()))
                .forEach(expected::add);
        payload.costs().stream()
                .map(cost -> new AggregateIdentity("COST", cost.id(), cost.revision()))
                .forEach(expected::add);
        var actual =
                ledger.aggregates().stream()
                        .map(
                                aggregate ->
                                        new AggregateIdentity(
                                                aggregate.kind(),
                                                aggregate.id(),
                                                aggregate.revision()))
                        .toList();
        if (actual.size() != expected.size() || !new HashSet<>(actual).equals(expected))
            throw invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다.");
        for (var aggregate : ledger.aggregates()) {
            if ("PROJECT".equals(aggregate.kind())) validateProjectLedger(payload, aggregate);
            else if ("COST".equals(aggregate.kind())) validateCostLedger(payload, aggregate);
            else throw invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다.");
        }
    }

    private static void validateProjectLedger(
            Payload payload, ItBudgetLedgerSnapshot.Aggregate aggregate) {
        var project =
                payload.projects().stream()
                        .filter(
                                candidate ->
                                        candidate.id().equals(aggregate.id())
                                                && candidate.revision() == aggregate.revision())
                        .findFirst()
                        .orElseThrow(() -> invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다."));
        var expectedParent = new RowIdentity("BPROJM", project.id(), project.revision());
        var actualParent = rowIdentity(aggregate.parent(), "ABUS_MNG_NO", "SNO");
        var expectedChildren =
                project.items().stream()
                        .map(
                                item ->
                                        new ChildIdentity(
                                                "BITEMM",
                                                item.id(),
                                                item.sequence(),
                                                project.id(),
                                                item.revision(),
                                                decimal(item.amount()),
                                                decimal(item.foreignAmount())))
                        .toList();
        var actualChildren =
                aggregate.children().stream()
                        .filter(row -> "N".equals(row.columns().get("DEL_YN")))
                        .map(
                                row ->
                                        childIdentity(
                                                row,
                                                "GCL_MNG_NO",
                                                "SNO",
                                                "ABUS_MNG_NO",
                                                "FNT_TB_CRY_SNO"))
                        .toList();
        if (!actualParent.equals(expectedParent) || !actualChildren.equals(expectedChildren))
            throw invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다.");
    }

    private static void validateCostLedger(
            Payload payload, ItBudgetLedgerSnapshot.Aggregate aggregate) {
        var cost =
                payload.costs().stream()
                        .filter(
                                candidate ->
                                        candidate.id().equals(aggregate.id())
                                                && candidate.revision() == aggregate.revision())
                        .findFirst()
                        .orElseThrow(() -> invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다."));
        var expectedParent = new RowIdentity("BCOSTM", cost.id(), cost.revision());
        var actualParent = rowIdentity(aggregate.parent(), "BG_NO", "BG_SNO");
        var expectedChildren =
                cost.terminals().stream()
                        .map(
                                terminal ->
                                        new ChildIdentity(
                                                "BTERMM",
                                                terminal.id(),
                                                terminal.sequence(),
                                                cost.id(),
                                                terminal.revision(),
                                                decimal(terminal.budgetAmount()),
                                                decimal(terminal.foreignAmount())))
                        .toList();
        var actualChildren =
                aggregate.children().stream()
                        .filter(row -> "N".equals(row.columns().get("DEL_YN")))
                        .map(row -> childIdentity(row, "TMN_MNG_NO", "SNO", "BG_NO", "BG_SNO"))
                        .toList();
        if (!actualParent.equals(expectedParent) || !actualChildren.equals(expectedChildren))
            throw invalid("표시 데이터와 원장 스냅샷이 일치하지 않습니다.");
    }

    private static RowIdentity rowIdentity(
            ItBudgetLedgerSnapshot.Row row, String idColumn, String revisionColumn) {
        return new RowIdentity(
                row.table(), row.columns().get(idColumn), row.columns().get(revisionColumn));
    }

    private static ChildIdentity childIdentity(
            ItBudgetLedgerSnapshot.Row row,
            String idColumn,
            String sequenceColumn,
            String parentIdColumn,
            String parentRevisionColumn) {
        return new ChildIdentity(
                row.table(),
                row.columns().get(idColumn),
                row.columns().get(sequenceColumn),
                row.columns().get(parentIdColumn),
                row.columns().get(parentRevisionColumn),
                row.columns().get("AMT"),
                row.columns().get("FC_AMT"));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private record AggregateIdentity(String kind, String id, int revision) {}

    private record RowIdentity(String table, Object id, Object revision) {}

    private record ChildIdentity(
            String table,
            Object id,
            Object sequence,
            Object parentId,
            Object parentRevision,
            Object amount,
            Object foreignAmount) {}

    private static final Set<String> COST_TYPES =
            Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw invalid("원장의 날짜 형식이 올바르지 않습니다.");
        }
    }

    private static void add(Set<String> target, String... values) {
        for (var value : values) if (value != null && !value.isBlank()) target.add(value);
    }

    private static List<List<String>> batches(Set<String> values) {
        var list = new ArrayList<>(values);
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 500)
            batches.add(list.subList(i, Math.min(i + 500, list.size())));
        return batches;
    }

    private static CodeLabel label(DisplayData display, String group, String value) {
        var code = display.catalog().get(new CodeKey(group, value));
        return new CodeLabel(
                value, code == null || code.getCdvaNm() == null ? "" : code.getCdvaNm());
    }

    private static CodeLabel storedLabel(String name) {
        return new CodeLabel(null, name);
    }

    private static Organization organization(DisplayData display, String code) {
        return new Organization(code, display.orgNames().getOrDefault(code, ""));
    }

    private static Person person(DisplayData display, String eno, boolean required) {
        if (eno == null && !required) return new Person(null, null, null);
        var user = display.people().get(eno);
        if (user == null) throw invalid("필수 담당자 정보를 찾을 수 없습니다.");
        return new Person(eno, user.getUsrNm(), user.getPtCNm());
    }

    private record SourceDigestData(
            Map<String, Object> parent, List<Map<String, Object>> children) {}

    private record CodeKey(String group, String code) {}

    private record DisplayData(
            Map<String, CuserI> people,
            Map<String, String> orgNames,
            Map<CodeKey, Ccodem> catalog) {}
}
