package com.kdb.it.domain.userguide.service;

import com.kdb.it.domain.userguide.dto.UserGuideDto;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.authz.UserGuideFileReadAuthorizer;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.service.FileService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자가이드 서비스
 *
 * <p>사용자가이드는 전용 테이블 없이 공통첨부파일기본(TPRMPP_CFILEM)을 재사용한다. 이 서비스가 {@code APG_FL_KD_NM='사용자가이드'}·{@code
 * APG_FL_LNK_CTZ_NM='HEADER'}·{@code FL_TP_CONE='첨부파일'} 규약을 강제하므로 클라이언트가 임의 값을 보낼 수 없다.
 *
 * <p><b>단일 파일 교체 방식</b>: {@code DEL_YN='N'}인 행은 항상 0건 또는 1건이다. 업로드와 되돌리기 모두 같은 트랜잭션에서 기존 활성 행을 먼저
 * 내린다. 이전 파일은 이력으로 남고 물리 파일은 어느 쪽에서도 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class UserGuideService {

    /** 사용자가이드 노출 위치 — 헤더는 특정 화면이 아니라 전역이므로 경로 대신 위치명을 쓴다. */
    public static final String USER_GUIDE_APG_FL_LNK_CTZ_NM = "HEADER";

    private static final String ATTACHMENT_FL_TP_CONE = "첨부파일";
    private static final String ACTIVE = "N";

    /** 사용자가이드로 허용하는 확장자. 공통 FileValidator는 이미지·압축도 통과시키므로 여기서 좁힌다. */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "hwp", "hwpx", "docx", "pptx");

    private final FileService fileService;
    private final FileRepository fileRepository;

    /**
     * 헤더 버튼에 노출할 현재 사용자가이드를 조회합니다.
     *
     * @return 현재 가이드. 등록된 가이드가 없으면 빈 Optional
     */
    @Transactional(readOnly = true)
    public Optional<UserGuideDto.Response> getActiveGuide() {
        return activeGuides().stream()
                .max(Comparator.comparing(Cfilem::getFlMpnId))
                .map(file -> toResponse(file, true));
    }

    /**
     * 관리 화면용으로 현재 가이드와 이력을 모두 조회합니다.
     *
     * @return 파일매핑ID 내림차순(최신 우선) 전체 목록
     */
    @Transactional(readOnly = true)
    public List<UserGuideDto.Response> getAllGuides() {
        return fileRepository
                .findAllByApgFlKdNmAndApgFlLnkCtzNmOrderByFlMpnIdAsc(
                        UserGuideFileReadAuthorizer.USER_GUIDE_KIND, USER_GUIDE_APG_FL_LNK_CTZ_NM)
                .stream()
                .sorted(Comparator.comparing(Cfilem::getFlMpnId).reversed())
                .map(file -> toResponse(file, ACTIVE.equals(file.getDelYn())))
                .toList();
    }

    /**
     * 사용자가이드를 업로드하고 현재 가이드로 지정합니다.
     *
     * <p>기존 현재 가이드는 같은 트랜잭션에서 이력으로 내려간다.
     *
     * @param file 업로드할 가이드 파일
     * @return 업로드된 가이드 정보 (현재 가이드 상태)
     * @throws CustomGeneralException 확장자가 없거나 허용 목록 밖인 경우
     */
    @Transactional
    public UserGuideDto.Response upload(MultipartFile file) {
        requireAllowedExtension(file.getOriginalFilename());
        deactivateAllActive();

        FileDto.Response uploaded =
                fileService.uploadFileAndGet(
                        file,
                        FileDto.UploadRequest.builder()
                                .flTpCone(ATTACHMENT_FL_TP_CONE)
                                .apgFlKdNm(UserGuideFileReadAuthorizer.USER_GUIDE_KIND)
                                .apgFlLnkCtzNm(USER_GUIDE_APG_FL_LNK_CTZ_NM)
                                .build());

        return UserGuideDto.Response.builder()
                .flMpnId(uploaded.getFlMpnId())
                .flNm(uploaded.getFlNm())
                .apgFlSz(uploaded.getApgFlSz())
                .active(true)
                .downloadUrl(downloadUrl(uploaded.getFlMpnId()))
                .fstEnrDtm(uploaded.getFstEnrDtm())
                .fstEnrUsid(uploaded.getFstEnrUsid())
                .build();
    }

    /**
     * 사용자가이드를 현재 가이드로 지정하거나 내립니다.
     *
     * @param flMpnId 대상 파일매핑ID
     * @param active {@code true}면 다른 활성 건을 내린 뒤 이 건을 현재 가이드로, {@code false}면 이 건을 내린다
     * @return 변경된 가이드 정보
     * @throws CustomGeneralException 해당 파일매핑ID가 없는 경우
     * @throws AccessDeniedException 대상이 사용자가이드가 아닌 경우
     */
    @Transactional
    public UserGuideDto.Response setActive(String flMpnId, boolean active) {
        Cfilem file = requireUserGuideFile(flMpnId);

        if (active) {
            deactivateAllActive();
            file.restore();
        } else {
            file.delete();
        }
        return toResponse(file, active);
    }

    /** 현재 활성 상태인 사용자가이드 행을 모두 조회한다. 불변식상 0건 또는 1건이다. */
    private List<Cfilem> activeGuides() {
        return fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(
                UserGuideFileReadAuthorizer.USER_GUIDE_KIND, USER_GUIDE_APG_FL_LNK_CTZ_NM, ACTIVE);
    }

    /** 활성 행을 모두 내려 "활성 1건 이하" 불변식을 유지한다. 과거에 활성이 여러 건 생겼더라도 여기서 수렴한다. */
    private void deactivateAllActive() {
        activeGuides().forEach(Cfilem::delete);
    }

    /** 파일매핑ID로 사용자가이드 파일을 조회한다. DEL_YN과 무관하게 조회하며 종류가 다르면 거부한다. */
    private Cfilem requireUserGuideFile(String flMpnId) {
        Cfilem file =
                fileRepository
                        .findById(flMpnId)
                        .orElseThrow(
                                () -> new CustomGeneralException("사용자가이드를 찾을 수 없습니다: " + flMpnId));

        if (!UserGuideFileReadAuthorizer.USER_GUIDE_KIND.equals(file.getApgFlKdNm())) {
            throw new AccessDeniedException("사용자가이드가 아닌 파일은 사용자가이드 API로 조회·변경할 수 없습니다.");
        }
        return file;
    }

    /** 확장자가 허용 목록에 있는지 검증한다. */
    private void requireAllowedExtension(String originalFilename) {
        int dot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        String extension =
                dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomGeneralException(
                    "사용자가이드는 문서 파일만 등록할 수 있습니다. 허용 확장자: pdf, hwp, hwpx, docx, pptx");
        }
    }

    private UserGuideDto.Response toResponse(Cfilem file, boolean active) {
        return UserGuideDto.Response.builder()
                .flMpnId(file.getFlMpnId())
                .flNm(file.getFlNm())
                .apgFlSz(file.getApgFlSz())
                .active(active)
                .downloadUrl(downloadUrl(file.getFlMpnId()))
                .fstEnrDtm(file.getFstEnrDtm())
                .fstEnrUsid(file.getFstEnrUsid())
                .build();
    }

    private String downloadUrl(String flMpnId) {
        return "/api/files/" + flMpnId + "/download";
    }
}
