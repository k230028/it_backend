package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequirementDocFileReadAuthorizerTest {

    private final ServiceRequestDocRepository docRepository = mock(ServiceRequestDocRepository.class);
    private final RequirementDocFileReadAuthorizer authorizer =
            new RequirementDocFileReadAuthorizer(docRepository);

    private Cfilem file(String docMngNo) {
        Cfilem f = mock(Cfilem.class);
        when(f.getPkCone()).thenReturn(docMngNo);
        return f;
    }

    private Brdocm doc(String owner, String svnDpmC) {
        // BaseEntity 상속 엔티티는 목킹 대신 실제 빌더로 생성한다(BoardFileReadAuthorizerTest와 동일 패턴).
        return Brdocm.builder()
                .fstEnrUsid(owner)
                .svnDpmC(svnDpmC)
                .build();
    }

    @Test
    @DisplayName("요구사항정의서 종류를 담당한다")
    void supports_requirementDoc() {
        assertThat(authorizer.supportedPkColNms()).containsExactly("요구사항정의서");
    }

    @Test
    @DisplayName("관리자는 문서 조회 없이 읽기 가능")
    void admin_canRead() {
        CustomUserDetails admin = new CustomUserDetails("A001", List.of("ITPAD001"), "IT001");
        assertThat(authorizer.canRead(mock(Cfilem.class), admin)).isTrue();
    }

    @Test
    @DisplayName("작성자 본인은 읽기 가능")
    void owner_canRead() {
        given(docRepository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-1", "N"))
                .willReturn(Optional.of(doc("E001", "DEPT-A")));
        CustomUserDetails owner = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-B");
        assertThat(authorizer.canRead(file("DOC-1"), owner)).isTrue();
    }

    @Test
    @DisplayName("주관부서 동일 사용자는 읽기 가능")
    void sameDept_canRead() {
        given(docRepository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-1", "N"))
                .willReturn(Optional.of(doc("E001", "DEPT-A")));
        CustomUserDetails sameDept = new CustomUserDetails("E999", List.of("ITPZZ001"), "DEPT-A");
        assertThat(authorizer.canRead(file("DOC-1"), sameDept)).isTrue();
    }

    @Test
    @DisplayName("타부서·타인은 읽기 불가")
    void otherDeptOther_cannotRead() {
        given(docRepository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-1", "N"))
                .willReturn(Optional.of(doc("E001", "DEPT-A")));
        CustomUserDetails other = new CustomUserDetails("E999", List.of("ITPZZ001"), "DEPT-B");
        assertThat(authorizer.canRead(file("DOC-1"), other)).isFalse();
    }

    @Test
    @DisplayName("문서가 없으면 읽기 불가")
    void docNotFound_cannotRead() {
        given(docRepository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-X", "N"))
                .willReturn(Optional.empty());
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-A");
        assertThat(authorizer.canRead(file("DOC-X"), user)).isFalse();
    }

    @Test
    @DisplayName("부모 ID가 없으면 읽기 불가")
    void nullParent_cannotRead() {
        CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "DEPT-A");
        assertThat(authorizer.canRead(file(null), user)).isFalse();
    }
}
