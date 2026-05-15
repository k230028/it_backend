package com.kdb.it.infra.file;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileOwnershipChecker 단위 테스트 — SEC-02
 *
 * <p>파일 소유권(업로드자 = 현재 사용자) 검증과 파일 다운로드 권한 검증 로직을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class FileOwnershipCheckerTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private BoardMetaRepository boardMetaRepository;
    @Mock
    private BoardPostRepository boardPostRepository;

    @InjectMocks
    private FileOwnershipChecker fileOwnershipChecker;

    // ── checkOwnership ──

    @Test
    @DisplayName("업로드자와 현재 사용자가 동일하면 예외 없음")
    void checkOwnership_sameUser_noException() {
        Cfilem file = mock(Cfilem.class);
        when(file.getFstEnrUsid()).thenReturn("E001");
        given(fileRepository.findByFlMngNoAndDelYn("FL_00000001", "N")).willReturn(Optional.of(file));

        assertThatCode(() -> fileOwnershipChecker.checkOwnership("FL_00000001", "E001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("업로드자와 현재 사용자가 다르면 CustomGeneralException 발생")
    void checkOwnership_differentUser_throwsException() {
        Cfilem file = mock(Cfilem.class);
        when(file.getFstEnrUsid()).thenReturn("E001");
        given(fileRepository.findByFlMngNoAndDelYn("FL_00000001", "N")).willReturn(Optional.of(file));

        assertThatThrownBy(() -> fileOwnershipChecker.checkOwnership("FL_00000001", "E002"))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("본인이 업로드한 파일만");
    }

    @Test
    @DisplayName("존재하지 않는 파일 ID 시 CustomGeneralException 발생")
    void checkOwnership_fileNotFound_throwsException() {
        given(fileRepository.findByFlMngNoAndDelYn("FL_99999999", "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileOwnershipChecker.checkOwnership("FL_99999999", "E001"))
                .isInstanceOf(CustomGeneralException.class);
    }

    // ── checkReadAccess ──

    @Nested
    @DisplayName("checkReadAccess — ORC_DTT 분기")
    class CheckReadAccess {

        @Test
        @DisplayName("ORC_DTT가 공통게시판이 아닌 경우 별도 검증 없이 통과한다")
        void checkReadAccess_nonBoardFile_alwaysPasses() {
            // Arrange
            Cfilem file = mock(Cfilem.class);
            when(file.getOrcDtt()).thenReturn("정보화사업");
            given(fileRepository.findByFlMngNoAndDelYn("FL_00000002", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails normalUser = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_00000002", normalUser))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("파일이 없으면 CustomGeneralException 발생")
        void checkReadAccess_fileNotFound_throws() {
            // Arrange
            given(fileRepository.findByFlMngNoAndDelYn("FL_00000099", "N"))
                    .willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_00000099", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("ORC_DTT=공통게시판이고 관리자이면 예외 없이 통과한다")
        void checkReadAccess_boardFile_adminBypass() {
            // Arrange
            Cfilem file = mock(Cfilem.class);
            when(file.getOrcDtt()).thenReturn("공통게시판");
            given(fileRepository.findByFlMngNoAndDelYn("FL_BOARD_01", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails adminUser = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

            // Act & Assert — 관리자 우회: boardPostRepository, boardMetaRepository 호출 없이 통과
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", adminUser))
                    .doesNotThrowAnyException();
        }
    }

    // ── verifyBoardFileAccess ──

    @Nested
    @DisplayName("verifyBoardFileAccess — 게시판 파일 접근 권한 검증")
    class VerifyBoardFileAccess {

        @BeforeEach
        void setUp() {
            Cfilem boardFile = mock(Cfilem.class);
            when(boardFile.getOrcDtt()).thenReturn("공통게시판");
            when(boardFile.getOrcPkVl()).thenReturn("NAC-2026-0001");
            given(fileRepository.findByFlMngNoAndDelYn("FL_BOARD_01", "N"))
                    .willReturn(Optional.of(boardFile));
        }

        @Test
        @DisplayName("게시물이 없으면 CustomGeneralException 발생")
        void verifyBoardFileAccess_postNotFound_throws() {
            // Arrange
            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("게시물을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("게시판이 없으면 CustomGeneralException 발생")
        void verifyBoardFileAccess_boardNotFound_throws() {
            // Arrange
            Cblbcm post = buildPost("NAC-2026-0001", "BLBM-2026-0001");
            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("게시판을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("boardOk=false — 사용자 권한이 게시판 조회권한과 불일치하면 예외 발생")
        void verifyBoardFileAccess_boardOkFalse_throws() {
            // Arrange — inqAthC=ROLE_ADMIN, 일반사용자는 ROLE_USER만 보유
            Cblbcm post = buildPost("NAC-2026-0001", "BLBM-2026-0001");
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("관리자게시판")
                    .inqAthC("ROLE_ADMIN").enrAthC("ROLE_ADMIN")
                    .repUseYn("N").cmmtUseYn("N")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails normalUser = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", normalUser))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
        }

        @Test
        @DisplayName("boardOk=true, inqAthC=ALL — 모든 사용자 접근 허용")
        void verifyBoardFileAccess_boardOkAll_passes() {
            // Arrange
            Cblbcm post = buildPost("NAC-2026-0001", "BLBM-2026-0001");
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
                    .inqAthC("ALL").enrAthC("ALL")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails normalUser = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", normalUser))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("postOk=false — sreYn=N인 게시물의 파일은 접근 불가")
        void verifyBoardFileAccess_postNotVisible_throws() {
            // Arrange — sreYn=N (화면표시 안 함)
            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("NAC-2026-0001")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("비공개 게시물")
                    .sreYn("N")
                    .sttDt(null).endDt(null)
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .pritC("01").hrkFxnYn("N")
                    .nacGrpNo("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
                    .inqAthC("ALL").enrAthC("ALL")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
        }

        @Test
        @DisplayName("postOk=false — 공개 시작일이 오늘 이후인 게시물의 파일은 접근 불가")
        void verifyBoardFileAccess_postNotStarted_throws() {
            // Arrange — sttDt = 미래 날짜
            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("NAC-2026-0001")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("예약 게시물")
                    .sreYn("Y")
                    .sttDt(LocalDate.now().plusDays(1))
                    .endDt(null)
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .pritC("01").hrkFxnYn("N")
                    .nacGrpNo("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
                    .inqAthC("ALL").enrAthC("ALL")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
        }

        @Test
        @DisplayName("postOk=false — 공개 종료일이 어제인 게시물의 파일은 접근 불가")
        void verifyBoardFileAccess_postExpired_throws() {
            // Arrange — endDt = 어제
            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("NAC-2026-0001")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("만료 게시물")
                    .sreYn("Y")
                    .sttDt(null)
                    .endDt(LocalDate.now().minusDays(1))
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .pritC("01").hrkFxnYn("N")
                    .nacGrpNo("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
                    .inqAthC("ALL").enrAthC("ALL")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
        }

        @Test
        @DisplayName("boardOk=true (권한 일치), postOk=true (공개중) — 정상 접근 허용")
        void verifyBoardFileAccess_allOk_passes() {
            // Arrange — ROLE_USER 권한 게시판, 공개중 게시물
            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("NAC-2026-0001")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("공개 게시물")
                    .sreYn("Y")
                    .sttDt(LocalDate.now().minusDays(1))
                    .endDt(LocalDate.now().plusDays(1))
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .pritC("01").hrkFxnYn("N")
                    .nacGrpNo("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("사용자게시판")
                    .inqAthC("ROLE_USER").enrAthC("ROLE_USER")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            // ITPZZ001 → ROLE_USER 권한 보유
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sttDt=null, endDt=null — 기간 제한 없는 공개 게시물은 접근 허용")
        void verifyBoardFileAccess_noDates_passes() {
            // Arrange — sttDt=null, endDt=null (항상 공개)
            Cblbcm post = buildPost("NAC-2026-0001", "BLBM-2026-0001");
            Cblbmm board = Cblbmm.builder()
                    .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
                    .inqAthC("ALL").enrAthC("ALL")
                    .repUseYn("N").cmmtUseYn("Y")
                    .bbrLmtnUseYn("N").useYn("Y").delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));
            given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                    .willReturn(Optional.of(board));

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .doesNotThrowAnyException();
        }
    }

    // ── 내부 헬퍼 ──

    /**
     * sreYn=Y, sttDt=null, endDt=null인 기본 게시물 엔티티 생성 헬퍼.
     */
    private Cblbcm buildPost(String nacMngNo, String blbMngNo) {
        return Cblbcm.builder()
                .nacMngNo(nacMngNo)
                .blbMngNo(blbMngNo)
                .nacNm("테스트 게시물")
                .sreYn("Y")
                .sttDt(null)
                .endDt(null)
                .nacInqNbr(0).flNbr(0).flApgYn("N")
                .pritC("01").hrkFxnYn("N")
                .nacGrpNo(nacMngNo).nacGrpSqn(0).nacGrpLev(0)
                .delYn("N")
                .build();
    }
}
