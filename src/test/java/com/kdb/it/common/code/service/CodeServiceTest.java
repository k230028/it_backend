package com.kdb.it.common.code.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CcodemResponseRow;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.exception.CustomGeneralException;
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

/**
 * CodeService 단위 테스트
 *
 * <p>공통코드 서비스의 조회·생성·수정·삭제 메서드와 예산신청기간 검증을 테스트합니다. Ccodem 엔티티는 protected 생성자를 우회하기 위해
 * Mockito.mock()으로 생성합니다. Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeServiceTest {

    @Mock private CodeRepository codeRepository;

    @InjectMocks private CodeService codeService;

    private Ccodem mockCcodem(String cdva, String cTp) {
        Ccodem ccodem = mock(Ccodem.class);
        given(ccodem.getCdva()).willReturn(cdva);
        given(ccodem.getCTp()).willReturn(cTp);
        given(ccodem.getCNm()).willReturn("테스트코드");
        given(ccodem.getCId()).willReturn("CD001");
        return ccodem;
    }

    /** REST 응답 경로(getCcodemsByCId/getCcodem/getCcodemsByCTp) 테스트용 응답 프로젝션 행 생성 */
    private CcodemResponseRow responseRow(String cId, String cdva, String cTp) {
        return new CcodemResponseRow(
                cId,
                cdva,
                "테스트코드값명",
                "테스트코드",
                "테스트약어",
                "테스트적요",
                "테스트상세코드",
                cTp,
                "테스트인스턴스내용",
                null,
                1,
                "20260101",
                "20991231",
                "N",
                null,
                null,
                null,
                null);
    }

    // ───────────────────────────────────────────────────────
    // getCcodemsByCId (카테고리 다건 조회)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCcodemsByCId: 코드ID로 조회하면 DTO 목록을 반환한다")
    void getCcodemsByCId_코드ID조회_DTO목록반환() {
        CcodemResponseRow r1 = responseRow("PRJ_TP", "001", "PRJ_TP");
        CcodemResponseRow r2 = responseRow("PRJ_TP", "002", "PRJ_TP");
        given(codeRepository.findResponseRowsByCIdWithValidDate(eq("PRJ_TP"), any()))
                .willReturn(List.of(r1, r2));

        List<CodeDto.Response> result = codeService.getCcodemsByCId("PRJ_TP", null);

        assertThat(result).hasSize(2);
    }

    // ───────────────────────────────────────────────────────
    // getCcodem (단건 조회)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCcodem: 유효한 코드ID+코드값으로 조회하면 Response DTO를 반환한다")
    void getCcodem_유효한코드_Response반환() {
        CcodemResponseRow row = responseRow("CD001", "001", "PRJ_TP");
        given(codeRepository.findResponseRowByCIdAndCdvaWithValidDate(eq("CD001"), eq("001"), any()))
                .willReturn(Optional.of(row));

        CodeDto.Response result = codeService.getCcodem("CD001", "001", null);

        assertThat(result.getCdva()).isEqualTo("001");
    }

    @Test
    @DisplayName("getCcodem: 존재하지 않으면 IllegalArgumentException을 던진다")
    void getCcodem_존재하지않음_IllegalArgumentException발생() {
        given(codeRepository.findResponseRowByCIdAndCdvaWithValidDate(eq("INVALID"), eq("001"), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> codeService.getCcodem("INVALID", "001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    // ───────────────────────────────────────────────────────
    // getCcodemsByCTp (코드타입 다건 조회)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCcodemsByCTp: 코드타입으로 조회하면 DTO 목록을 반환한다")
    void getCcodemsByCTp_코드타입조회_DTO목록반환() {
        CcodemResponseRow r1 = responseRow("CD001", "001", "IOE_LEAFE");
        CcodemResponseRow r2 = responseRow("CD001", "002", "IOE_LEAFE");
        given(codeRepository.findResponseRowsByCTpWithValidDate(eq("IOE_LEAFE"), any()))
                .willReturn(List.of(r1, r2));

        List<CodeDto.Response> result = codeService.getCcodemsByCTp("IOE_LEAFE", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCTp()).isEqualTo("IOE_LEAFE");
    }

    // ───────────────────────────────────────────────────────
    // createCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCcodem: 중복 코드이면 IllegalArgumentException을 던진다")
    void createCcodem_중복코드_IllegalArgumentException발생() {
        CodeDto.CreateRequest request = new CodeDto.CreateRequest();
        request.setCId("CD001");
        request.setCdva("001");
        request.setSttDt("20260101");
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CD001", "001", "20260101"))
                .willReturn(true);

        assertThatThrownBy(() -> codeService.createCcodem(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CD001");
    }

    @Test
    @DisplayName("createCcodem: 시작일자가 없으면 IllegalArgumentException을 던진다")
    void createCcodem_시작일자없음_IllegalArgumentException발생() {
        CodeDto.CreateRequest request = new CodeDto.CreateRequest();
        request.setCId("CD001");
        request.setCdva("001");

        assertThatThrownBy(() -> codeService.createCcodem(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시작일자");
    }

    @Test
    @DisplayName("createCcodem: 신규 코드이면 저장 후 cId를 반환한다")
    void createCcodem_신규코드_cId반환() {
        CodeDto.CreateRequest request = new CodeDto.CreateRequest();
        request.setCId("CD001");
        request.setCdva("001");
        request.setCNm("테스트코드");
        request.setCTp("PRJ_TP");
        request.setSttDt("20260101");
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CD001", "001", "20260101"))
                .willReturn(false);

        String result = codeService.createCcodem(request);

        assertThat(result).isEqualTo("CD001");
        verify(codeRepository).save(any(Ccodem.class));
    }

    // ───────────────────────────────────────────────────────
    // updateCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCcodem: 존재하지 않으면 IllegalArgumentException을 던진다")
    void updateCcodem_존재하지않음_IllegalArgumentException발생() {
        String sttDt = "20260101";
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("INVALID", "001", sttDt, "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                codeService.updateCcodem(
                                        "INVALID", "001", sttDt, new CodeDto.UpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    @DisplayName("updateCcodem: 존재하는 코드이면 update 후 cId를 반환한다")
    void updateCcodem_존재하는코드_update호출() {
        String sttDt = "20260101";
        Ccodem ccodem = mockCcodem("001", "PRJ_TP");
        CodeDto.UpdateRequest request = new CodeDto.UpdateRequest();
        request.setCNm("수정명");
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CD001", "001", sttDt, "N"))
                .willReturn(Optional.of(ccodem));

        String result = codeService.updateCcodem("CD001", "001", sttDt, request);

        assertThat(result).isEqualTo("CD001");
        verify(ccodem)
                .update(
                        eq("수정명"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                        isNull(), isNull(), isNull());
    }

    // ───────────────────────────────────────────────────────
    // deleteCcodem
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCcodem: 존재하지 않으면 IllegalArgumentException을 던진다")
    void deleteCcodem_존재하지않음_IllegalArgumentException발생() {
        String sttDt = "20260101";
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("INVALID", "001", sttDt, "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> codeService.deleteCcodem("INVALID", "001", sttDt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    @DisplayName("deleteCcodem: 존재하는 코드이면 논리 삭제를 수행한다")
    void deleteCcodem_존재하는코드_논리삭제수행() {
        Ccodem ccodem = mockCcodem("001", "PRJ_TP");
        String sttDt = "20260101";
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CD001", "001", sttDt, "N"))
                .willReturn(Optional.of(ccodem));

        codeService.deleteCcodem("CD001", "001", sttDt);

        verify(ccodem).delete();
    }

    // ───────────────────────────────────────────────────────
    // validateBudgetPeriod / getBudgetPeriod
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateBudgetPeriod: 현재 날짜가 신청기간 내이면 예외가 발생하지 않는다")
    void validateBudgetPeriod_기간내_예외없음() {
        Ccodem sta = mockCcodem("STA", "BG_RQS");
        given(sta.getCdvaDtlC()).willReturn("2020-01-01");
        Ccodem end = mockCcodem("END", "BG_RQS");
        given(end.getCdvaDtlC()).willReturn("2099-12-31");
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("STA"), any()))
                .willReturn(Optional.of(sta));
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("END"), any()))
                .willReturn(Optional.of(end));

        codeService.validateBudgetPeriod();
    }

    @Test
    @DisplayName("validateBudgetPeriod: 현재 날짜가 신청기간 이전이면 CustomGeneralException을 던진다")
    void validateBudgetPeriod_기간이전_CustomGeneralException발생() {
        Ccodem sta = mockCcodem("STA", "BG_RQS");
        given(sta.getCdvaDtlC()).willReturn("2099-01-01");
        Ccodem end = mockCcodem("END", "BG_RQS");
        given(end.getCdvaDtlC()).willReturn("2099-12-31");
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("STA"), any()))
                .willReturn(Optional.of(sta));
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("END"), any()))
                .willReturn(Optional.of(end));

        assertThatThrownBy(() -> codeService.validateBudgetPeriod())
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("예산 신청 기간이 아닙니다");
    }

    @Test
    @DisplayName("validateBudgetPeriod: 현재 날짜가 신청기간 이후이면 CustomGeneralException을 던진다")
    void validateBudgetPeriod_기간이후_CustomGeneralException발생() {
        Ccodem sta = mockCcodem("STA", "BG_RQS");
        given(sta.getCdvaDtlC()).willReturn("2000-01-01");
        Ccodem end = mockCcodem("END", "BG_RQS");
        given(end.getCdvaDtlC()).willReturn("2000-12-31");
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("STA"), any()))
                .willReturn(Optional.of(sta));
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("END"), any()))
                .willReturn(Optional.of(end));

        assertThatThrownBy(() -> codeService.validateBudgetPeriod())
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("예산 신청 기간이 아닙니다");
    }

    @Test
    @DisplayName("getBudgetPeriod: 종료 코드가 없으면 IllegalArgumentException을 던진다")
    void getBudgetPeriod_종료코드없음_IllegalArgumentException발생() {
        Ccodem sta = mockCcodem("STA", "BG_RQS");
        given(sta.getCdvaDtlC()).willReturn("2026-01-01");
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("STA"), any()))
                .willReturn(Optional.of(sta));
        given(codeRepository.findByCIdAndCdvaWithValidDate(eq("BG_RQS"), eq("END"), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> codeService.getBudgetPeriod())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일자");
    }

    // ───────────────────────────────────────────────────────
    // findCodeEntitiesByCId
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findCodeEntitiesByCId: 코드ID로 엔티티 목록을 반환한다")
    void findCodeEntitiesByCId_엔티티목록반환() {
        Ccodem code = mockCcodem("001", "PRJ_TP");
        given(codeRepository.findByCIdWithValidDate("PRJ_TP", null)).willReturn(List.of(code));

        assertThat(codeService.findCodeEntitiesByCId("PRJ_TP")).containsExactly(code);
    }
}
