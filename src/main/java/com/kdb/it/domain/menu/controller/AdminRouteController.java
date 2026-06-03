package com.kdb.it.domain.menu.controller;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.service.AdminRouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/routes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "라우트 카탈로그(관리자)", description = "화면경로 CRUD")
public class AdminRouteController {

    private final AdminRouteService adminRouteService;

    @GetMapping
    public ResponseEntity<List<Cmenud>> usable() {
        return ResponseEntity.ok(adminRouteService.listUsable());
    }

    @GetMapping("/all")
    public ResponseEntity<List<Cmenud>> all() {
        return ResponseEntity.ok(adminRouteService.listAll());
    }

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody MenuDto.Route req) {
        adminRouteService.create(req);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    public ResponseEntity<Void> update(@Valid @RequestBody MenuDto.Route req) {
        adminRouteService.update(req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam(name = "srePth") String srePth) {
        adminRouteService.delete(srePth);
        return ResponseEntity.noContent().build();
    }
}
