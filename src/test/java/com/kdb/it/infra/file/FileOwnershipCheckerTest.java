package com.kdb.it.infra.file;

import com.kdb.it.common.board.entity.Cblbcm;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    private BoardPostRepository boardPostRepository;

    @InjectMocks
    private FileOwnershipChecker fileOwnershipChecker;

    // ── verifyWriteAccess (owner-or-admin, 403) ──

    @Nested
    @DisplayName("verifyWriteAccess — 본인 또는 관리자만 허용(403)")
    class VerifyWriteAccess {

        @Test
        @DisplayName("업로드자 본인이면 예외 없이 통과한다")
        void verifyWriteAccess_owner_noException() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N")).willReturn(Optional.of(file));

            CustomUserDetails owner = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            assertThatCode(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", owner))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("관리자는 타인 파일이어도 예외 없이 통과한다(우회)")
        void verifyWriteAccess_admin_bypass() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N")).willReturn(Optional.of(file));

            CustomUserDetails admin = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

            assertThatCode(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", admin))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본인도 관리자도 아니면 AccessDeniedException(403) 발생")
        void verifyWriteAccess_other_throwsAccessDenied() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N")).willReturn(Optional.of(file));

            CustomUserDetails other = new CustomUserDetails("E002", List.of("ITPZZ001"), "IT001");

            assertThatThrownBy(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", other))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }

        @Test
        @DisplayName("파일이 존재하지 않으면 CustomGeneralException 발생")
        void verifyWriteAccess_fileNotFound_throws() {
            given(fileRepository.findByFlMpnIdAndDelYn("FL_99999999", "N")).willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            assertThatThrownBy(() -> fileOwnershipChecker.verifyWriteAccess("FL_99999999", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일을 찾을 수 없습니다");
        }
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
            when(file.getPkColNm()).thenReturn("정보화사업");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000002", "N"))
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
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000099", "N"))
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
            when(file.getPkColNm()).thenReturn("공통게시판");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_BOARD_01", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails adminUser = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

            // Act & Assert — 관리자 우회: boardPostRepository 호출 없이 통과
            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", adminUser))
                    .doesNotThrowAnyException();
        }
    }

    // ── canRead (boolean) ──

    @Nested
    @DisplayName("canRead — 목록 필터링용 boolean 권한 판정")
    class CanRead {

        @Test
        @DisplayName("공통게시판이 아닌 파일은 항상 읽기 가능(true) — 리포지토리 호출 없음")
        void canRead_nonBoardAlwaysTrue() {
            // Arrange
            Cfilem file = mock(Cfilem.class);
            when(file.getPkColNm()).thenReturn("요구사항정의서");

            CustomUserDetails normalUser = new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");

            // Act & Assert
            assertThat(fileOwnershipChecker.canRead(file, normalUser)).isTrue();
        }

        @Test
        @DisplayName("비공개(sreYn=N) 게시물의 게시판 파일은 비관리자에게 읽기 불가(false)")
        void canRead_hiddenBoardPostDeniedForNonAdmin() {
            // Arrange
            Cfilem file = mock(Cfilem.class);
            when(file.getPkColNm()).thenReturn("공통게시판");
            when(file.getPkCone()).thenReturn("POST-1");

            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("POST-1")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("비공개 게시물")
                    .sreYn("N")
                    .sttDt(null).endDt(null)
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .ancYn("N")
                    .nacUnqId("POST-1").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();
            given(boardPostRepository.findByNacMngNoAndDelYn("POST-1", "N"))
                    .willReturn(Optional.of(post));

            CustomUserDetails normalUser = new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");

            // Act & Assert
            assertThat(fileOwnershipChecker.canRead(file, normalUser)).isFalse();
        }
    }

    // ── checkReadAccess (게시판 파일) ──

    @Nested
    @DisplayName("checkReadAccess — 게시판 파일 접근 권한 검증")
    class CheckReadAccessBoardFile {

        @BeforeEach
        void setUp() {
            Cfilem boardFile = mock(Cfilem.class);
            when(boardFile.getPkColNm()).thenReturn("공통게시판");
            when(boardFile.getPkCone()).thenReturn("NAC-2026-0001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_BOARD_01", "N"))
                    .willReturn(Optional.of(boardFile));
        }

        @Test
        @DisplayName("게시물이 없으면 읽기 권한 없음 — CustomGeneralException 발생")
        void verifyBoardFileAccess_postNotFound_throws() {
            // Arrange
            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert — canRead로 일원화되어 게시물 부재 시 false → 권한 없음 메시지
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
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
                    .ancYn("N")
                    .nacUnqId("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));

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
                    .ancYn("N")
                    .nacUnqId("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));

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
                    .ancYn("N")
                    .nacUnqId("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            // Act & Assert
            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_BOARD_01", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일 다운로드 권한이 없습니다");
        }

        @Test
        @DisplayName("게시판 조회는 전체 공개 — 비관리자도 공개중 게시물의 파일에 접근할 수 있다")
        void verifyBoardFileAccess_normalUser_visiblePost_passes() {
            // Arrange — 공개중 게시물 (게시판 단위 권한 검증 없음)
            Cblbcm post = Cblbcm.builder()
                    .nacMngNo("NAC-2026-0001")
                    .blbMngNo("BLBM-2026-0001")
                    .nacNm("공개 게시물")
                    .sreYn("Y")
                    .sttDt(LocalDate.now().minusDays(1))
                    .endDt(LocalDate.now().plusDays(1))
                    .nacInqNbr(0).flNbr(0).flApgYn("N")
                    .ancYn("N")
                    .nacUnqId("NAC-2026-0001").nacGrpSqn(0).nacGrpLev(0)
                    .delYn("N")
                    .build();

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));

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

            given(boardPostRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                    .willReturn(Optional.of(post));

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
                .ancYn("N")
                .nacUnqId(nacMngNo).nacGrpSqn(0).nacGrpLev(0)
                .delYn("N")
                .build();
    }
}
