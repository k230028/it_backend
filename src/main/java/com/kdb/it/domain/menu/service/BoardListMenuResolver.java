package com.kdb.it.domain.menu.service;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** DYN 게시판 노드(MBRD0001)의 children을 서버 단에서 권한 필터링하여 생성. */
@Component
@RequiredArgsConstructor
public class BoardListMenuResolver implements MenuChildrenResolver {

    /** 시드의 게시판 DYN 노드 MNU_ID (V20260603_008 시드와 일치해야 함). */
    private static final String BOARD_DYN_MNU_ID = "MBRD0001";
    private static final String ADMIN_ATH_ID = "ITPAD001";

    private final BoardMetaService boardMetaService;

    @Override
    public String mnuId() { return BOARD_DYN_MNU_ID; }

    @Override
    public List<MenuDto.Node> resolveChildren(List<String> athIds) {
        boolean isAdmin = athIds != null && athIds.contains(ADMIN_ATH_ID);
        return boardMetaService.getAllActive().stream()
                .filter(b -> "ALL".equals(b.getInqAthC()) || isAdmin)
                .map(this::toNode)
                .collect(Collectors.toList());
    }

    private MenuDto.Node toNode(BoardMetaDto.Response b) {
        return MenuDto.Node.builder()
                .mnuId("MBRD-" + b.getBlbMngNo())
                .hrkMnuId(BOARD_DYN_MNU_ID)
                .sreTc("04")
                .mnuNm(b.getBlbNm())
                .mnuTpC("LNK")
                .srePth("/board/" + b.getBlbMngNo())
                .mnuDep(2)
                // 가변 리스트 필수: MenuQueryService.sortRecursive가 children을 in-place 정렬한다.
                .children(new ArrayList<>())
                .build();
    }
}
