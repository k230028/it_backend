package com.kdb.it.domain.menu.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.service.MenuQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@Tag(name = "메뉴 조회", description = "사이드바·Breadcrumb 공용 메뉴 트리")
public class MenuQueryController {

    private final MenuQueryService menuQueryService;

    @GetMapping
    public ResponseEntity<List<MenuDto.Node>> getMenus(
            @AuthenticationPrincipal CustomUserDetails user) {
        List<String> athIds = user == null ? List.of() : user.getAthIds();
        return ResponseEntity.ok(menuQueryService.getMenuTree(athIds));
    }
}
