package com.kdb.it.domain.userguide.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.userguide.dto.UserGuideDto;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.infra.file.service.FileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserGuideServiceTest {

    private static final String KIND = "사용자가이드";
    private static final String LINK = "HEADER";

    @Mock private FileService fileService;
    @Mock private FileRepository fileRepository;

    @InjectMocks private UserGuideService userGuideService;

    /** delYn을 지정해 사용자가이드 Cfilem 스텁을 만든다. */
    private Cfilem guide(String flMpnId, String delYn) {
        Cfilem file =
                Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm(flMpnId + ".pdf")
                        .flTpCone("첨부파일")
                        .apgFlSz(2048L)
                        .apgFlKdNm(KIND)
                        .apgFlLnkCtzNm(LINK)
                        .build();
        if ("Y".equals(delYn)) {
            file.delete();
        } else {
            file.restore();
        }
        return file;
    }

    private MockMultipartFile document(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "guide".getBytes());
    }

    @Test
    @DisplayName("현재 가이드가 없으면 빈 Optional을 반환한다")
    void getActiveGuide_empty() {
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(KIND, LINK, "N"))
                .willReturn(List.of());

        assertThat(userGuideService.getActiveGuide()).isEmpty();
    }

    @Test
    @DisplayName("현재 가이드를 내려받기 URL과 함께 반환한다")
    void getActiveGuide_present() {
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(KIND, LINK, "N"))
                .willReturn(List.of(guide("FL-00000007", "N")));

        UserGuideDto.Response result = userGuideService.getActiveGuide().orElseThrow();

        assertThat(result.getFlMpnId()).isEqualTo("FL-00000007");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/FL-00000007/download");
    }

    @Test
    @DisplayName("관리 목록은 이력을 포함해 파일매핑ID 내림차순(최신 우선)으로 반환한다")
    void getAllGuides_descending() {
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmOrderByFlMpnIdAsc(KIND, LINK))
                .willReturn(List.of(guide("FL-00000001", "Y"), guide("FL-00000002", "N")));

        List<UserGuideDto.Response> result = userGuideService.getAllGuides();

        assertThat(result)
                .extracting(UserGuideDto.Response::getFlMpnId)
                .containsExactly("FL-00000002", "FL-00000001");
        assertThat(result.get(0).isActive()).isTrue();
        assertThat(result.get(1).isActive()).isFalse();
    }

    @Test
    @DisplayName("업로드하면 기존 현재 가이드를 내리고 종류·부모 키·파일유형을 서버가 고정한다")
    void upload_replacesPreviousActive() {
        Cfilem previous = guide("FL-00000001", "N");
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(KIND, LINK, "N"))
                .willReturn(List.of(previous));
        given(fileService.uploadFileAndGet(any(), any()))
                .willReturn(
                        FileDto.Response.builder()
                                .flMpnId("FL-00000002")
                                .flNm("guide.pdf")
                                .apgFlSz(2048L)
                                .build());

        UserGuideDto.Response result = userGuideService.upload(document("guide.pdf"));

        assertThat(previous.getDelYn()).isEqualTo("Y");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/FL-00000002/download");

        ArgumentCaptor<FileDto.UploadRequest> captor =
                ArgumentCaptor.forClass(FileDto.UploadRequest.class);
        verify(fileService).uploadFileAndGet(any(), captor.capture());
        assertThat(captor.getValue().getApgFlKdNm()).isEqualTo(KIND);
        assertThat(captor.getValue().getApgFlLnkCtzNm()).isEqualTo(LINK);
        assertThat(captor.getValue().getFlTpCone()).isEqualTo("첨부파일");
    }

    @Test
    @DisplayName("허용 확장자 밖이면 업로드를 거부한다")
    void upload_rejectsDisallowedExtension() {
        assertThatThrownBy(() -> userGuideService.upload(document("guide.exe")))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("pdf");
    }

    @Test
    @DisplayName("확장자 비교는 대소문자를 무시한다")
    void upload_allowsUppercaseExtension() {
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(KIND, LINK, "N"))
                .willReturn(List.of());
        given(fileService.uploadFileAndGet(any(), any()))
                .willReturn(FileDto.Response.builder().flMpnId("FL-00000003").build());

        assertThat(userGuideService.upload(document("GUIDE.PDF")).isActive()).isTrue();
    }

    @Test
    @DisplayName("되돌리기는 다른 현재 가이드를 먼저 내려 활성 1건을 유지한다")
    void setActive_true_keepsSingleActive() {
        Cfilem current = guide("FL-00000002", "N");
        Cfilem history = guide("FL-00000001", "Y");
        given(fileRepository.findById("FL-00000001")).willReturn(Optional.of(history));
        given(fileRepository.findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(KIND, LINK, "N"))
                .willReturn(List.of(current));

        UserGuideDto.Response result = userGuideService.setActive("FL-00000001", true);

        assertThat(current.getDelYn()).isEqualTo("Y");
        assertThat(history.getDelYn()).isEqualTo("N");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("현재 가이드를 내리면 활성 0건이 된다")
    void setActive_false_deactivates() {
        Cfilem current = guide("FL-00000002", "N");
        given(fileRepository.findById("FL-00000002")).willReturn(Optional.of(current));

        UserGuideDto.Response result = userGuideService.setActive("FL-00000002", false);

        assertThat(current.getDelYn()).isEqualTo("Y");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("사용자가이드가 아닌 파일은 이 API로 변경할 수 없다")
    void setActive_rejectsOtherKind() {
        Cfilem banner =
                Cfilem.builder()
                        .flMpnId("FL-00000009")
                        .apgFlKdNm("배너")
                        .apgFlLnkCtzNm("/info")
                        .build();
        given(fileRepository.findById("FL-00000009")).willReturn(Optional.of(banner));

        assertThatThrownBy(() -> userGuideService.setActive("FL-00000009", true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("없는 파일매핑ID는 거부한다")
    void setActive_rejectsMissingFile() {
        given(fileRepository.findById("FL-99999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userGuideService.setActive("FL-99999999", true))
                .isInstanceOf(CustomGeneralException.class);
    }
}
