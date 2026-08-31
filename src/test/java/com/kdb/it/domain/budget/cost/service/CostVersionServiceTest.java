package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostVersionServiceTest {

    @Mock CostRepository costRepository;
    @Mock BtermmRepository terminalRepository;
    @Mock ApplicationMapRepository applicationMapRepository;

    @Test
    void 결재완료_전산업무비와_단말기를_다음순번_초안으로_복제한다() {
        Bcostm source =
                Bcostm.builder()
                        .costBgNo("COST-2027-0001")
                        .bgSno(1)
                        .lstYn("Y")
                        .cttNm("원본 계약")
                        .costTotXpAmt(new BigDecimal("1000"))
                        .dfrCleC("0")
                        .abusTc("10")
                        .delYn("N")
                        .build();
        Btermm terminal =
                Btermm.builder()
                        .tmnMngNo("TMN-1")
                        .sno(1)
                        .termBgNo(source.getCostBgNo())
                        .termBgSno(1)
                        .spfTmnNm("단말 원본")
                        .dfrCleC("0")
                        .delYn("N")
                        .build();
        given(costRepository.findCurrentVersionForUpdate(source.getCostBgNo()))
                .willReturn(Optional.of(source));
        given(costRepository.getNextSnoValue(source.getCostBgNo())).willReturn(2);
        given(applicationMapRepository.findLatestApplicationStatus("BCOSTM", source.getCostBgNo(), 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn(source.getCostBgNo(), 1, "N"))
                .willReturn(List.of(terminal));
        given(terminalRepository.getNextSnoValue("TMN-1")).willReturn(2);

        CostVersionService service =
                new CostVersionService(costRepository, terminalRepository, applicationMapRepository);
        CostVersionService.CostVersion result = service.createReapplication(source.getCostBgNo());

        assertThat(result.bgSno()).isEqualTo(2);
        ArgumentCaptor<Bcostm> costCaptor = ArgumentCaptor.forClass(Bcostm.class);
        verify(costRepository).save(costCaptor.capture());
        assertThat(costCaptor.getValue().getLstYn()).isEqualTo("N");
        assertThat(costCaptor.getValue().getCostTotXpAmt()).isEqualByComparingTo("1000");
        ArgumentCaptor<Btermm> terminalCaptor = ArgumentCaptor.forClass(Btermm.class);
        verify(terminalRepository).save(terminalCaptor.capture());
        assertThat(terminalCaptor.getValue().getTermBgSno()).isEqualTo(2);
        assertThat(terminalCaptor.getValue().getSpfTmnNm()).isEqualTo("단말 원본");
    }
}
