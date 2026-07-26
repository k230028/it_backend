package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardFileTargetWriteAuthorizerTest {

    private final BoardPostRepository boardPostRepository = mock(BoardPostRepository.class);
    private final BoardFileTargetWriteAuthorizer authorizer =
            new BoardFileTargetWriteAuthorizer(boardPostRepository);

    private CustomUserDetails user(String eno) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), "D001");
    }

    private Cblbcm post(String author) {
        return Cblbcm.builder().nacMngNo("NAC-2026-0001").fstEnrUsid(author).build();
    }

    @Test
    @DisplayName("활성 게시물 작성자는 첨부 대상에 쓸 수 있다")
    void authorCanWriteActiveBoardTarget() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post("AUTHOR")));

        assertThat(authorizer.canWrite("NAC-2026-0001", user("AUTHOR"))).isTrue();
    }

    @Test
    @DisplayName("활성 게시물이 확인된 관리자는 첨부 대상에 쓸 수 있다")
    void adminCanWriteActiveBoardTarget() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post("AUTHOR")));
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D999");

        assertThat(authorizer.canWrite("NAC-2026-0001", admin)).isTrue();
    }

    @Test
    @DisplayName("게시물 작성자가 아닌 일반 사용자는 첨부 대상에 쓸 수 없다")
    void unrelatedUserCannotWriteBoardTarget() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post("AUTHOR")));

        assertThat(authorizer.canWrite("NAC-2026-0001", user("OTHER"))).isFalse();
    }

    @Test
    @DisplayName("관리자도 없거나 공백인 게시물 대상에는 쓸 수 없다")
    void adminCannotWriteMissingOrBlankBoardTarget() {
        CustomUserDetails admin =
                new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D999");
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-9999", "N"))
                .willReturn(Optional.empty());

        assertThat(authorizer.canWrite("NAC-2026-9999", admin)).isFalse();
        assertThat(authorizer.canWrite(" ", admin)).isFalse();
        assertThat(authorizer.canWrite(null, admin)).isFalse();
    }
}
