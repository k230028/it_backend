package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 편성요청서 외화 행의 환율기준일자를 통화 공통코드 코드값상세로 채우는 규칙을 고정합니다. */
@ExtendWith(MockitoExtension.class)
class RequestFormXcrBaseDateAssignerTest {

    private static final String BSE_YY = "2026";

    @Mock private MigrationIoeCatalogReader catalogReader;

    private RequestFormXcrBaseDateAssigner assigner() {
        return new RequestFormXcrBaseDateAssigner(catalogReader);
    }

    @Test
    @DisplayName("외화 전산업무비·품목의 환율기준일자를 통화 코드값상세(YYYYMMDD)로 덮어쓴다")
    void assignsBaseDateFromCurrencyCode() {
        when(catalogReader.xcrBaseDateByCurrency())
                .thenReturn(Map.of("USD", "20260115", "JPY", "20260120"));
        CostDto.CreateRequest cost = cost("USD");
        ProjectDto.BitemmDto item = item("JPY");
        FormAdapterOutput output = output(List.of(project(item, "N")), List.of(cost));

        List<RequestFormDto.FormDiagnostic> diagnostics = assigner().assign(output, BSE_YY);

        assertThat(cost.getXcrBseDt()).isEqualTo("20260115");
        assertThat(item.getXcrBseDt()).isEqualTo("20260120");
        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("원화 행과 통화 미해석 행은 손대지 않는다")
    void leavesKrwAndUnresolvedRowsUntouched() {
        when(catalogReader.xcrBaseDateByCurrency()).thenReturn(Map.of("USD", "20260115"));
        CostDto.CreateRequest krw = cost("KRW");
        CostDto.CreateRequest unresolved = cost(null);
        FormAdapterOutput output = output(List.of(), List.of(krw, unresolved));

        List<RequestFormDto.FormDiagnostic> diagnostics = assigner().assign(output, BSE_YY);

        assertThat(krw.getXcrBseDt()).isEqualTo(BSE_YY + "0101");
        assertThat(unresolved.getXcrBseDt()).isEqualTo(BSE_YY + "0101");
        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("코드값상세가 등록되지 않은 통화는 예산연도 1월 1일을 유지하고 통화별 WARNING 하나를 낸다")
    void warnsOncePerCurrencyWhenBaseDateMissing() {
        when(catalogReader.xcrBaseDateByCurrency()).thenReturn(Map.of());
        CostDto.CreateRequest first = cost("USD");
        CostDto.CreateRequest second = cost("USD");
        ProjectDto.BitemmDto item = item("USD");
        FormAdapterOutput output = output(List.of(project(item, "Y")), List.of(first, second));

        List<RequestFormDto.FormDiagnostic> diagnostics = assigner().assign(output, BSE_YY);

        assertThat(first.getXcrBseDt()).isEqualTo("20260101");
        assertThat(second.getXcrBseDt()).isEqualTo("20260101");
        assertThat(item.getXcrBseDt()).isEqualTo("20260101");
        assertThat(diagnostics).hasSize(1);
        RequestFormDto.FormDiagnostic diagnostic = diagnostics.getFirst();
        assertThat(diagnostic.code()).isEqualTo(RequestFormDiagnosticCode.DATE_UNPARSEABLE);
        assertThat(diagnostic.severity()).isEqualTo(MigrationDto.Severity.WARNING);
        assertThat(diagnostic.field()).isEqualTo("xcrBseDt");
        assertThat(diagnostic.subject()).isEqualTo("USD");
        assertThat(diagnostic.message()).contains("등록되어 있지 않아").contains("3건").contains("20260101");
    }

    @Test
    @DisplayName("코드값상세가 YYYYMMDD가 아니면 기본값을 숨기지 않고 형식 오류 WARNING을 낸다")
    void warnsWhenBaseDateMalformed() {
        when(catalogReader.xcrBaseDateByCurrency())
                .thenReturn(Map.of("USD", "2026-01-15", "GBP", "20261332"));
        CostDto.CreateRequest usd = cost("USD");
        CostDto.CreateRequest gbp = cost("GBP");
        FormAdapterOutput output = output(List.of(), List.of(usd, gbp));

        List<RequestFormDto.FormDiagnostic> diagnostics = assigner().assign(output, BSE_YY);

        assertThat(usd.getXcrBseDt()).isEqualTo("20260101");
        assertThat(gbp.getXcrBseDt()).isEqualTo("20260101");
        assertThat(diagnostics)
                .extracting(RequestFormDto.FormDiagnostic::subject)
                .containsExactlyInAnyOrder("USD", "GBP");
        assertThat(diagnostics)
                .allSatisfy(
                        diagnostic -> {
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.DATE_UNPARSEABLE);
                            assertThat(diagnostic.message()).contains("YYYYMMDD 형식이 아니어서");
                        });
    }

    private static CostDto.CreateRequest cost(String curC) {
        CostDto.CreateRequest cost = new CostDto.CreateRequest();
        cost.setCttNm("계약");
        cost.setCurC(curC);
        cost.setFcAmt(curC == null || "KRW".equals(curC) ? null : new BigDecimal("10"));
        cost.setXcrBseDt(BSE_YY + "0101");
        return cost;
    }

    private static ProjectDto.BitemmDto item(String curC) {
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setSno(1);
        item.setGclNm("품목");
        item.setCurC(curC);
        item.setFcAmt(new BigDecimal("10"));
        item.setXcrBseDt(BSE_YY + "0101");
        return item;
    }

    private static ProjectDto.CreateRequest project(ProjectDto.BitemmDto item, String odnYn) {
        ProjectDto.CreateRequest project = new ProjectDto.CreateRequest();
        project.setAbusNm("사업");
        project.setOdnYn(odnYn);
        project.setItems(new ArrayList<>(List.of(item)));
        return project;
    }

    private static FormAdapterOutput output(
            List<ProjectDto.CreateRequest> projects, List<CostDto.CreateRequest> costs) {
        return new FormAdapterOutput(projects, costs, List.of(), null);
    }
}
