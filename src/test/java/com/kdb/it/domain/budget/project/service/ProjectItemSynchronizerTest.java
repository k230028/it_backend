package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("외화 FC_AMT 누락", "USD", "100", null, "외화 품목은 외화금액이 필요합니다."),
                Arguments.of("원화 FC_AMT 입력", "KRW", "100", "1", "원화 품목에는 외화금액을 입력할 수 없습니다."),
                Arguments.of("음수 AMT", "KRW", "-0.001", null, "당해 요청금액은 0 이상이어야 합니다."),
                Arguments.of("음수 FC_AMT", "USD", "0", "-0.001", "외화금액은 0 이상이어야 합니다."));
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
