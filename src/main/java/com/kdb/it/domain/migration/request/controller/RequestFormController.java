package com.kdb.it.domain.migration.request.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.RequestFormImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부점 편성요청서 반입 API입니다. 관리자만 호출할 수 있습니다.
 *
 * <p>{@code /api/admin/**}는 {@code SecurityConfig}에서 이미 {@code hasRole("ADMIN")}로 제한되어 있으므로 클래스 수준
 * {@link PreAuthorize}는 이중 방어입니다.
 *
 * <p>`multipart/form-data`로 파일과 manifest를 함께 받습니다 — multipart는 CORS 안전 목록이라 {@code
 * SimpleRequestCsrfFilter}가 `X-Requested-With` 헤더를 요구하며, 프론트 `$apiFetch`가 이를 자동으로 붙입니다.
 */
@Tag(name = "편성요청서 반입", description = "부점 제출 전산예산 편성요청서 일괄 반입")
@RestController
@RequestMapping("/api/admin/migration/requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RequestFormController {

    private final RequestFormImportService importService;

    /**
     * 반입 전 검증만 수행합니다. 원장을 만들지 않습니다.
     *
     * @param files 업로드 파일 목록
     * @param manifest 예산연도·파일별 부가 정보·보정값
     * @param user 인증된 관리자
     * @return 파일별 진단과 요약
     * @throws IllegalArgumentException 파일 수가 상한을 넘거나 manifest 항목 수와 다른 경우 (400)
     */
    @Operation(summary = "편성요청서 사전검증", description = "원장을 만들지 않고 진단만 돌려줍니다.")
    @PostMapping(path = "/dry-run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RequestFormDto.ImportResponse dryRun(
            @RequestPart(name = "files") List<MultipartFile> files,
            @Valid @RequestPart(name = "manifest") RequestFormDto.ImportManifest manifest,
            @AuthenticationPrincipal CustomUserDetails user) {
        return importService.importBatch(files, manifest, user.getEno(), true);
    }

    /**
     * 편성요청서를 원장에 반영합니다.
     *
     * <p>파일 1건이 트랜잭션 1개입니다. BLOCKER가 남은 파일은 건너뛰고 나머지는 반영합니다.
     *
     * @param files 업로드 파일 목록
     * @param manifest 예산연도·파일별 부가 정보·보정값
     * @param user 인증된 관리자
     * @return 파일별 결과와 생성된 관리번호
     * @throws IllegalArgumentException 파일 수가 상한을 넘거나 manifest 항목 수와 다른 경우 (400)
     */
    @Operation(summary = "편성요청서 반입", description = "정상 파일만 원장에 반영합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RequestFormDto.ImportResponse commit(
            @RequestPart(name = "files") List<MultipartFile> files,
            @Valid @RequestPart(name = "manifest") RequestFormDto.ImportManifest manifest,
            @AuthenticationPrincipal CustomUserDetails user) {
        return importService.importBatch(files, manifest, user.getEno(), false);
    }
}
