package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;

/**
 * FileService 단위 테스트
 *
 * <p>
 * 공통 첨부파일 서비스의 단건 조회·목록 조회·논리 삭제 메서드를 검증합니다.
 * Cfilem 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * 파일 업로드(uploadFile)는 디스크 I/O·EntityManager를 사용하므로 단위 테스트 범위에서 제외합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileService fileService;

    private static final String FL_MNG_NO = "FL_00000001";

    private Cfilem mockCfilem(String flMngNo) {
        Cfilem f = mock(Cfilem.class);
        given(f.getFlMngNo()).willReturn(flMngNo);
        given(f.getOrcFlNm()).willReturn("테스트파일.pdf");
        given(f.getSvrFlNm()).willReturn("SVR1_20260101120000_abc.pdf");
        given(f.getFlKpnPth()).willReturn("/data/files/요구사항정의서/2026/01");
        given(f.getFlDtt()).willReturn("첨부파일");
        given(f.getOrcPkVl()).willReturn("PRJ-2026-0001");
        given(f.getOrcDtt()).willReturn("요구사항정의서");
        return f;
    }

    // ───────────────────────────────────────────────────────
    // getFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFile: 존재하는 파일관리번호이면 응답 DTO를 반환한다")
    void getFile_존재하는파일_DTO반환() {
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N"))
                .willReturn(Optional.of(file));

        FileDto.Response result = fileService.getFile(FL_MNG_NO);

        assertThat(result.getFlMngNo()).isEqualTo(FL_MNG_NO);
        assertThat(result.getOrcFlNm()).isEqualTo("테스트파일.pdf");
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/" + FL_MNG_NO + "/download");
    }

    @Test
    @DisplayName("getFile: 존재하지 않는 파일관리번호이면 CustomGeneralException을 던진다")
    void getFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    // ───────────────────────────────────────────────────────
    // getFiles
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFiles: orcDtt 미입력이면 CustomGeneralException을 던진다")
    void getFiles_orcDtt없음_CustomGeneralException발생() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder().build();

        assertThatThrownBy(() -> fileService.getFiles(condition))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("orcDtt");
    }

    @Test
    @DisplayName("getFiles: orcDtt만 입력하면 해당 원본구분의 전체 파일 목록을 반환한다")
    void getFiles_orcDtt만있을때_전체목록반환() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder()
                .orcDtt("요구사항정의서")
                .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByOrcDttAndDelYn("요구사항정의서", "N"))
                .willReturn(List.of(file));

        List<FileDto.Response> result = fileService.getFiles(condition);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlMngNo()).isEqualTo(FL_MNG_NO);
    }

    @Test
    @DisplayName("getFiles: orcDtt + orcPkVl 입력이면 해당 원본구분·원본PK 파일 목록을 반환한다")
    void getFiles_orcDttAndPkVl_조건필터링반환() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder()
                .orcDtt("요구사항정의서")
                .orcPkVl("PRJ-2026-0001")
                .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(file));

        List<FileDto.Response> result = fileService.getFiles(condition);

        assertThat(result).hasSize(1);
    }

    // ───────────────────────────────────────────────────────
    // deleteFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: 존재하지 않는 파일이면 CustomGeneralException을 던진다")
    void deleteFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    @Test
    @DisplayName("deleteFile: 존재하는 파일이면 delete()를 호출하여 Soft Delete한다")
    void deleteFile_존재하는파일_SoftDelete호출() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMngNoAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        fileService.deleteFile(FL_MNG_NO);

        verify(cfilem).delete();
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일 2건을 일괄 Soft Delete하고 삭제 건수를 반환한다")
    void deleteFilesByOrc_파일2건_2반환() {
        Cfilem f1 = mockCfilem("FL_00000001");
        Cfilem f2 = mockCfilem("FL_00000002");
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(f1, f2));

        int count = fileService.deleteFilesByOrc("요구사항정의서", "PRJ-2026-0001");

        assertThat(count).isEqualTo(2);
        verify(f1).delete();
        verify(f2).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일이 없으면 0을 반환한다")
    void deleteFilesByOrc_파일없음_0반환() {
        given(fileRepository.findAllByOrcDttAndOrcPkVlAndDelYn("없는구분", "PRJ-9999-9999", "N"))
                .willReturn(List.of());

        int count = fileService.deleteFilesByOrc("없는구분", "PRJ-9999-9999");

        assertThat(count).isEqualTo(0);
    }
}
