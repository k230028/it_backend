package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.board.service.BoardPostFileCacheService;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.authz.FileTargetWriteAuthorizerRegistry;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileServiceLinkExistingTest {

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileOwnershipChecker fileOwnershipChecker = mock(FileOwnershipChecker.class);
    private final FileUploadUnitService fileUploadUnitService = mock(FileUploadUnitService.class);
    private final FileTargetWriteAuthorizerRegistry targetWriteAuthorizerRegistry =
            mock(FileTargetWriteAuthorizerRegistry.class);
    private final BoardPostFileCacheService boardPostFileCacheService =
            mock(BoardPostFileCacheService.class);

    private final FileService fileService =
            new FileService(
                    fileRepository,
                    fileOwnershipChecker,
                    fileUploadUnitService,
                    targetWriteAuthorizerRegistry,
                    boardPostFileCacheService);

    private FileDto.UploadRequest request() {
        return FileDto.UploadRequest.builder()
                .flTpCone("첨부파일")
                .apgFlKdNm("편성요청서반입")
                .apgFlLnkCtzNm("APF-2026-00000002")
                .build();
    }

    @Test
    @DisplayName("원본 파일의 물리 경로를 공유하는 메타행을 만들고 새 파일매핑ID를 돌려준다")
    void linkExistingFile_reusesPhysicalFile() {
        Cfilem source =
                Cfilem.builder()
                        .flMpnId("FL-00000001")
                        .flNm("편성요청서.xlsx")
                        .flPysNm("SVR1_20260818120000_abc.xlsx")
                        .flKpnPth("/data/files/편성요청서반입/2026/08")
                        .flTpCone("첨부파일")
                        .apgFlSz(2048L)
                        .apgFlKdNm("편성요청서반입")
                        .apgFlLnkCtzNm("APF-2026-00000001")
                        .build();
        Cfilem linked = Cfilem.builder().flMpnId("FL-00000002").build();

        given(fileRepository.findByFlMpnIdAndDelYn("FL-00000001", "N"))
                .willReturn(Optional.of(source));
        given(fileUploadUnitService.linkExistingFileInNewTransaction(any(), any()))
                .willReturn(linked);

        String flMpnId = fileService.linkExistingFile("FL-00000001", request());

        assertThat(flMpnId).isEqualTo("FL-00000002");
    }

    @Test
    @DisplayName("원본이 없으면 업무 예외를 던진다")
    void linkExistingFile_throwsWhenSourceMissing() {
        given(fileRepository.findByFlMpnIdAndDelYn("FL-99999999", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.linkExistingFile("FL-99999999", request()))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("FL-99999999");
    }
}
