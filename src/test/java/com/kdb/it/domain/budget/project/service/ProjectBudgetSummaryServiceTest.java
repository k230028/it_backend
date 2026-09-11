package com.kdb.it.domain.budget.project.service;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ProjectBudgetSummaryService 단위 테스트.
 *
 * <p>현재 요청금액과 통화별 MPL_AMT(예정금액)를 중앙 계산기로 합산해 응답 계약에 맞게 노출하는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectBudgetSummaryServiceTest {

    @Test
    @DisplayName("저장 금액 스냅샷 적용 대상이 없으면 아무 작업도 하지 않는다")
    void applyStoredAmountSnapshot_ignoresNullResponse() {
        service.applyStoredAmountSnapshot(null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record BudgetView(
            String gclMngNo,
            String abusMngNo,
            String ioeC,
            BigDecimal amt,
            BigDecimal mplAmt,
            String curC,
            BigDecimal xcr)
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

        @Override
        public String getCurC() {
            return curC;
        }

        @Override
        public BigDecimal getXcr() {
            return xcr;
        }
    }

    @Mock CodeService codeService;

    @Spy ProjectAmountCalculator amountCalculator = new ProjectAmountCalculator();

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
                                "G1",
                                "P1",
                                "C1",
                                new BigDecimal("1000"),
                                new BigDecimal("300"),
                                "KRW",
                                null),
                        new BudgetView(
                                "G2",
                                "P1",
                                "M1",
                                new BigDecimal("500"),
                                new BigDecimal("200"),
                                "KRW",
                                null)));

        assertThat(response.getAssetBg()).isEqualByComparingTo("1000");
        assertThat(response.getCostBg()).isEqualByComparingTo("500");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("1500");
        assertThat(declaredMethodNames(ProjectItemRepository.ProjectItemBudgetView.class))
                .containsExactlyInAnyOrder(
                        "getGclMngNo",
                        "getAbusMngNo",
                        "getIoeC",
                        "getAmt",
                        "getMplAmt",
                        "getCurC",
                        "getXcr");
    }

    @Test
    @DisplayName("외화 프로젝션 예정금액은 환율을 적용한 원화 금액으로 합산한다")
    void applyBudgetSummaryViews_convertsForeignPlannedAmount() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response response = ProjectDto.Response.builder().build();

        service.applyBudgetSummaryViews(
                response,
                List.of(
                        new BudgetView(
                                "G1",
                                "P1",
                                "C1",
                                new BigDecimal("140000"),
                                new BigDecimal("50"),
                                "USD",
                                new BigDecimal("1400"))));

        assertThat(response.getMplCpitAmt()).isEqualByComparingTo("70000");
        assertThat(response.getMplAmt()).isEqualByComparingTo("70000");
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
    @DisplayName("품목 MPL_AMT를 비목별로 합산하고 당해 요청금액을 유지한다")
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
        assertThat(res.getTyyBgAmt()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("당해 요청금액은 예정금액을 차감하지 않고 총소요금액은 두 금액을 합산한다")
    void applyBudgetSummary_setsDerivedTotals() {
        // A01을 자본예산 비목으로 분류해야 assetBg/costBg 합계(prjBgAmt)에 반영된다.
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        List<Bitemm> items =
                List.of(
                        Bitemm.builder()
                                .ioeC("A01")
                                .curC("KRW")
                                .amt(new BigDecimal("100"))
                                .mplAmt(new BigDecimal("500"))
                                .build());

        service.applyBudgetSummary(response, items);

        assertThat(response.getPrjBgAmt()).isEqualByComparingTo("600");
        assertThat(response.getMplAmt()).isEqualByComparingTo("500");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("저장 스냅샷이 있으면 네 응답 금액을 그 값 기준으로 복원한다")
    void applyStoredAmountSnapshot_overridesDerivedTotals() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        service.applyBudgetSummary(response, List.of(item("A01", 90L, 400L)));

        service.applyStoredAmountSnapshot(
                response, new BigDecimal("620"), new BigDecimal("500"), new BigDecimal("20"));

        assertThat(response.getPrjBgAmt()).isEqualByComparingTo("620");
        assertThat(response.getMplAmt()).isEqualByComparingTo("500");
        assertThat(response.getDfrAmt()).isEqualByComparingTo("20");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("지급금액이 없는 저장 스냅샷도 총소요금액에서 당해 요청금액을 복원한다")
    void applyStoredAmountSnapshot_restoresCurrentRequestWithoutPaidAmount() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        service.applyBudgetSummary(response, List.of(item("A01", 1000L, 300L)));

        service.applyStoredAmountSnapshot(
                response, new BigDecimal("1000"), new BigDecimal("300"), null);

        assertThat(response.getPrjBgAmt()).isEqualByComparingTo("1000");
        assertThat(response.getMplAmt()).isEqualByComparingTo("300");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("700");
    }

    @Test
    @DisplayName("저장 스냅샷의 예정금액이 null이면 파생값을 남기지 않고 0으로 복원한다")
    void applyStoredAmountSnapshot_normalizesNullPlannedAmount() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        service.applyBudgetSummary(response, List.of(item("A01", 1000L, 300L)));

        service.applyStoredAmountSnapshot(response, new BigDecimal("1000"), null, null);

        assertThat(response.getPrjBgAmt()).isEqualByComparingTo("1000");
        assertThat(response.getMplAmt()).isEqualByComparingTo("0");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("저장 스냅샷의 당해 요청금액 불변식 위반을 음수 그대로 드러낸다")
    void applyStoredAmountSnapshot_exposesNegativeCurrentRequestInvariant() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        service.applyBudgetSummary(response, List.of(item("A01", 1000L, 300L)));

        service.applyStoredAmountSnapshot(
                response, new BigDecimal("1000"), new BigDecimal("800"), new BigDecimal("400"));

        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("-200");
    }

    @Test
    @DisplayName("저장 스냅샷이 비어 있으면 파생 합계를 그대로 둔다")
    void applyStoredAmountSnapshot_keepsDerivedTotalsWhenNull() {
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("A01", "IOE_DVC")));
        ProjectDto.Response response = new ProjectDto.Response();
        service.applyBudgetSummary(response, List.of(item("A01", 1000L, 300L)));

        service.applyStoredAmountSnapshot(response, null, null, null);

        assertThat(response.getPrjBgAmt()).isEqualByComparingTo("1300");
        assertThat(response.getMplAmt()).isEqualByComparingTo("300");
        assertThat(response.getTyyBgAmt()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("파생 당해 요청금액은 예정금액이 더 커도 현재 요청금액을 그대로 사용한다")
    void keepsCurrentRequestAmountWhenPlannedAmountIsGreater() {
        // Arrange: C1=자본(IOE_DVC), amt=100이지만 mplAmt=250으로 초과
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        // Act
        service.applyBudgetSummary(res, List.of(item("C1", 100, 250)));

        assertThat(res.getTyyBgAmt()).isEqualByComparingTo("100");
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
        assertThat(res.getTyyBgAmt()).isEqualByComparingTo("130000");
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
        assertThat(res.getTyyBgAmt()).isEqualByComparingTo("130000");
    }

    @Test
    @DisplayName("외화 예정금액은 중앙 계산기의 원화 환산 결과로 비목 합산한다")
    void convertsForeignPlannedAmountForSummary() {
        // Arrange: C1=자본(IOE_DVC), 현재 요청금액=140000원, 예정금액=50달러, 환율=1400원
        when(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .thenReturn(List.of(code("C1", "IOE_DVC")));
        ProjectDto.Response res = ProjectDto.Response.builder().build();

        Bitemm foreignItem =
                Bitemm.builder()
                        .ioeC("C1")
                        .curC("USD")
                        .amt(new BigDecimal("140000"))
                        .mplAmt(new BigDecimal("50"))
                        .xcr(new BigDecimal("1400"))
                        .build();

        service.applyBudgetSummary(res, List.of(foreignItem));

        assertThat(res.getAssetBg()).isEqualByComparingTo("140000");
        assertThat(res.getMplCpitAmt()).isEqualByComparingTo("70000");
        assertThat(res.getMplAmt()).isEqualByComparingTo("70000");
        assertThat(res.getTyyBgAmt()).isEqualByComparingTo("140000");
        assertThat(res.getPrjBgAmt()).isEqualByComparingTo("210000");
    }

    @Test
    @DisplayName("스냅샷 합계는 비목 분류와 무관하게 활성 품목 전체를 더한다")
    void calculateAmountSnapshot_sumsAllItems() {
        List<Bitemm> items =
                List.of(
                        Bitemm.builder()
                                .ioeC("A01")
                                .amt(new BigDecimal("1000"))
                                .mplAmt(new BigDecimal("300"))
                                .build(),
                        Bitemm.builder()
                                .ioeC("ZZ9") // 자본·관리비 어느 집합에도 없는 비목
                                .amt(new BigDecimal("500"))
                                .mplAmt(new BigDecimal("200"))
                                .build());

        ProjectAmountSummary snapshot =
                service.calculateAmountSnapshot(items, new BigDecimal("20"));

        assertThat(snapshot.currentRequestAmt()).isEqualByComparingTo("1500");
        assertThat(snapshot.plannedAmt()).isEqualByComparingTo("500");
        assertThat(snapshot.paidAmt()).isEqualByComparingTo("20");
        assertThat(snapshot.totalRequiredAmt()).isEqualByComparingTo("2020");
    }

    @Test
    @DisplayName("금액이 null이거나 품목이 없으면 0을 반환한다")
    void calculateAmountSnapshot_nullSafe() {
        ProjectAmountSummary empty = service.calculateAmountSnapshot(List.of(), null);
        assertThat(empty.currentRequestAmt()).isEqualByComparingTo("0");
        assertThat(empty.plannedAmt()).isEqualByComparingTo("0");
        assertThat(empty.totalRequiredAmt()).isEqualByComparingTo("0");

        ProjectAmountSummary nulls =
                service.calculateAmountSnapshot(
                        List.of(Bitemm.builder().ioeC("A01").build()), null);
        assertThat(nulls.currentRequestAmt()).isEqualByComparingTo("0");
        assertThat(nulls.plannedAmt()).isEqualByComparingTo("0");
    }
}
