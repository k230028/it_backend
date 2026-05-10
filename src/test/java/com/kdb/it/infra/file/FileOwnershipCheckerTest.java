package com.kdb.it.infra.file;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileOwnershipChecker 단위 테스트 — SEC-02
 *
 * <p>파일 소유권(업로드자 = 현재 사용자) 검증 로직을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class FileOwnershipCheckerTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileOwnershipChecker fileOwnershipChecker;

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
}
