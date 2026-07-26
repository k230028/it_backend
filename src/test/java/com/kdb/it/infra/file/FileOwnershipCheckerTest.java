package com.kdb.it.infra.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.authz.FileReadAuthorizerRegistry;
import com.kdb.it.infra.file.authz.ReviewCommentFileWriteAuthorizer;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * FileOwnershipChecker 단위 테스트 — SEC-02, SEC-05
 *
 * <p>파일 소유권(업로드자 = 현재 사용자) 검증과, 읽기 판정을 종류별 authorizer 레지스트리로 위임하는 동작을 검증합니다. 종류별 읽기 규칙 자체는 각
 * authorizer 단위 테스트에서 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FileOwnershipCheckerTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileReadAuthorizerRegistry readAuthorizerRegistry;
    @Mock private ReviewCommentFileWriteAuthorizer reviewCommentFileWriteAuthorizer;

    @InjectMocks private FileOwnershipChecker fileOwnershipChecker;

    // ── verifyWriteAccess (owner-or-admin, 403) ──

    @Nested
    @DisplayName("verifyWriteAccess — 본인 또는 관리자만 허용(403)")
    class VerifyWriteAccess {

        @Test
        @DisplayName("업로드자 본인이면 예외 없이 통과한다")
        void verifyWriteAccess_owner_noException() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails owner = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            assertThatCode(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", owner))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("관리자는 타인 파일이어도 예외 없이 통과한다(우회)")
        void verifyWriteAccess_admin_bypass() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails admin = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

            assertThatCode(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", admin))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본인도 관리자도 아니면 AccessDeniedException(403) 발생")
        void verifyWriteAccess_other_throwsAccessDenied() {
            Cfilem file = mock(Cfilem.class);
            when(file.getFstEnrUsid()).thenReturn("E001");
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000001", "N"))
                    .willReturn(Optional.of(file));

            CustomUserDetails other = new CustomUserDetails("E002", List.of("ITPZZ001"), "IT001");

            assertThatThrownBy(() -> fileOwnershipChecker.verifyWriteAccess("FL_00000001", other))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }

        @Test
        @DisplayName("검토의견 첨부는 업로더가 달라도 댓글 작성자 판정이 허용하면 통과한다")
        void verifyWriteAccess_reviewComment_authorizerAllowsAuthor() {
            Cfilem file = mock(Cfilem.class);
            given(file.getPkColNm())
                    .willReturn(ReviewCommentFileWriteAuthorizer.REVIEW_COMMENT_KIND);
            given(fileRepository.findByFlMpnIdAndDelYn("FL_REVIEW_01", "N"))
                    .willReturn(Optional.of(file));
            CustomUserDetails author =
                    new CustomUserDetails("AUTHOR", List.of("ITPZZ001"), "IT001");
            given(reviewCommentFileWriteAuthorizer.canWrite(file, author)).willReturn(true);

            assertThatCode(() -> fileOwnershipChecker.verifyWriteAccess("FL_REVIEW_01", author))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("검토의견 첨부 업로더라도 댓글 작성자 판정이 거부하면 403이 발생한다")
        void verifyWriteAccess_reviewComment_authorizerDeniesUploader() {
            Cfilem file = mock(Cfilem.class);
            given(file.getPkColNm())
                    .willReturn(ReviewCommentFileWriteAuthorizer.REVIEW_COMMENT_KIND);
            given(fileRepository.findByFlMpnIdAndDelYn("FL_REVIEW_02", "N"))
                    .willReturn(Optional.of(file));
            CustomUserDetails uploader =
                    new CustomUserDetails("UPLOADER", List.of("ITPZZ001"), "IT001");
            given(reviewCommentFileWriteAuthorizer.canWrite(file, uploader)).willReturn(false);

            assertThatThrownBy(
                            () -> fileOwnershipChecker.verifyWriteAccess("FL_REVIEW_02", uploader))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("파일 쓰기 권한이 없습니다");
        }

        @Test
        @DisplayName("파일이 존재하지 않으면 CustomGeneralException 발생")
        void verifyWriteAccess_fileNotFound_throws() {
            given(fileRepository.findByFlMpnIdAndDelYn("FL_99999999", "N"))
                    .willReturn(Optional.empty());

            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            assertThatThrownBy(() -> fileOwnershipChecker.verifyWriteAccess("FL_99999999", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일을 찾을 수 없습니다");
        }
    }

    // ── checkReadAccess — 레지스트리 판정 위임 ──

    @Nested
    @DisplayName("checkReadAccess — 레지스트리 판정 위임")
    class CheckReadAccess {

        @Test
        @DisplayName("레지스트리가 읽기 허용하면 예외 없이 통과한다")
        void checkReadAccess_allowed_passes() {
            Cfilem file = mock(Cfilem.class);
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000002", "N"))
                    .willReturn(Optional.of(file));
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
            given(readAuthorizerRegistry.canRead(file, user)).willReturn(true);

            assertThatCode(() -> fileOwnershipChecker.checkReadAccess("FL_00000002", user))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("레지스트리가 거부하면 AccessDeniedException 발생")
        void checkReadAccess_denied_throws() {
            Cfilem file = mock(Cfilem.class);
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000002", "N"))
                    .willReturn(Optional.of(file));
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
            given(readAuthorizerRegistry.canRead(file, user)).willReturn(false);

            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_00000002", user))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("파일 읽기 권한이 없습니다");
        }

        @Test
        @DisplayName("파일이 없으면 CustomGeneralException 발생")
        void checkReadAccess_fileNotFound_throws() {
            given(fileRepository.findByFlMpnIdAndDelYn("FL_00000099", "N"))
                    .willReturn(Optional.empty());
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");

            assertThatThrownBy(() -> fileOwnershipChecker.checkReadAccess("FL_00000099", user))
                    .isInstanceOf(CustomGeneralException.class)
                    .hasMessageContaining("파일을 찾을 수 없습니다");
        }
    }

    // ── canRead — 레지스트리 위임(목록 필터링용 boolean) ──

    @Nested
    @DisplayName("canRead — 레지스트리 위임")
    class CanRead {

        @Test
        @DisplayName("레지스트리 판정 결과(true)를 그대로 반환한다")
        void canRead_delegatesTrue() {
            Cfilem file = mock(Cfilem.class);
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
            given(readAuthorizerRegistry.canRead(file, user)).willReturn(true);

            assertThat(fileOwnershipChecker.canRead(file, user)).isTrue();
        }

        @Test
        @DisplayName("레지스트리 판정 결과(false)를 그대로 반환한다")
        void canRead_delegatesFalse() {
            Cfilem file = mock(Cfilem.class);
            CustomUserDetails user = new CustomUserDetails("E001", List.of("ITPZZ001"), "IT001");
            given(readAuthorizerRegistry.canRead(file, user)).willReturn(false);

            assertThat(fileOwnershipChecker.canRead(file, user)).isFalse();
        }
    }
}
