package com.kdb.it.domain.menu.controller;

import com.kdb.it.common.i18n.model.SupportedLanguage;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.service.MenuQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 메뉴 조회 REST 컨트롤러.
 *
 * <p>기본 URL: {@code /api/menus}. 권한 필터링된 메뉴 트리를 반환하며, 프론트의 사이드바·Breadcrumb 단일 소스로 사용된다. 인증된 사용자라면
 * 접근 가능하고, 노드별 노출 권한은 서비스 계층에서 필터링한다.
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@Tag(name = "메뉴 조회", description = "사이드바·Breadcrumb 공용 메뉴 트리")
public class MenuQueryController {

    private final MenuQueryService menuQueryService;

    /**
     * 현재 인증 사용자의 자격등급으로 필터링된 메뉴 트리를 조회한다.
     *
     * @param user JWT에서 복원된 사용자 정보. 비로그인 요청이면 빈 권한 목록으로 공개 메뉴만 조회한다.
     * @return 사이드바와 Breadcrumb에서 함께 사용하는 메뉴 노드 목록
     */
    @GetMapping
    public ResponseEntity<List<MenuDto.Node>> getMenus(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(name = "lang", required = false) String lang) {
        List<String> athIds = user == null ? List.of() : user.getAthIds();
        return ResponseEntity.ok(
                menuQueryService.getMenuTree(athIds, SupportedLanguage.normalize(lang)));
    }
}
