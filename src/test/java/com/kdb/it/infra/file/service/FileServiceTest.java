package com.kdb.it.infra.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.board.service.BoardPostFileCacheService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.request.service.RequestFormSourceFileArchiver;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.FileValidator;
import com.kdb.it.infra.file.authz.BannerFileTargetWriteAuthorizer;
import com.kdb.it.infra.file.authz.FileTargetWriteAuthorizerRegistry;
import com.kdb.it.infra.file.authz.RequestFormFileTargetWriteAuthorizer;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FileService 단위 테스트
 *
 * <p>공통 첨부파일 서비스의 단건 조회·목록 조회·논리 삭제 메서드를 검증합니다. Cfilem 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로
 * 생성합니다. 파일 업로드(uploadFile)는 디스크 I/O·EntityManager를 사용하므로 단위 테스트 범위에서 제외합니다. Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock private FileRepository fileRepository;

    @Mock private EntityManager entityManager;

    @Mock private FileValidator fileValidator;

    @Mock private FileOwnershipChecker fileOwnershipChecker;

    @Mock private BoardPostFileCacheService boardPostFileCacheService;

    private FileUploadUnitService fileUploadUnitService;

    private FileService fileService;

    private static final String FL_MNG_NO = "FL_00000001";

    @BeforeEach
    void setUp() {
        fileUploadUnitService = new FileUploadUnitService(fileRepository, fileValidator);
        fileService =
                new FileService(
                        fileRepository,
                        fileOwnershipChecker,
                        fileUploadUnitService,
                        new FileTargetWriteAuthorizerRegistry(
                                List.of(
                                        new RequestFormFileTargetWriteAuthorizer(),
                                        new BannerFileTargetWriteAuthorizer())),
                        boardPostFileCacheService);
    }

    /** 목록 조회 권한 필터링용 일반 사용자 (canRead 기본 허용 가정) */
    private static final CustomUserDetails USER =
            new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");

    private Cfilem mockCfilem(String flMngNo) {
        Cfilem f = mock(Cfilem.class);
        given(f.getFlMpnId()).willReturn(flMngNo);
        given(f.getFlNm()).willReturn("테스트파일.pdf");
        given(f.getFlPysNm()).willReturn("SVR1_20260101120000_abc.pdf");
        given(f.getFlKpnPth()).willReturn("/data/files/요구사항정의서/2026/01");
        given(f.getFlTpCone()).willReturn("첨부파일");
        given(f.getPkCone()).willReturn("PRJ-2026-0001");
        given(f.getPkColNm()).willReturn("요구사항정의서");
        given(f.getApgFlSz()).willReturn(1234L);
        given(f.getFstEnrUsid()).willReturn("E0001");
        return f;
    }

    private void configureUploadUnit(java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileUploadUnitService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(fileUploadUnitService, "instanceId", "SVR1");
        ReflectionTestUtils.setField(fileUploadUnitService, "entityManager", entityManager);
    }

    /** 관리자 사용자 (소유권 검증 우회) */
    private static final CustomUserDetails ADMIN =
            new CustomUserDetails("E9999", List.of(CustomUserDetails.ATH_ADMIN), "18001");

    // ───────────────────────────────────────────────────────
    // getFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFile: 존재하는 파일관리번호이면 응답 DTO를 반환한다")
    void getFile_존재하는파일_DTO반환() {
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(file.getApgFlPth()).willReturn("2026/IT부(D01)/01. 사업/근거.pdf");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(file));

        FileDto.Response result = fileService.getFile(FL_MNG_NO);

        assertThat(result.getFlMpnId()).isEqualTo(FL_MNG_NO);
        assertThat(result.getFlNm()).isEqualTo("테스트파일.pdf");
        assertThat(result.getApgFlSz()).isEqualTo(1234L);
        assertThat(result.getRelativePath()).isEqualTo("2026/IT부(D01)/01. 사업/근거.pdf");
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/" + FL_MNG_NO + "/download");
    }

    @Test
    @DisplayName("getFile: 존재하지 않는 파일관리번호이면 CustomGeneralException을 던진다")
    void getFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    // ───────────────────────────────────────────────────────
    // getFiles
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFiles: pkColNm 미입력이면 CustomGeneralException을 던진다")
    void getFiles_pkColNm없음_CustomGeneralException발생() {
        FileDto.SearchCondition condition = FileDto.SearchCondition.builder().build();

        assertThatThrownBy(() -> fileService.getFiles(condition, USER))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("pkColNm");
    }

    @Test
    @DisplayName("getFiles: orcDtt만 입력하면 해당 원본구분의 전체 파일 목록을 반환한다")
    void getFiles_orcDtt만있을때_전체목록반환() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder().pkColNm("요구사항정의서").build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByPkColNmAndDelYn("요구사항정의서", "N")).willReturn(List.of(file));
        given(fileOwnershipChecker.canRead(file, USER)).willReturn(true);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlMpnId()).isEqualTo(FL_MNG_NO);
    }

    @Test
    @DisplayName("getFiles: orcDtt + orcPkVl 입력이면 해당 원본구분·원본PK 파일 목록을 반환한다")
    void getFiles_orcDttAndPkVl_조건필터링반환() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder()
                        .pkColNm("요구사항정의서")
                        .pkCone("PRJ-2026-0001")
                        .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(file));
        given(fileOwnershipChecker.canRead(file, USER)).willReturn(true);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getFiles: orcDtt + orcPkVl + flDtt 입력이면 세 조건으로 필터링한다")
    void getFiles_파일구분포함_조건필터링반환() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder()
                        .pkColNm("요구사항정의서")
                        .pkCone("PRJ-2026-0001")
                        .flTpCone("이미지")
                        .build();
        Cfilem file = mockCfilem(FL_MNG_NO);
        given(
                        fileRepository.findAllByPkColNmAndPkConeAndFlTpConeAndDelYn(
                                "요구사항정의서", "PRJ-2026-0001", "이미지", "N"))
                .willReturn(List.of(file));
        given(fileOwnershipChecker.canRead(file, USER)).willReturn(true);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPreviewUrl()).isEqualTo("/api/files/" + FL_MNG_NO + "/preview");
    }

    // ───────────────────────────────────────────────────────
    // getFiles — 부모 판정 요청 범위 캐시(N+1 제거)
    // ───────────────────────────────────────────────────────

    /**
     * 목록 캐시 검증용 파일 mock — 지정한 (종류, 부모)와 파일ID만 스텁한다.
     *
     * <p>읽기 판정 캐시는 {@code (PK_COL_NM, PK_CONE)} 조합만으로 키를 만들므로 각 파일이 자신의 종류·부모를 반환하도록 개별 스텁한다.
     */
    private Cfilem mockCfilemWithParent(String flMngNo, String pkColNm, String pkCone) {
        Cfilem f = mock(Cfilem.class);
        given(f.getFlMpnId()).willReturn(flMngNo);
        given(f.getPkColNm()).willReturn(pkColNm);
        given(f.getPkCone()).willReturn(pkCone);
        return f;
    }

    @Test
    @DisplayName("getFiles: 같은 부모(종류·PK_CONE) 파일 3건이면 canRead를 1회만 호출하고 3건을 반환한다")
    void getFiles_같은부모3건_canRead1회_3건반환() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder().pkColNm("요구사항정의서").pkCone("DOC-1").build();
        Cfilem first = mockCfilemWithParent("FL_00000001", "요구사항정의서", "DOC-1");
        Cfilem second = mockCfilemWithParent("FL_00000002", "요구사항정의서", "DOC-1");
        Cfilem third = mockCfilemWithParent("FL_00000003", "요구사항정의서", "DOC-1");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of(first, second, third));
        // computeIfAbsent는 각 키의 첫 파일(first)로 lambda를 호출하므로 first에만 stub
        given(fileOwnershipChecker.canRead(first, USER)).willReturn(true);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).hasSize(3);
        verify(fileOwnershipChecker, times(1)).canRead(first, USER);
        verify(fileOwnershipChecker, never()).canRead(second, USER);
        verify(fileOwnershipChecker, never()).canRead(third, USER);
    }

    @Test
    @DisplayName("getFiles: 부모 PK_CONE가 서로 다른 파일이면 각 부모마다 canRead를 호출한다(2회)")
    void getFiles_서로다른부모2건_canRead2회() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder().pkColNm("요구사항정의서").build();
        Cfilem doc1 = mockCfilemWithParent("FL_00000001", "요구사항정의서", "DOC-1");
        Cfilem doc2 = mockCfilemWithParent("FL_00000002", "요구사항정의서", "DOC-2");
        given(fileRepository.findAllByPkColNmAndDelYn("요구사항정의서", "N"))
                .willReturn(List.of(doc1, doc2));
        given(fileOwnershipChecker.canRead(doc1, USER)).willReturn(true);
        given(fileOwnershipChecker.canRead(doc2, USER)).willReturn(true);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).hasSize(2);
        verify(fileOwnershipChecker, times(1)).canRead(doc1, USER);
        verify(fileOwnershipChecker, times(1)).canRead(doc2, USER);
    }

    @Test
    @DisplayName("getFiles: 같은 부모가 거부되면 canRead를 1회만 호출하고 빈 목록을 반환한다")
    void getFiles_같은부모거부3건_canRead1회_빈목록() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder().pkColNm("요구사항정의서").pkCone("DOC-DENY").build();
        Cfilem first = mockCfilemWithParent("FL_00000001", "요구사항정의서", "DOC-DENY");
        Cfilem second = mockCfilemWithParent("FL_00000002", "요구사항정의서", "DOC-DENY");
        Cfilem third = mockCfilemWithParent("FL_00000003", "요구사항정의서", "DOC-DENY");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-DENY", "N"))
                .willReturn(List.of(first, second, third));
        given(fileOwnershipChecker.canRead(first, USER)).willReturn(false);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        assertThat(result).isEmpty();
        verify(fileOwnershipChecker, times(1)).canRead(first, USER);
        verify(fileOwnershipChecker, never()).canRead(second, USER);
        verify(fileOwnershipChecker, never()).canRead(third, USER);
    }

    @Test
    @DisplayName("getFiles: 종류가 같아도 PK_CONE가 다르면 캐시를 공유하지 않는다(부모별 개별 판정)")
    void getFiles_같은종류다른부모_캐시미공유() {
        FileDto.SearchCondition condition =
                FileDto.SearchCondition.builder().pkColNm("요구사항정의서").build();
        Cfilem doc1a = mockCfilemWithParent("FL_00000001", "요구사항정의서", "DOC-1");
        Cfilem doc1b = mockCfilemWithParent("FL_00000002", "요구사항정의서", "DOC-1");
        Cfilem doc2 = mockCfilemWithParent("FL_00000003", "요구사항정의서", "DOC-2");
        given(fileRepository.findAllByPkColNmAndDelYn("요구사항정의서", "N"))
                .willReturn(List.of(doc1a, doc1b, doc2));
        given(fileOwnershipChecker.canRead(doc1a, USER)).willReturn(true);
        given(fileOwnershipChecker.canRead(doc2, USER)).willReturn(false);

        List<FileDto.Response> result = fileService.getFiles(condition, USER);

        // DOC-1 허용 2건만 포함, DOC-2 거부 제외 — 종류가 같아도 부모가 다르면 캐시 미공유
        assertThat(result)
                .extracting(file -> file.getFlMpnId())
                .containsExactly("FL_00000001", "FL_00000002");
        verify(fileOwnershipChecker, times(1)).canRead(doc1a, USER);
        verify(fileOwnershipChecker, never()).canRead(doc1b, USER);
        verify(fileOwnershipChecker, times(1)).canRead(doc2, USER);
    }

    // ───────────────────────────────────────────────────────
    // getFilesBatch — 여러 부모 일괄 조회
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFilesBatch: 중복 부모를 제거해 한 번 조회하고 요청한 모든 부모를 결과에 포함한다")
    void getFilesBatch_중복부모_한번조회_빈그룹포함() {
        Cfilem first = mockCfilemWithParent("FL_00000002", "검토의견", "101");
        Cfilem second = mockCfilemWithParent("FL_00000001", "검토의견", "101");
        Cfilem denied = mockCfilemWithParent("FL_00000003", "검토의견", "102");
        given(
                        fileRepository.findAllByPkColNmAndPkConeInAndDelYn(
                                "검토의견", Set.of("101", "102", "103"), "N"))
                .willReturn(List.of(first, denied, second));
        given(fileOwnershipChecker.canRead(first, USER)).willReturn(true);
        given(fileOwnershipChecker.canRead(denied, USER)).willReturn(false);

        Map<String, List<FileDto.Response>> result =
                fileService.getFilesBatch("검토의견", List.of("101", "102", "101", "103"), USER);

        assertThat(result.keySet()).containsExactly("101", "102", "103");
        assertThat(result.get("101"))
                .extracting(FileDto.Response::getFlMpnId)
                .containsExactly("FL_00000001", "FL_00000002");
        assertThat(result.get("102")).isEmpty();
        assertThat(result.get("103")).isEmpty();
        verify(fileRepository, times(1))
                .findAllByPkColNmAndPkConeInAndDelYn("검토의견", Set.of("101", "102", "103"), "N");
        verify(fileOwnershipChecker, times(1)).canRead(first, USER);
        verify(fileOwnershipChecker, never()).canRead(second, USER);
        verify(fileOwnershipChecker, times(1)).canRead(denied, USER);
    }

    @Test
    @DisplayName("getFilesBatch: 부모 키 목록이 비어 있으면 조회하지 않고 거부한다")
    void getFilesBatch_빈부모목록_거부() {
        assertThatThrownBy(() -> fileService.getFilesBatch("검토의견", List.of(), USER))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("pkCone");

        verifyNoInteractions(fileRepository);
    }

    @Test
    @DisplayName("getFilesBatch: 종류나 부모 키에 공백이 있으면 조회하지 않고 거부한다")
    void getFilesBatch_공백입력_거부() {
        assertThatThrownBy(() -> fileService.getFilesBatch(" ", List.of("101"), USER))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("pkColNm");
        assertThatThrownBy(() -> fileService.getFilesBatch("검토의견", List.of("101", " "), USER))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("pkCone");

        verifyNoInteractions(fileRepository);
    }

    // ───────────────────────────────────────────────────────
    // deleteFile
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: 존재하지 않는 파일이면 CustomGeneralException을 던진다")
    void deleteFile_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    @Test
    @DisplayName("deleteFile: 존재하는 파일이면 delete()를 호출하여 Soft Delete한다")
    void deleteFile_존재하는파일_SoftDelete호출() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        fileService.deleteFile(FL_MNG_NO);

        verify(cfilem).delete();
    }

    @Test
    @DisplayName("deleteFile: 공식 반입 원본은 generic 삭제 경로에서 지울 수 없다")
    void deleteFile_공식반입원본_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getPkColNm()).willReturn(RequestFormSourceFileArchiver.PK_COL_NM);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.deleteFile(FL_MNG_NO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never()).delete();
    }

    @Test
    @DisplayName("deleteFile: 배너 파일은 generic 삭제 경로에서 지울 수 없다 — /api/banners 창구만 배너를 관리한다")
    void deleteFile_배너파일_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getPkColNm()).willReturn("배너");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.deleteFile(FL_MNG_NO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never()).delete();
    }

    @Test
    @DisplayName("deleteFile: 공통게시판 파일 삭제 뒤 활성 파일 수를 다시 세어 부모 캐시를 동기화한다")
    void deleteFile_공통게시판_활성파일수동기화() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getPkColNm()).willReturn("공통게시판");
        given(cfilem.getPkCone()).willReturn("NAC-001");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));
        fileService.deleteFile(FL_MNG_NO);

        verify(cfilem).delete();
        verify(boardPostFileCacheService).syncFromActiveFiles("NAC-001");
    }

    @Test
    @DisplayName("deleteFile: 공통게시판 외 파일은 게시물 캐시를 동기화하지 않는다")
    void deleteFile_다른종류_게시물캐시미동기화() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        fileService.deleteFile(FL_MNG_NO);

        verifyNoInteractions(boardPostFileCacheService);
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일 2건을 일괄 Soft Delete하고 삭제 건수를 반환한다")
    void deleteFilesByOrc_파일2건_2반환() {
        Cfilem f1 = mockCfilem("FL_00000001");
        Cfilem f2 = mockCfilem("FL_00000002");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "PRJ-2026-0001", "N"))
                .willReturn(List.of(f1, f2));

        int count = fileService.deleteFilesByOrc("요구사항정의서", "PRJ-2026-0001", USER);

        assertThat(count).isEqualTo(2);
        verify(f1).delete();
        verify(f2).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 타인 소유 파일이 섞이면 AccessDeniedException을 던진다")
    void deleteFilesByOrc_deniedWhenOtherOwned() {
        Cfilem mine = mockCfilem("FL_00000001");
        given(mine.getFstEnrUsid()).willReturn("E0001");
        Cfilem others = mockCfilem("FL_00000002");
        given(others.getFstEnrUsid()).willReturn("E0002");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of(mine, others));

        assertThatThrownBy(
                        () ->
                                fileService.deleteFilesByOrc(
                                        "요구사항정의서",
                                        "DOC-1",
                                        new CustomUserDetails(
                                                "E0001", List.of("ITPZZ001"), "18001")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteFilesByOrc: 사용자 정보가 없으면 AccessDeniedException을 던진다")
    void deleteFilesByOrc_deniedWhenNullUser() {
        Cfilem owned = mockCfilem("FL_00000001");
        given(owned.getFstEnrUsid()).willReturn("E0001");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of(owned));

        assertThatThrownBy(() -> fileService.deleteFilesByOrc("요구사항정의서", "DOC-1", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteFilesByOrc: 사용자 정보가 없으면 대상 목록이 비어 있어도 AccessDeniedException을 던진다")
    void deleteFilesByOrc_deniedWhenNullUserAndEmptyList() {
        // Arrange: 매칭 파일 0건 + 인증 정보 없음 → 빈 목록이라도 거부되어야 함(서비스 계약)
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> fileService.deleteFilesByOrc("요구사항정의서", "DOC-1", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteFilesByOrc: 모든 파일이 본인 소유이면 일괄 삭제하고 건수를 반환한다")
    void deleteFilesByOrc_allowedWhenAllOwned() {
        Cfilem f1 = mockCfilem("FL_00000001");
        given(f1.getFstEnrUsid()).willReturn("E0001");
        Cfilem f2 = mockCfilem("FL_00000002");
        given(f2.getFstEnrUsid()).willReturn("E0001");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of(f1, f2));

        int count =
                fileService.deleteFilesByOrc(
                        "요구사항정의서",
                        "DOC-1",
                        new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001"));

        assertThat(count).isEqualTo(2);
        verify(f1).delete();
        verify(f2).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 관리자는 타인 소유 파일이 섞여도 일괄 삭제한다")
    void deleteFilesByOrc_allowedForAdmin() {
        Cfilem mine = mockCfilem("FL_00000001");
        given(mine.getFstEnrUsid()).willReturn("E0001");
        Cfilem others = mockCfilem("FL_00000002");
        given(others.getFstEnrUsid()).willReturn("E0002");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("요구사항정의서", "DOC-1", "N"))
                .willReturn(List.of(mine, others));

        int count = fileService.deleteFilesByOrc("요구사항정의서", "DOC-1", ADMIN);

        assertThat(count).isEqualTo(2);
        verify(mine).delete();
        verify(others).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 관리자로도 공식 반입 원본을 generic 일괄 삭제할 수 없다")
    void deleteFilesByOrc_관리자공식반입원본_AccessDeniedException발생() {
        Cfilem official = mockCfilem(FL_MNG_NO);
        given(official.getPkColNm()).willReturn(RequestFormSourceFileArchiver.PK_COL_NM);
        given(
                        fileRepository.findAllByPkColNmAndPkConeAndDelYn(
                                RequestFormSourceFileArchiver.PK_COL_NM, "APF-2026-00000001", "N"))
                .willReturn(List.of(official));

        assertThatThrownBy(
                        () ->
                                fileService.deleteFilesByOrc(
                                        RequestFormSourceFileArchiver.PK_COL_NM,
                                        "APF-2026-00000001",
                                        ADMIN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(official, never()).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 관리자로도 배너를 generic 일괄 삭제할 수 없다")
    void deleteFilesByOrc_관리자배너_AccessDeniedException발생() {
        Cfilem banner = mockCfilem(FL_MNG_NO);
        given(banner.getPkColNm()).willReturn("배너");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("배너", "/info", "N"))
                .willReturn(List.of(banner));

        assertThatThrownBy(() -> fileService.deleteFilesByOrc("배너", "/info", ADMIN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(banner, never()).delete();
    }

    @Test
    @DisplayName("deleteFilesByOrc: 연관 파일이 없으면 0을 반환한다")
    void deleteFilesByOrc_파일없음_0반환() {
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("없는구분", "PRJ-9999-9999", "N"))
                .willReturn(List.of());

        int count = fileService.deleteFilesByOrc("없는구분", "PRJ-9999-9999", USER);

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("updateFileMeta: 존재하는 파일이면 원본 정보를 변경하고 파일관리번호를 반환한다")
    void updateFileMeta_존재하는파일_메타수정() {
        Cfilem cfilem = mock(Cfilem.class);
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder().pkColNm("정보화사업").pkCone("PRJ-2026-0002").build();
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        String result = fileService.updateFileMeta(FL_MNG_NO, request);

        assertThat(result).isEqualTo(FL_MNG_NO);
        verify(cfilem).updateMeta("PRJ-2026-0002", "정보화사업");
    }

    @Test
    @DisplayName("updateFileMeta: 공식 반입 원본은 generic 경로로 다른 종류에 재연결할 수 없다")
    void updateFileMeta_공식반입원본_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getPkColNm()).willReturn(RequestFormSourceFileArchiver.PK_COL_NM);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder().pkColNm("정보화사업").pkCone("PRJ-2026-0002").build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never()).updateMeta("PRJ-2026-0002", "정보화사업");
    }

    @Test
    @DisplayName("updateFileMeta: 일반 파일을 공식 반입 원본 종류로 재연결할 수 없다")
    void updateFileMeta_공식반입종류로변경_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder()
                        .pkColNm(RequestFormSourceFileArchiver.PK_COL_NM)
                        .pkCone("APF-2026-00000001")
                        .build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never())
                .updateMeta("APF-2026-00000001", RequestFormSourceFileArchiver.PK_COL_NM);
    }

    @Test
    @DisplayName("updateFileMeta: 배너 파일은 generic 경로로 다른 종류에 재연결할 수 없다")
    void updateFileMeta_배너파일_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getPkColNm()).willReturn("배너");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder().pkColNm("정보화사업").pkCone("PRJ-2026-0002").build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never()).updateMeta("PRJ-2026-0002", "정보화사업");
    }

    @Test
    @DisplayName("updateFileMeta: 일반 파일을 배너 종류로 generic 재연결할 수 없다")
    void updateFileMeta_배너종류로변경_AccessDeniedException발생() {
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder().pkColNm("배너").pkCone("/info").build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("generic");

        verify(cfilem, never()).updateMeta("/info", "배너");
    }

    @Test
    @DisplayName("updateFileMeta: 존재하지 않는 파일이면 CustomGeneralException을 던진다")
    void updateFileMeta_존재하지않는파일_CustomGeneralException발생() {
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.empty());
        FileDto.UpdateRequest request = FileDto.UpdateRequest.builder().pkColNm("정보화사업").build();

        assertThatThrownBy(() -> fileService.updateFileMeta(FL_MNG_NO, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining(FL_MNG_NO);
    }

    @Test
    @DisplayName("downloadFile: 저장 경로가 기준 경로 밖이면 다운로드를 차단한다")
    void downloadFile_경로이탈_CustomGeneralException발생(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(tempDir.resolveSibling("outside").toString());
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.downloadFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("허용되지 않는 파일 경로");
    }

    @Test
    @DisplayName("downloadFile: 존재하는 파일이면 Resource와 MIME 타입을 반환한다")
    void downloadFile_존재하는파일_리소스반환(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("요구사항정의서").resolve("2026").resolve("05");
        Files.createDirectories(storageDir);
        java.nio.file.Path filePath = storageDir.resolve("SVR1_test.pdf");
        Files.writeString(filePath, "PDF", StandardCharsets.UTF_8);
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
        given(cfilem.getFlPysNm()).willReturn("SVR1_test.pdf");
        given(cfilem.getFlNm()).willReturn("요구사항정의서.pdf");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        FileService.FileDownloadResult result = fileService.downloadFile(FL_MNG_NO);

        assertThat(result.resource().exists()).isTrue();
        assertThat(result.originalFilename()).isEqualTo("요구사항정의서.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("downloadFile: 원본 파일 확장자별 MIME 타입을 반환한다")
    void downloadFile_확장자별Mime타입반환(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("첨부");
        Files.createDirectories(storageDir);

        Object[][] cases = {
            {"jpg", "image/jpeg"},
            {"jpeg", "image/jpeg"},
            {"png", "image/png"},
            {"gif", "image/gif"},
            {"webp", "image/webp"},
            {"svg", "image/svg+xml"},
            {"bmp", "image/bmp"},
            {"ico", "image/x-icon"},
            {"doc", "application/msword"},
            {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"xls", "application/vnd.ms-excel"},
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"ppt", "application/vnd.ms-powerpoint"},
            {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {"hwp", "application/x-hwp"},
            {"txt", "text/plain"},
            {"csv", "text/csv"},
            {"json", "application/json"},
            {"zip", "application/zip"},
            {"bin", "application/octet-stream"}
        };

        for (Object[] testCase : cases) {
            String ext = (String) testCase[0];
            String expected = (String) testCase[1];
            String flMngNo = "FL_" + ext;
            String svrFlNm = "server." + ext;
            Files.writeString(storageDir.resolve(svrFlNm), "data", StandardCharsets.UTF_8);
            Cfilem cfilem = mockCfilem(flMngNo);
            given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
            given(cfilem.getFlPysNm()).willReturn(svrFlNm);
            given(cfilem.getFlNm()).willReturn("origin." + ext);
            given(fileRepository.findByFlMpnIdAndDelYn(flMngNo, "N"))
                    .willReturn(Optional.of(cfilem));

            FileService.FileDownloadResult result = fileService.downloadFile(flMngNo);

            assertThat(result.contentType()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("downloadFile: 원본 파일명이 없으면 서버 파일명 확장자로 MIME 타입을 판정한다")
    void downloadFile_원본파일명없음_서버파일명확장자사용(@TempDir java.nio.file.Path tempDir) throws Exception {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        java.nio.file.Path storageDir = tempDir.resolve("첨부");
        Files.createDirectories(storageDir);
        Files.writeString(storageDir.resolve("server.png"), "data", StandardCharsets.UTF_8);
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(storageDir.toString());
        given(cfilem.getFlPysNm()).willReturn("server.png");
        given(cfilem.getFlNm()).willReturn(null);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        FileService.FileDownloadResult result = fileService.downloadFile(FL_MNG_NO);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.originalFilename()).isEqualTo("server.png");
    }

    static Stream<Arguments> incompleteDownloadMetadata() {
        return Stream.of(
                Arguments.of("저장 경로 null", true, null),
                Arguments.of("저장 경로 공백", true, " "),
                Arguments.of("물리 파일명 null", false, null),
                Arguments.of("물리 파일명 공백", false, " "));
    }

    @ParameterizedTest(name = "downloadFile: {0}이면 불완전 메타데이터 예외를 반환한다")
    @MethodSource("incompleteDownloadMetadata")
    void downloadFile_저장경로또는물리파일명없음_CustomGeneralException발생(
            String caseName,
            boolean storagePathMissing,
            String missingValue,
            @TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth())
                .willReturn(storagePathMissing ? missingValue : tempDir.toString());
        given(cfilem.getFlPysNm()).willReturn(storagePathMissing ? "server.pdf" : missingValue);
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.downloadFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("파일 메타데이터가 불완전")
                .hasMessageContaining(FL_MNG_NO)
                .hasMessageNotContaining(tempDir.toString());
    }

    @Test
    @DisplayName("downloadFile: 메타데이터가 없거나 실제 파일을 읽을 수 없으면 예외가 발생한다")
    void downloadFile_파일없음_CustomGeneralException발생(@TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
        given(fileRepository.findByFlMpnIdAndDelYn("MISSING", "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.downloadFile("MISSING"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("존재하지 않는 파일");

        Cfilem cfilem = mockCfilem(FL_MNG_NO);
        given(cfilem.getFlKpnPth()).willReturn(tempDir.toString());
        given(cfilem.getFlPysNm()).willReturn("missing.pdf");
        given(fileRepository.findByFlMpnIdAndDelYn(FL_MNG_NO, "N")).willReturn(Optional.of(cfilem));

        assertThatThrownBy(() -> fileService.downloadFile(FL_MNG_NO))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("uploadFile: 빈 파일이면 저장소 접근 없이 예외를 던진다")
    void uploadFile_빈파일_CustomGeneralException발생() {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request = FileDto.UploadRequest.builder().pkColNm("요구사항정의서").build();

        assertThatThrownBy(() -> fileService.uploadFile(emptyFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("uploadFile: 공통게시판 단건 업로드 성공 뒤 실제 활성 파일 수로 부모 캐시를 동기화한다")
    void uploadFile_공통게시판_활성파일수동기화(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "보고서.pdf",
                        "application/pdf",
                        "PDF".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .pkColNm("공통게시판")
                        .pkCone("NAC-001")
                        .flTpCone("첨부파일")
                        .build();

        String result = fileService.uploadFile(file, request);

        assertThat(result).isEqualTo("FL-00000001");
        verify(boardPostFileCacheService).syncFromActiveFiles("NAC-001");
    }

    @Test
    @DisplayName("uploadFileAndGet: 공통게시판 단건 업로드 성공 뒤 실제 활성 파일 수로 부모 캐시를 동기화한다")
    void uploadFileAndGet_공통게시판_활성파일수동기화(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "설계서.pdf",
                        "application/pdf",
                        "PDF".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .pkColNm("공통게시판")
                        .pkCone("NAC-001")
                        .flTpCone("첨부파일")
                        .build();

        FileDto.Response result = fileService.uploadFileAndGet(file, request);

        assertThat(result.getFlNm()).isEqualTo("설계서.pdf");
        verify(boardPostFileCacheService).syncFromActiveFiles("NAC-001");
    }

    @Test
    @DisplayName("uploadFileAndGet: 파일을 저장하고 업로드 응답 DTO를 반환한다")
    void uploadFileAndGet_정상파일_응답반환(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "요구사항.pdf",
                        "application/pdf",
                        "PDF".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .pkColNm("요구사항정의서")
                        .pkCone("PRJ-2026-0001")
                        .flTpCone("첨부파일")
                        .build();

        FileDto.Response result = fileService.uploadFileAndGet(file, request);

        assertThat(result.getFlMpnId()).isEqualTo("FL-00000001");
        assertThat(result.getFlNm()).isEqualTo("요구사항.pdf");
        assertThat(result.getFlPysNm()).startsWith("SVR1_").endsWith(".pdf");
        assertThat(result.getDownloadUrl()).isEqualTo("/api/files/FL-00000001/download");
        org.mockito.Mockito.verify(entityManager)
                .persist(org.mockito.ArgumentMatchers.any(Cfilem.class));
        org.mockito.Mockito.verify(entityManager).flush();
    }

    @Test
    @DisplayName("uploadFileInternal: 디렉토리 생성 IOException 발생 시 cause 포함 예외 반환 — ERR-02")
    void uploadFileInternal_디렉토리생성IOException_cause포함(@TempDir java.nio.file.Path tempDir)
            throws Exception {
        // orcDtt 이름으로 파일을 미리 생성 → 같은 이름의 하위 디렉토리 생성 불가 (NotADirectoryException)
        java.nio.file.Path blockingFile = tempDir.resolve("요구사항정의서");
        java.nio.file.Files.createFile(blockingFile);

        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "test.pdf",
                        "application/pdf",
                        "content".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("요구사항정의서").flTpCone("첨부파일").build();

        // 현재 구현: CustomGeneralException(메시지, e)로 IOException을 cause로 포함하여 래핑
        assertThatThrownBy(() -> fileService.uploadFileInternal(file, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("uploadFiles: 일부 파일 실패 시 성공 목록과 실패 파일명을 함께 반환한다")
    void uploadFiles_부분실패_결과분리(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile okFile =
                new MockMultipartFile(
                        "files", "ok.txt", "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile emptyFile =
                new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("첨부").flTpCone("첨부파일").build();

        FileDto.BulkUploadResponse result =
                fileService.uploadFiles(List.of(okFile, emptyFile), request);

        assertThat(result.getSuccessList()).hasSize(1);
        assertThat(result.getFailList()).hasSize(1);
        assertThat(result.getFailList().get(0)).contains("empty.txt");
    }

    @Test
    @DisplayName("uploadFiles: 공통게시판 부분 성공 완료 뒤 실제 활성 파일 수로 한 번 동기화한다")
    void uploadFiles_공통게시판부분성공_활성파일수동기화(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L);
        MockMultipartFile okFile =
                new MockMultipartFile(
                        "files", "ok.txt", "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile emptyFile =
                new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder()
                        .pkColNm("공통게시판")
                        .pkCone("NAC-001")
                        .flTpCone("첨부파일")
                        .build();

        FileDto.BulkUploadResponse result =
                fileService.uploadFiles(List.of(okFile, emptyFile), request);

        assertThat(result.getSuccessList()).hasSize(1);
        assertThat(result.getFailList()).hasSize(1);
        verify(boardPostFileCacheService).syncFromActiveFiles("NAC-001");
    }

    @Test
    @DisplayName("uploadFiles: 두 번째 DB 저장 실패 시 첫 번째 성공 응답은 유지하고 두 번째 파일만 실패 목록에 담는다")
    void uploadFiles_secondDbSaveFailure_keepsFirstSuccess(@TempDir java.nio.file.Path tempDir) {
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(1L, 2L);
        org.mockito.Mockito.doNothing()
                .doThrow(new RuntimeException("DB 저장 실패"))
                .when(entityManager)
                .flush();
        MockMultipartFile firstFile =
                new MockMultipartFile(
                        "files",
                        "first.txt",
                        "text/plain",
                        "first".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondFile =
                new MockMultipartFile(
                        "files",
                        "second.txt",
                        "text/plain",
                        "second".getBytes(StandardCharsets.UTF_8));
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("첨부").flTpCone("첨부파일").build();

        FileDto.BulkUploadResponse result =
                fileService.uploadFiles(List.of(firstFile, secondFile), request);

        assertThat(result.getSuccessList())
                .extracting(file -> file.getFlNm())
                .containsExactly("first.txt");
        assertThat(result.getFailList())
                .singleElement()
                .satisfies(
                        message -> assertThat(message).contains("second.txt").contains("DB 저장 실패"));
    }

    // ───────────────────────────────────────────────────────
    // deleteFile — 이미 삭제된 파일(DEL_YN=Y) 재삭제 시도
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: DEL_YN=Y 상태 파일(조회 결과 없음)은 CustomGeneralException을 던진다")
    void deleteFile_이미삭제된파일_CustomGeneralException발생() {
        // Arrange: DEL_YN=Y인 파일은 findByFlMpnIdAndDelYn("N") 결과에서 제외됨
        given(fileRepository.findByFlMpnIdAndDelYn("FL_DELETED", "N"))
                .willReturn(java.util.Optional.empty());

        // Act & Assert: 이미 논리 삭제된 파일 재삭제 시도 → 예외 발생
        assertThatThrownBy(() -> fileService.deleteFile("FL_DELETED"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("존재하지 않는 파일");
    }

    // ───────────────────────────────────────────────────────
    // uploadFile — 0바이트(빈 파일) 업로드
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFile: getSize()=0인 파일이면 EntityManager 접근 없이 예외를 던진다")
    void uploadFile_0바이트파일_CustomGeneralException발생() {
        // Arrange: 내용 없는 MockMultipartFile (isEmpty() == true)
        MockMultipartFile zeroByteFile =
                new MockMultipartFile("file", "zero.pdf", "application/pdf", new byte[0]);
        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("요구사항정의서").flTpCone("첨부파일").build();

        // Act & Assert: 빈 파일 → "업로드할 파일이 비어있습니다" 예외, EntityManager 미호출
        assertThatThrownBy(() -> fileService.uploadFile(zeroByteFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("업로드할 파일이 비어있습니다");
        verifyNoInteractions(entityManager);
    }

    // ───────────────────────────────────────────────────────
    // uploadFiles — 다중 파일 일괄 업로드, 일부 실패 (null 파일 포함)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFiles: null 파일이 포함된 경우 해당 파일만 실패 목록에 포함된다")
    void uploadFiles_null파일포함_해당파일실패목록포함(@TempDir java.nio.file.Path tempDir) {
        // Arrange: 정상 파일 1개 + null 파일 1개
        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(2L);

        MockMultipartFile validFile =
                new MockMultipartFile(
                        "files",
                        "valid.pdf",
                        "application/pdf",
                        "content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // null MultipartFile은 NullPointerException → failList에 포함
        MockMultipartFile nullContentFile =
                new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("요구사항정의서").flTpCone("첨부파일").build();

        // Act
        FileDto.BulkUploadResponse result =
                fileService.uploadFiles(
                        java.util.Arrays.asList(validFile, nullContentFile), request);

        // Assert: 정상 1개 성공, 빈 파일 1개 실패
        assertThat(result.getSuccessList()).hasSize(1);
        assertThat(result.getFailList()).hasSize(1);
        assertThat(result.getFailList().get(0)).contains("empty.txt");
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc — 존재하지 않는 원본구분·원본PK로 일괄 삭제 시도
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFilesByOrc: 해당 원본구분·원본PK에 파일이 없으면 예외 없이 0을 반환한다")
    void deleteFilesByOrc_파일없는원본PK_0반환() {
        // Arrange: DB에 매칭되는 파일 없음
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("없는구분", "PRJ-0000-0000", "N"))
                .willReturn(java.util.Collections.emptyList());

        // Act
        int count = fileService.deleteFilesByOrc("없는구분", "PRJ-0000-0000", USER);

        // Assert: 예외 없이 0 반환
        assertThat(count).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // deleteFilesByOrc — 일부 파일 삭제 (3건 중 Soft Delete 3건)
    // ───────────────────────────────────────────────────────

    // ───────────────────────────────────────────────────────
    // uploadFileInternal — Files.copy IOException (파일 디스크 저장 실패)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "uploadFileInternal: getInputStream()이 IOException을 던지면 파일 저장 실패 예외가 cause 포함으로 반환된다")
    void uploadFileInternal_파일copy실패_cause포함IOException(@TempDir java.nio.file.Path tempDir)
            throws Exception {
        // Arrange: MultipartFile.getInputStream()이 IOException을 던지도록 mock 구성
        // MockMultipartFile은 생성 시 바이트를 미리 읽으므로 mock(MultipartFile)을 사용한다
        org.springframework.web.multipart.MultipartFile mockFile =
                mock(org.springframework.web.multipart.MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false);
        given(mockFile.getOriginalFilename()).willReturn("report.pdf");
        given(mockFile.getInputStream()).willThrow(new IOException("디스크 쓰기 시뮬레이션 오류"));

        configureUploadUnit(tempDir);
        given(fileRepository.getNextSequenceValue()).willReturn(99L);

        FileDto.UploadRequest request =
                FileDto.UploadRequest.builder().pkColNm("파일copy실패").flTpCone("첨부파일").build();

        // Act & Assert: Files.copy(inputStream, ...) → IOException → CustomGeneralException(메시지, e)
        assertThatThrownBy(() -> fileService.uploadFileInternal(mockFile, request))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("파일 저장에 실패했습니다")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("deleteFilesByOrc: 3건 파일을 일괄 Soft Delete하고 3을 반환한다")
    void deleteFilesByOrc_3건일괄삭제_3반환() {
        // Arrange: 파일 3건
        Cfilem f1 = mockCfilem("FL_00000011");
        Cfilem f2 = mockCfilem("FL_00000012");
        Cfilem f3 = mockCfilem("FL_00000013");
        given(fileRepository.findAllByPkColNmAndPkConeAndDelYn("정보화사업", "BIZ-2026-0001", "N"))
                .willReturn(java.util.Arrays.asList(f1, f2, f3));

        // Act
        int count = fileService.deleteFilesByOrc("정보화사업", "BIZ-2026-0001", USER);

        // Assert: 3건 모두 delete() 호출, 반환값 3
        assertThat(count).isEqualTo(3);
        verify(f1).delete();
        verify(f2).delete();
        verify(f3).delete();
    }
}
