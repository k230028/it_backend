package com.kdb.it.domain.budget.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.OwnershipVerifier;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalWriteGuardTest {

    private static final String TABLE = "BPROJM";
    private static final String ID = "PRJ-001";
    private static final int SNO = 3;

    @Mock private ApplicationMapRepository applicationMapRepository;

    private ApprovalWriteGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ApprovalWriteGuard(applicationMapRepository);
    }

    @Test
    void 일반사용자는_결재중_결재완료_수기등록을_수정할_수_없다() {
        try (MockedStatic<OwnershipVerifier> ownership = mockStatic(OwnershipVerifier.class)) {
            ownership.when(OwnershipVerifier::isCurrentUserAdmin).thenReturn(false);

            assertThat(ApprovalWriteGuard.blockingStatuses())
                    .containsExactlyInAnyOrder(
                            ApprovalStatus.IN_PROGRESS.code(),
                            ApprovalStatus.COMPLETED.code(),
                            ApprovalStatus.MANUAL.code());
        }
    }

    @Test
    void 시스템관리자는_결재중만_수정할_수_없다() {
        try (MockedStatic<OwnershipVerifier> ownership = mockStatic(OwnershipVerifier.class)) {
            ownership.when(OwnershipVerifier::isCurrentUserAdmin).thenReturn(true);

            assertThat(ApprovalWriteGuard.blockingStatuses())
                    .containsExactly(ApprovalStatus.IN_PROGRESS.code());
        }
    }

    @Test
    void 일반사용자_차단안내에_수기등록을_포함한다() {
        try (MockedStatic<OwnershipVerifier> ownership = mockStatic(OwnershipVerifier.class)) {
            ownership.when(OwnershipVerifier::isCurrentUserAdmin).thenReturn(false);

            assertThat(ApprovalWriteGuard.blockMessage("수정")).contains("수기등록");
        }
    }

    @Test
    void 신청서가_없으면_삭제할_수_있다() {
        when(applicationMapRepository.findLatestApplicationStatus(TABLE, ID, SNO))
                .thenReturn(Optional.empty());

        assertThatCode(() -> guard.verifyDeletable(TABLE, ID, SNO)).doesNotThrowAnyException();
    }

    @Test
    void 작성완료이면_삭제할_수_있다() {
        when(applicationMapRepository.findLatestApplicationStatus(TABLE, ID, SNO))
                .thenReturn(Optional.of(ApprovalStatus.DRAFTED.code()));

        assertThatCode(() -> guard.verifyDeletable(TABLE, ID, SNO)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = ApprovalStatus.class,
            names = {"DRAFTED"},
            mode = EnumSource.Mode.EXCLUDE)
    void 작성완료_외의_결재상태는_삭제할_수_없다(ApprovalStatus status) {
        when(applicationMapRepository.findLatestApplicationStatus(TABLE, ID, SNO))
                .thenReturn(Optional.of(status.code()));

        assertThatThrownBy(() -> guard.verifyDeletable(TABLE, ID, SNO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("임시저장 또는 작성완료");
    }
}
