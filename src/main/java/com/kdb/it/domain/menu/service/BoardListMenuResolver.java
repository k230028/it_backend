package com.kdb.it.domain.menu.service;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 활성 게시판 전체를 동적 게시판 노드(MBRD0001)의 하위 메뉴로 생성합니다. */
@Component
@RequiredArgsConstructor
public class BoardListMenuResolver implements MenuChildrenResolver {

    /** 시드의 게시판 동적 노드 MNU_ID (V20260603_008 시드와 일치해야 함). */
    private static final String BOARD_DYNAMIC_MNU_ID = "MBRD0001";

    /** 게시판 최상위 그룹 MNU_ID. 마이그레이션 006 시드와 일치해야 함. */
    private static final String BOARD_ROOT_GROUP_MNU_ID = "MHED0006";

    private final BoardMetaService boardMetaService;

    @Override
    public String mnuId() {
        return BOARD_DYNAMIC_MNU_ID;
    }

    @Override
    public List<MenuDto.Node> resolveChildren(List<String> athIds) {
        // 게시판 조회는 인증된 모든 사용자에게 공개되므로 athIds와 관계없이 활성 게시판 전체를 노출합니다.
        return boardMetaService.getAllActive().stream().map(this::toNode).toList();
    }

    private MenuDto.Node toNode(BoardMetaDto.Response b) {
        String childMnuId = "MBRD-" + b.getBlbMngNo();
        return MenuDto.Node.builder()
                .mnuId(childMnuId)
                .hrkMnuId(BOARD_DYNAMIC_MNU_ID)
                .mnuNm(b.getBlbNm())
                .mnuTpC("PGE")
                .srePth("/board/" + b.getBlbMngNo())
                .mnuDep(3)
                // Breadcrumb가 조상(게시판 최상위 그룹·동적 그룹)을 해석하도록 전체 경로를 채운다.
                .whlMnuPth(
                        "/"
                                + BOARD_ROOT_GROUP_MNU_ID
                                + "/"
                                + BOARD_DYNAMIC_MNU_ID
                                + "/"
                                + childMnuId)
                // 가변 리스트 필수: MenuQueryService.sortRecursive가 children을 in-place 정렬한다.
                .children(new ArrayList<>())
                .build();
    }
}
