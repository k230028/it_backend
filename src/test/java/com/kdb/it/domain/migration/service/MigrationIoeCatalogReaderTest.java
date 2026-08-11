package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 비목·환율 공통코드를 이관용 맵으로 접는 규칙을 고정합니다. */
@ExtendWith(MockitoExtension.class)
class MigrationIoeCatalogReaderTest {

    @Mock private CodeRepository codeRepository;

    private MigrationIoeCatalogReader readerWithRepo() {
        return new MigrationIoeCatalogReader(codeRepository);
    }

    @Test
    @DisplayName("비목 코드값명 → 코드값 맵을 만든다")
    void 비목_맵을_만든다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(List.of(code("001", "국내전산임차료"), code("011", "유지보수료")));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).containsEntry("국내전산임차료", "001").containsEntry("유지보수료", "011");
    }

    @Test
    @DisplayName("같은 코드값명이 중복되면 먼저 나온 코드값을 유지한다")
    void 중복_코드값명은_먼저나온값을_유지한다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(List.of(code("001", "유지보수료"), code("999", "유지보수료")));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).containsEntry("유지보수료", "001");
    }

    @Test
    @DisplayName("코드값명이 null인 행은 건너뛴다")
    void 코드값명이_null이면_건너뛴다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(
                        List.of(Ccodem.builder().cId(CommonCodeGroups.IOE).cdva("001").build()));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("통화 → 예산환율 맵을 만든다")
    void 환율_맵을_만든다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N"))
                .thenReturn(List.of(currency("GBP", "1924"), currency("USD", "1432.5")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result)
                .containsEntry("GBP", new BigDecimal("1924"))
                .containsEntry("USD", new BigDecimal("1432.5"));
    }

    @Test
    @DisplayName("환율 값이 숫자로 파싱되지 않는 행은 예외를 던지지 않고 건너뛴다")
    void 숫자가_아닌_환율은_건너뛴다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N"))
                .thenReturn(List.of(currency("XXX", "해당없음"), currency("GBP", "1924")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).doesNotContainKey("XXX").containsEntry("GBP", new BigDecimal("1924"));
    }

    @Test
    @DisplayName("환율 값이 null인 행은 건너뛴다")
    void 환율값이_null이면_건너뛴다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N"))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId(CommonCodeGroups.CURRENCY)
                                        .cdva("JPY")
                                        .build()));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 통화가 중복되면 먼저 나온 환율을 유지한다")
    void 중복_통화는_먼저나온값을_유지한다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N"))
                .thenReturn(List.of(currency("GBP", "1924"), currency("GBP", "9999")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).containsEntry("GBP", new BigDecimal("1924"));
    }

    private static Ccodem code(String cdva, String cdvaNm) {
        return Ccodem.builder().cId(CommonCodeGroups.IOE).cdva(cdva).cdvaNm(cdvaNm).build();
    }

    private static Ccodem currency(String cdva, String cdvaDtlC) {
        return Ccodem.builder()
                .cId(CommonCodeGroups.CURRENCY)
                .cdva(cdva)
                .cdvaDtlC(cdvaDtlC)
                .build();
    }
}
