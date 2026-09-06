package com.kdb.it.common.approval.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.*;
import com.kdb.it.common.approval.notification.ApprovalRequestNotifier;
import com.kdb.it.common.approval.repository.*;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.*;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.*;
import com.kdb.it.domain.council.repository.*;
import com.kdb.it.domain.council.service.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;

/** 실제 협의회 생산자→공통 저장→결재/결재선 경로를 연결하고 외부 저장소만 대역으로 둔다. */
class CouncilJsonlessApprovalWorkflowTest {
    enum Producer {
        REVIEW,
        RESULT,
        SKIP
    }

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationMapRepository maps = mock(ApplicationMapRepository.class);
    private final ApproverRepository decisions = mock(ApproverRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Map<String, Capplm> storedApplications = new LinkedHashMap<>();
    private final List<Cappla> storedMaps = new ArrayList<>();
    private final List<Cdecim> storedDecisions = new ArrayList<>();
    private ApplicationService service;
    private ApprovalLineManagementService lines;

    @BeforeEach
    void setup() {
        when(applications.getNextVal()).thenAnswer(i -> (long) storedApplications.size() + 1);
        when(applications.save(any()))
                .thenAnswer(
                        i -> {
                            Capplm app = i.getArgument(0);
                            storedApplications.put(app.getApfMngNo(), app);
                            return app;
                        });
        when(applications.findByIdForUpdate(anyString()))
                .thenAnswer(i -> Optional.ofNullable(storedApplications.get(i.getArgument(0))));
        when(maps.save(any()))
                .thenAnswer(
                        i -> {
                            Cappla link = i.getArgument(0);
                            storedMaps.add(link);
                            return link;
                        });
        when(maps.findDetailSourcesByApplicationIds(anyList()))
                .thenAnswer(
                        i -> {
                            List<String> ids = i.getArgument(0);
                            return storedMaps.stream()
                                    .filter(
                                            link ->
                                                    ids.contains(link.getApfDcmNo())
                                                            && storedApplications
                                                                            .get(link.getApfDcmNo())
                                                                            .getDcdReqInf()
                                                                    == null)
                                    .map(
                                            link -> {
                                                var view =
                                                        mock(
                                                                ApplicationMapRepository
                                                                        .DetailSourceView.class);
                                                when(view.getApfDcmNo())
                                                        .thenReturn(link.getApfDcmNo());
                                                when(view.getFntTbNm())
                                                        .thenReturn(link.getFntTbNm());
                                                when(view.getPkColNm())
                                                        .thenReturn(link.getPkColNm());
                                                when(view.getFntTbCrySno())
                                                        .thenReturn(link.getFntTbCrySno());
                                                return view;
                                            })
                                    .toList();
                        });
        when(decisions.save(any()))
                .thenAnswer(
                        i -> {
                            Cdecim row = i.getArgument(0);
                            if (!storedDecisions.contains(row)) storedDecisions.add(row);
                            return row;
                        });
        when(decisions.findByDcdMngNoOrderByDcrSqnSnoAsc(anyString()))
                .thenAnswer(i -> rows(i.getArgument(0)));
        doAnswer(
                        i -> {
                            storedDecisions.remove(i.getArgument(0));
                            return null;
                        })
                .when(decisions)
                .delete(any());
        doAnswer(
                        i -> {
                            Iterable<Cdecim> removed = i.getArgument(0);
                            removed.forEach(storedDecisions::remove);
                            return null;
                        })
                .when(decisions)
                .deleteAll(org.mockito.ArgumentMatchers.<Iterable<Cdecim>>any());
        when(decisions.saveAll(any()))
                .thenAnswer(
                        i -> {
                            List<Cdecim> values = i.getArgument(0);
                            storedDecisions.addAll(values);
                            return values;
                        });
        when(users.findById(anyString())).thenAnswer(i -> Optional.of(user(i.getArgument(0))));
        when(users.findByEnoIn(anyList()))
                .thenAnswer(
                        i -> {
                            List<String> enos = i.getArgument(0);
                            return enos.stream().map(this::user).toList();
                        });
        var projects = mock(ProjectRepository.class);
        var costs = mock(CostRepository.class);
        var sync = mock(BprojaSyncService.class);
        var notifier = mock(ApprovalRequestNotifier.class);
        var delegate = delegate(MAPPER);
        var persistence =
                new ApplicationPersistenceService(
                        applications, decisions, maps, projects, costs, users, sync, notifier);
        service =
                new ApplicationService(
                        applications,
                        decisions,
                        maps,
                        projects,
                        costs,
                        users,
                        mock(OrganizationRepository.class),
                        events,
                        delegate,
                        sync,
                        notifier,
                        persistence,
                        new ApprovalDetailPolicy(maps),
                        reader());
        lines =
                new ApprovalLineManagementService(
                        applications, decisions, users, delegate, new ApprovalDetailPolicy(maps));
    }

    @ParameterizedTest
    @CsvSource({
        "REVIEW, APPROVE",
        "REVIEW, REJECT",
        "REVIEW, RECALL",
        "RESULT, APPROVE",
        "RESULT, REJECT",
        "RESULT, RECALL",
        "SKIP, APPROVE",
        "SKIP, REJECT",
        "SKIP, RECALL"
    })
    void actualCouncilSubmissionCanCompleteDecisionWorkflow(Producer producer, String action)
            throws Exception {
        String id = submit(producer);
        Capplm app = storedApplications.get(id);
        assertThat(app.getDcdReqInf()).isNull();
        assertThat(storedMaps)
                .singleElement()
                .satisfies(
                        link -> {
                            assertThat(link.getFntTbNm())
                                    .isEqualTo(producer == Producer.SKIP ? "BASKPM" : "BASCTM");
                            assertThat(link.getPkColNm()).isEqualTo("C1");
                            assertThat(link.getFntTbCrySno()).isNull();
                        });

        switch (action) {
            case "APPROVE" -> {
                for (Cdecim row : rows(id)) service.approve(id, decision(row.getDcrEno(), "2"));
                assertThat(app.getItPtlApfPrgStsC()).isEqualTo("2");
                assertThat(app.getDcdReqInf()).isNull();
                assertThat(rows(id)).allMatch(row -> "2".equals(row.getItPtlDcdStsC()));
            }
            case "REJECT" -> {
                service.approve(id, decision("E1", "3"));
                assertThat(app.getItPtlApfPrgStsC()).isEqualTo("3");
                assertThat(app.getDcdReqInf()).isNull();
            }
            case "RECALL" -> {
                var request = new ApplicationDto.RecallRequest();
                request.setRecallOpnn("회수 사유");
                service.recall(id, request, "U1", false);
                assertThat(app.getItPtlApfPrgStsC()).isEqualTo("4");
                assertThat(
                                MAPPER.readTree(app.getDcdReqInf())
                                        .at("/recallInfo/recallerEno")
                                        .asText())
                        .isEqualTo("U1");
                assertThat(rows(id)).noneMatch(row -> "1".equals(row.getItPtlDcdStsC()));
            }
            default -> throw new AssertionError(action);
        }
        verify(events, atLeastOnce()).publishEvent(any(Object.class));
    }

    @ParameterizedTest
    @EnumSource(Producer.class)
    void actualCouncilSubmissionSupportsLineManagement(Producer producer) {
        String id = submit(producer);
        lines.addApprover(id, "E3", "U1", true);
        assertThat(rows(id)).extracting(Cdecim::getDcrEno).contains("E3");
        lines.deleteApprover(id, rows(id).getLast().getDcrSqnSno(), "U1", true);
        assertThat(rows(id)).extracting(Cdecim::getDcrEno).doesNotContain("E3");
        lines.reorderPendingApprovers(
                id, rows(id).reversed().stream().map(Cdecim::getDcrSqnSno).toList(), "U1", true);
        lines.replacePendingApprovers(id, List.of("E4"), "U1", true);
        assertThat(rows(id)).extracting(Cdecim::getDcrEno).containsExactly("E4");
        assertThat(storedApplications.get(id).getDcdReqInf()).isNull();
        verify(maps, times(4)).findDetailSourcesByApplicationIds(List.of(id));
        service.approve(id, decision("E4", "2"));
        assertThat(storedApplications.get(id).getItPtlApfPrgStsC()).isEqualTo("2");
    }

    static Stream<Arguments> invalidSourcesAndCommands() {
        return Stream.of(
                        "BPROJM",
                        "BCOSTM",
                        "UNKNOWN",
                        "MISSING",
                        "MIXED",
                        "DUPLICATE",
                        "REVISION",
                        "EMPTY_KEY")
                .flatMap(
                        source ->
                                Stream.of(
                                                "APPROVE", "RECALL", "ADD", "DELETE", "REORDER",
                                                "REPLACE")
                                        .map(command -> Arguments.of(source, command)));
    }

    @ParameterizedTest
    @MethodSource("invalidSourcesAndCommands")
    void missingRequiredSnapshotStillBlocksEveryCommand(String source, String command) {
        String id = submit(Producer.RESULT);
        if (!source.equals("MIXED") && !source.equals("DUPLICATE")) storedMaps.clear();
        if (!source.equals("MISSING"))
            storedMaps.add(
                    Cappla.builder()
                            .apfDcmNo(id)
                            .fntTbNm(
                                    switch (source) {
                                        case "DUPLICATE", "REVISION", "EMPTY_KEY" -> "BASCTM";
                                        case "MIXED" -> "BPROJM";
                                        default -> source;
                                    })
                            .pkColNm(source.equals("EMPTY_KEY") ? " " : "C2")
                            .fntTbCrySno(source.equals("REVISION") ? 1 : null)
                            .build());

        assertThatThrownBy(() -> executeCommand(id, command))
                .isInstanceOf(com.kdb.it.exception.DataCorruptionException.class);
        assertThat(storedApplications.get(id).getItPtlApfPrgStsC()).isEqualTo("1");
    }

    static Stream<Arguments> invalidJsonAndCommands() throws Exception {
        var corrupt = v2();
        object(corrupt, "/payload/projects/0").put("name", "변조");
        return Stream.of(
                        "",
                        " ",
                        "{}",
                        "{bad",
                        "{\"form\":{\"id\":\"it-budget\",\"version\":1}}",
                        corrupt.toString())
                .flatMap(
                        raw ->
                                Stream.of(
                                                "APPROVE", "RECALL", "ADD", "DELETE", "REORDER",
                                                "REPLACE")
                                        .map(command -> Arguments.of(raw, command)));
    }

    @ParameterizedTest
    @MethodSource("invalidJsonAndCommands")
    void councilMappingDoesNotBypassPresentButInvalidJson(String raw, String command) {
        String id = submit(Producer.RESULT);
        storedApplications.get(id).updateDetailContent(raw);
        assertThatThrownBy(() -> executeCommand(id, command))
                .isInstanceOf(com.kdb.it.exception.DataCorruptionException.class);
        assertThat(storedApplications.get(id).getDcdReqInf()).isEqualTo(raw);
        verify(maps, never()).findDetailSourcesByApplicationIds(anyList());
    }

    @Test
    void bulkCouncilApprovalClassifiesAllDocumentsInOneQuery() {
        List<String> ids = List.of(submit(Producer.REVIEW), submit(Producer.REVIEW));
        var request = new ApplicationDto.BulkApproveRequest();
        request.setApprovals(
                ids.stream()
                        .map(
                                id -> {
                                    var item = new ApplicationDto.ApprovalItem();
                                    item.setApfMngNo(id);
                                    item.setDcdEno("E1");
                                    item.setDcdSts("2");
                                    return item;
                                })
                        .toList());
        assertThat(service.bulkApprove(request).getSuccessCount()).isEqualTo(2);
        assertThat(storedApplications.values())
                .allMatch(
                        app -> "2".equals(app.getItPtlApfPrgStsC()) && app.getDcdReqInf() == null);
        verify(maps).findDetailSourcesByApplicationIds(ids);
        verify(maps, times(1)).findDetailSourcesByApplicationIds(anyList());
    }

    private void executeCommand(String id, String command) {
        switch (command) {
            case "APPROVE" -> service.approve(id, decision("E1", "2"));
            case "RECALL" -> {
                var request = new ApplicationDto.RecallRequest();
                request.setRecallOpnn("회수");
                service.recall(id, request, "U1", false);
            }
            case "ADD" -> lines.addApprover(id, "E3", "U1", true);
            case "DELETE" -> lines.deleteApprover(id, 2, "U1", true);
            case "REORDER" -> lines.reorderPendingApprovers(id, List.of(2, 1), "U1", true);
            case "REPLACE" -> lines.replacePendingApprovers(id, List.of("E3"), "U1", true);
            default -> throw new AssertionError(command);
        }
    }

    private String submit(Producer producer) {
        var councils = mock(CouncilService.class);
        var council = mock(Basctm.class);
        when(councils.findActiveCouncil("C1")).thenReturn(council);
        var requester = new CustomUserDetails("U1", List.of("ITPAD001", "ITPAD002"), "D1");
        var approvals =
                new CouncilApprovalService(
                        councils, mock(ProjectOverviewRepository.class), service);
        if (producer == Producer.REVIEW) {
            when(council.getItPtlAsctPrgStsTc()).thenReturn("02");
            return approvals
                    .requestApproval("C1", new CouncilDto.ApprovalRequest("E1", "상신"), requester)
                    .apfMngNo();
        }
        if (producer == Producer.RESULT) {
            when(council.getItPtlAsctPrgStsTc()).thenReturn("11");
            return approvals
                    .requestResultApproval(
                            "C1", new CouncilDto.ResultApprovalRequest("E1", "E2", "상신"), requester)
                    .apfMngNo();
        }
        when(council.getItPtlAsctPrgStsTc()).thenReturn("04");
        when(council.getItPtlAsctDbrTc()).thenReturn("04");
        var skips = mock(BaskpmRepository.class);
        var em = mock(EntityManager.class);
        AtomicReference<Baskpm> storedSkip = new AtomicReference<>();
        when(skips.findByItPtlAsctIdAndDelYn("C1", "N"))
                .thenAnswer(i -> Optional.ofNullable(storedSkip.get()));
        doAnswer(
                        i -> {
                            storedSkip.set(i.getArgument(0));
                            return null;
                        })
                .when(em)
                .persist(any());
        var skipService = new CouncilSkipService(skips, councils, service, events, em);
        skipService.createSkipRequest(
                "C1", new CouncilDto.SkipRequestCreate("요청", "F1"), requester);
        skipService.submitDecision(
                "C1",
                new CouncilDto.SkipDecisionRequest("Y", "판정", List.of("E1", "E2")),
                requester);
        return storedSkip.get().getApfMngNo();
    }

    private List<Cdecim> rows(String id) {
        return storedDecisions.stream()
                .filter(row -> id.equals(row.getDcdMngNo()))
                .sorted(Comparator.comparing(Cdecim::getDcrSqnSno))
                .toList();
    }

    private CuserI user(String eno) {
        return CuserI.builder().eno(eno).usrNm("사용자").ptCNm("팀장").bbrC("D1").delYn("N").build();
    }

    private ApplicationDto.ApproveRequest decision(String eno, String status) {
        var request = new ApplicationDto.ApproveRequest();
        request.setDcdEno(eno);
        request.setDcdSts(status);
        request.setDcdOpnn("결재");
        return request;
    }
}
