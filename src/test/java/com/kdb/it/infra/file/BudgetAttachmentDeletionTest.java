package com.kdb.it.infra.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.board.service.BoardPostFileCacheService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.request.service.RequestFormSourceArchiveService;
import com.kdb.it.infra.file.authz.*;
import com.kdb.it.infra.file.controller.FileController;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.service.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

/** 실제 컨트롤러·파일 권한·서비스 조합으로 예산 첨부 삭제와 기존 보호 경계를 검증합니다. */
class BudgetAttachmentDeletionTest {
    private static final String ID = "FL-BUDGET";
    private static final String PARENT = "BUDGET-001";
    private final FileRepository files = mock(FileRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CostRepository costs = mock(CostRepository.class);
    private final BudgetFileDeleteAuthorizer departmentPolicy =
            new BudgetFileDeleteAuthorizer(projects, costs);
    private final FileOwnershipChecker checker =
            new FileOwnershipChecker(
                    files,
                    mock(FileReadAuthorizerRegistry.class),
                    mock(ReviewCommentFileWriteAuthorizer.class),
                    departmentPolicy);
    private final FileTargetWriteAuthorizerRegistry policies =
            new FileTargetWriteAuthorizerRegistry(
                    List.of(
                            new ProjectFileTargetWriteAuthorizer(projects),
                            new CostFileTargetWriteAuthorizer(costs),
                            new RequestFormFileTargetWriteAuthorizer(),
                            new BannerFileTargetWriteAuthorizer(),
                            new UserGuideFileTargetWriteAuthorizer()),
                    mock(FileKindRegistry.class));
    private final FileService service =
            new FileService(
                    files,
                    checker,
                    mock(FileUploadUnitService.class),
                    policies,
                    mock(BoardPostFileCacheService.class));
    private final FileController controller =
            new FileController(
                    service,
                    checker,
                    policies,
                    mock(RequestFormSourceArchiveService.class),
                    mock(BoardAttachmentArchiveService.class));

    private CustomUserDetails user(String eno, String department, String role) {
        return new CustomUserDetails(eno, List.of(role), department);
    }

    private Cfilem file(String kind, String owner) {
        var file = mock(Cfilem.class);
        when(file.getApgFlKdNm()).thenReturn(kind);
        when(file.getApgFlLnkCtzNm()).thenReturn(PARENT);
        when(file.getFstEnrUsid()).thenReturn(owner);
        return file;
    }

    private void parent(String kind, String department) {
        if ("정보화사업".equals(kind)) {
            var project = mock(Bprojm.class);
            when(project.getSvnDpmC()).thenReturn(department);
            when(projects.findByAbusMngNoAndDelYn(PARENT, "N")).thenReturn(Optional.of(project));
        } else {
            var cost = mock(Bcostm.class);
            when(cost.getCostSvnDpmC()).thenReturn(department);
            when(costs.findByCostBgNoAndLstYnAndDelYn(PARENT, "Y", "N"))
                    .thenReturn(Optional.of(cost));
        }
    }

    @ParameterizedTest(name = "{0}: 사용자={1}, 부서={2}, 권한={3}, 허용={4}")
    @CsvSource({
        "정보화사업, OWNER, D999, ITPZZ001, true",
        "정보화사업, COLLEAGUE, D001, ITPZZ001, true",
        "정보화사업, ADMIN, D999, ITPAD001, true",
        "정보화사업, OTHER, D999, ITPZZ001, false",
        "정보화사업, MANAGER, D999, ITPZZ002, false",
        "전산업무비, OWNER, D999, ITPZZ001, true",
        "전산업무비, COLLEAGUE, D001, ITPZZ001, true",
        "전산업무비, ADMIN, D999, ITPAD001, true",
        "전산업무비, OTHER, D999, ITPZZ001, false",
        "전산업무비, MANAGER, D999, ITPZZ002, false"
    })
    @DisplayName("단건 삭제는 본인·주관부서·관리자를 허용하고 다른 부서를 차단한다")
    void deletionAcrossRoles(String kind, String eno, String dept, String role, boolean allowed) {
        var file = file(kind, "OWNER");
        parent(kind, "D001");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        var actor = user(eno, dept, role);
        if (allowed) {
            assertThat(controller.deleteFile(ID, actor).getStatusCode().value()).isEqualTo(204);
            verify(file).delete();
        } else {
            assertThatThrownBy(() -> controller.deleteFile(ID, actor))
                    .isInstanceOf(AccessDeniedException.class);
            verify(file, never()).delete();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"정보화사업", "전산업무비"})
    @DisplayName("동일 부서의 여러 업로더 첨부를 부모 1회 조회로 일괄 삭제한다")
    void bulkSameDepartmentQueriesParentOnce(String kind) {
        parent(kind, "D001");
        var first = file(kind, "OWNER");
        var second = file(kind, "COLLEAGUE");
        when(files.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(kind, PARENT, "N"))
                .thenReturn(List.of(first, second));
        assertThat(service.deleteFilesByOrc(kind, PARENT, user("NEXT", "D001", "ITPZZ001")))
                .isEqualTo(2);
        verify(first).delete();
        verify(second).delete();
        if ("정보화사업".equals(kind)) verify(projects).findByAbusMngNoAndDelYn(PARENT, "N");
        else verify(costs).findByCostBgNoAndLstYnAndDelYn(PARENT, "Y", "N");
    }

    @ParameterizedTest
    @ValueSource(strings = {"정보화사업", "전산업무비"})
    @DisplayName("타부서의 일괄 삭제가 거부되면 본인 파일도 부분 삭제하지 않는다")
    void bulkForeignDepartmentDoesNotPartiallyDelete(String kind) {
        parent(kind, "D001");
        var own = file(kind, "OTHER");
        var colleague = file(kind, "OWNER");
        when(files.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(kind, PARENT, "N"))
                .thenReturn(List.of(own, colleague));
        assertThatThrownBy(
                        () ->
                                service.deleteFilesByOrc(
                                        kind, PARENT, user("OTHER", "D999", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class);
        verify(own, never()).delete();
        verify(colleague, never()).delete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"정보화사업", "전산업무비"})
    @DisplayName("예산 첨부의 부서 삭제 권한으로 파일 연결 정보를 바꿀 수 없다")
    void departmentDeleteDoesNotGrantMetadataWrite(String kind) {
        parent(kind, "D001");
        var file = file(kind, "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        var actor = user("COLLEAGUE", "D001", "ITPZZ001");
        assertThatCode(() -> checker.verifyDeleteAccess(ID, actor)).doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                controller.updateFileMeta(
                                        ID,
                                        FileDto.UpdateRequest.builder()
                                                .apgFlLnkCtzNm("OTHER-PARENT")
                                                .build(),
                                        actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(file, never()).updateMeta(any(), any());
    }

    @Test
    @DisplayName("사업 파일 소유자도 보호된 연결 정보를 범용 수정할 수 없다")
    void projectMetadataRemainsProtected() {
        var file = file("정보화사업", "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        assertThatThrownBy(
                        () ->
                                controller.updateFileMeta(
                                        ID,
                                        FileDto.UpdateRequest.builder()
                                                .apgFlLnkCtzNm("OTHER-PARENT")
                                                .build(),
                                        user("OWNER", "D001", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");
        verify(file, never()).updateMeta(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"편성요청서반입", "배너", "사용자가이드"})
    @DisplayName("보호된 종류의 단건·일괄 삭제는 관리자도 계속 차단한다")
    void protectedKindsStayProtected(String kind) {
        var file = file(kind, "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        var admin = user("ADMIN", "D001", "ITPAD001");
        assertThatThrownBy(() -> controller.deleteFile(ID, admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteFilesByOrc(kind, PARENT, admin))
                .isInstanceOf(AccessDeniedException.class);
        verify(file, never()).delete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"공통게시판", "요구사항정의서", "협의회관련자료", "가이드문서"})
    @DisplayName("예산 이외 첨부에는 부서 삭제 권한을 확대하지 않는다")
    void otherKindsDoNotGainDepartmentPermission(String kind) {
        var file = file(kind, "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        assertThatThrownBy(() -> controller.deleteFile(ID, user("COLLEAGUE", "D001", "ITPZZ001")))
                .isInstanceOf(AccessDeniedException.class);
        verify(file, never()).delete();
        verifyNoInteractions(projects, costs);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("부서코드 누락은 동일 부서로 간주하지 않는다")
    void missingDepartmentCannotDeleteOthers(String dept) {
        for (String kind : List.of("정보화사업", "전산업무비")) {
            var file = file(kind, "OWNER");
            when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
            assertThatThrownBy(() -> controller.deleteFile(ID, user("COLLEAGUE", dept, "ITPZZ001")))
                    .isInstanceOf(AccessDeniedException.class);
            verify(file, never()).delete();
        }
        verifyNoInteractions(projects, costs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"정보화사업", "전산업무비"})
    @DisplayName("부모 누락 또는 주관부서 누락이면 타인의 파일을 삭제할 수 없다")
    void missingParentOrParentDepartmentDoesNotGrantAccess(String kind) {
        var file = file(kind, "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        var actor = user("COLLEAGUE", "D001", "ITPZZ001");
        assertThatThrownBy(() -> controller.deleteFile(ID, actor))
                .isInstanceOf(AccessDeniedException.class);
        parent(kind, null);
        assertThatThrownBy(() -> controller.deleteFile(ID, actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(file, never()).delete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"정보화사업", "전산업무비"})
    @DisplayName("비인증 요청은 단건·빈 목록 일괄 삭제를 모두 거부한다")
    void anonymousCannotDelete(String kind) {
        var file = file(kind, "OWNER");
        when(files.findByFlMpnIdAndDelYn(ID, "N")).thenReturn(Optional.of(file));
        assertThatThrownBy(() -> controller.deleteFile(ID, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteFilesByOrc(kind, PARENT, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(file, never()).delete();
    }
}
