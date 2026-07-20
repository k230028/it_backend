package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouncilFileReadAuthorizerTest {

    private final CouncilRepository councilRepository = mock(CouncilRepository.class);
    private final CommitteeRepository committeeRepository = mock(CommitteeRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final CouncilFileReadAuthorizer authorizer =
            new CouncilFileReadAuthorizer(councilRepository, committeeRepository, projectRepository);

    private Cfilem file(String asctId) {
        Cfilem f = mock(Cfilem.class);
        when(f.getPkCone()).thenReturn(asctId);
        return f;
    }

    @Test
    @DisplayName("세 협의회 종류를 담당한다")
    void supports_threeCouncilKinds() {
        assertThat(authorizer.supportedPkColNms())
                .containsExactlyInAnyOrder("사업계획서", "타당성검토표", "협의회관련자료");
    }

    @Test
    @DisplayName("정보보안관리자는 위원·부서 조회 없이 읽기 가능")
    void infoSecAdmin_canRead() {
        CustomUserDetails infosec = new CustomUserDetails("S001", List.of("ITPAD002"), "IT001");
        assertThat(authorizer.canRead(mock(Cfilem.class), infosec)).isTrue();
    }

    @Test
    @DisplayName("해당 협의회 위원은 읽기 가능")
    void committeeMember_canRead() {
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-1", "E001", "N"))
                .willReturn(Optional.of(mock(Bcmmtm.class)));
        CustomUserDetails member = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-B");
        assertThat(authorizer.canRead(file("ASCT-1"), member)).isTrue();
    }

    @Test
    @DisplayName("위원이 아니어도 협의회 사업 주관부서와 같은 부서면 읽기 가능")
    void sameProjectDept_canRead() {
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-1", "E001", "N"))
                .willReturn(Optional.empty());
        Basctm council = mock(Basctm.class);
        when(council.getAbusMngNo()).thenReturn("ABUS-1");
        when(council.getSno()).thenReturn(1);
        given(councilRepository.findByItPtlAsctIdAndDelYn("ASCT-1", "N")).willReturn(Optional.of(council));
        Bprojm project = mock(Bprojm.class);
        when(project.getSvnDpmC()).thenReturn("DEPT-A");
        given(projectRepository.findById(new BprojmId("ABUS-1", 1))).willReturn(Optional.of(project));
        CustomUserDetails sameDept = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-A");
        assertThat(authorizer.canRead(file("ASCT-1"), sameDept)).isTrue();
    }

    @Test
    @DisplayName("위원도 아니고 타부서면 읽기 불가")
    void nonMemberOtherDept_cannotRead() {
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-1", "E001", "N"))
                .willReturn(Optional.empty());
        Basctm council = mock(Basctm.class);
        when(council.getAbusMngNo()).thenReturn("ABUS-1");
        when(council.getSno()).thenReturn(1);
        given(councilRepository.findByItPtlAsctIdAndDelYn("ASCT-1", "N")).willReturn(Optional.of(council));
        Bprojm project = mock(Bprojm.class);
        when(project.getSvnDpmC()).thenReturn("DEPT-A");
        given(projectRepository.findById(new BprojmId("ABUS-1", 1))).willReturn(Optional.of(project));
        CustomUserDetails other = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-B");
        assertThat(authorizer.canRead(file("ASCT-1"), other)).isFalse();
    }

    @Test
    @DisplayName("부모 ID가 없으면 읽기 불가")
    void nullParent_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-A");
        assertThat(authorizer.canRead(file(null), user)).isFalse();
    }
}
