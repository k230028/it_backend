package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewCommentFileWriteAuthorizerTest {

    private final BrivgmRepository commentRepository = mock(BrivgmRepository.class);
    private final ReviewCommentFileWriteAuthorizer authorizer =
            new ReviewCommentFileWriteAuthorizer(commentRepository);

    private Cfilem file(String commentId, String uploader) {
        return Cfilem.builder()
                .flMpnId("FL-1")
                .apgFlKdNm(ReviewCommentFileWriteAuthorizer.REVIEW_COMMENT_KIND)
                .apgFlLnkCtzNm(commentId)
                .fstEnrUsid(uploader)
                .build();
    }

    private Brivgm comment(String author) {
        Brivgm comment = Brivgm.create("DOC-1", new BigDecimal("101"), "G", "검토의견", null, null);
        ReflectionTestUtils.setField(comment, "fstEnrUsid", author);
        return comment;
    }

    private CustomUserDetails user(String eno) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), "D001");
    }

    @Test
    @DisplayName("파일 업로더가 달라도 검토의견 작성자는 쓸 수 있다")
    void commentAuthorCanWriteWhenUploaderIsDifferent() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment("AUTHOR")));

        assertThat(authorizer.canWrite(file("101", "UPLOADER"), user("AUTHOR"))).isTrue();
    }

    @Test
    @DisplayName("파일 업로더라도 검토의견 작성자가 아니면 쓸 수 없다")
    void uploaderCannotWriteWhenNotCommentAuthor() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment("AUTHOR")));

        assertThat(authorizer.canWrite(file("101", "UPLOADER"), user("UPLOADER"))).isFalse();
    }

    @Test
    @DisplayName("활성 검토의견이 존재하면 관리자는 쓸 수 있다")
    void adminCanWriteExistingComment() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment("AUTHOR")));
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D999");

        assertThat(authorizer.canWrite(file("101", "UPLOADER"), admin)).isTrue();
    }

    @Test
    @DisplayName("관리자도 존재하지 않는 검토의견 부모에는 쓸 수 없다")
    void adminCannotWriteUnknownComment() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(404L, "N")).willReturn(Optional.empty());
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D999");

        assertThat(authorizer.canWrite(file("404", "UPLOADER"), admin)).isFalse();
    }

    @Test
    @DisplayName("null·숫자가 아닌 부모와 비인증 사용자는 조회 없이 거부한다")
    void invalidParentOrAnonymousIsDeniedWithoutLookup() {
        assertThat(authorizer.canWrite(file(null, "UPLOADER"), user("AUTHOR"))).isFalse();
        assertThat(authorizer.canWrite(file("not-a-number", "UPLOADER"), user("AUTHOR"))).isFalse();
        assertThat(authorizer.canWrite(file("101", "UPLOADER"), null)).isFalse();

        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("대상 부모 판정도 활성 검토의견 작성자 또는 관리자만 허용한다")
    void targetWriteUsesActiveCommentAuthor() {
        given(commentRepository.findByIpmOpnnSnoAndDelYn(101L, "N"))
                .willReturn(Optional.of(comment("AUTHOR")));

        assertThat(authorizer.canWrite("101", user("AUTHOR"))).isTrue();
        assertThat(authorizer.canWrite("101", user("OTHER"))).isFalse();
    }

    @Test
    @DisplayName("대상 부모가 없거나 숫자가 아니면 관리자도 거부한다")
    void targetWriteRejectsMissingOrMalformedParentForAdmin() {
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D999");
        given(commentRepository.findByIpmOpnnSnoAndDelYn(404L, "N")).willReturn(Optional.empty());

        assertThat(authorizer.canWrite("404", admin)).isFalse();
        assertThat(authorizer.canWrite("not-a-number", admin)).isFalse();
        assertThat(authorizer.canWrite((String) null, admin)).isFalse();
    }
}
