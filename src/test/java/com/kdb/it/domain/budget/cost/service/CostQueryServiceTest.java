package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CostQueryServiceTest {

    private CostRepository costRepository;
    private BtermmRepository terminalRepository;
    private CostQueryService queryService;

    @BeforeEach
    void setUp() {
        costRepository = mock(CostRepository.class);
        terminalRepository = mock(BtermmRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CodeRepository codeRepository = mock(CodeRepository.class);
        CostTerminalAssembler terminalAssembler =
                new CostTerminalAssembler(
                        terminalRepository, userRepository, new CodeNameMapBuilder(codeRepository));
        CostQueryAssembler assembler =
                new CostQueryAssembler(
                        mock(ApplicationMapRepository.class),
                        mock(ApplicationRepository.class),
                        mock(OrganizationRepository.class),
                        userRepository,
                        mock(ApproverRepository.class),
                        codeRepository,
                        mock(BbugtmRepository.class),
                        costRepository,
                        new CodeNameMapBuilder(codeRepository),
                        terminalAssembler);
        queryService = new CostQueryService(costRepository, assembler);
    }

    @Test
    @DisplayName("단건 조회: 활성 비용이 없으면 관리번호를 포함한 예외를 반환한다")
    void getCost_미존재_예외() {
        given(costRepository.findByCostBgNoAndDelYn("COST-NOT-FOUND", "N")).willReturn(List.of());

        assertThatThrownBy(() -> queryService.getCost("COST-NOT-FOUND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COST-NOT-FOUND");
    }

    @Test
    @DisplayName("단건 조회: 대표행 규칙으로 최신 활성 이력을 상세 응답에 사용한다")
    void getCost_복수이력_대표행상세반환() {
        Bcostm latest = cost("COST-DETAIL", 2, "Y");
        given(costRepository.findByCostBgNoAndDelYn("COST-DETAIL", "N"))
                .willReturn(List.of(latest));

        CostDto.Response result = queryService.getCost("COST-DETAIL");

        assertThat(result.getCostBgNo()).isEqualTo("COST-DETAIL");
        assertThat(result.getBgSno()).isEqualTo(2);
        assertThat(result.getTerminals()).isEmpty();
        verify(costRepository).findByCostBgNoAndDelYn("COST-DETAIL", "N");
    }

    @Test
    @DisplayName("전체 목록 조회: 활성 비용 목록을 실제 조립 결과로 반환한다")
    void getCostList_활성목록_조립결과반환() {
        given(costRepository.findAllByDelYn("N"))
                .willReturn(List.of(cost("COST-LIST-1", 1, "Y"), cost("COST-LIST-2", 1, "Y")));

        List<CostDto.Response> result = queryService.getCostList();

        assertThat(result)
                .extracting(CostDto.Response::getCostBgNo)
                .containsExactly("COST-LIST-1", "COST-LIST-2");
    }

    @Test
    @DisplayName("검색 목록 조회: 검색 조건을 저장소에 적용하고 실제 조립 결과를 반환한다")
    void searchCostList_검색조건_조립결과반환() {
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        condition.setBseYy("2027");
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-SEARCH")
                        .bgSno(1)
                        .lstYn("Y")
                        .bseYy("2027")
                        .delYn("N")
                        .build();
        given(
                        costRepository.searchByCondition(
                                org.mockito.ArgumentMatchers.eq(condition),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(cost));

        List<CostDto.Response> result = queryService.searchCostList(condition);

        assertThat(result)
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getCostBgNo()).isEqualTo("COST-SEARCH");
                            assertThat(value.getBseYy()).isEqualTo("2027");
                        });
    }

    @Test
    @DisplayName("일괄 조회: 요청이 null이면 저장소 호출 없이 빈 성공·실패 목록을 반환한다")
    void getCostsByIds_null요청_빈응답() {
        CostDto.BulkResponse result = queryService.getCostsByIds(null);

        assertThat(result.items()).isEmpty();
        assertThat(result.failedIds()).isEmpty();
        verifyNoInteractions(costRepository);
    }

    @Test
    @DisplayName("일괄 조회: 관리번호 목록이 null 또는 빈 목록이면 빈 응답을 반환한다")
    void getCostsByIds_null또는빈목록_빈응답() {
        CostDto.BulkGetRequest nullIds = new CostDto.BulkGetRequest();
        CostDto.BulkGetRequest emptyIds = new CostDto.BulkGetRequest(List.of(), "2027");

        assertThat(queryService.getCostsByIds(nullIds).items()).isEmpty();
        assertThat(queryService.getCostsByIds(emptyIds).failedIds()).isEmpty();
        verifyNoInteractions(costRepository);
    }

    @Test
    @DisplayName("일괄 조회: 부분 성공과 중복 요청을 입력 순서 그대로 반환한다")
    void getCostsByIds_부분성공과중복_입력순서보존() {
        Bcostm first = cost("COST-1", 1, "Y");
        Bcostm thirdLatest = cost("COST-3", 2, "Y");
        List<String> requested = List.of("COST-1", "COST-2", "COST-3", "COST-1");
        // 조회는 중복을 제거해 보내고, 응답은 요청 순서와 중복을 그대로 보존한다.
        given(costRepository.findByCostBgNoInAndDelYn(List.of("COST-1", "COST-2", "COST-3"), "N"))
                .willReturn(List.of(first, thirdLatest));
        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest(requested, null);

        CostDto.BulkResponse result = queryService.getCostsByIds(request);

        assertThat(result.items())
                .extracting(CostDto.Response::getCostBgNo)
                .containsExactly("COST-1", "COST-3", "COST-1");
        assertThat(result.items()).extracting(CostDto.Response::getBgSno).containsExactly(1, 2, 1);
        assertThat(result.failedIds()).containsExactly("COST-2");
    }

    @Test
    @DisplayName("일괄 조회: 단말여부 N이어도 단건과 같은 활성 단말기를 한 번의 배치 조회로 반환한다")
    void getCostsByIds_단말여부N_단건과단말기동등() {
        assertSingleAndBulkTerminalsEqual("N", "COST-TERMINAL-N", "TER-N");
    }

    @Test
    @DisplayName("일괄 조회: 단말여부 null이어도 단건과 같은 활성 단말기를 한 번의 배치 조회로 반환한다")
    void getCostsByIds_단말여부Null_단건과단말기동등() {
        assertSingleAndBulkTerminalsEqual(null, "COST-TERMINAL-NULL", "TER-NULL");
    }

    @Test
    @DisplayName("개정본 상세: 지정한 BG_SNO의 BCOSTM과 BTERMM만 함께 반환한다")
    void getCost_개정순번_같은순번단말기만반환() {
        String costBgNo = "COST-2027-0001";
        Bcostm revision =
                Bcostm.builder()
                        .costBgNo(costBgNo)
                        .bgSno(3)
                        .costTotXpAmt(new java.math.BigDecimal("200000000"))
                        .lstYn("N")
                        .delYn("N")
                        .build();
        Btermm revisionTerminal =
                Btermm.builder()
                        .tmnMngNo("TER-REV-3")
                        .sno(1)
                        .termBgNo(costBgNo)
                        .termBgSno(3)
                        .termRqmBgAmt(new java.math.BigDecimal("200000000"))
                        .delYn("N")
                        .build();
        given(costRepository.findByCostBgNoAndBgSnoAndDelYn(costBgNo, 3, "N"))
                .willReturn(java.util.Optional.of(revision));
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn(costBgNo, 3, "N"))
                .willReturn(List.of(revisionTerminal));

        CostDto.Response result = queryService.getCost(costBgNo, 3);

        assertThat(result.getBgSno()).isEqualTo(3);
        assertThat(result.getCostTotXpAmt()).isEqualByComparingTo("200000000");
        assertThat(result.getTerminals())
                .singleElement()
                .satisfies(
                        terminal -> {
                            assertThat(terminal.getTmnMngNo()).isEqualTo("TER-REV-3");
                            assertThat(terminal.getTermRqmBgAmt())
                                    .isEqualByComparingTo("200000000");
                        });
    }

    private void assertSingleAndBulkTerminalsEqual(
            String terminalYn, String costBgNo, String terminalNo) {
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo(costBgNo)
                        .bgSno(1)
                        .lstYn("Y")
                        .tmnYn(terminalYn)
                        .delYn("N")
                        .build();
        Btermm terminal =
                Btermm.builder()
                        .tmnMngNo(terminalNo)
                        .sno(1)
                        .termBgNo(costBgNo)
                        .termBgSno(1)
                        .delYn("N")
                        .build();
        given(costRepository.findByCostBgNoAndDelYn(costBgNo, "N")).willReturn(List.of(cost));
        given(costRepository.findByCostBgNoInAndDelYn(List.of(costBgNo), "N"))
                .willReturn(List.of(cost));
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn(costBgNo, 1, "N"))
                .willReturn(List.of(terminal));
        given(terminalRepository.findByTermBgNoInAndDelYn(List.of(costBgNo), "N"))
                .willReturn(List.of(terminal));

        CostDto.Response detail = queryService.getCost(costBgNo);
        CostDto.Response bulk =
                queryService
                        .getCostsByIds(new CostDto.BulkGetRequest(List.of(costBgNo), null))
                        .items()
                        .getFirst();

        assertThat(detail.getTerminals())
                .extracting(CostDto.TerminalDto::getTmnMngNo)
                .containsExactly(terminalNo);
        assertThat(bulk.getTerminals())
                .extracting(CostDto.TerminalDto::getTmnMngNo)
                .containsExactlyElementsOf(
                        detail.getTerminals().stream()
                                .map(CostDto.TerminalDto::getTmnMngNo)
                                .toList());
        verify(terminalRepository, times(1)).findByTermBgNoInAndDelYn(List.of(costBgNo), "N");
    }

    private static Bcostm cost(String costBgNo, int bgSno, String lstYn) {
        return Bcostm.builder().costBgNo(costBgNo).bgSno(bgSno).lstYn(lstYn).delYn("N").build();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("bulk 조회에 버전을 지정하면 최종본이 아니라 그 개정본을 반환한다")
    void getCostsByIds_버전을_지정하면_해당_개정본을_반환한다() {
        Bcostm draft =
                Bcostm.builder().costBgNo("COST-2026-0001").bgSno(2).lstYn("N").delYn("N").build();
        given(
                        costRepository.findByCostBgNoInAndDelYnAndBgSnoIn(
                                java.util.List.of("COST-2026-0001"), "N", java.util.List.of(2)))
                .willReturn(java.util.List.of(draft));

        CostDto.BulkGetRequest request = new CostDto.BulkGetRequest();
        request.setCostBgNos(java.util.List.of("COST-2026-0001"));
        request.setVersions(java.util.List.of(new CostDto.VersionRef("COST-2026-0001", 2)));

        CostDto.BulkResponse response = queryService.getCostsByIds(request);

        assertThat(response.failedIds()).isEmpty();
        assertThat(response.items())
                .singleElement()
                .extracting(CostDto.Response::getBgSno)
                .isEqualTo(2);
    }
}
