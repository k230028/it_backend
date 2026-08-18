package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.BcostmId;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFormFileReadAuthorizerTest {

    private final ApplicationMapRepository applicationMapRepository =
            mock(ApplicationMapRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final CostRepository costRepository = mock(CostRepository.class);
    private final RequestFormFileReadAuthorizer authorizer =
            new RequestFormFileReadAuthorizer(
                    applicationMapRepository, projectRepository, costRepository);

    private Cfilem file(String apfMngNo) {
        Cfilem f = mock(Cfilem.class);
        when(f.getPkCone()).thenReturn(apfMngNo);
        return f;
    }

    private Cappla map(String fntTbNm, String pk) {
        Cappla cappla = mock(Cappla.class);
        when(cappla.getFntTbNm()).thenReturn(fntTbNm);
        when(cappla.getPkColNm()).thenReturn(pk);
        when(cappla.getFntTbCrySno()).thenReturn(1);
        return cappla;
    }

    private Bprojm project(String svnDpmC) {
        Bprojm bprojm = mock(Bprojm.class);
        when(bprojm.getSvnDpmC()).thenReturn(svnDpmC);
        return bprojm;
    }

    @Test
    @DisplayName("편성요청서반입 종류를 담당한다")
    void supports_requestFormKind() {
        assertThat(authorizer.supportedPkColNms()).containsExactly("편성요청서반입");
    }

    @Test
    @DisplayName("미인증은 읽을 수 없다")
    void anonymous_cannotRead() {
        assertThat(authorizer.canRead(file("APF-1"), null)).isFalse();
    }

    @Test
    @DisplayName("관리자는 원장 조회 없이 읽을 수 있다")
    void admin_canRead() {
        CustomUserDetails admin = new CustomUserDetails("A001", List.of("ITPAD001"), "D99");

        assertThat(authorizer.canRead(mock(Cfilem.class), admin)).isTrue();
    }

    @Test
    @DisplayName("사업 주관부서가 같은 사용자는 읽을 수 있다")
    void sameProjectDepartment_canRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Cappla projectMap = map("BPROJM", "ABUS-1");
        Bprojm project = project("D01");
        given(applicationMapRepository.findByApfDcmNo("APF-1"))
                .willReturn(List.of(projectMap));
        given(projectRepository.findById(new BprojmId("ABUS-1", 1)))
                .willReturn(Optional.of(project));

        assertThat(authorizer.canRead(file("APF-1"), user)).isTrue();
    }

    @Test
    @DisplayName("다른 부점 사용자는 읽을 수 없다")
    void otherDepartment_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E002", List.of("ITPZZ001"), "D02");
        Cappla projectMap = map("BPROJM", "ABUS-1");
        Bprojm project = project("D01");
        given(applicationMapRepository.findByApfDcmNo("APF-1"))
                .willReturn(List.of(projectMap));
        given(projectRepository.findById(new BprojmId("ABUS-1", 1)))
                .willReturn(Optional.of(project));

        assertThat(authorizer.canRead(file("APF-1"), user)).isFalse();
    }

    @Test
    @DisplayName("전산업무비도 담당부서로 판정한다")
    void costDepartment_canRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Bcostm bcostm = mock(Bcostm.class);
        Cappla costMap = map("BCOSTM", "CTT-1");
        when(bcostm.getCostSvnDpmC()).thenReturn("D01");
        given(applicationMapRepository.findByApfDcmNo("APF-2"))
                .willReturn(List.of(costMap));
        given(costRepository.findById(new BcostmId("CTT-1", 1))).willReturn(Optional.of(bcostm));

        assertThat(authorizer.canRead(file("APF-2"), user)).isTrue();
    }

    @Test
    @DisplayName("연결된 원장을 찾지 못하면 읽을 수 없다")
    void missingLedger_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        given(applicationMapRepository.findByApfDcmNo("APF-3")).willReturn(List.of());

        assertThat(authorizer.canRead(file("APF-3"), user)).isFalse();
    }

    @Test
    @DisplayName("부모 신청서번호가 비어 있으면 읽을 수 없다")
    void blankParent_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");

        assertThat(authorizer.canRead(file(null), user)).isFalse();
    }

    @Test
    @DisplayName("파일 엔티티가 없으면 읽을 수 없다")
    void nullFile_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");

        assertThat(authorizer.canRead(null, user)).isFalse();
    }

    @Test
    @DisplayName("사용자 부점이 비어 있으면 읽을 수 없다")
    void blankUserDepartment_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), " ");

        assertThat(authorizer.canRead(file("APF-4"), user)).isFalse();
    }

    @Test
    @DisplayName("미지원 원천테이블 매핑은 읽을 수 없다")
    void unsupportedSource_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Cappla unsupportedMap = map("UNKNOWN", "KEY-1");
        given(applicationMapRepository.findByApfDcmNo("APF-5"))
                .willReturn(List.of(unsupportedMap));

        assertThat(authorizer.canRead(file("APF-5"), user)).isFalse();
    }

    @Test
    @DisplayName("원천테이블명이 없는 매핑은 읽을 수 없다")
    void blankSourceTable_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Cappla incompleteMap = map(null, "ABUS-1");
        given(applicationMapRepository.findByApfDcmNo("APF-6"))
                .willReturn(List.of(incompleteMap));

        assertThat(authorizer.canRead(file("APF-6"), user)).isFalse();
    }

    @Test
    @DisplayName("원천키가 없는 매핑은 읽을 수 없다")
    void blankSourceKey_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Cappla incompleteMap = map("BPROJM", null);
        given(applicationMapRepository.findByApfDcmNo("APF-7"))
                .willReturn(List.of(incompleteMap));

        assertThat(authorizer.canRead(file("APF-7"), user)).isFalse();
    }

    @Test
    @DisplayName("원천순번이 없는 매핑은 읽을 수 없다")
    void missingSourceSerial_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "D01");
        Cappla incompleteMap = map("BPROJM", "ABUS-1");
        when(incompleteMap.getFntTbCrySno()).thenReturn(null);
        given(applicationMapRepository.findByApfDcmNo("APF-8"))
                .willReturn(List.of(incompleteMap));

        assertThat(authorizer.canRead(file("APF-8"), user)).isFalse();
    }
}
