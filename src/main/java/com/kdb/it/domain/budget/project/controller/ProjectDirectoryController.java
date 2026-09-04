package com.kdb.it.domain.budget.project.controller;

import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.service.ProjectDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 전 직원의 사업 검색과 접근 제한 담당자 안내 API입니다. */
@RestController
@RequestMapping("/api/project-directory")
@RequiredArgsConstructor
@Tag(name = "Project Directory", description = "전 직원 사업 검색용 안전한 요약 API")
public class ProjectDirectoryController {

    private final ProjectDirectoryService projectDirectoryService;

    /** 모든 활성 사업의 안전한 검색 요약을 반환합니다. */
    @GetMapping
    @Operation(summary = "사업 검색 디렉터리 조회")
    public List<ProjectDirectoryDto.Response> getDirectory() {
        return projectDirectoryService.findAll();
    }

    /** 접근 제한 화면에 표시할 사업 담당 부서·담당자 요약을 반환합니다. */
    @GetMapping("/{abusMngNo}")
    @Operation(summary = "사업 담당자 안내 조회")
    public ProjectDirectoryDto.Response getDirectoryEntry(
            @PathVariable("abusMngNo") String abusMngNo) {
        return projectDirectoryService.findOne(abusMngNo);
    }
}
