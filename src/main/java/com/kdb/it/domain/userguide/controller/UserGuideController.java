package com.kdb.it.domain.userguide.controller;

import com.kdb.it.domain.userguide.dto.UserGuideDto;
import com.kdb.it.domain.userguide.service.UserGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자가이드 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/user-guides}
 *
 * <p>사용자가이드는 전용 테이블 없이 공통첨부파일기본(TPRMPP_CFILEM)을 재사용하며 {@code APG_FL_KD_NM='사용자가이드'}·{@code
 * APG_FL_LNK_CTZ_NM='HEADER'} 규약은 {@link UserGuideService}가 강제한다.
 *
 * <p>파일 내려받기는 이 컨트롤러가 아니라 공통 {@code GET /api/files/{flMpnId}/download}를 쓴다. 종류 {@code 사용자가이드}의 읽기
 * 권한은 {@code UserGuideFileReadAuthorizer}가 인증 사용자 전체로 판정한다.
 *
 * <p>보안: 현재 가이드 조회는 인증 사용자 전체, 나머지는 관리자 전용이다.
 */
@RestController
@RequestMapping("/api/user-guides")
@RequiredArgsConstructor
@Tag(name = "UserGuide", description = "사용자가이드 API")
public class UserGuideController {

    private final UserGuideService userGuideService;

    /**
     * 헤더 버튼에 노출할 현재 사용자가이드를 조회합니다.
     *
     * @return 현재 가이드. 등록된 가이드가 없으면 {@code 204 No Content}
     */
    @GetMapping("/active")
    @Operation(
            summary = "현재 사용자가이드 조회",
            description =
                    "DEL_YN='N'인 사용자가이드 1건을 조회합니다. 인증 사용자 전체가 조회할 수 있습니다. "
                            + "등록된 가이드가 없으면 204를 반환합니다 — 조회 실패(5xx)와 구분되는 정상 상태입니다.")
    public ResponseEntity<UserGuideDto.Response> getActiveGuide() {
        return userGuideService
                .getActiveGuide()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 관리 화면용으로 현재 가이드와 이력을 모두 조회합니다.
     *
     * @return 파일매핑ID 내림차순(최신 우선) 전체 목록
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "사용자가이드 전체 목록 조회 (관리자)",
            description = "이력(DEL_YN='Y')을 포함해 전체를 최신순으로 조회합니다.")
    public ResponseEntity<List<UserGuideDto.Response>> getAllGuides() {
        return ResponseEntity.ok(userGuideService.getAllGuides());
    }

    /**
     * 사용자가이드를 업로드하고 현재 가이드로 지정합니다.
     *
     * @param file 업로드할 가이드 파일 (pdf·hwp·hwpx·docx·pptx)
     * @return 생성된 가이드 정보
     * @throws com.kdb.it.exception.CustomGeneralException 허용 확장자가 아닌 경우
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "사용자가이드 업로드 (관리자)",
            description =
                    "multipart/form-data로 가이드 파일 1개를 업로드합니다. "
                            + "첨부파일종류명('사용자가이드')·첨부파일연결콘텐츠명('HEADER')·파일유형내용('첨부파일')은 서버가 고정합니다. "
                            + "기존 현재 가이드는 같은 트랜잭션에서 이력으로 내려갑니다. "
                            + "허용 확장자는 pdf, hwp, hwpx, docx, pptx입니다.")
    public ResponseEntity<UserGuideDto.Response> upload(
            @Parameter(description = "업로드할 사용자가이드 파일", required = true) @RequestPart("file")
                    MultipartFile file) {
        UserGuideDto.Response response = userGuideService.upload(file);
        return ResponseEntity.created(URI.create("/api/user-guides/" + response.getFlMpnId()))
                .body(response);
    }

    /**
     * 사용자가이드를 현재 가이드로 지정하거나 내립니다.
     *
     * @param flMpnId 대상 파일매핑ID
     * @param request 활성 여부 요청. {@code active} 누락 시 Bean Validation이 400을 발생시킨다
     * @return 변경된 가이드 정보
     * @throws com.kdb.it.exception.CustomGeneralException 해당 가이드가 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 대상이 사용자가이드가 아닌 경우
     */
    @PatchMapping("/{flMpnId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "현재 사용자가이드 지정·해제 (관리자)",
            description =
                    "active=true면 다른 활성 건을 내린 뒤 이 건을 현재 가이드로 지정하고, false면 이 건을 내린다. "
                            + "물리 파일은 어느 쪽에서도 삭제하지 않습니다.")
    public ResponseEntity<UserGuideDto.Response> setActive(
            @PathVariable("flMpnId") String flMpnId,
            @Valid @RequestBody UserGuideDto.ActiveRequest request) {
        return ResponseEntity.ok(userGuideService.setActive(flMpnId, request.getActive()));
    }
}
