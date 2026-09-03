package com.kdb.it.domain.budget.cost.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO 구현 분리 시 공개 전산업무비 매핑 계약이 유지되는지 확인합니다. */
class CostDtoMappingTest {

    @Test
    @DisplayName("생성 요청은 날짜를 정규화하고 엔티티 기본값을 보존한다")
    void createRequestToEntity_normalizesDatesAndPreservesDefaults() {
        CostDto.CreateRequest request =
                CostDto.CreateRequest.builder()
                        .costBgNo("COST-2026-001")
                        .ioeC("571")
                        .cttNm("유지보수")
                        .costTotXpAmt(new BigDecimal("1000"))
                        .fstDfrDt("2026-09-03")
                        .xcrBseDt("2026.09.02")
                        .sectSysUtzYn(null)
                        .dfrCleC(null)
                        .abusTc(null)
                        .fcAmt(new BigDecimal("10"))
                        .build();

        Bcostm entity = request.toEntity(7);

        assertThat(entity.getCostBgNo()).isEqualTo("COST-2026-001");
        assertThat(entity.getBgSno()).isEqualTo(7);
        assertThat(entity.getIoeC()).isEqualTo("571");
        assertThat(entity.getCttNm()).isEqualTo("유지보수");
        assertThat(entity.getCostTotXpAmt()).isEqualByComparingTo("1000");
        assertThat(entity.getFstDfrDt()).isEqualTo("20260903");
        assertThat(entity.getXcrBseDt()).isEqualTo("20260902");
        assertThat(entity.getSectSysUtzYn()).isEqualTo("N");
        assertThat(entity.getDfrCleC()).isEqualTo("0");
        assertThat(entity.getAbusTc()).isEqualTo("0");
        assertThat(entity.getFcAmt()).isEqualByComparingTo("10");
        assertThat(entity.getLstYn()).isEqualTo("Y");
    }
}
