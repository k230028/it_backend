package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectFileTargetWriteAuthorizerTest {

    private static final String PRJ = "PRJ-2026-0001";

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectFileTargetWriteAuthorizer authorizer =
            new ProjectFileTargetWriteAuthorizer(projectRepository);

    private CustomUserDetails user(String eno, String bbrC) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), bbrC);
    }

    private CustomUserDetails deptManager(String eno, String bbrC) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_DEPT_MGR), bbrC);
    }

    private CustomUserDetails admin() {
        return new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "99999");
    }

    private Bprojm project(String owner, String svnDpmC) {
        return Bprojm.builder().abusMngNo(PRJ).sno(1).svnDpmC(svnDpmC).fstEnrUsid(owner).build();
    }

    private void givenActiveProject(String owner, String svnDpmC) {
        given(projectRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                .willReturn(Optional.of(project(owner, svnDpmC)));
    }

    @Test
    @DisplayName("정보화사업 종류만 담당한다")
    void supportsOnlyProjectKind() {
        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("정보화사업");
    }

    @Test
    @DisplayName("사업 작성자는 첨부 대상에 쓸 수 있다")
    void ownerCanWrite() {
        givenActiveProject("E001", "18001");

        assertThat(authorizer.canWrite(PRJ, user("E001", "18001"))).isTrue();
    }

    @Test
    @DisplayName("관리자는 부서가 달라도 첨부 대상에 쓸 수 있다")
    void adminCanWrite() {
        givenActiveProject("E001", "18001");

        assertThat(authorizer.canWrite(PRJ, admin())).isTrue();
    }

    @Test
    @DisplayName("주관부서가 같은 기획통할담당자는 첨부 대상에 쓸 수 있다")
    void sameDepartmentManagerCanWrite() {
        givenActiveProject("E001", "18001");

        assertThat(authorizer.canWrite(PRJ, deptManager("E002", "18001"))).isTrue();
    }

    @Test
    @DisplayName("주관부서가 다른 기획통할담당자는 첨부 대상에 쓸 수 없다")
    void otherDepartmentManagerCannotWrite() {
        givenActiveProject("E001", "18001");

        assertThat(authorizer.canWrite(PRJ, deptManager("E002", "29001"))).isFalse();
    }

    @Test
    @DisplayName("사업과 무관한 일반 사용자는 첨부 대상에 쓸 수 없다")
    void unrelatedUserCannotWrite() {
        givenActiveProject("E001", "18001");

        assertThat(authorizer.canWrite(PRJ, user("E999", "18001"))).isFalse();
    }

    @Test
    @DisplayName("관리자도 없거나 공백인 사업 대상에는 쓸 수 없다")
    void adminCannotWriteMissingOrBlankTarget() {
        given(projectRepository.findByAbusMngNoAndDelYn(PRJ, "N")).willReturn(Optional.empty());

        assertThat(authorizer.canWrite(PRJ, admin())).isFalse();
        assertThat(authorizer.canWrite(" ", admin())).isFalse();
        assertThat(authorizer.canWrite(null, admin())).isFalse();
    }

    @Test
    @DisplayName("인증 정보가 없으면 사업을 조회하지 않고 거부한다")
    void anonymousCannotWrite() {
        assertThat(authorizer.canWrite(PRJ, null)).isFalse();

        then(projectRepository).shouldHaveNoInteractions();
    }
}
