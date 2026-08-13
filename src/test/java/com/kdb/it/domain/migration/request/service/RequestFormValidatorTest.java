package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestFormValidatorTest {

    @Mock private CostRepository costRepository;
    @Mock private ProjectRepository projectRepository;

    private RequestFormValidator validator() {
        return new RequestFormValidator(costRepository, projectRepository);
    }

    private static CostDto.CreateRequest cost(String ioeC, String cttNm, BigDecimal amount) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setIoeC(ioeC);
        request.setCttNm(cttNm);
        request.setCttOppNm("Bloomberg");
        request.setCostSvnDpmC("0210");
        request.setCurC("KRW");
        request.setCostTotXpAmt(amount);
        return request;
    }

    private static ProjectDto.CreateRequest project(String name) {
        ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
        request.setAbusNm(name);
        request.setItems(List.of());
        return request;
    }

    private static FormAdapterOutput costsOf(CostDto.CreateRequest... costs) {
        return new FormAdapterOutput(List.of(), List.of(costs), List.of(), null);
    }

    private static FormAdapterOutput projectsOf(ProjectDto.CreateRequest... projects) {
        return new FormAdapterOutput(List.of(projects), List.of(), List.of(), null);
    }

    @Test
    @DisplayName("비목코드가 비면 필수값 누락으로 막는다")
    void blocksMissingIoeCode() {
        assertThat(
                        validator()
                                .validate(
                                        costsOf(cost(null, "회선사용료", new BigDecimal("1000"))),
                                        "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.REQUIRED_MISSING);
    }

    @Test
    @DisplayName("금액이 원화·외화 모두 비면 막는다")
    void blocksMissingAmount() {
        assertThat(validator().validate(costsOf(cost("010", "회선사용료", null)), "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.REQUIRED_MISSING);
    }

    @Test
    @DisplayName("계약명이 100자를 넘으면 길이 초과로 막는다")
    void blocksOverlongContractName() {
        assertThat(
                        validator()
                                .validate(
                                        costsOf(
                                                cost(
                                                        "010",
                                                        "가".repeat(101),
                                                        new BigDecimal("1000"))),
                                        "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.LENGTH_EXCEEDED);
    }

    @Test
    @DisplayName("이미 있는 전산업무비 자연키면 재업로드로 보아 막는다")
    void blocksDuplicateCost() {
        Bcostm existing =
                Bcostm.builder()
                        .costSvnDpmC("0210")
                        .ioeC("010")
                        .cttOppNm("Bloomberg")
                        .cttNm("블룸버그 회선사용료")
                        .build();
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(existing));

        assertThat(
                        validator()
                                .validate(
                                        costsOf(cost("010", "블룸버그 회선사용료", new BigDecimal("1000"))),
                                        "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
    }

    @Test
    @DisplayName("부서가 다르면 같은 계약명이어도 중복이 아니다")
    void allowsSameContractNameInAnotherDepartment() {
        Bcostm existing =
                Bcostm.builder()
                        .costSvnDpmC("0930")
                        .ioeC("010")
                        .cttOppNm("Bloomberg")
                        .cttNm("블룸버그 회선사용료")
                        .build();
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(existing));

        assertThat(
                        validator()
                                .validate(
                                        costsOf(cost("010", "블룸버그 회선사용료", new BigDecimal("1000"))),
                                        "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
    }

    @Test
    @DisplayName("사업명은 공백을 무시하고 중복을 판정한다")
    void blocksDuplicateProjectIgnoringWhitespace() {
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(Bprojm.builder().abusNm("국채전문유통시장  접속인프라 도입").build()));

        assertThat(validator().validate(projectsOf(project("국채전문유통시장 접속인프라 도입")), "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
    }

    @Test
    @DisplayName("같은 배치 안에서 사업명이 겹쳐도 막는다")
    void blocksDuplicateWithinSameBatch() {
        assertThat(validator().validate(projectsOf(project("같은 사업"), project("같은 사업")), "2026"))
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.DUPLICATE_EXISTS)
                .hasSize(1);
    }

    @Test
    @DisplayName("품목의 비목코드와 금액도 함께 본다")
    void validatesItems() {
        ProjectDto.CreateRequest withBadItem = project("사업");
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setGclNm("품목");
        withBadItem.setItems(List.of(item));

        assertThat(validator().validate(projectsOf(withBadItem), "2026"))
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.REQUIRED_MISSING)
                .hasSize(2);
    }

    @Test
    @DisplayName("문제가 없으면 진단을 내지 않는다")
    void staysQuietWhenValid() {
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(project("새 사업")),
                        List.of(cost("010", "새 계약", new BigDecimal("1000"))),
                        List.of(),
                        null);

        assertThat(validator().validate(output, "2026")).isEmpty();
    }

    @Test
    @DisplayName("BLOCKER 판정은 심각도로만 한다")
    void detectsBlockerBySeverity() {
        List<RequestFormDto.FormDiagnostic> onlyWarnings =
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null,
                                null,
                                "x",
                                RequestFormDiagnosticCode.OPTIONAL_MISSING,
                                "경고",
                                List.of()));
        List<RequestFormDto.FormDiagnostic> withBlocker =
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null,
                                null,
                                "x",
                                RequestFormDiagnosticCode.CODE_UNRESOLVED,
                                "차단",
                                List.of()));

        assertThat(RequestFormValidator.hasBlocker(onlyWarnings)).isFalse();
        assertThat(RequestFormValidator.hasBlocker(withBlocker)).isTrue();
        assertThat(RequestFormValidator.hasBlocker(List.of())).isFalse();
    }
}
