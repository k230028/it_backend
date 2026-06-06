package com.kdb.it.domain.budget.cost.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * {@link XcrLookupService} 단위 테스트.
 *
 * <p>CONTEXT.md 결정 E / REQUIREMENTS.md R3.7 의 표준 환율 조회 헬퍼를 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class XcrLookupServiceTest {

    @Mock private CodeRepository codeRepository;

    @InjectMocks
    private XcrLookupService xcrLookupService;

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 5, 24);

    @Test
    @DisplayName("외화 정상: USD 조회 시 Ccodem.cNm을 BigDecimal로 반환")
    void resolveXcr_외화정상_BigDecimal반환() {
        // given: Ccodem(CUR/USD/XCR, cNm="1400") 존재
        Ccodem ccodem = Ccodem.builder()
                .cId("CUR_C")
                .cdva("USD")
                .cTp("XCR")
                .cNm("1400")
                .sttDt(LocalDate.of(2026, 1, 1))
                .build();
        given(codeRepository.findByCIdAndCdvaWithValidDate("CUR_C", "USD", BASE_DATE))
                .willReturn(Optional.of(ccodem));

        // when
        BigDecimal result = xcrLookupService.resolveXcr("USD", BASE_DATE);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("1400"));
        verify(codeRepository).findByCIdAndCdvaWithValidDate("CUR_C", "USD", BASE_DATE);
    }

    @Test
    @DisplayName("외화 미등록: Ccodem 부재 시 IllegalStateException + 한글 메시지")
    void resolveXcr_외화미등록_IllegalStateException발생() {
        // given: 유효 Ccodem 없음
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("CUR_C"), eq("XYZ"), any(LocalDate.class)))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> xcrLookupService.resolveXcr("XYZ", BASE_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환율 미등록: XYZ")
                .hasMessageContaining("기준일: 2026-05-24");
    }

    @Test
    @DisplayName("KRW/null: Ccodem 조회 미수행하고 null 반환")
    void resolveXcr_KRW또는null_null반환() {
        // when
        BigDecimal krwResult = xcrLookupService.resolveXcr("KRW", LocalDate.now());
        BigDecimal nullResult = xcrLookupService.resolveXcr(null, LocalDate.now());

        // then
        assertThat(krwResult).isNull();
        assertThat(nullResult).isNull();
        verify(codeRepository, never())
                .findByCIdAndCdvaWithValidDate(anyString(), anyString(), any(LocalDate.class));
    }
}
