package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import com.kdb.it.common.approval.itbudget.service.ItBudgetPreviewTokenService.PreviewBinding;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotBuilder.BuiltDocument;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSourceLoader.SourceAggregate;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 주체와 원장을 검증하여 저장 없이 전산예산 v2 미리보기와 서명 토큰을 만든다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItBudgetApprovalFacade {
    private final ItBudgetSourceLoader loader;
    private final ItBudgetSnapshotBuilder builder;
    private final ItBudgetCanonicalJson canonical;
    private final ItBudgetPreviewTokenService tokens;
    private final UserRepository users;
    private final ObjectMapper mapper;

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
        if (actor == null || actor.getEno() == null || actor.getEno().isBlank())
            throw new AccessDeniedException("인증 정보가 없습니다.");
        var normalized = normalize(request);
        var refs = normalized.documents().stream().flatMap(d -> d.sourceRefs().stream()).toList();
        var aggregates = loader.load(refs);
        // 부모 데이터는 인가 판단에 필요하다. 모든 대상의 인가를 끝내기 전 표시정보·문서는 생성하지 않는다.
        for (var aggregate : aggregates) authorize(actor, aggregate);
        var line = approvalLine(actor.getEno(), normalized.approvers());
        var built = builder.buildDocuments(normalized.documents(), aggregates);
        String requestDigest = canonical.digest(normalized);
        String sourceSetDigest =
                canonical.digest(built.stream().map(BuiltDocument::sources).toList());
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

    private PreviewRequest normalize(PreviewRequest request) {
        if (request == null
                || request.documents() == null
                || request.documents().isEmpty()
                || request.documents().size() > 100) throw invalid("문서는 1~100개여야 합니다.");
        var approvers = request.approvers();
        if (approvers == null || approvers.isEmpty() || approvers.size() > 102)
            throw invalid("결재자는 1~102명이어야 합니다.");
        Set<String> enos = new HashSet<>();
        Set<ApproverRole> fixedRoles = new HashSet<>();
        int previousRole = -1;
        for (var a : approvers) {
            if (a == null
                    || a.role() == null
                    || a.eno() == null
                    || a.eno().isBlank()
                    || a.eno().length() > 14
                    || !enos.add(a.eno())) throw invalid("결재자 입력이 올바르지 않습니다.");
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
        var parent = aggregate.parent();
        String department =
                parent instanceof Bprojm p ? p.getSvnDpmC() : ((Bcostm) parent).getCostSvnDpmC();
        BudgetDetailAccessVerifier.verifyReadable(department, actor);
        if (!OwnershipVerifier.canModify(parent.getFstEnrUsid(), department, actor))
            throw new AccessDeniedException("신청 대상의 상신 권한이 없습니다.");
        if (!"N".equals(parent.getDelYn())) throw ItBudgetSourceLoader.notFound();
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
                                            p.getEno(), p.getUsrNm(), p.getPtCNm(), null);
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
                                .map(p -> new ApprovalPerson(p.eno(), p.name(), p.rank(), p.date()))
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
                document.sources().stream()
                        .map(
                                s ->
                                        new SourceDigest(
                                                SourceKind.valueOf(s.kind()),
                                                s.id(),
                                                s.revision(),
                                                s.order(),
                                                s.digest()))
                        .toList());
    }

    private Payload publicPayload(ItBudgetSnapshot.Payload payload) {
        try {
            // canonical writer가 3/4/0 scale의 plain 숫자를 출력하고 고정 DTO가 그 토큰을 String으로 읽는다.
            // 해시는 변환 이전 typed payload만 사용한다. 공개 문자열 JSON의 해시와 혼용하지 않는다.
            return mapper.readValue(canonical.write(payload), Payload.class);
        } catch (JsonProcessingException exception) {
            throw invalid("스냅샷 공개 값 변환에 실패했습니다.");
        }
    }

    private static ItBudgetApprovalException invalid(String message) {
        return ItBudgetSourceLoader.invalid(message);
    }
}
