package com.kdb.it.domain.menu.controller;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.service.AdminRouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 경로 카탈로그 REST 컨트롤러.
 *
 * <p>기본 URL: {@code /api/admin/routes}. PGE·LNK 메뉴가 참조하는 경로 카탈로그(Cmenud)의 CRUD를 제공한다. 클래스 레벨
 * {@code @PreAuthorize("hasRole('ADMIN')")}로 관리자 전용이다.
 */
@RestController
@RequestMapping("/api/admin/routes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "경로 관리(관리자)", description = "PGE·LNK 메뉴 참조 경로 카탈로그 CRUD")
public class AdminRouteController {

    private final AdminRouteService adminRouteService;

    /**
     * 메뉴에 연결할 수 있는 사용 가능 라우트를 조회합니다.
     *
     * @return 사용 가능 라우트 목록
     */
    @GetMapping
    public ResponseEntity<List<Cmenud>> usable() {
        return ResponseEntity.ok(adminRouteService.listUsable());
    }

    /**
     * 사용 여부와 관계없이 전체 라우트 카탈로그를 조회합니다.
     *
     * @return 전체 라우트 목록
     */
    @GetMapping("/all")
    public ResponseEntity<List<Cmenud>> all() {
        return ResponseEntity.ok(adminRouteService.listAll());
    }

    /**
     * 라우트 카탈로그 항목을 생성합니다. 경로가 중복되면 서비스 검증 예외가 발생합니다.
     *
     * @param req 생성할 화면 경로와 설명
     * @return 응답 본문 없는 204 응답
     */
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody MenuDto.Route req) {
        adminRouteService.create(req);
        return ResponseEntity.noContent().build();
    }

    /**
     * 라우트 카탈로그 항목을 수정합니다. 대상 경로가 없으면 서비스 검증 예외가 발생합니다.
     *
     * @param req 수정할 화면 경로와 설명
     * @return 응답 본문 없는 204 응답
     */
    @PutMapping
    public ResponseEntity<Void> update(@Valid @RequestBody MenuDto.Route req) {
        adminRouteService.update(req);
        return ResponseEntity.noContent().build();
    }

    /**
     * 라우트 카탈로그 항목을 삭제합니다. 메뉴가 사용 중이면 서비스 검증 예외가 발생합니다.
     *
     * @param srePth 삭제할 화면 경로
     * @return 응답 본문 없는 204 응답
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam(name = "srePth") String srePth) {
        adminRouteService.delete(srePth);
        return ResponseEntity.noContent().build();
    }
}
