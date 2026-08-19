package com.kdb.it.domain.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.banner.dto.BannerDto;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.service.FileService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock private FileService fileService;
    @Mock private FileRepository fileRepository;

    @InjectMocks private BannerService bannerService;

    /** delYn을 지정해 Cfilem 스텁을 만든다. */
    private Cfilem banner(String flMpnId, String delYn) {
        Cfilem file =
                Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm(flMpnId + ".png")
                        .flTpCone("이미지")
                        .apgFlSz(1024L)
                        .pkColNm("배너")
                        .pkCone("/info")
                        .build();
        if ("Y".equals(delYn)) {
            file.delete();
        } else {
            file.restore();
        }
        return file;
    }

    @Test
    @DisplayName("활성 배너만 파일매핑ID 오름차순으로 반환한다")
    void getActiveBanners_sortedAscending() {
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("배너", "/info", "N"))
                .willReturn(List.of(banner("FL-00000003", "N"), banner("FL-00000001", "N")));

        List<BannerDto.Response> result = bannerService.getActiveBanners();

        assertThat(result).extracting(BannerDto.Response::getFlMpnId)
                .containsExactly("FL-00000001", "FL-00000003");
        assertThat(result).allMatch(BannerDto.Response::isActive);
        assertThat(result.get(0).getPreviewUrl()).isEqualTo("/api/files/FL-00000001/preview");
    }

    @Test
    @DisplayName("관리자 목록은 비활성 배너도 active=false로 함께 반환한다")
    void getAllBanners_includesInactive() {
        given(fileRepository.findAllByPkColNmAndPkConeOrderByFlMpnIdAsc("배너", "/info"))
                .willReturn(List.of(banner("FL-00000001", "N"), banner("FL-00000002", "Y")));

        List<BannerDto.Response> result = bannerService.getAllBanners();

        assertThat(result).extracting(BannerDto.Response::isActive).containsExactly(true, false);
    }

    @Test
    @DisplayName("업로드는 배너 규약(pkColNm·pkCone·flTpCone)을 서버가 고정한다")
    void upload_forcesBannerContract() {
        MockMultipartFile file =
                new MockMultipartFile("file", "hero.png", "image/png", new byte[] {1});
        given(fileService.uploadFileAndGet(any(), any()))
                .willReturn(
                        FileDto.Response.builder()
                                .flMpnId("FL-00000009")
                                .flNm("hero.png")
                                .apgFlSz(1L)
                                .previewUrl("/api/files/FL-00000009/preview")
                                .build());

        BannerDto.Response result = bannerService.upload(file);

        ArgumentCaptor<FileDto.UploadRequest> captor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        verify(fileService).uploadFileAndGet(any(), captor.capture());
        assertThat(captor.getValue().getPkColNm()).isEqualTo("배너");
        assertThat(captor.getValue().getPkCone()).isEqualTo("/info");
        assertThat(captor.getValue().getFlTpCone()).isEqualTo("이미지");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getFlMpnId()).isEqualTo("FL-00000009");
    }

    @Test
    @DisplayName("이미지가 아닌 확장자는 업로드를 거부한다")
    void upload_rejectsNonImageExtension() {
        MockMultipartFile file =
                new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> bannerService.upload(file))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("이미지");
    }

    @Test
    @DisplayName("확장자 비교는 대소문자를 무시한다")
    void upload_extensionComparisonIsCaseInsensitive() {
        MockMultipartFile file =
                new MockMultipartFile("file", "hero.PNG", "image/png", new byte[] {1});
        given(fileService.uploadFileAndGet(any(), any()))
                .willReturn(FileDto.Response.builder().flMpnId("FL-00000010").build());

        assertThat(bannerService.upload(file).getFlMpnId()).isEqualTo("FL-00000010");
    }

    @Test
    @DisplayName("확장자가 없는 파일은 업로드를 거부한다")
    void upload_rejectsMissingExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "hero", "image/png", new byte[] {1});

        assertThatThrownBy(() -> bannerService.upload(file))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("이미지");
    }

    @Test
    @DisplayName("비활성화하면 DEL_YN='Y'가 되고 active=false를 반환한다")
    void setActive_false_marksDeleted() {
        Cfilem file = banner("FL-00000001", "N");
        given(fileRepository.findById("FL-00000001")).willReturn(Optional.of(file));

        BannerDto.Response result = bannerService.setActive("FL-00000001", false);

        assertThat(file.getDelYn()).isEqualTo("Y");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("재활성화하면 DEL_YN='N'으로 복원된다")
    void setActive_true_restores() {
        Cfilem file = banner("FL-00000002", "Y");
        given(fileRepository.findById("FL-00000002")).willReturn(Optional.of(file));

        BannerDto.Response result = bannerService.setActive("FL-00000002", true);

        assertThat(file.getDelYn()).isEqualTo("N");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 파일은 토글을 거부한다")
    void setActive_missingFile_throws() {
        given(fileRepository.findById("FL-99999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.setActive("FL-99999999", true))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("배너를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("배너가 아닌 파일은 토글을 거부한다 — 배너 API로 다른 파일을 복원할 수 없다")
    void setActive_nonBannerFile_throws() {
        Cfilem other =
                Cfilem.builder()
                        .flMpnId("FL-00000007")
                        .pkColNm("공통게시판")
                        .pkCone("NAC-1")
                        .build();
        other.delete();
        given(fileRepository.findById("FL-00000007")).willReturn(Optional.of(other));

        assertThatThrownBy(() -> bannerService.setActive("FL-00000007", true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ───────────────────────────────────────────────────────
    // getAdminPreviewImage — 관리자 전용 미리보기 (DEL_YN 무관)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("비활성 배너도 관리자 미리보기는 DEL_YN과 무관하게 서빙한다")
    void getAdminPreviewImage_servesInactiveBanner() {
        Cfilem file = banner("FL-00000405", "Y");
        given(fileRepository.findById("FL-00000405")).willReturn(Optional.of(file));
        Resource resource = new InputStreamResource(new ByteArrayInputStream(new byte[] {1}));
        FileService.FileDownloadResult expected =
                new FileService.FileDownloadResult(resource, "FL-00000405.png", "image/png");
        given(fileService.downloadFile(file)).willReturn(expected);

        FileService.FileDownloadResult result = bannerService.getAdminPreviewImage("FL-00000405");

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("존재하지 않는 배너는 관리자 미리보기를 거부한다")
    void getAdminPreviewImage_missingFile_throws() {
        given(fileRepository.findById("FL-99999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.getAdminPreviewImage("FL-99999999"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("배너를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("배너가 아닌 파일은 관리자 미리보기를 거부한다")
    void getAdminPreviewImage_nonBannerFile_throws() {
        Cfilem other =
                Cfilem.builder()
                        .flMpnId("FL-00000007")
                        .pkColNm("공통게시판")
                        .pkCone("NAC-1")
                        .build();
        other.delete();
        given(fileRepository.findById("FL-00000007")).willReturn(Optional.of(other));

        assertThatThrownBy(() -> bannerService.getAdminPreviewImage("FL-00000007"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
