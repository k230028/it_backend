package com.kdb.it.domain.menu.controller;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.service.AdminMenuService;
import com.kdb.it.domain.menu.service.MenuQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 관리자 메뉴 관리 REST 컨트롤러.
 *
 * <p>기본 URL: {@code /api/admin/menus}. DB 기반 메뉴 트리의 CRUD·정렬·이동(reparent)을 제공한다.
 * 클래스 레벨 {@code @PreAuthorize("hasRole('ADMIN')")}로 관리자 전용이다.</p>
 */
@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "메뉴 관리(관리자)", description = "메뉴 CRUD·정렬·이동")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;
    private final MenuQueryService menuQueryService;

    @GetMapping
    public ResponseEntity<List<MenuDto.Node>> getAll() {
        return ResponseEntity.ok(menuQueryService.getAdminMenuTree());
    }

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody MenuDto.UpsertRequest req,
                                         UriComponentsBuilder uri) {
        String mnuId = adminMenuService.create(req);
        URI loc = uri.path("/api/admin/menus/{id}").buildAndExpand(mnuId).toUri();
        return ResponseEntity.created(loc).body(mnuId);
    }

    @PutMapping("/{mnuId}")
    public ResponseEntity<Void> update(@PathVariable(name = "mnuId") String mnuId,
                                       @Valid @RequestBody MenuDto.UpsertRequest req) {
        adminMenuService.update(mnuId, req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{mnuId}")
    public ResponseEntity<Void> delete(@PathVariable(name = "mnuId") String mnuId) {
        adminMenuService.delete(mnuId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody MenuDto.ReorderRequest req) {
        adminMenuService.reorder(req.getOrderedMnuIds());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{mnuId}/move")
    public ResponseEntity<Void> move(@PathVariable(name = "mnuId") String mnuId,
                                     @RequestBody MenuDto.MoveRequest req) {
        adminMenuService.move(mnuId, req.getNewHrkMnuId());
        return ResponseEntity.noContent().build();
    }
}
