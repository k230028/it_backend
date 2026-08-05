package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BoardListMenuResolver 단위 테스트
 *
 * <p>게시판 동적 노드(MBRD0001)의 children을 게시판 목록으로 변환하는 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoardListMenuResolverTest {

    @Mock private BoardMetaService boardMetaService;

    @InjectMocks private BoardListMenuResolver resolver;

    /** 테스트용 게시판 메타 응답 DTO 생성 헬퍼. */
    private BoardMetaDto.Response board(String blbMngNo, String blbNm) {
        return BoardMetaDto.Response.builder().blbMngNo(blbMngNo).blbNm(blbNm).build();
    }

    @Test
    @DisplayName("mnuId: MBRD0001을 반환한다")
    void mnuId_returnsBoardDynMnuId() {
        // when & then
        assertThat(resolver.mnuId()).isEqualTo("MBRD0001");
    }

    @Test
    @DisplayName("resolveChildren: 활성 게시판이 없으면 빈 목록을 반환한다")
    void resolveChildren_빈게시판목록_빈노드목록반환() {
        // given
        given(boardMetaService.getAllActive()).willReturn(List.of());

        // when
        List<MenuDto.Node> result = resolver.resolveChildren(List.of("ITPZZ001"));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resolveChildren: 게시판 목록을 PGE 노드로 변환한다")
    void resolveChildren_게시판목록_노드변환() {
        // given
        given(boardMetaService.getAllActive())
                .willReturn(List.of(board("BLB-2026-0001", "공지사항"), board("BLB-2026-0002", "자료실")));

        // when
        List<MenuDto.Node> result = resolver.resolveChildren(List.of("ITPZZ001"));

        // then: 게시판 수만큼 노드 생성
        assertThat(result).hasSize(2);

        MenuDto.Node first = result.get(0);
        assertThat(first.getMnuId()).isEqualTo("MBRD-BLB-2026-0001");
        assertThat(first.getHrkMnuId()).isEqualTo("MBRD0001");
        assertThat(first.getMnuNm()).isEqualTo("공지사항");
        assertThat(first.getMnuTpC()).isEqualTo("PGE");
        assertThat(first.getSrePth()).isEqualTo("/board/BLB-2026-0001");
        assertThat(first.getMnuDep()).isEqualTo(3);
        assertThat(first.getWhlMnuPth()).isEqualTo("/MHED0006/MBRD0001/MBRD-BLB-2026-0001");
        // children은 빈 가변 리스트여야 한다 (정렬/가지치기에서 in-place 변형 가능)
        assertThat(first.getChildren()).isNotNull().isEmpty();

        MenuDto.Node second = result.get(1);
        assertThat(second.getMnuId()).isEqualTo("MBRD-BLB-2026-0002");
        assertThat(second.getMnuNm()).isEqualTo("자료실");
    }

    @Test
    @DisplayName("resolveChildren: athIds와 무관하게 모든 활성 게시판을 반환한다")
    void resolveChildren_권한무관_전체게시판반환() {
        // given: 모든 인증 사용자에게 게시판 전체 공개
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLB-001", "테스트게시판")));

        // when: 관리자 권한으로 조회
        List<MenuDto.Node> adminResult = resolver.resolveChildren(List.of("ITPAD001"));

        // then
        assertThat(adminResult).hasSize(1);
        assertThat(adminResult.get(0).getHrkMnuId()).isEqualTo("MBRD0001");
        assertThat(adminResult.get(0).getMnuDep()).isEqualTo(3);
    }
}
