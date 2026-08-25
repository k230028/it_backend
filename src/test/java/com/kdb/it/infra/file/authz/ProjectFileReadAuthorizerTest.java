package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectFileReadAuthorizerTest {

    private static final String PRJ = "PRJ-2026-0001";

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectFileReadAuthorizer authorizer =
            new ProjectFileReadAuthorizer(projectRepository);

    private CustomUserDetails user(String eno) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), "18001");
    }

    private Cfilem file(String parentId) {
        return Cfilem.builder()
                .apgFlKdNm(ProjectFileReadAuthorizer.PROJECT_KIND)
                .apgFlLnkCtzNm(parentId)
                .build();
    }

    private Bprojm project() {
        return Bprojm.builder().abusMngNo(PRJ).sno(1).svnDpmC("18001").build();
    }

    @Test
    @DisplayName("정보화사업 종류만 담당한다(경상사업도 같은 원장이라 종류를 공유한다)")
    void supportsOnlyProjectKind() {
        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("정보화사업");
    }

    @Test
    @DisplayName("활성 사업의 첨부는 사업과 무관한 인증 사용자도 읽을 수 있다")
    void authenticatedUserCanReadActiveProjectFile() {
        given(projectRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                .willReturn(Optional.of(project()));

        assertThat(authorizer.canRead(file(PRJ), user("OTHER"))).isTrue();
    }

    @Test
    @DisplayName("삭제되었거나 없는 사업의 첨부는 읽을 수 없다")
    void missingProjectFileCannotBeRead() {
        given(projectRepository.findByAbusMngNoAndDelYn(PRJ, "N")).willReturn(Optional.empty());

        assertThat(authorizer.canRead(file(PRJ), user("E001"))).isFalse();
    }

    @Test
    @DisplayName("부모 사업관리번호가 비어 있으면 조회하지 않고 거부한다")
    void blankParentIsRejectedWithoutLookup() {
        assertThat(authorizer.canRead(file(" "), user("E001"))).isFalse();
        assertThat(authorizer.canRead(file(null), user("E001"))).isFalse();

        then(projectRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비인증(null) 사용자는 조회하지 않고 거부한다")
    void anonymousIsRejectedWithoutLookup() {
        assertThat(authorizer.canRead(file(PRJ), null)).isFalse();

        then(projectRepository).shouldHaveNoInteractions();
    }
}
