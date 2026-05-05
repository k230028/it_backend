package com.kdb.it.common.code.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.exception.CustomGeneralException;

/**
 * CodeService 단위 테스트
 *
 * <p>
 * 공통코드 서비스의 조회·생성·수정·삭제 메서드와 예산신청기간 검증을 테스트합니다.
 * Ccodem 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeServiceTest {

    @Mock
    private CodeRepository codeRepository;

    @InjectMocks
    private CodeService codeService;

    private Ccodem mockCcodem(String cdId, String cttTp) {
        Ccodem ccodem = mock(Ccodem.class);
        given(ccodem.getCdId()).willReturn(cdId);
        given(ccodem.getCttTp()).willReturn(cttTp);
        given(ccodem.getCdNm()).willReturn("테스트코드");
        given(ccodem.getCdva()).willReturn("value");
        return ccodem;
    }

    // ───────────────────────────────────────────────────────
    // getCcodemById
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCcodemById: 유효한 코드ID로 조회하면 Response DTO를 반환한다")
    void getCcodemById_유효한코드ID_Response반환() {
        // given
        Ccodem ccodem = mockCcodem("CD001", "PRJ_TP");
        given(codeRepository.findByCdIdWithValidDate(eq("CD001"), any())).willReturn(Optional.of(ccodem));

        // when
        CodeDto.Response result = codeService.getCcodemById("CD001", null);

        // then
        assertThat(result.getCdId()).isEqualTo("CD001");
        assertThat(result.getCttTp()).isEqualTo("PRJ_TP");
    }

    @Test
    @DisplayName("getCcodemById: 존재하지 않는 코드ID이면 IllegalArgumentException을 던진다")
    void getCcodemById_존재하지않는코드ID_IllegalArgumentException발생() {
        // given
        given(codeRepository.findByCdIdWithValidDate(eq("INVALID"), any())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> codeService.getCcodemById("INVALID", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    // ───────────────────────────────────────────────────────
    // getCcodemByCttTp
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCcodemByCttTp: 코드값구분으로 조회하면 DTO 목록을 반환한다")
    void getCcodemByCttTp_코드값구분조회_DTO목록반환() {
        // given
        Ccodem c1 = mockCcodem("CD001", "PRJ_TP");
        Ccodem c2 = mockCcodem("CD002", "PRJ_TP");
        given(codeRepository.findByCttTpWithValidDate(eq("PRJ_TP"), any())).willReturn(List.of(c1, c2));

        // when
        List<CodeDto.Response> result = codeService.getCcodemByCttTp("PRJ_TP", null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCdId()).isEqualTo("CD001");
        assertThat(result.get(1).getCdId()).isEqualTo("CD002");
    }

    // ───────────────────────────────────────────────────────
    // createCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCcodem: 중복 코드ID이면 IllegalArgumentException을 던진다")
    void createCcodem_중복코드ID_IllegalArgumentException발생() {
        // given
        CodeDto.CreateRequest request = new CodeDto.CreateRequest();
        request.setCdId("CD001");
        request.setSttDt(LocalDate.of(2026, 1, 1));
        given(codeRepository.existsByCdIdAndSttDt("CD001", LocalDate.of(2026, 1, 1))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> codeService.createCcodem(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CD001");
    }

    @Test
    @DisplayName("createCcodem: 신규 코드이면 저장 후 코드ID를 반환한다")
    void createCcodem_신규코드_코드ID반환() {
        // given
        CodeDto.CreateRequest request = new CodeDto.CreateRequest();
        request.setCdId("CD001");
        request.setCdNm("테스트코드");
        request.setCttTp("PRJ_TP");
        request.setSttDt(LocalDate.of(2026, 1, 1));
        given(codeRepository.existsByCdIdAndSttDt("CD001", LocalDate.of(2026, 1, 1))).willReturn(false);

        // when
        String result = codeService.createCcodem(request);

        // then
        assertThat(result).isEqualTo("CD001");
        verify(codeRepository).save(any(Ccodem.class));
    }

    // ───────────────────────────────────────────────────────
    // updateCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCcodem: 존재하지 않는 코드ID이면 IllegalArgumentException을 던진다")
    void updateCcodem_존재하지않는코드ID_IllegalArgumentException발생() {
        // given
        LocalDate sttDt = LocalDate.of(2026, 1, 1);
        given(codeRepository.findByCdIdAndSttDtAndDelYn("INVALID", sttDt, "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> codeService.updateCcodem("INVALID", sttDt, new CodeDto.UpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    // ───────────────────────────────────────────────────────
    // deleteCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCcodem: 존재하지 않는 코드ID이면 IllegalArgumentException을 던진다")
    void deleteCcodem_존재하지않는코드ID_IllegalArgumentException발생() {
        // given
        LocalDate sttDt = LocalDate.of(2026, 1, 1);
        given(codeRepository.findByCdIdAndSttDtAndDelYn("INVALID", sttDt, "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> codeService.deleteCcodem("INVALID", sttDt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    @DisplayName("deleteCcodem: 존재하는 코드이면 논리 삭제를 수행한다")
    void deleteCcodem_존재하는코드_논리삭제수행() {
        // given
        Ccodem ccodem = mockCcodem("CD001", "PRJ_TP");
        LocalDate sttDt = LocalDate.of(2026, 1, 1);
        given(codeRepository.findByCdIdAndSttDtAndDelYn("CD001", sttDt, "N")).willReturn(Optional.of(ccodem));

        // when
        codeService.deleteCcodem("CD001", sttDt);

        // then
        verify(ccodem).delete();
    }

    // ───────────────────────────────────────────────────────
    // validateBudgetPeriod
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateBudgetPeriod: 현재 날짜가 신청기간 내이면 예외가 발생하지 않는다")
    void validateBudgetPeriod_기간내_예외없음() {
        // given — 시작일은 과거, 종료일은 미래
        Ccodem startCode = mockCcodem("BG-RQS-STA", "BUDGET");
        given(startCode.getCdva()).willReturn("2020-01-01");
        Ccodem endCode = mockCcodem("BG-RQS-END", "BUDGET");
        given(endCode.getCdva()).willReturn("2099-12-31");
        given(codeRepository.findByCdIdWithValidDate(eq("BG-RQS-STA"), any())).willReturn(Optional.of(startCode));
        given(codeRepository.findByCdIdWithValidDate(eq("BG-RQS-END"), any())).willReturn(Optional.of(endCode));

        // when & then — 예외 없이 정상 완료
        codeService.validateBudgetPeriod();
    }

    @Test
    @DisplayName("validateBudgetPeriod: 현재 날짜가 신청기간 이전이면 CustomGeneralException을 던진다")
    void validateBudgetPeriod_기간외_CustomGeneralException발생() {
        // given — 시작일과 종료일 모두 미래 (현재는 기간 전)
        Ccodem startCode = mockCcodem("BG-RQS-STA", "BUDGET");
        given(startCode.getCdva()).willReturn("2099-01-01");
        Ccodem endCode = mockCcodem("BG-RQS-END", "BUDGET");
        given(endCode.getCdva()).willReturn("2099-12-31");
        given(codeRepository.findByCdIdWithValidDate(eq("BG-RQS-STA"), any())).willReturn(Optional.of(startCode));
        given(codeRepository.findByCdIdWithValidDate(eq("BG-RQS-END"), any())).willReturn(Optional.of(endCode));

        // when & then
        assertThatThrownBy(() -> codeService.validateBudgetPeriod())
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("예산 신청 기간이 아닙니다");
    }
}
