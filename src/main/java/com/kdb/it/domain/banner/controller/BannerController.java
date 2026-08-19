package com.kdb.it.domain.banner.controller;

import com.kdb.it.domain.banner.dto.BannerDto;
import com.kdb.it.domain.banner.service.BannerService;
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
 * /info 홈 배너 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/banners}
 *
 * <p>배너는 전용 테이블 없이 공통첨부파일기본(TPRMPP_CFILEM)을 재사용하며,
 * {@code PK_COL_NM='배너'}·{@code PK_CONE='/info'} 규약은 {@link BannerService}가 강제한다.
 *
 * <p>보안: 활성 배너 조회는 인증 사용자 전체, 나머지는 관리자 전용이다.
 */
@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
@Tag(name = "Banner", description = "/info 홈 배너 API")
public class BannerController {

    private final BannerService bannerService;

    /**
     * 홈 캐러셀에 노출할 활성 배너를 조회합니다.
     *
     * @return 업로드 순 활성 배너 목록. 배너가 없으면 빈 배열
     */
    @GetMapping
    @Operation(
            summary = "활성 배너 목록 조회",
            description = "DEL_YN='N'인 배너를 파일매핑ID 오름차순(업로드 순)으로 조회합니다. 인증 사용자 전체가 조회할 수 있습니다.")
    public ResponseEntity<List<BannerDto.Response>> getActiveBanners() {
        return ResponseEntity.ok(bannerService.getActiveBanners());
    }

    /**
     * 관리 화면용으로 활성·비활성 배너를 모두 조회합니다.
     *
     * @return 파일매핑ID 오름차순 전체 배너 목록
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "전체 배너 목록 조회 (관리자)",
            description = "비활성(DEL_YN='Y') 배너를 포함해 전체를 조회합니다.")
    public ResponseEntity<List<BannerDto.Response>> getAllBanners() {
        return ResponseEntity.ok(bannerService.getAllBanners());
    }

    /**
     * 배너 이미지를 업로드합니다.
     *
     * @param file 업로드할 이미지 파일 (jpg·jpeg·png·gif)
     * @return 생성된 배너 정보
     * @throws com.kdb.it.exception.CustomGeneralException 허용 이미지 확장자가 아닌 경우
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "배너 업로드 (관리자)",
            description =
                    "multipart/form-data로 배너 이미지 1개를 업로드합니다. "
                            + "주식별자컬럼명('배너')·주식별자내용('/info')·파일유형내용('이미지')은 서버가 고정합니다. "
                            + "허용 확장자는 jpg, jpeg, png, gif입니다.")
    public ResponseEntity<BannerDto.Response> upload(
            @Parameter(description = "업로드할 배너 이미지", required = true) @RequestPart("file")
                    MultipartFile file) {
        BannerDto.Response response = bannerService.upload(file);
        return ResponseEntity.created(URI.create("/api/banners/" + response.getFlMpnId()))
                .body(response);
    }

    /**
     * 배너 활성 상태를 변경합니다.
     *
     * @param flMpnId 배너 파일매핑ID
     * @param request 활성 여부 요청. {@code active} 누락 시 {@link BannerDto.ActiveRequest}의 Bean
     *     Validation이 {@code MethodArgumentNotValidException}(400)을 발생시킨다 — 유일한 검증 지점이다.
     * @return 변경된 배너 정보
     * @throws com.kdb.it.exception.CustomGeneralException 해당 배너가 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 대상이 배너가 아닌 경우
     */
    @PatchMapping("/{flMpnId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "배너 활성 상태 변경 (관리자)",
            description =
                    "active=true면 DEL_YN='N'으로 복원해 홈에 노출하고, false면 'Y'로 비활성화해 감춥니다. "
                            + "물리 파일은 어느 쪽에서도 삭제하지 않습니다.")
    public ResponseEntity<BannerDto.Response> setActive(
            @PathVariable("flMpnId") String flMpnId,
            @Valid @RequestBody BannerDto.ActiveRequest request) {
        return ResponseEntity.ok(bannerService.setActive(flMpnId, request.getActive()));
    }
}
