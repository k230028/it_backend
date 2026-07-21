package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
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
 * <p>저장 시점에 원화로 환산된 품목 금액을 요약에서 그대로 합산하고, MPL_AMT(예정금액) 파생값을 올바르게 계산하는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectBudgetSummaryServiceTest {

    private record BudgetView(
            String gclMngNo, String abusMngNo, String ioeC, BigDecimal amt, BigDecimal mplAmt)
            implements ProjectItemRepository.ProjectItemBudgetView {
        @Override
        public String getGclMngNo() {
            return gclMngNo;
        }

        @Override
        public String getAbusMngNo() {
            return abusMngNo;
        }

        @Override
        public String getIoeC() {
            return ioeC;
        }

        @Override
        public BigDecimal getAmt() {
            return amt;
        }

        @Override
        public BigDecimal getMplAmt() {
            return mplAmt;
        }
    }

    @Mock CodeService codeService;

    @InjectMocks ProjectBudgetSummaryService service;

    /**
     * 테스트용 Ccodem 생성 헬퍼. Ccodem은 복합 PK(cId, cdva, sttDt)를 가지므로 최소 필드를 채워 빌드합니다.
     *
     * @param cdva 코드값ID (비목코드, 예: "C1", "M1")
     * @param cTp 코드타입 (예: "IOE_DVC", "IOE_SEVS")
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
     * 테스트용 Bitemm 생성 헬퍼. Bitemm은 복합 PK(gclMngNo, sno) 및 필수 연관 컬럼을 요구합니다.
     *
     * @param ioeC 비목코드 (예: "C1", "M1")
     * @param amt 품목금액 (원화 기준)
     * @param mplAmt 예정금액
     * @return 테스트용 Bitemm 인스턴스
     */
    private Bitemm item(String ioeC, long amt, long mplAmt) {
        return item(ioeC, amt, mplAmt, BigDecimal.ONE);
    }

    /**
     * 테스트용 Bitemm 생성 헬퍼.
     *
     * @param ioeC 비목코드
     * @param amt 품목금액 (원화 기준)
     * @param mplAmt 예정금액 (원화 기준)
     * @param xcr 저장 시 적용된 환율
     * @param fcAmt 원통화 금액
     * @return 테스트용 Bitemm 인스턴스
     */
    private Bitemm item(String ioeC, long amt, long mplAmt, BigDecimal xcr, BigDecimal fcAmt) {
        return Bitemm.builder()
                .gclMngNo("GCL-2026-0001")
                .sno(1)
                .abusMngNo("PRJ-2026-0001")
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .gclNm("품목")
                .curC("KRW")
                .xcr(xcr)
                .amt(BigDecimal.valueOf(amt))
                .fcAmt(fcAmt)
                .mplAmt(BigDecimal.valueOf(mplAmt))
                .lstYn("Y")
                .build();
    }

    /**
     * 테스트용 Bitemm 생성 헬퍼.
     *
     * @param ioeC 비목코드
     * @param amt 품목금액 (원화 기준)
     * @param mplAmt 예정금액 (원화 기준)
     * @param xcr 저장 시 적용된 환율
     * @return 테스트용 Bitemm 인스턴스
     */
    private Bitemm item(String ioeC, long amt, long mplAmt, BigDecimal xcr) {
        return Bitemm.builder()
                .gclMngNo("GCL-2026-0001")
                .sno(1)
                .abusMngNo("PRJ-2026-0001")
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .gclNm("품목")
                .curC("KRW")
                .xcr(xcr)
                .amt(BigDecimal.valueOf(amt))
                .fcAmt(BigDecimal.valueOf(100))
                .mplAmt(BigDecimal.valueOf(mplAmt))
                .lstYn("Y")
                .build();
    }

    @Test
    @DisplayName("프로젝션 경로도 엔티티 경로와 같은 예산 합계를 계산한다")
    void appliesSameSummaryFromBudgetViews() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC"), code("M1", "IOE_SEVS")));
        ProjectDto.Response response = ProjectDto.Response.builder().build();

        service.applyBudgetSummaryViews(
                response,
                List.of(
                        new BudgetView(
                                "G1", "P1", "C1", new BigDecimal("1000"), new BigDecimal("300")),
                        new BudgetView(
                                "G2", "P1", "M1", new BigDecimal("500"), new BigDecimal("200"))));

        assertThat(response.getAssetBg()).isEqualByComparingTo("1000");
        assertThat(response.getCostBg()).isEqualByComparingTo("500");
        assertThat(response.getTotRqmAmt()).isEqualByComparingTo("1000");
        assertThat(ProjectItemRepository.ProjectItemBudgetView.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder(
                        "getGclMngNo", "getAbusMngNo", "getIoeC", "getAmt", "getMplAmt");
    }

    @Test
    @DisplayName("프로젝션 합산 입력이 null이면 실패한다")
    void rejectsNullProjectionInputs() {
        assertThatThrownBy(
                        () ->
                                service.applyBudgetSummaryViews(
                                        ProjectDto.Response.builder().build(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.applyBudgetSummaryViews(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("품목 MPL_AMT를 비목별로 합산하고 당해예산을 파생한다")
    void appliesDerivedPlannedAmounts() {
        // Arrange: C1=자본(IOE_DVC), M1=관리비(IOE_SEVS)
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
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
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // Act
        service.applyBudgetSummary(res, List.of(item("C1", 100, 250)));

        // Assert: 음수 → 0으로 보정
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("외화 품목은 저장된 KRW amt를 요약에서 그대로 합산한다")
    void usesPersistedKrwAmountForSummary() {
        // Arrange: C1=자본(IOE_DVC), fcAmt=100, xcr=1400, 저장 amt=130000
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // Act
        service.applyBudgetSummary(
                res, List.of(item("C1", 130000, 0, new BigDecimal("1400"), new BigDecimal("100"))));

        // Assert: 원천금액 100이나 이중환산 169000000이 아닌 저장 KRW 금액 130000이어야 한다.
        assertThat(res.getAssetBg()).isEqualByComparingTo("130000");
        assertThat(res.getDvcBg()).isEqualByComparingTo("130000");
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("130000");
    }

    @Test
    @DisplayName("외화 품목은 fcAmt*xcr=140000이어도 저장된 amt 130000을 그대로 합산한다")
    void summarize_foreignCurrency_usesKrwAmtWithoutDoubleConversion() {
        // Given: fcAmt=100, xcr=1400이면 140000이지만 저장 amt는 130000인 품목
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // When: 프로젝트 요약을 계산
        service.applyBudgetSummary(
                res, List.of(item("C1", 130000, 0, new BigDecimal("1400"), new BigDecimal("100"))));

        // Then: fcAmt * xcr = 140000이지만 저장된 amt 130000을 그대로 사용한다
        assertThat(res.getAssetBg()).isEqualByComparingTo("130000");
        assertThat(res.getDvcBg()).isEqualByComparingTo("130000");
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("130000");
    }

    @Test
    @DisplayName("외화 예정금액도 저장된 KRW mplAmt를 요약에서 그대로 차감한다")
    void usesPersistedKrwPlannedAmountForSummary() {
        // Arrange: C1=자본(IOE_DVC), 저장 amt=130000, 저장 mplAmt=52000, xcr=1300
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // Act
        service.applyBudgetSummary(res, List.of(item("C1", 130000, 52000, new BigDecimal("1300"))));

        // Assert: 총 130000원에서 저장된 예정금액 52000원을 그대로 차감한다.
        assertThat(res.getAssetBg()).isEqualByComparingTo("130000");
        assertThat(res.getMplCpitAmt()).isEqualByComparingTo("52000");
        assertThat(res.getTotRqmAmt()).isEqualByComparingTo("78000");
    }
}
