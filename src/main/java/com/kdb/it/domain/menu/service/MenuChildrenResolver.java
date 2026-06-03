package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import java.util.List;

/** DYN 메뉴의 children을 MNU_ID 기준으로 생성하는 SPI. Plan 2에서 게시판 어댑터 등록. */
public interface MenuChildrenResolver {
    /** 이 resolver가 담당하는 DYN 노드의 MNU_ID. */
    String mnuId();
    /** 현재 사용자(athIds) 기준으로 권한 필터링된 children 노드. */
    List<MenuDto.Node> resolveChildren(List<String> athIds);
}
