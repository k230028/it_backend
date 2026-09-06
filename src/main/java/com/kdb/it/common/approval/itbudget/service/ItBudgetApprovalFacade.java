package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.PreviewBinding;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotBuilder.BuiltDocument;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.SourceAggregate;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.SourceKey;
import com.kdb.it.common.approval.service.ApplicationPersistenceService.ApplicationDraft;
import com.kdb.it.common.approval.service.ApplicationPersistenceService.SourceLink;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 인증 주체와 원장을 검증해 전산예산 v2 미리보기를 발급하고 잠금 아래 원자적으로 상신한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItBudgetApprovalFacade {
    private static final Logger log = LoggerFactory.getLogger(ItBudgetApprovalFacade.class);

    private final ItBudgetSourceLoader loader;
    private final ItBudgetSnapshotBuilder builder;
    private final ItBudgetCanonicalJson canonical;
    private final ItBudgetPreviewTokenService tokens;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final com.kdb.it.common.approval.service.ApplicationPersistenceService persistence;
    private final com.kdb.it.domain.budget.common.security.ApprovalWriteGuard approvalGuard;
    private final MeterRegistry meterRegistry;

    /**
     * 서명된 미리보기와 잠긴 현재 원장을 비교하고 모든 문서를 하나의 트랜잭션으로 저장한다.
     *
     * @param actor 서버 인증 주체
     * @param request 미리보기 토큰·문서별 다이제스트·역할별 결재자 입력
     * @return 입력 문서 순서의 신청관리번호
     * @throws ItBudgetApprovalException 변조(400), 변경·만료·오래된 미리보기·잠금 경합(409)
     * @throws AccessDeniedException 인증 또는 현재 원장의 상신 권한이 없는 경우
     */
    @Transactional
    public SubmissionResponse submit(CustomUserDetails actor, SubmissionRequest request) {
        Timer.Sample sample = startTimer("submission");
        try {
            var response = doSubmit(actor, request);
            completeSubmission(
                    sample,
                    "success",
                    true,
                    response.applicationNumbers().size(),
                    request.documents().stream().mapToInt(d -> d.sources().size()).sum());
            return response;
        } catch (ItBudgetApprovalException exception) {
            recordFailureDetails(exception);
            completeSubmission(sample, outcome(exception.code()), false, 0, 0);
            throw exception;
        } catch (RuntimeException exception) {
            completeSubmission(sample, "error", false, 0, 0);
            throw exception;
        }
    }

    private SubmissionResponse doSubmit(CustomUserDetails actor, SubmissionRequest request) {
        requireActor(actor);
        var claims = tokens.verify(request == null ? null : request.previewToken(), actor.getEno());
        var normalized = boundRequest(request, claims);
        var refs = normalized.documents().stream().flatMap(d -> d.sourceRefs().stream()).toList();
        List<SourceAggregate> aggregates;
        try {
            aggregates = loader.loadForSubmission(refs);
        } catch (RuntimeException exception) {
            if (isLockTimeout(exception))
                throw conflict("IT_BUDGET_CONCURRENT_UPDATE", "다른 작업이 신청 대상을 변경 중입니다.");
            throw exception;
        }
        boolean approvalBlocked = false;
        for (var aggregate : aggregates) {
            authorize(actor, aggregate, false);
            try {
                approvalGuard.verifyWritable(
                        table(aggregate.ref().kind()),
                        aggregate.ref().id(),
                        aggregate.ref().revision(),
                        "상신");
            } catch (IllegalStateException exception) {
                approvalBlocked = true;
            }
        }
        // 삭제·누락은 payload 생성 이전에 비교하며 원장 변경을 표시 정보 stale보다 먼저 보고한다.
        var changed = changedSources(request, aggregates);
        if (!changed.isEmpty())
            throw new ItBudgetApprovalException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "IT_BUDGET_SOURCE_CHANGED",
                    "신청 대상이 중간에 변경되었습니다.",
                    changed);
        if (approvalBlocked) throw stale();

        ItBudgetSnapshot.ApprovalLine line;
        List<BuiltDocument> built;
        try {
            line = approvalLine(actor.getEno(), normalized.approvers());
            built = builder.buildDocuments(normalized.documents(), aggregates);
        } catch (ItBudgetApprovalException exception) {
            // 업무 다이제스트가 같으므로 여기서 사라진 필수 표시 정보는 미리보기의 갱신 사유다.
            throw stale();
        }
        if (!claims.payloadSetDigest()
                        .equals(
                                canonical.digest(
                                        built.stream().map(BuiltDocument::payloadDigest).toList()))
                || !claims.sourceSetDigest()
                        .equals(canonical.digest(built.stream().map(this::publicSources).toList()))
                || !claims.previewDigest()
                        .equals(
                                canonical.digest(
                                        new PreviewView(
                                                actor.getEno(),
                                                normalized,
                                                line,
                                                built.stream()
                                                        .map(BuiltDocument::payload)
                                                        .toList())))
                || !built.stream()
                        .map(BuiltDocument::clientDocumentKey)
                        .toList()
                        .equals(
                                normalized.documents().stream()
                                        .map(DocumentRequest::clientDocumentKey)
                                        .toList())) throw stale();
        var numbers = new ArrayList<String>();
        for (var document : built) {
            String json;
            try {
                json =
                        mapper.writeValueAsString(
                                publicDocument(document, line, claims.issuedAt()).snapshot());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("신청서 스냅샷을 저장할 수 없습니다.", exception);
            }
            numbers.add(
                    persistence.persist(
                            ApplicationDraft.itBudgetV2(
                                    "전산예산 결재 신청",
                                    json,
                                    actor.getEno(),
                                    applicationSummary(publicSources(document)),
                                    "전산예산 결재를 요청합니다.",
                                    document.sources().stream()
                                            .map(
                                                    s ->
                                                            new SourceLink(
                                                                    table(
                                                                            SourceKind.valueOf(
                                                                                    s.kind())),
                                                                    s.id(),
                                                                    Integer.toString(s.revision())))
                                            .toList(),
                                    normalized.approvers().stream()
                                            .map(ApproverRef::eno)
                                            .toList())));
        }
        return new SubmissionResponse(List.copyOf(numbers));
    }

    /**
     * 신청 대상·결재선을 한 번 정규화하고 모든 대상의 조회·수정 범위를 검사한다.
     *
     * @param actor 서버 인증 주체
     * @param request 선택 문서·원장·결재자 입력
     * @return 고정 문자열 수치의 v2 문서와 서버 발급 시각에 결속된 토큰
     * @throws AccessDeniedException 인증 주체 또는 원장 권한이 없는 경우
     * @throws ItBudgetApprovalException 입력·원장 값 오류(400), 원장 미존재·삭제(404)
     */
    public PreviewResponse preview(CustomUserDetails actor, PreviewRequest request) {
        Timer.Sample sample = startTimer("preview");
        try {
            var response = doPreview(actor, request);
            recordPreview("success");
            return response;
        } catch (ItBudgetApprovalException exception) {
            recordPreview(outcome(exception.code()));
            throw exception;
        } catch (RuntimeException exception) {
            recordPreview("error");
            throw exception;
        } finally {
            stopTimer(sample, "approval.it_budget.preview", "preview");
        }
    }

    private PreviewResponse doPreview(CustomUserDetails actor, PreviewRequest request) {
        requireActor(actor);
        var normalized = normalize(request, false);
        var refs = normalized.documents().stream().flatMap(d -> d.sourceRefs().stream()).toList();
        var aggregates = loader.load(refs);
        // 부모 데이터는 인가 판단에 필요하다. 모든 대상의 인가를 끝내기 전 표시정보·문서는 생성하지 않는다.
        for (var aggregate : aggregates) authorize(actor, aggregate);
        var line = approvalLine(actor.getEno(), normalized.approvers());
        var built = builder.buildDocuments(normalized.documents(), aggregates);
        String requestDigest = canonical.digest(normalized);
        String sourceSetDigest = canonical.digest(built.stream().map(this::publicSources).toList());
        String payloadSetDigest =
                canonical.digest(built.stream().map(BuiltDocument::payloadDigest).toList());
        String previewDigest =
                canonical.digest(
                        new PreviewView(
                                actor.getEno(),
                                normalized,
                                line,
                                built.stream().map(BuiltDocument::payload).toList()));
        var issued =
                tokens.issue(
                        new PreviewBinding(
                                actor.getEno(),
                                requestDigest,
                                sourceSetDigest,
                                payloadSetDigest,
                                previewDigest));
        var documents =
                built.stream()
                        .map(d -> publicDocument(d, line, issued.claims().issuedAt()))
                        .toList();
        return new PreviewResponse(
                previewDigest, issued.token(), issued.claims().expiresAt(), documents);
    }

    /** 문서 배열·결재자 배열은 입력 순서, 원장은 명시된 order 순서다. 시각·integrity는 해시 뷰에 들어가지 않는다. */
    private record PreviewView(
            String requesterEno,
            PreviewRequest request,
            ItBudgetSnapshot.ApprovalLine approvalLine,
            List<ItBudgetSnapshot.Payload> payloads) {}

    private PreviewRequest normalize(PreviewRequest request, boolean requireApprovers) {
        if (request == null
                || request.documents() == null
                || request.documents().isEmpty()
                || request.documents().size() > 100) throw invalid("문서는 1~100개여야 합니다.");
        var approvers = request.approvers();
        if (approvers == null
                || (requireApprovers && approvers.size() < 2)
                || approvers.size() > 102) throw invalid("결재자는 2~102명이어야 합니다.");
        Set<ApproverRole> fixedRoles = new HashSet<>();
        int previousRole = -1;
        for (var a : approvers) {
            if (a == null
                    || a.role() == null
                    || a.eno() == null
                    || a.eno().isBlank()
                    || a.eno().length() > 14) throw invalid("결재자 입력이 올바르지 않습니다.");
            if (a.role().ordinal() < previousRole
                    || a.role() != ApproverRole.ADDITIONAL && !fixedRoles.add(a.role()))
                throw invalid("결재 역할 또는 순서가 올바르지 않습니다.");
            previousRole = a.role().ordinal();
        }
        Set<String> keys = new HashSet<>();
        List<SourceRef> allRefs = new ArrayList<>();
        List<DocumentRequest> documents = new ArrayList<>();
        for (var d : request.documents()) {
            if (d == null
                    || d.clientDocumentKey() == null
                    || d.clientDocumentKey().isBlank()
                    || d.clientDocumentKey().length() > 64
                    || !keys.add(d.clientDocumentKey())) throw invalid("문서 식별자가 올바르지 않습니다.");
            ItBudgetSourceLoader.validateRefs(d.sourceRefs());
            Set<Integer> orders = new HashSet<>();
            for (var r : d.sourceRefs())
                if (!orders.add(r.order())) throw invalid("문서 안의 대상 순서가 중복되었습니다.");
            allRefs.addAll(d.sourceRefs());
            documents.add(
                    new DocumentRequest(
                            d.clientDocumentKey(),
                            d.sourceRefs().stream()
                                    .sorted(Comparator.comparing(SourceRef::order))
                                    .toList()));
        }
        ItBudgetSourceLoader.validateRefs(allRefs);
        return new PreviewRequest(List.copyOf(approvers), List.copyOf(documents));
    }

    private void authorize(CustomUserDetails actor, SourceAggregate aggregate) {
        authorize(actor, aggregate, true);
    }

    private void authorize(
            CustomUserDetails actor, SourceAggregate aggregate, boolean requireActive) {
        var parent = aggregate.parent();
        String department =
                parent instanceof Bprojm p ? p.getSvnDpmC() : ((Bcostm) parent).getCostSvnDpmC();
        BudgetDetailAccessVerifier.verifyReadable(department, actor);
        if (!OwnershipVerifier.canModify(parent.getFstEnrUsid(), department, actor))
            throw new AccessDeniedException("신청 대상의 상신 권한이 없습니다.");
        if (requireActive && !"N".equals(parent.getDelYn())) throw ItBudgetSourceLoader.notFound();
    }

    private ItBudgetSnapshot.ApprovalLine approvalLine(
            String requesterEno, List<ApproverRef> approvers) {
        Set<String> enos = new LinkedHashSet<>();
        enos.add(requesterEno);
        approvers.forEach(a -> enos.add(a.eno()));
        Map<String, CuserI> people = new HashMap<>();
        for (var person : users.findByEnoIn(enos)) {
            if (!enos.contains(person.getEno()) || !"N".equals(person.getDelYn())) continue;
            if (people.putIfAbsent(person.getEno(), person) != null)
                throw invalid("결재 사용자 정보가 중복되었습니다.");
        }
        var requester = requiredPerson(people, requesterEno, false);
        var line =
                approvers.stream()
                        .map(
                                a -> {
                                    var p = requiredPerson(people, a.eno(), true);
                                    return new ItBudgetSnapshot.ApprovalPerson(
                                            a.role(), p.getEno(), p.getUsrNm(), p.getPtCNm(), null);
                                })
                        .toList();
        return new ItBudgetSnapshot.ApprovalLine(
                new ItBudgetSnapshot.Requester(
                        requester.getEno(), requester.getUsrNm(), requester.getPtCNm()),
                line);
    }

    private CuserI requiredPerson(Map<String, CuserI> people, String eno, boolean requireRank) {
        var p = people.get(eno);
        if (p == null
                || p.getUsrNm() == null
                || p.getUsrNm().isBlank()
                || requireRank && (p.getPtCNm() == null || p.getPtCNm().isBlank()))
            throw invalid("필수 결재 사용자 정보를 확인할 수 없습니다.");
        return p;
    }

    private PreviewDocument publicDocument(
            BuiltDocument document, ItBudgetSnapshot.ApprovalLine line, Instant capturedAt) {
        var sources =
                document.sources().stream()
                        .map(
                                s ->
                                        new SnapshotSource(
                                                SourceKind.valueOf(s.kind()),
                                                s.id(),
                                                s.revision(),
                                                s.order(),
                                                s.digest()))
                        .toList();
        var publicLine =
                new SnapshotApprovalLine(
                        new Requester(
                                line.requester().eno(),
                                line.requester().name(),
                                line.requester().rank()),
                        line.approvers().stream()
                                .map(
                                        p ->
                                                new ApprovalPerson(
                                                        p.role(), p.eno(), p.name(), p.rank(),
                                                        p.date()))
                                .toList());
        var snapshot =
                new com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ItBudgetSnapshot(
                        new Form("it-budget", 2),
                        publicPayload(document.payload()),
                        publicLine,
                        new Integrity(
                                "SHA-256",
                                "IT_BUDGET_V2",
                                document.payloadDigest(),
                                capturedAt,
                                sources));
        return new PreviewDocument(
                document.clientDocumentKey(),
                snapshot,
                document.payloadDigest(),
                publicSources(document));
    }

    private List<SourceDigest> publicSources(BuiltDocument document) {
        return document.sources().stream()
                .map(
                        s ->
                                new SourceDigest(
                                        SourceKind.valueOf(s.kind()),
                                        s.id(),
                                        s.revision(),
                                        s.order(),
                                        s.digest(),
                                        snapshotName(document, s)))
                .toList();
    }

    /** 기존 전산예산 신청내용 표기 규칙(첫 항목명 + 나머지 건수)을 저장용 요약으로 만든다. */
    private String applicationSummary(List<SourceDigest> sources) {
        String firstName = sources.getFirst().displayName();
        return sources.size() == 1 ? firstName : firstName + " 외 " + (sources.size() - 1) + "건";
    }

    private String snapshotName(BuiltDocument document, ItBudgetSnapshot.Source source) {
        String name =
                source.kind().equals("PROJECT")
                        ? document.payload().projects().stream()
                                .filter(
                                        p ->
                                                p.id().equals(source.id())
                                                        && p.revision() == source.revision())
                                .map(p -> p.name() == null ? "" : p.name())
                                .findFirst()
                                .orElse("")
                        : document.payload().costs().stream()
                                .filter(
                                        c ->
                                                c.id().equals(source.id())
                                                        && c.revision() == source.revision())
                                .map(c -> c.name() == null ? "" : c.name())
                                .findFirst()
                                .orElse("");
        return name.isBlank() ? source.id() : name;
    }

    private PreviewRequest boundRequest(
            SubmissionRequest request, ItBudgetPreviewTokenService.Claims claims) {
        if (request.documents() == null
                || request.documents().isEmpty()
                || request.documents().size() > 100) throw invalid("문서는 1~100개여야 합니다.");
        var documents = new ArrayList<DocumentRequest>();
        var sourceSets = new ArrayList<List<SourceDigest>>();
        for (var d : request.documents()) {
            if (d == null
                    || d.sources() == null
                    || d.sources().isEmpty()
                    || d.sources().size() > 500
                    || d.payloadDigest() == null
                    || !d.payloadDigest().matches("[a-f0-9]{64}"))
                throw invalid("상신 문서가 올바르지 않습니다.");
            for (var s : d.sources()) {
                if (s == null
                        || s.sourceDigest() == null
                        || !s.sourceDigest().matches("[a-f0-9]{64}")
                        || s.displayName() == null
                        || s.displayName().isBlank()) throw invalid("원장 다이제스트가 올바르지 않습니다.");
            }
            var ordered =
                    d.sources().stream()
                            .sorted(Comparator.comparingInt(SourceDigest::order))
                            .toList();
            sourceSets.add(ordered);
            documents.add(
                    new DocumentRequest(
                            d.clientDocumentKey(),
                            ordered.stream()
                                    .map(
                                            s ->
                                                    new SourceRef(
                                                            s.kind(),
                                                            s.id(),
                                                            s.revision(),
                                                            s.order()))
                                    .toList()));
        }
        var normalized = normalize(new PreviewRequest(request.approvers(), documents), true);
        if (!claims.requestDigest().equals(canonical.digest(normalized))
                || !claims.sourceSetDigest().equals(canonical.digest(sourceSets))
                || !claims.payloadSetDigest()
                        .equals(
                                canonical.digest(
                                        request.documents().stream()
                                                .map(SubmissionDocument::payloadDigest)
                                                .toList()))
                || !claims.previewDigest().equals(request.previewDigest()))
            throw invalid("미리보기 요청 결속이 올바르지 않습니다.");
        return normalized;
    }

    private List<ChangedSource> changedSources(
            SubmissionRequest request, List<SourceAggregate> aggregates) {
        Map<SourceKey, SourceAggregate> byKey = new HashMap<>();
        aggregates.forEach(a -> byKey.put(a.key(), a));
        var changed = new ArrayList<SourceDigest>();
        for (var document : request.documents()) {
            for (var source :
                    document.sources().stream()
                            .sorted(Comparator.comparingInt(SourceDigest::order))
                            .toList()) {
                var aggregate =
                        byKey.get(new SourceKey(source.kind(), source.id(), source.revision()));
                if (aggregate == null
                        || !"N".equals(aggregate.parent().getDelYn())
                        || !sourceMatches(source.sourceDigest(), aggregate)) changed.add(source);
            }
        }
        if (changed.isEmpty()) return List.of();
        var modifierIds = new LinkedHashSet<String>();
        for (var source : changed) {
            var aggregate = byKey.get(new SourceKey(source.kind(), source.id(), source.revision()));
            if (aggregate != null && aggregate.latestAudit().modifierUserId() != null)
                modifierIds.add(aggregate.latestAudit().modifierUserId());
        }
        Map<String, String> names = new HashMap<>();
        if (!modifierIds.isEmpty())
            for (var person : users.findByEnoIn(modifierIds)) {
                if (person.getUsrNm() != null && !person.getUsrNm().isBlank())
                    names.put(person.getEno(), person.getUsrNm());
            }
        return changed.stream()
                .map(
                        source -> {
                            var aggregate =
                                    byKey.get(
                                            new SourceKey(
                                                    source.kind(), source.id(), source.revision()));
                            String name =
                                    aggregate == null
                                            ? null
                                            : aggregate.parent() instanceof Bprojm p
                                                    ? p.getAbusNm()
                                                    : ((Bcostm) aggregate.parent()).getCttNm();
                            String modifier =
                                    aggregate == null
                                            ? null
                                            : aggregate.latestAudit().modifierUserId();
                            return new ChangedSource(
                                    source.kind(),
                                    source.id(),
                                    source.revision(),
                                    name == null || name.isBlank() ? source.displayName() : name,
                                    modifier == null || modifier.isBlank()
                                            ? "확인 불가"
                                            : names.getOrDefault(modifier, modifier),
                                    aggregate == null
                                            ? null
                                            : aggregate.latestAudit().modifiedAt());
                        })
                .toList();
    }

    private boolean sourceMatches(String expected, SourceAggregate aggregate) {
        try {
            return expected.equals(builder.sourceDigest(aggregate));
        } catch (ItBudgetApprovalException exception) {
            // 발급 때 유효했던 업무 값이 정규형으로 표현할 수 없게 바뀐 경우도 원장 변경이다.
            if ("IT_BUDGET_PREVIEW_INVALID".equals(exception.code())) return false;
            throw exception;
        }
    }

    private static void requireActor(CustomUserDetails actor) {
        if (actor == null || actor.getEno() == null || actor.getEno().isBlank())
            throw new AccessDeniedException("인증 정보가 없습니다.");
    }

    private static String table(SourceKind kind) {
        return kind == SourceKind.PROJECT ? "BPROJM" : "BCOSTM";
    }

    private static ItBudgetApprovalException stale() {
        return conflict("IT_BUDGET_PREVIEW_STALE", "미리보기를 다시 확인해 주세요.");
    }

    private static ItBudgetApprovalException conflict(String code, String message) {
        return new ItBudgetApprovalException(
                org.springframework.http.HttpStatus.CONFLICT, code, message, List.of());
    }

    private static boolean isLockTimeout(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof jakarta.persistence.LockTimeoutException
                    || cause instanceof org.springframework.dao.CannotAcquireLockException
                    || cause instanceof java.sql.SQLException sql
                            && (sql.getErrorCode() == 30006 || sql.getErrorCode() == 54))
                return true;
        }
        return false;
    }

    private void recordPreview(String outcome) {
        recordCounter("approval.it_budget.preview.outcome", "preview", outcome);
        log.info("전산예산 미리보기 완료: outcome={}", outcome);
    }

    private void recordSubmission(String outcome) {
        recordCounter("approval.it_budget.submission", "submission", outcome);
    }

    private void recordFailureDetails(ItBudgetApprovalException exception) {
        if (exception.reason() == ItBudgetApprovalException.Reason.SIGNATURE_FAILURE) {
            recordTaggedCounter("approval.it_budget.preview.signature_failure");
        }
        if ("IT_BUDGET_SOURCE_CHANGED".equals(exception.code())) {
            exception.changedSources().stream()
                    .map(ChangedSource::kind)
                    .distinct()
                    .forEach(
                            kind -> {
                                recordTaggedCounter(
                                        "approval.it_budget.submission.source_changed",
                                        "source_kind",
                                        kind.name());
                                log.info(
                                        "전산예산 상신 충돌: code=IT_BUDGET_SOURCE_CHANGED, source_kind={}",
                                        kind.name());
                            });
        }
    }

    private void completeSubmission(
            Timer.Sample sample,
            String outcome,
            boolean successful,
            int documentCount,
            int sourceCount) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            completeSubmissionMetrics(sample, outcome, documentCount, sourceCount);
            return;
        }
        var completed = new AtomicBoolean();
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (!completed.compareAndSet(false, true)) return;
                            completeSubmissionMetrics(
                                    sample,
                                    successful && status != STATUS_COMMITTED ? "error" : outcome,
                                    documentCount,
                                    sourceCount);
                        }
                    });
        } catch (RuntimeException exception) {
            metricFailure("submission", "synchronization_registration");
        }
    }

    private void completeSubmissionMetrics(
            Timer.Sample sample, String outcome, int documentCount, int sourceCount) {
        try {
            recordSubmission(outcome);
            if ("success".equals(outcome)) {
                recordSummary("approval.it_budget.submission.documents", documentCount);
                recordSummary("approval.it_budget.submission.sources", sourceCount);
            }
            log.info(
                    "전산예산 상신 완료: outcome={}, documents={}, sources={}",
                    outcome,
                    "success".equals(outcome) ? documentCount : 0,
                    "success".equals(outcome) ? sourceCount : 0);
        } finally {
            stopTimer(sample, "approval.it_budget.submission.duration", "submission");
        }
    }

    private Timer.Sample startTimer(String operation) {
        try {
            return Timer.start(meterRegistry);
        } catch (RuntimeException exception) {
            metricFailure(operation, "timer_start");
            return null;
        }
    }

    private void recordCounter(String metric, String operation, String outcome) {
        try {
            meterRegistry.counter(metric, "outcome", outcome).increment();
        } catch (RuntimeException exception) {
            metricFailure(operation, "counter");
        }
    }

    private void recordTaggedCounter(String metric, String... tags) {
        try {
            meterRegistry.counter(metric, tags).increment();
        } catch (RuntimeException exception) {
            metricFailure("submission", "counter");
        }
    }

    private void recordSummary(String metric, int count) {
        try {
            meterRegistry.summary(metric).record(count);
        } catch (RuntimeException exception) {
            metricFailure("submission", "summary");
        }
    }

    private void stopTimer(Timer.Sample sample, String metric, String operation) {
        if (sample == null) return;
        try {
            sample.stop(meterRegistry.timer(metric));
        } catch (RuntimeException exception) {
            metricFailure(operation, "timer_stop");
        }
    }

    private static void metricFailure(String operation, String stage) {
        log.warn("전산예산 메트릭 기록 실패: operation={}, stage={}", operation, stage);
    }

    private static String outcome(String code) {
        return switch (code) {
            case "IT_BUDGET_PREVIEW_INVALID" -> "invalid";
            case "IT_BUDGET_PREVIEW_EXPIRED" -> "expired";
            case "IT_BUDGET_SOURCE_CHANGED" -> "source_changed";
            case "IT_BUDGET_PREVIEW_STALE" -> "stale";
            case "IT_BUDGET_CONCURRENT_UPDATE" -> "concurrent_update";
            default -> "error";
        };
    }

    private Payload publicPayload(ItBudgetSnapshot.Payload payload) {
        try {
            return ItBudgetSnapshotCodec.toPublic(mapper, canonical, payload);
        } catch (JsonProcessingException exception) {
            throw invalid("스냅샷 공개 값 변환에 실패했습니다.");
        }
    }

    private static ItBudgetApprovalException invalid(String message) {
        return ItBudgetSourceLoader.invalid(message);
    }
}
