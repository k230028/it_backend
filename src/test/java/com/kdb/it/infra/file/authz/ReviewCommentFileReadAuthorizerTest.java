package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewCommentFileReadAuthorizerTest {

    private final BrivgmRepository commentRepository = mock(BrivgmRepository.class);
    private final ServiceRequestDocRepository docRepository =
            mock(ServiceRequestDocRepository.class);
    private final ReviewCommentFileReadAuthorizer authorizer =
            new ReviewCommentFileReadAuthorizer(commentRepository, docRepository);

    private Cfilem file(String commentId) {
        Cfilem file = mock(Cfilem.class);
        given(file.getPkCone()).willReturn(commentId);
        return file;
    }

    private Brivgm comment(String owner, String docMngNo, String storedVersion) {
        Brivgm comment =
                Brivgm.create(docMngNo, new BigDecimal(storedVersion), "G", "검토의견", null, null);
        ReflectionTestUtils.setField(comment, "fstEnrUsid", owner);
        return comment;
    }

    private Brdocm doc(String owner, String leadDepartment, String storedVersion) {
        return Brdocm.builder()
                .docMngNo("DOC-1")
                .docVrsSno(new BigDecimal(storedVersion))
                .fstEnrUsid(owner)
                .svnDpmC(leadDepartment)
                .build();
    }

    private CustomUserDetails user(String eno, String department) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), department);
    }

    @Test
    @DisplayName("검토의견 파일 종류를 담당한다")
    void supportsReviewComment() {
        assertThat(authorizer.supportedPkColNms()).containsExactly("검토의견");
    }

    @Test
    @DisplayName("유효한 검토의견 부모를 확인한 관리자는 읽을 수 있다")
    void adminCanReadAfterParentLookup() {
        Brivgm comment = comment("E001", "DOC-1", "101");
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment));
        CustomUserDetails admin =
                new CustomUserDetails("A001", List.of(CustomUserDetails.ATH_ADMIN), "OTHER");

        assertThat(authorizer.canRead(file("101"), admin)).isTrue();

        verify(commentRepository).findByIpmOpnnSnoAndDelYn(101L, "N");
        verifyNoInteractions(docRepository);
    }

    @Test
    @DisplayName("관리자도 존재하지 않는 검토의견 부모는 읽을 수 없다")
    void adminCannotReadUnknownParent() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(404L, "N")).willReturn(Optional.empty());
        CustomUserDetails admin =
                new CustomUserDetails("A001", List.of(CustomUserDetails.ATH_ADMIN), "OTHER");

        assertThat(authorizer.canRead(file("404"), admin)).isFalse();

        verify(commentRepository).findByIpmOpnnSnoAndDelYn(404L, "N");
        verifyNoInteractions(docRepository);
    }

    @Test
    @DisplayName("관리자도 null 또는 숫자가 아닌 검토의견 부모는 읽을 수 없다")
    void adminCannotReadMissingOrMalformedParent() {
        CustomUserDetails admin =
                new CustomUserDetails("A001", List.of(CustomUserDetails.ATH_ADMIN), "OTHER");

        assertThat(authorizer.canRead(file(null), admin)).isFalse();
        assertThat(authorizer.canRead(file("not-a-number"), admin)).isFalse();

        verifyNoInteractions(commentRepository, docRepository);
    }

    @Test
    @DisplayName("검토의견 작성자는 문서 조회 없이 읽을 수 있다")
    void commentAuthorCanReadWithoutDocumentLookup() {
        Brivgm comment = comment("E001", "DOC-1", "101");
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment));

        assertThat(authorizer.canRead(file("101"), user("E001", "OTHER"))).isTrue();

        verifyNoInteractions(docRepository);
    }

    @Test
    @DisplayName("검토의견의 정확한 문서 버전 주관부서 사용자는 읽을 수 있다")
    void leadDepartmentCanReadExactDocumentVersion() {
        Brivgm comment = comment("E001", "DOC-1", "101");
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment));
        given(docRepository.findByDocMngNoAndDocVrsSnoAndDelYn("DOC-1", new BigDecimal("101"), "N"))
                .willReturn(Optional.of(doc("E999", "LEAD", "101")));

        assertThat(authorizer.canRead(file("101"), user("E002", "LEAD"))).isTrue();

        verify(docRepository)
                .findByDocMngNoAndDocVrsSnoAndDelYn("DOC-1", new BigDecimal("101"), "N");
    }

    @Test
    @DisplayName("작성자도 정확한 문서 버전 주관부서도 아닌 사용자는 읽을 수 없다")
    void unrelatedUserCannotRead() {
        Brivgm comment = comment("E001", "DOC-1", "101");
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment));
        given(docRepository.findByDocMngNoAndDocVrsSnoAndDelYn("DOC-1", new BigDecimal("101"), "N"))
                .willReturn(Optional.of(doc("E001", "LEAD", "101")));

        assertThat(authorizer.canRead(file("101"), user("E999", "OTHER"))).isFalse();
    }

    @Test
    @DisplayName("부모가 없거나 숫자가 아니거나 댓글이 없으면 안전하게 거부한다")
    void missingMalformedOrUnknownParentIsDeniedSafely() {
        assertThat(authorizer.canRead(file(null), user("E001", "LEAD"))).isFalse();
        assertThat(authorizer.canRead(file("not-a-number"), user("E001", "LEAD"))).isFalse();
        given(commentRepository.findByIpmOpnnSnoAndDelYn(404L, "N")).willReturn(Optional.empty());
        assertThat(authorizer.canRead(file("404"), user("E001", "LEAD"))).isFalse();

        verifyNoInteractions(docRepository);
    }

    @Test
    @DisplayName("비인증 사용자는 부모를 조회하지 않고 거부한다")
    void anonymousIsDeniedWithoutLookup() {
        assertThat(authorizer.canRead(file("101"), null)).isFalse();

        verifyNoInteractions(commentRepository, docRepository);
    }
}
