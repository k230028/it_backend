package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import java.util.List;

/** 동적 메뉴의 하위 노드를 메뉴 ID와 사용자 권한 기준으로 생성하는 확장 지점입니다. */
public interface MenuChildrenResolver {
    /** 이 resolver가 담당하는 동적 확장 대상 노드의 MNU_ID. */
    String mnuId();

    /** 현재 사용자(athIds) 기준으로 권한 필터링된 children 노드. */
    List<MenuDto.Node> resolveChildren(List<String> athIds);
}
