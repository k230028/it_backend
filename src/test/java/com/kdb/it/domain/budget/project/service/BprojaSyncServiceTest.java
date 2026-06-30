package com.kdb.it.domain.budget.project.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;

/**
 * BprojaSyncService 단위 테스트.
 *
 * <p>upsert · softDelete 두 공개 메서드의 모든 분기(no-op 가드, 기존 행 갱신, 신규 저장, Soft Delete)를
 * Oracle DB 없이 검증합니다. BprojaRepository는 @Mock으로 교체합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BprojaSyncServiceTest {

    @Mock
    private BprojaRepository bprojaRepository;

    @InjectMocks
    private BprojaSyncService bprojaSyncService;

    // ─────────────────────────────────────────────────────────────────────────
    // 공통 상수
    // ─────────────────────────────────────────────────────────────────────────
    private static final String ABUS_MNG_NO = "PROJ-2026-0001";
    private static final String CNCD_RFR_NO = "REQ-2026-0001";
    private static final String STS_TC      = "42";

    // ─────────────────────────────────────────────────────────────────────────
    // upsert
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upsert 메서드")
    class UpsertTests {

        @Test
        @DisplayName("성공: 행이 존재하지 않으면 신규 Bproja를 save()한다")
        void upsert_행없음_신규저장() {
            // Arrange
            given(bprojaRepository.findById(any(BprojaId.class)))
                    .willReturn(Optional.empty());

            // Act
            bprojaSyncService.upsert(ABUS_MNG_NO, CNCD_RFR_NO, STS_TC);

            // Assert: save()가 1회 호출되어야 한다
            verify(bprojaRepository).save(any(Bproja.class));
        }

        @Test
        @DisplayName("성공: 행이 이미 존재하면 changeStatus·restore를 호출하고 별도 save()를 호출하지 않는다")
        void upsert_행존재_상태갱신후restore() {
            // Arrange: 기존 행을 Mockito mock으로 생성 (protected 생성자 우회)
            Bproja existing = mock(Bproja.class);
            given(bprojaRepository.findById(any(BprojaId.class)))
                    .willReturn(Optional.of(existing));

            // Act
            bprojaSyncService.upsert(ABUS_MNG_NO, CNCD_RFR_NO, STS_TC);

            // Assert: 상태 변경과 복원이 각각 1회 호출되어야 한다
            verify(existing).changeStatus(STS_TC);
            verify(existing).restore();
            // JPA Dirty Checking 활용이므로 save()는 별도로 호출되지 않는다
            verify(bprojaRepository, never()).save(any(Bproja.class));
        }

        @Test
        @DisplayName("가드: abusMngNo가 null이면 no-op — findById 미호출")
        void upsert_abusMngNo_null_noOp() {
            // Act
            bprojaSyncService.upsert(null, CNCD_RFR_NO, STS_TC);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
            verify(bprojaRepository, never()).save(any(Bproja.class));
        }

        @Test
        @DisplayName("가드: abusMngNo가 공백 문자열이면 no-op — findById 미호출")
        void upsert_abusMngNo_blank_noOp() {
            // Act
            bprojaSyncService.upsert("   ", CNCD_RFR_NO, STS_TC);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: cncdRfrNo가 null이면 no-op — findById 미호출")
        void upsert_cncdRfrNo_null_noOp() {
            // Act
            bprojaSyncService.upsert(ABUS_MNG_NO, null, STS_TC);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: cncdRfrNo가 빈 문자열이면 no-op — findById 미호출")
        void upsert_cncdRfrNo_empty_noOp() {
            // Act
            bprojaSyncService.upsert(ABUS_MNG_NO, "", STS_TC);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: itPtlStsTc가 null이면 no-op — findById 미호출")
        void upsert_itPtlStsTc_null_noOp() {
            // Act
            bprojaSyncService.upsert(ABUS_MNG_NO, CNCD_RFR_NO, null);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("경계: 세 인자 모두 null이면 no-op — findById 미호출")
        void upsert_모두null_noOp() {
            // Act
            bprojaSyncService.upsert(null, null, null);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // softDelete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("softDelete 메서드")
    class SoftDeleteTests {

        @Test
        @DisplayName("성공: 행이 존재하면 delete()를 호출한다")
        void softDelete_행존재_delete호출() {
            // Arrange
            Bproja existing = mock(Bproja.class);
            given(bprojaRepository.findById(any(BprojaId.class)))
                    .willReturn(Optional.of(existing));

            // Act
            bprojaSyncService.softDelete(ABUS_MNG_NO, CNCD_RFR_NO);

            // Assert
            verify(existing).delete();
        }

        @Test
        @DisplayName("성공: 행이 존재하지 않으면 delete()를 호출하지 않는다 (no-op)")
        void softDelete_행없음_noOp() {
            // Arrange
            given(bprojaRepository.findById(any(BprojaId.class)))
                    .willReturn(Optional.empty());

            // Act
            bprojaSyncService.softDelete(ABUS_MNG_NO, CNCD_RFR_NO);

            // Assert: findById는 호출되었지만 행이 없으므로 delete()는 미호출
            verify(bprojaRepository).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: abusMngNo가 null이면 no-op — findById 미호출")
        void softDelete_abusMngNo_null_noOp() {
            // Act
            bprojaSyncService.softDelete(null, CNCD_RFR_NO);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: abusMngNo가 공백이면 no-op — findById 미호출")
        void softDelete_abusMngNo_blank_noOp() {
            // Act
            bprojaSyncService.softDelete("  ", CNCD_RFR_NO);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: cncdRfrNo가 null이면 no-op — findById 미호출")
        void softDelete_cncdRfrNo_null_noOp() {
            // Act
            bprojaSyncService.softDelete(ABUS_MNG_NO, null);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("가드: cncdRfrNo가 빈 문자열이면 no-op — findById 미호출")
        void softDelete_cncdRfrNo_empty_noOp() {
            // Act
            bprojaSyncService.softDelete(ABUS_MNG_NO, "");

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }

        @Test
        @DisplayName("경계: 두 입력이 모두 null이면 no-op — findById 미호출")
        void softDelete_모두null_noOp() {
            // Act
            bprojaSyncService.softDelete(null, null);

            // Assert
            verify(bprojaRepository, never()).findById(any(BprojaId.class));
        }
    }
}
