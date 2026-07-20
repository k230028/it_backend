package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardFileReadAuthorizerTest {

    private final BoardPostRepository boardPostRepository = mock(BoardPostRepository.class);
    private final BoardFileReadAuthorizer authorizer = new BoardFileReadAuthorizer(boardPostRepository);

    private final CustomUserDetails normalUser = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
    private final CustomUserDetails adminUser = new CustomUserDetails("A001", List.of("ITPAD001"), "IT001");

    private Cfilem boardFile(String nacMngNo) {
        Cfilem file = mock(Cfilem.class);
        when(file.getPkCone()).thenReturn(nacMngNo);
        return file;
    }

    private Cblbcm post(String sreYn, LocalDate stt, LocalDate end) {
        return Cblbcm.builder()
                .nacMngNo("NAC-1").blbMngNo("BLBM-1").nacNm("게시물")
                .sreYn(sreYn).sttDt(stt).endDt(end)
                .nacInqNbr(0).flNbr(0).flApgYn("N").ancYn("N")
                .nacUnqId("NAC-1").nacGrpSqn(0).nacGrpLev(0).delYn("N")
                .build();
    }

    @Test
    @DisplayName("공통게시판 종류를 담당한다")
    void supports_board() {
        assertThat(authorizer.supportedPkColNms()).containsExactly("공통게시판");
    }

    @Test
    @DisplayName("관리자는 게시물 조회 없이 읽기 가능")
    void admin_canRead() {
        assertThat(authorizer.canRead(mock(Cfilem.class), adminUser)).isTrue();
    }

    @Test
    @DisplayName("게시물이 없으면 읽기 불가")
    void postNotFound_cannotRead() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-1", "N")).willReturn(Optional.empty());
        assertThat(authorizer.canRead(boardFile("NAC-1"), normalUser)).isFalse();
    }

    @Test
    @DisplayName("비공개(sreYn=N) 게시물은 읽기 불가")
    void hiddenPost_cannotRead() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-1", "N"))
                .willReturn(Optional.of(post("N", null, null)));
        assertThat(authorizer.canRead(boardFile("NAC-1"), normalUser)).isFalse();
    }

    @Test
    @DisplayName("공개중(sreYn=Y, 기간 내) 게시물은 읽기 가능")
    void visiblePost_canRead() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-1", "N"))
                .willReturn(Optional.of(post("Y", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        assertThat(authorizer.canRead(boardFile("NAC-1"), normalUser)).isTrue();
    }

    @Test
    @DisplayName("공개 시작 전(sttDt 미래) 게시물은 읽기 불가")
    void notStartedPost_cannotRead() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-1", "N"))
                .willReturn(Optional.of(post("Y", LocalDate.now().plusDays(1), null)));
        assertThat(authorizer.canRead(boardFile("NAC-1"), normalUser)).isFalse();
    }

    @Test
    @DisplayName("공개 종료(endDt 과거) 게시물은 읽기 불가")
    void expiredPost_cannotRead() {
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-1", "N"))
                .willReturn(Optional.of(post("Y", null, LocalDate.now().minusDays(1))));
        assertThat(authorizer.canRead(boardFile("NAC-1"), normalUser)).isFalse();
    }

    @Test
    @DisplayName("비인증 사용자와 부모 ID 없는 파일은 읽기 불가")
    void unauthenticatedOrNullParent_cannotRead() {
        assertThat(authorizer.canRead(boardFile("NAC-1"), null)).isFalse();
        assertThat(authorizer.canRead(boardFile(null), normalUser)).isFalse();
    }
}
