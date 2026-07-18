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

    /**
     * 삭제 메뉴를 제외한 관리자용 전체 메뉴 트리를 조회합니다.
     *
     * @return 계층 구조로 정렬된 메뉴 목록
     */
    @GetMapping
    public ResponseEntity<List<MenuDto.Node>> getAll() {
        return ResponseEntity.ok(menuQueryService.getAdminMenuTree());
    }

    /**
     * 메뉴와 메뉴별 권한을 생성합니다.
     *
     * @param req 메뉴 속성과 권한 목록
     * @param uri 생성 리소스 URI 빌더
     * @return 생성된 메뉴 ID와 Location 헤더
     */
    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody MenuDto.UpsertRequest req,
                                         UriComponentsBuilder uri) {
        String mnuId = adminMenuService.create(req);
        URI loc = uri.path("/api/admin/menus/{id}").buildAndExpand(mnuId).toUri();
        return ResponseEntity.created(loc).body(mnuId);
    }

    /**
     * 기존 메뉴 속성과 권한을 수정합니다.
     *
     * @param mnuId 수정할 메뉴 ID
     * @param req 변경할 메뉴 속성과 권한 목록
     * @return 응답 본문 없는 204 응답
     */
    @PutMapping("/{mnuId}")
    public ResponseEntity<Void> update(@PathVariable(name = "mnuId") String mnuId,
                                       @Valid @RequestBody MenuDto.UpsertRequest req) {
        adminMenuService.update(mnuId, req);
        return ResponseEntity.noContent().build();
    }

    /**
     * 메뉴를 논리 삭제합니다. 하위 메뉴가 있거나 메뉴가 없으면 서비스 검증 예외가 발생합니다.
     *
     * @param mnuId 삭제할 메뉴 ID
     * @return 응답 본문 없는 204 응답
     */
    @DeleteMapping("/{mnuId}")
    public ResponseEntity<Void> delete(@PathVariable(name = "mnuId") String mnuId) {
        adminMenuService.delete(mnuId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 같은 계층의 메뉴 노출 순서를 요청 배열 순서대로 변경합니다.
     *
     * @param req 순서대로 정렬된 메뉴 ID 목록
     * @return 응답 본문 없는 204 응답
     */
    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody MenuDto.ReorderRequest req) {
        adminMenuService.reorder(req.getOrderedMnuIds());
        return ResponseEntity.noContent().build();
    }

    /**
     * 메뉴를 새 상위 메뉴 아래로 이동합니다. 순환 계층이면 서비스 검증 예외가 발생합니다.
     *
     * @param mnuId 이동할 메뉴 ID
     * @param req 새 상위 메뉴 ID
     * @return 응답 본문 없는 204 응답
     */
    @PatchMapping("/{mnuId}/move")
    public ResponseEntity<Void> move(@PathVariable(name = "mnuId") String mnuId,
                                     @Valid @RequestBody MenuDto.MoveRequest req) {
        adminMenuService.move(mnuId, req.getNewHrkMnuId());
        return ResponseEntity.noContent().build();
    }
}
