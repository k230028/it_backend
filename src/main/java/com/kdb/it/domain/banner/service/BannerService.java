package com.kdb.it.domain.banner.service;

import com.kdb.it.domain.banner.dto.BannerDto;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.authz.BannerFileReadAuthorizer;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.service.FileService;
import com.kdb.it.infra.file.service.FileService.FileDownloadResult;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * /info 홈 배너 서비스
 *
 * <p>배너는 전용 테이블 없이 공통첨부파일기본(TPRMPP_CFILEM)을 재사용한다. 이 서비스가 {@code APG_FL_KD_NM='배너'}·{@code
 * APG_FL_LNK_CTZ_NM='/info'}·{@code FL_TP_CONE='이미지'} 규약을 강제하므로 클라이언트가 임의 값을 보낼 수 없다.
 *
 * <p>활성·비활성은 {@code DEL_YN}으로 표현한다. {@code 'N'}이 활성, {@code 'Y'}가 비활성이며 물리 파일은 어느 쪽에서도 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    /** 배너 노출 위치 — 현재는 /info 홈 한 곳이다. */
    public static final String BANNER_APG_FL_LNK_CTZ_NM = "/info";

    private static final String IMAGE_FL_TP_CONE = "이미지";
    private static final String ACTIVE = "N";

    /** 배너로 허용하는 이미지 확장자. FileValidator는 pdf·hwp도 통과시키므로 여기서 좁힌다. */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");

    private final FileService fileService;
    private final FileRepository fileRepository;

    /**
     * 홈 캐러셀에 노출할 활성 배너를 조회합니다.
     *
     * @return 파일매핑ID 오름차순(업로드 순) 활성 배너 목록. 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<BannerDto.Response> getActiveBanners() {
        return fileRepository
                .findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(
                        BannerFileReadAuthorizer.BANNER_KIND, BANNER_APG_FL_LNK_CTZ_NM, ACTIVE)
                .stream()
                .sorted(Comparator.comparing(Cfilem::getFlMpnId))
                .map(file -> toResponse(file, true))
                .toList();
    }

    /**
     * 관리 화면용으로 활성·비활성 배너를 모두 조회합니다.
     *
     * @return 파일매핑ID 오름차순 전체 배너 목록
     */
    @Transactional(readOnly = true)
    public List<BannerDto.Response> getAllBanners() {
        return fileRepository
                .findAllByApgFlKdNmAndApgFlLnkCtzNmOrderByFlMpnIdAsc(
                        BannerFileReadAuthorizer.BANNER_KIND, BANNER_APG_FL_LNK_CTZ_NM)
                .stream()
                .map(file -> toResponse(file, ACTIVE.equals(file.getDelYn())))
                .toList();
    }

    /**
     * 배너 이미지를 업로드합니다.
     *
     * @param file 업로드할 이미지 파일
     * @return 업로드된 배너 정보 (활성 상태)
     * @throws CustomGeneralException 확장자가 없거나 허용 이미지 확장자가 아닌 경우
     */
    @Transactional
    public BannerDto.Response upload(MultipartFile file) {
        requireImageExtension(file.getOriginalFilename());

        FileDto.Response uploaded =
                fileService.uploadFileAndGet(
                        file,
                        FileDto.UploadRequest.builder()
                                .flTpCone(IMAGE_FL_TP_CONE)
                                .apgFlKdNm(BannerFileReadAuthorizer.BANNER_KIND)
                                .apgFlLnkCtzNm(BANNER_APG_FL_LNK_CTZ_NM)
                                .build());

        return BannerDto.Response.builder()
                .flMpnId(uploaded.getFlMpnId())
                .flNm(uploaded.getFlNm())
                .apgFlSz(uploaded.getApgFlSz())
                .active(true)
                .previewUrl(previewUrl(uploaded.getFlMpnId()))
                .adminPreviewUrl(adminPreviewUrl(uploaded.getFlMpnId()))
                .fstEnrDtm(uploaded.getFstEnrDtm())
                .fstEnrUsid(uploaded.getFstEnrUsid())
                .build();
    }

    /**
     * 배너 활성 상태를 변경합니다.
     *
     * @param flMpnId 배너 파일매핑ID
     * @param active {@code true}면 DEL_YN='N'으로 복원, {@code false}면 'Y'로 비활성화
     * @return 변경된 배너 정보
     * @throws CustomGeneralException 해당 파일매핑ID가 없는 경우
     * @throws AccessDeniedException 대상 파일이 배너가 아닌 경우
     */
    @Transactional
    public BannerDto.Response setActive(String flMpnId, boolean active) {
        Cfilem file = requireBannerFile(flMpnId);

        if (active) {
            file.restore();
        } else {
            file.delete();
        }
        return toResponse(file, active);
    }

    /**
     * 관리자 전용으로 배너 미리보기 이미지를 조회합니다. {@code DEL_YN}과 무관하게 서빙합니다.
     *
     * <p>일반 {@code /api/files/{id}/preview}는 {@code DEL_YN='N'}만 서빙하므로 비활성화된 배너는 관리 화면에서 깨진 이미지로
     * 보인다. 배너 관리자는 재활성화 대상을 미리 봐야 하므로 이 배너 전용 경로에서만 삭제 여부를 무시한다.
     *
     * @param flMpnId 배너 파일매핑ID
     * @return 파일 다운로드 결과 (Resource·원본파일명·MIME 타입)
     * @throws CustomGeneralException 해당 파일매핑ID가 없는 경우
     * @throws AccessDeniedException 대상 파일이 배너가 아닌 경우
     */
    @Transactional(readOnly = true)
    public FileDownloadResult getAdminPreviewImage(String flMpnId) {
        Cfilem file = requireBannerFile(flMpnId);
        return fileService.downloadFile(file);
    }

    /**
     * 파일매핑ID로 배너 파일을 조회합니다. {@code DEL_YN}과 무관하게 조회하며 배너가 아니면 거부합니다.
     *
     * @param flMpnId 배너 파일매핑ID
     * @return 조회된 배너 파일 엔티티
     * @throws CustomGeneralException 해당 파일매핑ID가 없는 경우
     * @throws AccessDeniedException 대상 파일이 배너가 아닌 경우
     */
    private Cfilem requireBannerFile(String flMpnId) {
        Cfilem file =
                fileRepository
                        .findById(flMpnId)
                        .orElseThrow(() -> new CustomGeneralException("배너를 찾을 수 없습니다: " + flMpnId));

        // 배너 API로 다른 종류의 파일을 조회·변경할 수 없게 막는다.
        if (!BannerFileReadAuthorizer.BANNER_KIND.equals(file.getApgFlKdNm())) {
            throw new AccessDeniedException("배너가 아닌 파일은 배너 API로 조회·변경할 수 없습니다.");
        }
        return file;
    }

    /** 확장자가 허용 이미지 목록에 있는지 검증한다. */
    private void requireImageExtension(String originalFilename) {
        int dot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        String extension =
                dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new CustomGeneralException("배너는 이미지 파일만 등록할 수 있습니다. 허용 확장자: jpg, jpeg, png, gif");
        }
    }

    private BannerDto.Response toResponse(Cfilem file, boolean active) {
        return BannerDto.Response.builder()
                .flMpnId(file.getFlMpnId())
                .flNm(file.getFlNm())
                .apgFlSz(file.getApgFlSz())
                .active(active)
                .previewUrl(previewUrl(file.getFlMpnId()))
                .adminPreviewUrl(adminPreviewUrl(file.getFlMpnId()))
                .fstEnrDtm(file.getFstEnrDtm())
                .fstEnrUsid(file.getFstEnrUsid())
                .build();
    }

    private String previewUrl(String flMpnId) {
        return "/api/files/" + flMpnId + "/preview";
    }

    /** 관리자 전용 배너 미리보기 URL — {@code DEL_YN}과 무관하게 서빙하므로 비활성 배너도 관리 화면에서 렌더링된다. */
    private String adminPreviewUrl(String flMpnId) {
        return "/api/banners/" + flMpnId + "/preview";
    }
}
