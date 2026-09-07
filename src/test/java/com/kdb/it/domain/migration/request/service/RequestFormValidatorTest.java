package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setSno(1);
        item.setIoeC("001");
        item.setGclNm("서버");
        item.setAmt(BigDecimal.ONE);
        request.setItems(List.of(item));
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
    @DisplayName("전산업무비 금액이 0원이면 막는다")
    void blocksZeroCostAmount() {
        assertThat(validator().validate(costsOf(cost("010", "회선사용료", BigDecimal.ZERO)), "2026"))
                .filteredOn(d -> "amt".equals(d.field()))
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(diagnostic.message()).isEqualTo("금액이 0원입니다.");
                        });
    }

    @Test
    @DisplayName("사업에 소요자원이 없으면 금액 0원으로 막는다")
    void blocksProjectWithoutItems() {
        ProjectDto.CreateRequest emptyProject = project("소요자원 누락 사업");
        emptyProject.setItems(List.of());

        assertThat(validator().validate(projectsOf(emptyProject), "2026"))
                .filteredOn(d -> "amt".equals(d.field()))
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.BLOCKER);
                            assertThat(diagnostic.subject()).isEqualTo("소요자원 누락 사업");
                            assertThat(diagnostic.message()).isEqualTo("사업 소요금액이 0원입니다.");
                        });
    }

    @Test
    @DisplayName("사업의 모든 소요자원 금액이 0원이면 막는다")
    void blocksProjectWithZeroAmountItems() {
        ProjectDto.CreateRequest zeroProject = project("0원 사업");
        zeroProject.getItems().get(0).setAmt(BigDecimal.ZERO);

        assertThat(validator().validate(projectsOf(zeroProject), "2026"))
                .filteredOn(d -> "사업 소요금액이 0원입니다.".equals(d.message()))
                .singleElement()
                .extracting(RequestFormDto.FormDiagnostic::severity)
                .isEqualTo(MigrationDto.Severity.BLOCKER);
    }

    @Test
    @DisplayName("한글 계약명이 100바이트를 넘으면 UTF-8 문자를 보존해 잘라 넣고 경고한다")
    void truncatesOverlongContractName() {
        CostDto.CreateRequest cost = cost("010", "가".repeat(34), new BigDecimal("1000"));

        assertThat(validator().validate(costsOf(cost), "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.TEXT_TRUNCATED)
                .doesNotContain(RequestFormDiagnosticCode.LENGTH_EXCEEDED);
        assertThat(cost.getCttNm()).isEqualTo("가".repeat(33));
        assertThat(cost.getCttNm().getBytes(StandardCharsets.UTF_8)).hasSize(99);
    }

    @Test
    @DisplayName("VARCHAR2 현황만 자르고 CLOB 사업 서술은 원문을 유지한다")
    void truncatesOnlyVarcharProjectNarrativesWithWarnings() {
        ProjectDto.CreateRequest project = project("길이 검증 사업");
        project.setCpnSafCone("현".repeat(1001));
        project.setAbusPulNcsInf("가".repeat(101));
        project.setAbusPulDrcnInf("범".repeat(201));

        List<RequestFormDto.FormDiagnostic> diagnostics =
                validator().validate(projectsOf(project), "2026");

        assertThat(project.getCpnSafCone().getBytes(StandardCharsets.UTF_8)).hasSize(999);
        assertThat(project.getAbusPulNcsInf()).isEqualTo("가".repeat(101));
        assertThat(project.getAbusPulDrcnInf()).isEqualTo("범".repeat(201));
        assertThat(diagnostics)
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.TEXT_TRUNCATED)
                .extracting(RequestFormDto.FormDiagnostic::field)
                .containsExactly("cpnSafCone");
        assertThat(diagnostics)
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.TEXT_TRUNCATED)
                .extracting(RequestFormDto.FormDiagnostic::severity)
                .containsOnly(MigrationDto.Severity.WARNING);
    }

    @Test
    @DisplayName("한글 품목명이 100바이트를 넘으면 차단하지 않고 UTF-8 문자 경계에서 줄인다")
    void truncatesOverlongItemNameWithWarning() {
        ProjectDto.CreateRequest project = project("품목명 길이 검증 사업");
        project.getItems().get(0).setGclNm("가".repeat(34));

        List<RequestFormDto.FormDiagnostic> diagnostics =
                validator().validate(projectsOf(project), "2026");

        assertThat(project.getItems().get(0).getGclNm()).isEqualTo("가".repeat(33));
        assertThat(diagnostics)
                .filteredOn(d -> "gclNm".equals(d.field()))
                .singleElement()
                .satisfies(
                        diagnostic -> {
                            assertThat(diagnostic.code())
                                    .isEqualTo(RequestFormDiagnosticCode.TEXT_TRUNCATED);
                            assertThat(diagnostic.severity())
                                    .isEqualTo(MigrationDto.Severity.WARNING);
                        });
    }

    @Test
    @DisplayName("양식 담당자 이름은 ID 길이로 차단하지 않는다")
    void allowsPersonNameLongerThanIdColumn() {
        ProjectDto.CreateRequest project = project("담당자 길이 검증 사업");
        project.setDvmTlrUsid("A".repeat(15));

        assertThat(validator().validate(projectsOf(project), "2026"))
                .noneMatch(d -> "dvmTlrUsid".equals(d.field()));
    }

    @Test
    @DisplayName("기존 전산업무비와 건명 및 속성이 같아도 별도 사업으로 허용한다")
    void allowsDuplicateCost() {
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
                .doesNotContain(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
    }

    @Test
    @DisplayName("같은 파일 안의 동일 건명도 서로 다른 사업으로 허용한다")
    void allowsDuplicateCostsWithinBatch() {
        assertThat(
                        validator()
                                .validate(
                                        costsOf(
                                                cost("010", "동일 건명", new BigDecimal("1000")),
                                                cost("010", "동일 건명", new BigDecimal("2000"))),
                                        "2026"))
                .extracting(RequestFormDto.FormDiagnostic::code)
                .doesNotContain(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
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
    void warnsDuplicateProjectIgnoringWhitespace() {
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(Bprojm.builder().abusNm("국채전문유통시장  접속인프라 도입").build()));

        assertThat(validator().validate(projectsOf(project("국채전문유통시장 접속인프라 도입")), "2026"))
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.DUPLICATE_EXISTS)
                .extracting(RequestFormDto.FormDiagnostic::severity)
                .containsExactly(MigrationDto.Severity.WARNING);
    }

    @Test
    @DisplayName("같은 배치 안에서 사업명이 겹치면 경고한다")
    void warnsDuplicateWithinSameBatch() {
        assertThat(validator().validate(projectsOf(project("같은 사업"), project("같은 사업")), "2026"))
                .filteredOn(d -> d.code() == RequestFormDiagnosticCode.DUPLICATE_EXISTS)
                .singleElement()
                .extracting(RequestFormDto.FormDiagnostic::severity)
                .isEqualTo(MigrationDto.Severity.WARNING);
    }

    @Test
    @DisplayName("기존 및 배치 내 중복 사업은 저장 대상에서 제외한다")
    void excludesDuplicateProjectsFromImportTarget() {
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(Bprojm.builder().abusNm("기존 사업").build()));
        FormAdapterOutput output = projectsOf(project("기존 사업"), project("신규 사업"), project("신규 사업"));

        FormAdapterOutput filtered = validator().withoutDuplicateProjects(output, "2026");

        assertThat(filtered.projects())
                .extracting(ProjectDto.CreateRequest::getAbusNm)
                .containsExactly("신규 사업");
        assertThat(filtered.projectAmounts()).hasSize(1);
    }

    @Test
    @DisplayName("사업명은 같아도 총소요금액이 다르면 별도 사업으로 반입한다")
    void keepsSameNamedProjectsWhenDeclaredAmountsDiffer() {
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(
                        List.of(
                                Bprojm.builder()
                                        .abusNm("비설치형 보안 S/W 도입")
                                        .totRqmAmt(new BigDecimal("100000000"))
                                        .build()));
        ProjectDto.CreateRequest imported = project("비설치형 보안 S/W 도입");
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(imported),
                        List.of(),
                        List.of(),
                        null,
                        List.of(
                                new com.kdb.it.domain.migration.request.service.adapter
                                        .ProjectAmounts(
                                        new BigDecimal("132000000"),
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO)));

        FormAdapterOutput filtered = validator().withoutDuplicateProjects(output, "2026");

        assertThat(filtered.projects()).containsExactly(imported);
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
    @DisplayName("품목 진단은 어느 사업의 몇 번 품목인지 대상으로 짚어 준다")
    void namesTheItemBehindEachDiagnostic() {
        // 한 사업이 품목을 수십 건 담아 같은 문구가 여러 줄 늘어서므로 대상이 없으면 짚어낼 수 없다
        ProjectDto.CreateRequest project = project("국채 접속인프라");
        ProjectDto.BitemmDto first = new ProjectDto.BitemmDto();
        first.setSno(1);
        first.setGclNm("Rack");
        ProjectDto.BitemmDto second = new ProjectDto.BitemmDto();
        second.setSno(2);
        project.setItems(List.of(first, second));

        assertThat(validator().validate(projectsOf(project), "2026"))
                .filteredOn(d -> "ioeC".equals(d.field()))
                .extracting(RequestFormDto.FormDiagnostic::subject)
                .containsExactly("국채 접속인프라 · 품목 1 Rack", "국채 접속인프라 · 품목 2 품목명 미기재");
    }

    @Test
    @DisplayName("어댑터가 이미 짚은 비목은 필수값 누락으로 다시 보고하지 않는다")
    void doesNotRepeatIoeAlreadyReportedByAdapter() {
        // 같은 사건을 두 번 내면 행 진단에서 비목을 골라도 고칠 수 없는 차단이 남아 파일이 계속 막힌다
        ProjectDto.CreateRequest project = project("사업");
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setSno(1);
        item.setGclNm("전용망 회선 이용료");
        project.setItems(List.of(item));
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(project),
                        List.of(),
                        List.of(
                                RequestFormDto.FormDiagnostic.about(
                                        FormSheetKind.CAPITAL_RESOURCE,
                                        22,
                                        "ioeC",
                                        "전용망 회선 이용료",
                                        RequestFormDiagnosticCode.CODE_AMBIGUOUS,
                                        "구분 `전산제비`의 비목을 정하지 못했습니다.",
                                        List.of(new MigrationDto.Candidate("013", "국외회선사용료")))),
                        null);

        assertThat(validator().validate(output, "2026"))
                .extracting(RequestFormDto.FormDiagnostic::field)
                .doesNotContain("ioeC");
    }

    @Test
    @DisplayName("긴 계약명이 잘려도 어댑터의 비목 진단을 필수값 누락으로 반복하지 않는다")
    void doesNotRepeatIoeAfterContractNameTruncation() {
        String longName = "오픈소스 소프트웨어 점검 서비스 구독 계약, Labrador SCM Customized 라이선스 12개월";
        CostDto.CreateRequest cost = cost(null, longName, BigDecimal.ONE);
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(),
                        List.of(cost),
                        List.of(
                                RequestFormDto.FormDiagnostic.about(
                                        FormSheetKind.GENERAL_EXPENSE,
                                        14,
                                        "ioeC",
                                        longName,
                                        RequestFormDiagnosticCode.CODE_AMBIGUOUS,
                                        "비목을 골라 주세요.",
                                        List.of(new MigrationDto.Candidate("001", "국내전산임차료")))),
                        null);

        assertThat(validator().validate(output, "2025"))
                .filteredOn(diagnostic -> "ioeC".equals(diagnostic.field()))
                .isEmpty();
    }

    @Test
    @DisplayName("어댑터가 짚지 않은 품목의 비목 누락은 그대로 보고한다")
    void stillReportsIoeMissingWithoutAdapterDiagnostic() {
        ProjectDto.CreateRequest project = project("사업");
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setSno(1);
        item.setGclNm("다른 품목");
        project.setItems(List.of(item));

        assertThat(validator().validate(projectsOf(project), "2026"))
                .extracting(RequestFormDto.FormDiagnostic::field)
                .contains("ioeC");
    }

    @Test
    @DisplayName("어댑터가 이미 짚은 통화 미해석 행은 통화·금액 필수값을 다시 보고하지 않는다")
    void doesNotRepeatCurrencyAlreadyReportedByAdapter() {
        // 통화 미해석 행은 curC·금액이 모두 null로 남는다 — 같은 사건을 두 번 내면
        // 통화를 골라도 금액 누락 차단이 남아 파일이 계속 막힌다
        CostDto.CreateRequest unresolvedCurrency = new CostDto.CreateRequest();
        unresolvedCurrency.setIoeC("010");
        unresolvedCurrency.setCttNm("블룸버그 회선사용료");
        unresolvedCurrency.setCttOppNm("Bloomberg");
        unresolvedCurrency.setCostSvnDpmC("0210");
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(),
                        List.of(unresolvedCurrency),
                        List.of(
                                RequestFormDto.FormDiagnostic.about(
                                        FormSheetKind.GENERAL_EXPENSE,
                                        5,
                                        "curC",
                                        "블룸버그 회선사용료",
                                        RequestFormDiagnosticCode.CODE_UNRESOLVED,
                                        "통화 구분이 비어 있습니다. 통화를 골라 주세요.",
                                        List.of(new MigrationDto.Candidate("KRW", "원화")))),
                        null);

        assertThat(validator().validate(output, "2026"))
                .extracting(RequestFormDto.FormDiagnostic::field)
                .doesNotContain("curC", "amt");
    }

    @Test
    @DisplayName("어댑터가 짚지 않은 행의 통화·금액 누락은 그대로 보고한다")
    void stillReportsCurrencyAndAmountMissingWithoutAdapterDiagnostic() {
        CostDto.CreateRequest missingCurrency = new CostDto.CreateRequest();
        missingCurrency.setIoeC("010");
        missingCurrency.setCttNm("다른 계약");
        missingCurrency.setCttOppNm("Bloomberg");
        missingCurrency.setCostSvnDpmC("0210");

        assertThat(validator().validate(costsOf(missingCurrency), "2026"))
                .extracting(RequestFormDto.FormDiagnostic::field)
                .contains("curC", "amt");
    }

    @Test
    @DisplayName("전산업무비 진단은 계약명을 대상으로 짚어 준다")
    void namesTheContractBehindEachDiagnostic() {
        FormAdapterOutput output =
                new FormAdapterOutput(
                        List.of(),
                        List.of(cost(null, "블룸버그 회선사용료", new BigDecimal("1000"))),
                        List.of(),
                        null);

        assertThat(validator().validate(output, "2026"))
                .filteredOn(d -> "ioeC".equals(d.field()))
                .singleElement()
                .satisfies(
                        d -> {
                            // 대상은 subject가 들고, 문구는 무엇이 문제인지만 말한다 (화면에서 중복 표기 방지)
                            assertThat(d.subject()).isEqualTo("블룸버그 회선사용료");
                            assertThat(d.message())
                                    .startsWith("비목코드이(가) 비어 있습니다.")
                                    .doesNotContain("블룸버그");
                        });
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
