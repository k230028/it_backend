package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectItemSynchronizerTest {

    @Mock private ProjectItemRepository itemRepository;
    @Mock private XcrLookupService xcrLookupService;

    private ProjectItemSynchronizer synchronizer;
    private Bprojm project;

    @BeforeEach
    void setUp() {
        synchronizer = new ProjectItemSynchronizer(itemRepository, xcrLookupService);
        project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAmounts")
    @DisplayName("등록은 통화별 금액 불변식 위반을 공용 폴백 전에 거부한다")
    void createAll_rejectsInvalidCurrencyAmountInvariant(
            String ignored,
            String currency,
            String amount,
            String foreignAmount,
            String expectedMessage) {
        ProjectDto.BitemmDto item = item(null, currency, amount, foreignAmount);

        assertThatThrownBy(() -> synchronizer.createAll(project, List.of(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        verify(xcrLookupService, never())
                .resolveXcr(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(itemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAmounts")
    @DisplayName("수정은 기존값과 같아도 통화별 금액 불변식 위반을 거부한다")
    void sync_rejectsInvalidCurrencyAmountInvariant(
            String ignored,
            String currency,
            String amount,
            String foreignAmount,
            String expectedMessage) {
        ProjectDto.BitemmDto requested = item("GCL-2026-0001", currency, amount, foreignAmount);
        Bitemm existing =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(1)
                        .abusMngNo(project.getAbusMngNo())
                        .fntTbCrySno(project.getSno())
                        .curC(currency)
                        .amt(decimal(amount))
                        .fcAmt(decimal(foreignAmount))
                        .mplAmt(BigDecimal.ZERO)
                        .lstYn("Y")
                        .build();
        given(
                        itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                project.getAbusMngNo(), project.getSno(), "N"))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> synchronizer.sync(project, List.of(requested)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        verify(xcrLookupService, never())
                .resolveXcr(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(itemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("등록은 품목별 AMT를 저장 스케일로 먼저 반올림해 재조회 합계를 보존한다")
    void createAll_normalizesEachCurrentAmountBeforeSummaryCalculation() {
        given(itemRepository.getNextSequenceValue()).willReturn(1L, 2L);
        given(
                        xcrLookupService.resolveXcr(
                                org.mockito.ArgumentMatchers.eq("KRW"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(null);
        ProjectDto.BitemmDto first = item(null, "KRW", "0.0005", null);
        ProjectDto.BitemmDto second = item(null, "KRW", "0.0005", null);

        synchronizer.createAll(project, List.of(first, second));

        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        verify(itemRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Bitemm::getAmt)
                .containsExactly(new BigDecimal("0.001"), new BigDecimal("0.001"));
        ProjectAmountSummary reread =
                new ProjectAmountCalculator().calculate(captor.getAllValues(), BigDecimal.ZERO);
        assertThat(reread.currentRequestAmt()).isEqualTo(new BigDecimal("0.002"));
    }

    @Test
    @DisplayName("외화 MPL은 저장 스케일로 반올림한 뒤 재조회 시 같은 환산 합계를 만든다")
    void createAll_normalizesForeignPlannedAmountBeforeSummaryCalculation() {
        given(itemRepository.getNextSequenceValue()).willReturn(1L);
        given(
                        xcrLookupService.resolveXcr(
                                org.mockito.ArgumentMatchers.eq("USD"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(new BigDecimal("2"));
        ProjectDto.BitemmDto requested = item(null, "USD", "999", "1");
        requested.setMplAmt(new BigDecimal("1.2345"));

        synchronizer.createAll(project, List.of(requested));

        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        verify(itemRepository).save(captor.capture());
        Bitemm stored = captor.getValue();
        assertThat(stored.getMplAmt()).isEqualTo(new BigDecimal("1.235"));
        ProjectAmountSummary reread =
                new ProjectAmountCalculator().calculate(List.of(stored), BigDecimal.ZERO);
        assertThat(reread.plannedAmt()).isEqualTo(new BigDecimal("2.470"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedStoredAmounts")
    @DisplayName("등록은 개별 FC/MPL의 NUMBER(18,3) 초과를 업무 예외로 거부한다")
    void createAll_rejectsOversizedStoredAmount(
            String ignored,
            String currency,
            String foreignAmount,
            String plannedAmount,
            String expectedMessage) {
        ProjectDto.BitemmDto requested = item(null, currency, "1", foreignAmount);
        requested.setMplAmt(decimal(plannedAmount));

        assertThatThrownBy(() -> synchronizer.createAll(project, List.of(requested)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        verify(itemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedStoredAmounts")
    @DisplayName("수정은 개별 FC/MPL의 NUMBER(18,3) 초과를 업무 예외로 거부한다")
    void sync_rejectsOversizedStoredAmount(
            String ignored,
            String currency,
            String foreignAmount,
            String plannedAmount,
            String expectedMessage) {
        ProjectDto.BitemmDto requested = item("GCL-2026-0001", currency, "1", foreignAmount);
        requested.setMplAmt(decimal(plannedAmount));
        Bitemm existing =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(1)
                        .abusMngNo(project.getAbusMngNo())
                        .fntTbCrySno(project.getSno())
                        .curC(currency)
                        .amt(BigDecimal.ONE)
                        .fcAmt("USD".equals(currency) ? BigDecimal.ONE : null)
                        .mplAmt(BigDecimal.ZERO)
                        .lstYn("Y")
                        .build();
        given(
                        itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                project.getAbusMngNo(), project.getSno(), "N"))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> synchronizer.sync(project, List.of(requested)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    @DisplayName("수정은 유효한 KRW 품목을 정규화된 FC로 USD 품목으로 전환한다")
    void sync_changesKrwToUsdWithForeignAmount() {
        Bitemm existing = existingItem("KRW", "10.000", null);
        ProjectDto.BitemmDto requested = item("GCL-2026-0001", "USD", "999", "2.0005");
        given(
                        itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                project.getAbusMngNo(), project.getSno(), "N"))
                .willReturn(List.of(existing));
        given(
                        xcrLookupService.resolveXcr(
                                org.mockito.ArgumentMatchers.eq("USD"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(new BigDecimal("3"));

        synchronizer.sync(project, List.of(requested));

        assertThat(existing.getCurC()).isEqualTo("USD");
        assertThat(existing.getFcAmt()).isEqualTo(new BigDecimal("2.001"));
        assertThat(existing.getAmt()).isEqualTo(new BigDecimal("6.003"));
    }

    @Test
    @DisplayName("수정은 유효한 USD 품목을 FC 없이 정규화된 KRW 품목으로 전환한다")
    void sync_changesUsdToKrwWithoutForeignAmount() {
        Bitemm existing = existingItem("USD", "6.000", "2.000");
        ProjectDto.BitemmDto requested = item("GCL-2026-0001", "KRW", "7.1235", null);
        given(
                        itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                project.getAbusMngNo(), project.getSno(), "N"))
                .willReturn(List.of(existing));
        given(
                        xcrLookupService.resolveXcr(
                                org.mockito.ArgumentMatchers.eq("KRW"),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(null);

        synchronizer.sync(project, List.of(requested));

        assertThat(existing.getCurC()).isEqualTo("KRW");
        assertThat(existing.getFcAmt()).isNull();
        assertThat(existing.getAmt()).isEqualTo(new BigDecimal("7.124"));
    }

    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("외화 FC_AMT 누락", "USD", "100", null, "외화 품목은 외화금액이 필요합니다."),
                Arguments.of("원화 FC_AMT 입력", "KRW", "100", "1", "원화 품목에는 외화금액을 입력할 수 없습니다."),
                Arguments.of("음수 AMT", "KRW", "-0.001", null, "당해 요청금액은 0 이상이어야 합니다."),
                Arguments.of("음수 FC_AMT", "USD", "0", "-0.001", "외화금액은 0 이상이어야 합니다."));
    }

    private static Stream<Arguments> oversizedStoredAmounts() {
        return Stream.of(
                Arguments.of(
                        "FC_AMT 초과",
                        "USD",
                        "1000000000000000",
                        "0",
                        "외화금액이 저장 가능한 NUMBER(18,3) 범위를 넘습니다."),
                Arguments.of(
                        "MPL_AMT 초과",
                        "KRW",
                        null,
                        "1000000000000000",
                        "예정금액이 저장 가능한 NUMBER(18,3) 범위를 넘습니다."));
    }

    private Bitemm existingItem(String currency, String amount, String foreignAmount) {
        return Bitemm.builder()
                .gclMngNo("GCL-2026-0001")
                .sno(1)
                .abusMngNo(project.getAbusMngNo())
                .fntTbCrySno(project.getSno())
                .curC(currency)
                .amt(decimal(amount))
                .fcAmt(decimal(foreignAmount))
                .mplAmt(BigDecimal.ZERO)
                .lstYn("Y")
                .build();
    }

    private static ProjectDto.BitemmDto item(
            String gclMngNo, String currency, String amount, String foreignAmount) {
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setGclMngNo(gclMngNo);
        item.setCurC(currency);
        item.setAmt(decimal(amount));
        item.setFcAmt(decimal(foreignAmount));
        item.setMplAmt(BigDecimal.ZERO);
        return item;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
