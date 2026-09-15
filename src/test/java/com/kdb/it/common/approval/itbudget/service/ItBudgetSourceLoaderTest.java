package com.kdb.it.common.approval.itbudget.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.domain.budget.cost.entity.*;
import com.kdb.it.domain.budget.cost.repository.*;
import com.kdb.it.domain.budget.project.entity.*;
import com.kdb.it.domain.budget.project.repository.*;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ItBudgetSourceLoaderTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectItemRepository items = mock(ProjectItemRepository.class);
    final CostRepository costs = mock(CostRepository.class);
    final BtermmRepository terminals = mock(BtermmRepository.class);
    final FileRepository files = mock(FileRepository.class);
    final ItBudgetSourceLoader loader =
            new ItBudgetSourceLoader(projects, items, costs, terminals, files);

    @Test
    void legacyAggregateConstructorDefaultsAttachmentsToEmptyAndPreservesSourceKey() {
        var source =
                new ItBudgetSourceLoader.SourceAggregate(
                        ref("P1", 2, 1),
                        project("P1", 2),
                        List.of(),
                        new ItBudgetSourceLoader.Audit("U1", null));

        assertThat(source.attachments()).isEmpty();
        assertThat(source.key())
                .isEqualTo(new ItBudgetSourceLoader.SourceKey(SourceKind.PROJECT, "P1", 2));
    }

    @Test
    void loadsOnlyActiveRelatedFilesInBulkAndKeepsThemOutOfLatestAudit() {
        var changedAt = LocalDateTime.of(2026, 9, 15, 11, 0);
        var project = project("P1", 1);
        var cost = Bcostm.builder().costBgNo("C1").bgSno(2).delYn("N").build();
        when(projects.findVersions(anyCollection(), anyCollection())).thenReturn(List.of(project));
        when(costs.findVersions(anyCollection(), anyCollection())).thenReturn(List.of(cost));
        var projectFile =
                Cfilem.builder()
                        .flMpnId("FL-2")
                        .apgFlKdNm("정보화사업")
                        .apgFlLnkCtzNm("P1")
                        .delYn("N")
                        .lstChgDtm(changedAt)
                        .lstChgUsid("file-user")
                        .build();
        var costFile =
                Cfilem.builder()
                        .flMpnId("FL-1")
                        .apgFlKdNm("전산업무비")
                        .apgFlLnkCtzNm("C1")
                        .delYn("N")
                        .build();
        when(files.findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("정보화사업", Set.of("P1"), "N"))
                .thenReturn(List.of(projectFile));
        when(files.findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("전산업무비", Set.of("C1"), "N"))
                .thenReturn(List.of(costFile));

        var result =
                loader.load(List.of(ref("P1", 1, 1), new SourceRef(SourceKind.COST, "C1", 2, 2)));

        assertThat(result.getFirst().attachments()).containsExactly(projectFile);
        assertThat(result.get(1).attachments()).containsExactly(costFile);
        assertThat(result.getFirst().latestAudit().modifierUserId()).isNotEqualTo("file-user");
        verify(files).findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("정보화사업", Set.of("P1"), "N");
        verify(files).findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("전산업무비", Set.of("C1"), "N");
    }

    @Test
    void groupsFilesByManagementNumberAcrossRevisionsAndSortsByFileId() {
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(project("P1", 1), project("P1", 2), project("P2", 1)));
        var first =
                Cfilem.builder()
                        .flMpnId("FL-1")
                        .apgFlKdNm("정보화사업")
                        .apgFlLnkCtzNm("P1")
                        .delYn("N")
                        .build();
        var last =
                Cfilem.builder()
                        .flMpnId("FL-3")
                        .apgFlKdNm("정보화사업")
                        .apgFlLnkCtzNm("P1")
                        .delYn("N")
                        .build();
        var other =
                Cfilem.builder()
                        .flMpnId("FL-2")
                        .apgFlKdNm("정보화사업")
                        .apgFlLnkCtzNm("P2")
                        .delYn("N")
                        .build();
        when(files.findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("정보화사업", Set.of("P1", "P2"), "N"))
                .thenReturn(List.of(last, other, first));

        var result = loader.load(List.of(ref("P1", 1, 1), ref("P1", 2, 2), ref("P2", 1, 3)));

        assertThat(result.get(0).attachments()).containsExactly(first, last);
        assertThat(result.get(1).attachments()).containsExactly(first, last);
        assertThat(result.get(2).attachments()).containsExactly(other);
        verify(files)
                .findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn("정보화사업", Set.of("P1", "P2"), "N");
        verifyNoMoreInteractions(files);
    }

    @Test
    void filtersCartesianPairsAndSortsChildrenWithoutDiscardingDeletedRows() {
        var p1 = project("P1", 1);
        var p2 = project("P2", 2);
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(p2, project("P1", 2), p1));
        var a = item("I1", 1, "P1", 1, "N");
        var deleted = item("I2", 2, "P1", 1, "Y");
        when(items.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(deleted, item("wrong", 1, "P1", 2, "N"), a));
        var result = loader.load(List.of(ref("P2", 2, 2), ref("P1", 1, 1)));
        assertThat(result).extracting(v -> v.key().id()).containsExactly("P1", "P2");
        assertThat(result.getFirst().children()).containsExactly(a, deleted);
        assertThat(result.getLast().children()).isEmpty();
        verify(projects).findVersions(List.of("P1", "P2"), List.of(1, 2));
        verifyNoInteractions(costs, terminals);
    }

    @Test
    void costChildrenUseExactParentRevision() {
        var cost = Bcostm.builder().costBgNo("C1").bgSno(2).delYn("Y").build();
        var term =
                Btermm.builder()
                        .tmnMngNo("T1")
                        .sno(3)
                        .termBgNo("C1")
                        .termBgSno(2)
                        .delYn("N")
                        .build();
        when(costs.findVersions(anyCollection(), anyCollection())).thenReturn(List.of(cost));
        when(terminals.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(
                                term,
                                Btermm.builder()
                                        .tmnMngNo("T2")
                                        .sno(1)
                                        .termBgNo("C1")
                                        .termBgSno(1)
                                        .build()));
        var result = loader.load(List.of(new SourceRef(SourceKind.COST, "C1", 2, 1)));
        assertThat(result.getFirst().parent()).isSameAs(cost);
        assertThat(result.getFirst().children()).containsExactly(term);
    }

    @Test
    void latestAuditUsesChildSequenceThenIdentifierToBreakTies() {
        var time = LocalDateTime.of(2026, 9, 6, 1, 0);
        var p =
                Bprojm.builder()
                        .abusMngNo("P1")
                        .sno(1)
                        .delYn("N")
                        .lstChgDtm(time)
                        .lstChgUsid("parent")
                        .build();
        when(projects.findVersions(anyCollection(), anyCollection())).thenReturn(List.of(p));
        var first =
                Bitemm.builder()
                        .gclMngNo("Z")
                        .sno(1)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .lstChgDtm(time)
                        .lstChgUsid("first")
                        .build();
        var last =
                Bitemm.builder()
                        .gclMngNo("A")
                        .sno(2)
                        .abusMngNo("P1")
                        .fntTbCrySno(1)
                        .lstChgDtm(time)
                        .lstChgUsid("last")
                        .build();
        when(items.findSourceVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(last, first));
        assertThat(loader.load(List.of(ref("P1", 1, 1))).getFirst().latestAudit().modifierUserId())
                .isEqualTo("last");
    }

    @Test
    void rejectsDuplicateAndMissingExactVersions() {
        invalid(() -> loader.load(List.of(ref("P1", 1, 1), ref("P1", 1, 2))));
        when(projects.findVersions(anyCollection(), anyCollection()))
                .thenReturn(List.of(project("P1", 2)));
        assertThatThrownBy(() -> loader.load(List.of(ref("P1", 1, 1))))
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo("IT_BUDGET_SOURCE_NOT_FOUND");
                            assertThat(e.status().value()).isEqualTo(404);
                        });
    }

    @Test
    void rejectsEmptyAndOver500BeforeAnyQuery() {
        invalid(() -> loader.load(List.of()));
        invalid(
                () ->
                        loader.load(
                                IntStream.rangeClosed(1, 501)
                                        .mapToObj(i -> ref("P" + i, 1, i))
                                        .toList()));
        verifyNoInteractions(projects, items, costs, terminals);
    }

    @Test
    void accepts500AndLocksInStableKindIdRevisionOrder() {
        var refs = IntStream.rangeClosed(1, 500).mapToObj(i -> ref("P" + i, 1, i)).toList();
        when(projects.findVersionsForUpdate(anyCollection(), anyCollection()))
                .thenReturn(refs.stream().map(r -> project(r.id(), 1)).toList());
        assertThat(loader.loadForUpdate(refs)).hasSize(500);
        verify(projects).findVersionsForUpdate(anyCollection(), anyCollection());
        verify(projects, never()).findVersions(anyCollection(), anyCollection());
    }

    static SourceRef ref(String id, int revision, int order) {
        return new SourceRef(SourceKind.PROJECT, id, revision, order);
    }

    static Bprojm project(String id, int revision) {
        return Bprojm.builder().abusMngNo(id).sno(revision).usid("U1").delYn("N").build();
    }

    static Bitemm item(String id, int sequence, String parent, int revision, String deleted) {
        return Bitemm.builder()
                .gclMngNo(id)
                .sno(sequence)
                .abusMngNo(parent)
                .fntTbCrySno(revision)
                .delYn(deleted)
                .build();
    }

    static void invalid(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ItBudgetApprovalException.class,
                        e -> assertThat(e.code()).isEqualTo("IT_BUDGET_PREVIEW_INVALID"));
    }
}
