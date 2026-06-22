package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ProjectBudgetSummaryService 단위 테스트.
 *
 * <p>품목 MPL_AMT(예정금액)를 비목(IOE_C)별로 합산하고,
 * 당해예산(totRqmAmt)을 올바르게 파생하는지 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectBudgetSummaryServiceTest {

    @Mock
    CodeService codeService;

    @InjectMocks
    ProjectBudgetSummaryService service;

    /**
     * 테스트용 Ccodem 생성 헬퍼.
     * Ccodem은 복합 PK(cId, cdva, sttDt)를 가지므로 최소 필드를 채워 빌드합니다.
     *
     * @param cdva 코드값ID (비목코드, 예: "C1", "M1")
     * @param cTp  코드타입 (예: "IOE_DVC", "IOE_SEVS")
     * @return 테스트용 Ccodem 인스턴스
     */
    private Ccodem code(String cdva, String cTp) {
        return Ccodem.builder()
                .cId(CommonCodeGroups.IOE)
                .cdva(cdva)
                .sttDt("20260101")
                .cTp(cTp)
                .build();
    }

    /**
     * 테스트용 Bitemm 생성 헬퍼.
     * Bitemm은 복합 PK(gclMngNo, sno) 및 필수 연관 컬럼을 요구합니다.
     *
     * @param ioeC   비목코드 (예: "C1", "M1")
     * @param amt    품목금액 (원화 기준)
     * @param mplAmt 예정금액
     * @return 테스트용 Bitemm 인스턴스
     */
    private Bitemm item(String ioeC, long amt, long mplAmt) {
        return Bitemm.builder()
                .gclMngNo("GCL-2026-0001")
                .sno(1)
                .abusMngNo("PRJ-2026-0001")
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .gclNm("품목")
                .curC("KRW")
                .amt(BigDecimal.valueOf(amt))
                .mplAmt(BigDecimal.valueOf(mplAmt))
                .lstYn("Y")
                .build();
    }

    @Test
    @DisplayName("품목 MPL_AMT를 비목별로 합산하고 당해예산을 파생한다")
    void appliesDerivedPlannedAmounts() {
        // Arrange: C1=자본(IOE_DVC), M1=관리비(IOE_SEVS)
        when(codeService.findCodeEntitiesByCId(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC"), code("M1", "IOE_SEVS")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();
        // C1: amt=1000, mplAmt=300 / M1: amt=500, mplAmt=200
        List<Bitemm> items = List.of(item("C1", 1000, 300), item("M1", 500, 200));

        // Act
        service.applyBudgetSummary(res, items);

        // Assert
        assertThat(res.getMplCpitAmt()).isEqualByComparingTo("300"); // 자본 예정금액 합산
        assertThat(res.getMplMngcAmt()).isEqualByComparingTo("200"); // 관리비 예정금액 합산
        // 당해예산 = (1000+500) - (300+200) = 1000
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("당해예산이 음수면 0으로 보정한다")
    void clampsNegativeCurrentYearToZero() {
        // Arrange: C1=자본(IOE_DVC), amt=100이지만 mplAmt=250으로 초과
        when(codeService.findCodeEntitiesByCId(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // Act
        service.applyBudgetSummary(res, List.of(item("C1", 100, 250)));

        // Assert: 음수 → 0으로 보정
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("0");
    }
}
