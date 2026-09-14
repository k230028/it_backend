package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostQueryAssemblerTest {

    private record ApplicationMapView(String apfDcmNo, String pkColNm, Integer fntTbCrySno)
            implements ApplicationMapRepository.ApplicationMapView {
        @Override
        public String getApfDcmNo() {
            return apfDcmNo;
        }

        @Override
        public String getPkColNm() {
            return pkColNm;
        }

        @Override
        public Integer getFntTbCrySno() {
            return fntTbCrySno;
        }
    }

    private record ApplicationSummaryView(String apfMngNo, String itPtlApfPrgStsC)
            implements ApplicationRepository.ApplicationSummaryView {
        @Override
        public String getApfMngNo() {
            return apfMngNo;
        }

        @Override
        public String getItPtlApfPrgStsC() {
            return itPtlApfPrgStsC;
        }

        @Override
        public String getDcdReqTtl() {
            return "전산업무비 신청";
        }

        @Override
        public String getDcdReqUsid() {
            return "10001";
        }

        @Override
        public LocalDate getDcdReqDtm() {
            return LocalDate.of(2026, 8, 5);
        }

        @Override
        public String getRgprDcdReqCone() {
            return "신청 사유";
        }
    }

    private record ApproverView(String dcdMngNo, String dcrEno)
            implements ApproverRepository.ApproverReadView {
        @Override
        public String getDcdMngNo() {
            return dcdMngNo;
        }

        @Override
        public Integer getDcrSqnSno() {
            return 1;
        }

        @Override
        public String getDcrEno() {
            return dcrEno;
        }

        @Override
        public String getItPtlDcdStsC() {
            return "2";
        }

        @Override
        public LocalDate getDcdDtm() {
            return LocalDate.of(2026, 8, 5);
        }

        @Override
        public String getDcrOpnnCone() {
            return "승인";
        }

        @Override
        public String getLstDcdYn() {
            return "Y";
        }
    }

    private record OrgView(String prlmOgzCCone, String bbrNm)
            implements OrganizationRepository.OrganizationNameView {
        @Override
        public String getPrlmOgzCCone() {
            return prlmOgzCCone;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }
    }

    private record UserView(String eno, String usrNm, String ptCNm)
            implements UserRepository.UserNameView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return ptCNm;
        }
    }

    @Mock private ApplicationMapRepository applicationMapRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApproverRepository approverRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private BbugtmRepository budgetRepository;
    @Mock private CostRepository costRepository;
    @Mock private BtermmRepository terminalRepository;
    @Mock private CostConcurrencyStamper concurrencyStamper;

    private final ObjectMapper mapper = new ObjectMapper();

    private CostQueryAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = assemblerWith(concurrencyStamper);
    }

    /** 스탬프 계산기만 바꿔 끼운 조립기를 만든다. 배치·상세 스탬프 동등성 검증은 실제 계산기를 넣는다. */
    private CostQueryAssembler assemblerWith(CostConcurrencyStamper stamper) {
        CodeNameMapBuilder codeNameMapBuilder = new CodeNameMapBuilder(codeRepository);
        return new CostQueryAssembler(
                applicationMapRepository,
                applicationRepository,
                organizationRepository,
                userRepository,
                approverRepository,
                codeRepository,
                budgetRepository,
                costRepository,
                codeNameMapBuilder,
                new CostTerminalAssembler(terminalRepository, userRepository, codeNameMapBuilder),
                stamper);
    }

    @Test
    @DisplayName("단건 조립: 신청·결재·조직·사용자·코드·단말기·전년도 예산을 함께 채운다")
    void assembleDetail_전체연관정보_조립() {
        Bcostm cost = enrichedCost("COST-DETAIL", 2);
        given(
                        applicationMapRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BCOSTM", "COST-DETAIL", 2))
                .willReturn(List.of(new ApplicationMapView("APF-1", "COST-DETAIL", 2)));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-1")))
                .willReturn(
                        List.of(
                                new ApplicationSummaryView(
                                        "APF-1", ApprovalStatus.COMPLETED.code())));
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-1"))
                .willReturn(List.of(new ApproverView("APF-1", "10002")));
        given(organizationRepository.findNameViewByPrlmOgzCCone("D01"))
                .willReturn(Optional.of(new OrgView("D01", "정보기술부")));
        given(organizationRepository.findNameViewByPrlmOgzCCone("T01"))
                .willReturn(Optional.of(new OrgView("T01", "개발팀")));
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new UserView("10001", "담당자", "과장")));
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new UserView("20001", "단말담당자", "대리")));
        stubCodes();
        Btermm terminal =
                Btermm.builder()
                        .tmnMngNo("TER-1")
                        .sno(1)
                        .termBgNo("COST-DETAIL")
                        .termBgSno(2)
                        .cgprId("20001")
                        .tmnClsfC("SVC")
                        .tmnKdTc("KIND")
                        .dfrCleC("M")
                        .build();
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-DETAIL", 2, "N"))
                .willReturn(List.of(terminal));
        given(
                        costRepository.findByCostBgNoInAndBseYyAndLstYnAndDelYn(
                                List.of("COST-PREV"), "2026", "Y", "N"))
                .willReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-PREV")
                                        .curC("USD")
                                        .fcAmt(BigDecimal.valueOf(900))
                                        .costTotXpAmt(BigDecimal.valueOf(1_350_000))
                                        .build()));

        CostDto.Response result = assembler.assembleDetail(cost);

        assertThat(result.getApfMngNo()).isEqualTo("APF-1");
        assertThat(result.getApfSts()).isEqualTo(ApprovalStatus.COMPLETED.label());
        assertThat(result.getApplicationInfo().getApprovers()).hasSize(1);
        assertThat(result.getCostSvnDpmNm()).isEqualTo("정보기술부");
        assertThat(result.getSvnTemNm()).isEqualTo("개발팀");
        assertThat(result.getCgprNm()).isEqualTo("담당자");
        assertThat(result.getCgprPtCNm()).isEqualTo("과장");
        assertThat(result.getBgUntAbusCNm()).isEqualTo("본부사업");
        assertThat(result.getDfrCleCNm()).isEqualTo("매월");
        assertThat(result.getTmnYnNm()).isEqualTo("단말기 있음");
        assertThat(result.getAbusTcNm()).isEqualTo("계속");
        assertThat(result.getIoeCNm()).isEqualTo("개발비");
        assertThat(result.getPrevBgAmt()).isEqualByComparingTo("900");
        assertThat(result.getPrevCurC()).isEqualTo("USD");
        assertThat(result.getAssetBg()).isEqualByComparingTo("1000");
        assertThat(result.getDvcBg()).isEqualByComparingTo("1000");
        assertThat(result.getTerminals())
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getCgprNm()).isEqualTo("단말담당자");
                            assertThat(value.getTmnClsfCNm()).isEqualTo("금융망");
                            assertThat(value.getTmnKdTcNm()).isEqualTo("전용");
                            assertThat(value.getDfrCleCNm()).isEqualTo("매월");
                        });
    }

    @Test
    @DisplayName("상세 조립은 활성 단말로 계산한 동시성 스탬프를 응답에 싣는다")
    void assembleDetailAttachesConcurrencyStamp() {
        Bcostm cost = Bcostm.builder().costBgNo("COST_2026_0001").bgSno(2).build();
        List<Btermm> terminals = List.of(Btermm.builder().tmnMngNo("TMN-1").sno(1).build());
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST_2026_0001", 2, "N"))
                .willReturn(terminals);
        given(concurrencyStamper.stamp(cost, terminals)).willReturn("a".repeat(64));

        CostDto.Response response = assembler.assembleDetail(cost);

        assertThat(response.getConcurrencyStamp()).isEqualTo("a".repeat(64));
    }

    @Test
    @DisplayName("목록 조립: 배치 경계에서 이전예산과 이전 편성예산을 행 기준연도로 채운다")
    void assembleList_이전예산과편성예산_배치조립() {
        Bcostm cost = enrichedCost("COST-LIST", 1);
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(new OrgView("D01", "정보기술부"), new OrgView("T01", "개발팀")));
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new UserView("10001", "담당자", "과장")));
        stubCodes();
        given(
                        costRepository.findByCostBgNoInAndBseYyAndLstYnAndDelYn(
                                List.of("COST-PREV"), "2026", "Y", "N"))
                .willReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-PREV")
                                        .curC("USD")
                                        .fcAmt(BigDecimal.valueOf(900))
                                        .build()));
        given(budgetRepository.sumDupBgByItMngcNos(List.of("COST-PREV"), "2026"))
                .willReturn(java.util.Map.of("COST-PREV", BigDecimal.valueOf(700)));

        CostDto.Response result = assembler.assembleList(List.of(cost)).getFirst();

        assertThat(result.getCostSvnDpmNm()).isEqualTo("정보기술부");
        assertThat(result.getCgprNm()).isEqualTo("담당자");
        assertThat(result.getPrevBgAmt()).isEqualByComparingTo("900");
        assertThat(result.getPrevCurC()).isEqualTo("USD");
        assertThat(result.getPrevDupBg()).isEqualByComparingTo("700");
    }

    @Test
    @DisplayName("이력 조립: 개정본 세 건의 단말기·신청·사용자·코드를 각각 한 번만 배치 조회한다")
    void assembleHistory_개정본세건_단말기연관정보배치조회() {
        List<Bcostm> history =
                List.of(
                        Bcostm.builder().costBgNo("COST-HISTORY").bgSno(1).build(),
                        Bcostm.builder().costBgNo("COST-HISTORY").bgSno(2).build(),
                        Bcostm.builder().costBgNo("COST-HISTORY").bgSno(3).build());
        given(terminalRepository.findByTermBgNoInAndDelYn(List.of("COST-HISTORY"), "N"))
                .willReturn(
                        List.of(
                                historyTerminal("TMN-1", 1, "20001"),
                                historyTerminal("TMN-2", 2, "20002"),
                                historyTerminal("TMN-3", 3, "20003")));
        given(
                        applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                "BCOSTM", List.of("COST-HISTORY")))
                .willReturn(
                        List.of(
                                new ApplicationMapView("APF-3", "COST-HISTORY", 3),
                                new ApplicationMapView("APF-2", "COST-HISTORY", 2),
                                new ApplicationMapView("APF-1", "COST-HISTORY", 1)));
        given(
                        applicationRepository.findSummaryViewsByApfMngNoIn(
                                List.of("APF-3", "APF-2", "APF-1")))
                .willReturn(
                        List.of(
                                new ApplicationSummaryView(
                                        "APF-1", ApprovalStatus.COMPLETED.code()),
                                new ApplicationSummaryView(
                                        "APF-2", ApprovalStatus.IN_PROGRESS.code()),
                                new ApplicationSummaryView(
                                        "APF-3", ApprovalStatus.REJECTED.code())));
        given(
                        approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(
                                List.of("APF-3", "APF-2", "APF-1")))
                .willReturn(
                        List.of(
                                new ApproverView("APF-1", "10011"),
                                new ApproverView("APF-2", "10012"),
                                new ApproverView("APF-3", "10013")));
        given(userRepository.findNameViewsByEnoIn(java.util.Set.of("20001", "20002", "20003")))
                .willReturn(
                        List.of(
                                new UserView("20001", "단말담당1", "대리"),
                                new UserView("20002", "단말담당2", "대리"),
                                new UserView("20003", "단말담당3", "대리")));
        stubCodes();

        List<CostDto.Response> responses = assembler.assembleHistory(history);

        assertThat(responses).extracting(CostDto.Response::getBgSno).containsExactly(1, 2, 3);
        assertThat(responses)
                .extracting(response -> response.getTerminals().getFirst().getTmnMngNo())
                .containsExactly("TMN-1", "TMN-2", "TMN-3");
        assertThat(responses)
                .extracting(response -> response.getTerminals().getFirst().getCgprNm())
                .containsExactly("단말담당1", "단말담당2", "단말담당3");
        assertThat(responses)
                .extracting(CostDto.Response::getApfMngNo)
                .containsExactly("APF-1", "APF-2", "APF-3");
        assertThat(responses)
                .extracting(CostDto.Response::getApfSts)
                .containsExactly(
                        ApprovalStatus.COMPLETED.label(),
                        ApprovalStatus.IN_PROGRESS.label(),
                        ApprovalStatus.REJECTED.label());
        assertThat(responses)
                .extracting(response -> response.getApplicationInfo().getApprovers())
                .allSatisfy(approvers -> assertThat(approvers).hasSize(1));
        assertThat(responses)
                .extracting(
                        response ->
                                response.getApplicationInfo().getApprovers().getFirst().getDcdEno())
                .containsExactly("10011", "10012", "10013");
        assertThat(responses)
                .extracting(response -> response.getTerminals().getFirst().getTmnClsfCNm())
                .containsOnly("금융망");
        assertThat(responses)
                .extracting(response -> response.getTerminals().getFirst().getTmnKdTcNm())
                .containsOnly("전용");
        assertThat(responses)
                .extracting(response -> response.getTerminals().getFirst().getDfrCleCNm())
                .containsOnly("매월");
        verify(applicationMapRepository)
                .findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BCOSTM", List.of("COST-HISTORY"));
        verify(terminalRepository).findByTermBgNoInAndDelYn(List.of("COST-HISTORY"), "N");
        verify(userRepository).findNameViewsByEnoIn(java.util.Set.of("20001", "20002", "20003"));
        verify(codeRepository, times(1))
                .findByCIdWithValidDate(CommonCodeGroups.TERM_SERVICE, null);
        verify(codeRepository, times(1)).findByCIdWithValidDate(CommonCodeGroups.TERM_KIND, null);
        verify(codeRepository, times(1)).findByCIdWithValidDate(CommonCodeGroups.DFR_CLE, null);
        verifyNoMoreInteractions(userRepository, codeRepository);
    }

    @Test
    @DisplayName("담당자 이름이 저장된 전산업무비는 이름 필드에 두고 사번 필드를 비운다")
    void assembleList_separatesStoredManagerNameFromEmployeeId() {
        Bcostm cost =
                Bcostm.builder().costBgNo("COST-NAME").bgSno(1).lstYn("Y").cgprId("김담당").build();

        CostDto.Response result = assembler.assembleList(List.of(cost)).getFirst();

        assertThat(result.getCgprNm()).isEqualTo("김담당");
        assertThat(result.getCgprId()).isNull();
    }

    @Test
    @DisplayName("목록 조립: 행번이 빈 행은 담당자명 스냅샷을 조인 결과로 덮지 않고 유지한다")
    void assembleList_행번빈행_담당자명스냅샷유지() {
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-EMPTY-ID")
                        .bgSno(1)
                        .lstYn("Y")
                        .cgprId(null)
                        .cgprNm("홍길동")
                        .build();

        CostDto.Response result = assembler.assembleList(List.of(cost)).getFirst();

        assertThat(result.getCgprNm()).isEqualTo("홍길동");
        assertThat(result.getCgprId()).isNull();
    }

    @Test
    @DisplayName("목록 조립: 미해석 행번(퇴직 등)이라도 담당자명 스냅샷을 null로 덮지 않는다")
    void assembleList_미해석행번_담당자명스냅샷유지() {
        /* 사번 형태(K999999)지만 사용자 조회가 비는 행 — 종전에는 이름이 null로 덮여 사라졌다 */
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-RETIRED")
                        .bgSno(1)
                        .lstYn("Y")
                        .cgprId("K999999")
                        .cgprNm("퇴직자")
                        .build();

        CostDto.Response result = assembler.assembleList(List.of(cost)).getFirst();

        assertThat(result.getCgprNm()).isEqualTo("퇴직자");
        assertThat(result.getCgprId()).isEqualTo("K999999");
    }

    @Test
    @DisplayName("단건 조립: 최초 작성자·최근 수정자 감사 필드를 응답에 싣고 사번을 이름으로 채운다")
    void assembleDetail_감사자명_배치조회로채움() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 5, 9, 12);
        LocalDateTime modified = LocalDateTime.of(2026, 9, 14, 10, 30);
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-AUDIT")
                        .bgSno(1)
                        .lstYn("Y")
                        .fstEnrUsid("K100001")
                        .fstEnrDtm(created)
                        .lstChgUsid("K100002")
                        .lstChgDtm(modified)
                        .build();
        given(
                        userRepository.findNameViewsByEnoIn(
                                argThat(enos -> enos.containsAll(List.of("K100001", "K100002")))))
                .willReturn(
                        List.of(
                                new UserView("K100001", "작성자", "대리"),
                                new UserView("K100002", "수정자", "과장")));

        CostDto.Response result = assembler.assembleDetail(cost);

        assertThat(result.getFstEnrUsid()).isEqualTo("K100001");
        assertThat(result.getFstEnrUsNm()).isEqualTo("작성자");
        assertThat(result.getFstEnrDtm()).isEqualTo(created);
        assertThat(result.getLstChgUsid()).isEqualTo("K100002");
        assertThat(result.getLstChgUsNm()).isEqualTo("수정자");
        assertThat(result.getLstChgDtm()).isEqualTo(modified);
    }

    @Test
    @DisplayName("단건 조립: 감사자 사번이 미해석(DB 기본값·퇴직)이면 이름은 비우고 사번은 그대로 둔다")
    void assembleDetail_감사자미해석_사번유지_이름null() {
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-AUDIT-LEGACY")
                        .bgSno(1)
                        .lstYn("Y")
                        .fstEnrUsid("00000000000000")
                        .lstChgUsid("K999999")
                        .build();
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(List.of());

        CostDto.Response result = assembler.assembleDetail(cost);

        assertThat(result.getFstEnrUsid()).isEqualTo("00000000000000");
        assertThat(result.getFstEnrUsNm()).isNull();
        assertThat(result.getLstChgUsid()).isEqualTo("K999999");
        assertThat(result.getLstChgUsNm()).isNull();
    }

    @Test
    @DisplayName("단건 조립: 행번이 빈 행은 담당자명 스냅샷을 조인 결과로 덮지 않고 유지한다")
    void assembleDetail_행번빈행_담당자명스냅샷유지() {
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-EMPTY-ID")
                        .bgSno(1)
                        .lstYn("Y")
                        .cgprId(null)
                        .cgprNm("홍길동")
                        .build();

        CostDto.Response result = assembler.assembleDetail(cost);

        assertThat(result.getCgprNm()).isEqualTo("홍길동");
        assertThat(result.getCgprId()).isNull();
    }

    @Test
    @DisplayName("대표행 동등성: 같은 대표행은 단건·목록·bulk에서 같은 이력과 편성예산 분류를 만든다")
    void 대표행_단건목록bulk_동등성() {
        Bcostm representative =
                CostRepresentativeSelector.pick(
                        List.of(
                                Bcostm.builder().costBgNo("COST-EQUAL").bgSno(1).lstYn("N").build(),
                                Bcostm.builder()
                                        .costBgNo("COST-EQUAL")
                                        .bgSno(3)
                                        .lstYn("Y")
                                        .ioeC("101")
                                        .costTotXpAmt(BigDecimal.valueOf(1000))
                                        .build()));
        stubCodes();
        given(budgetRepository.sumDupBgByItMngcNos(List.of("COST-EQUAL"), "2027"))
                .willReturn(java.util.Map.of("COST-EQUAL", BigDecimal.valueOf(600)));

        CostDto.Response detail = assembler.assembleDetail(representative);
        CostDto.Response list = assembler.assembleList(List.of(representative)).getFirst();
        CostDto.Response bulk = assembler.assembleBulk(List.of(representative), "2027").getFirst();

        assertThat(List.of(detail.getBgSno(), list.getBgSno(), bulk.getBgSno())).containsOnly(3);
        assertThat(List.of(detail.getAssetBg(), list.getAssetBg(), bulk.getAssetBg()))
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("1000"));
        assertThat(bulk.getAssetDupBg()).isEqualByComparingTo("600");
        assertThat(bulk.getCostDupBg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("목록·일괄·이력 조립도 활성 단말로 계산한 동시성 스탬프를 응답에 싣는다")
    void assembleBatchAttachesConcurrencyStamp() {
        Bcostm cost = Bcostm.builder().costBgNo("COST-BATCH").bgSno(2).tmnYn("Y").build();
        Btermm terminal =
                Btermm.builder()
                        .tmnMngNo("TMN-1")
                        .sno(1)
                        .termBgNo("COST-BATCH")
                        .termBgSno(2)
                        .build();
        given(terminalRepository.findByTermBgNoInAndDelYn(List.of("COST-BATCH"), "N"))
                .willReturn(List.of(terminal));
        given(concurrencyStamper.stamp(cost, List.of(terminal))).willReturn("b".repeat(64));
        stubCodes();

        assertThat(assembler.assembleList(List.of(cost)).getFirst().getConcurrencyStamp())
                .isEqualTo("b".repeat(64));
        assertThat(assembler.assembleBulk(List.of(cost), "2027").getFirst().getConcurrencyStamp())
                .isEqualTo("b".repeat(64));
        assertThat(assembler.assembleHistory(List.of(cost)).getFirst().getConcurrencyStamp())
                .isEqualTo("b".repeat(64));
    }

    @Test
    @DisplayName("배치 조립은 단말을 IN 조회 한 번만 하고 행마다 조회하지 않는다")
    void assembleBatchLoadsTerminalsInOneQuery() {
        List<Bcostm> costs =
                List.of(
                        Bcostm.builder().costBgNo("COST-N1").bgSno(1).tmnYn("Y").build(),
                        Bcostm.builder().costBgNo("COST-N2").bgSno(1).tmnYn("Y").build(),
                        Bcostm.builder().costBgNo("COST-N3").bgSno(1).tmnYn("Y").build());
        given(
                        terminalRepository.findByTermBgNoInAndDelYn(
                                List.of("COST-N1", "COST-N2", "COST-N3"), "N"))
                .willReturn(List.of());
        stubCodes();

        assembler.assembleList(costs);

        verify(terminalRepository, times(1))
                .findByTermBgNoInAndDelYn(List.of("COST-N1", "COST-N2", "COST-N3"), "N");
        verify(terminalRepository, never()).findByTermBgNoAndTermBgSnoAndDelYn(any(), any(), any());
    }

    @Test
    @DisplayName("단말 보유 표시가 N이어도 남아 있는 활성 단말을 스탬프에 포함한다")
    void assembleBatchStampsUseActiveTerminalsEvenWhenTerminalFlagIsNo() {
        Bcostm cost = Bcostm.builder().costBgNo("COST-FLAG").bgSno(1).tmnYn("N").build();
        Btermm orphan =
                Btermm.builder()
                        .tmnMngNo("TMN-9")
                        .sno(1)
                        .termBgNo("COST-FLAG")
                        .termBgSno(1)
                        .build();
        given(terminalRepository.findByTermBgNoInAndDelYn(List.of("COST-FLAG"), "N"))
                .willReturn(List.of(orphan));
        given(concurrencyStamper.stamp(cost, List.of(orphan))).willReturn("c".repeat(64));
        stubCodes();

        assertThat(assembler.assembleList(List.of(cost)).getFirst().getConcurrencyStamp())
                .isEqualTo("c".repeat(64));
    }

    @Test
    @DisplayName("같은 개정본이면 단건·목록·일괄·이력 스탬프가 모두 같다")
    void detailAndBatchProduceIdenticalStamp() {
        CostQueryAssembler realStampAssembler =
                assemblerWith(new CostConcurrencyStamper(new ItBudgetCanonicalJson(mapper)));
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-SAME")
                        .bgSno(2)
                        .tmnYn("Y")
                        .cttNm("계약")
                        .costTotXpAmt(new BigDecimal("1000"))
                        .build();
        List<Btermm> terminals =
                List.of(
                        Btermm.builder()
                                .tmnMngNo("TMN-2")
                                .sno(1)
                                .termBgNo("COST-SAME")
                                .termBgSno(2)
                                .spfTmnNm("단말B")
                                .termRqmBgAmt(new BigDecimal("500"))
                                .build(),
                        Btermm.builder()
                                .tmnMngNo("TMN-1")
                                .sno(1)
                                .termBgNo("COST-SAME")
                                .termBgSno(2)
                                .spfTmnNm("단말A")
                                .termRqmBgAmt(new BigDecimal("300"))
                                .build());
        given(terminalRepository.findByTermBgNoAndTermBgSnoAndDelYn("COST-SAME", 2, "N"))
                .willReturn(terminals);
        given(terminalRepository.findByTermBgNoInAndDelYn(List.of("COST-SAME"), "N"))
                .willReturn(terminals);
        stubCodes();

        String detail = realStampAssembler.assembleDetail(cost).getConcurrencyStamp();
        String list =
                realStampAssembler.assembleList(List.of(cost)).getFirst().getConcurrencyStamp();
        String bulk =
                realStampAssembler
                        .assembleBulk(List.of(cost), "2027")
                        .getFirst()
                        .getConcurrencyStamp();
        String history =
                realStampAssembler.assembleHistory(List.of(cost)).getFirst().getConcurrencyStamp();

        assertThat(detail).matches("[a-f0-9]{64}");
        assertThat(List.of(list, bulk, history)).containsOnly(detail);
    }

    private Bcostm enrichedCost(String costBgNo, int bgSno) {
        return Bcostm.builder()
                .costBgNo(costBgNo)
                .bgSno(bgSno)
                .lstYn("Y")
                .ioeC("101")
                .costTotXpAmt(BigDecimal.valueOf(1000))
                .dfrCleC("M")
                .cgprId("10001")
                .costSvnDpmC("D01")
                .svnTemC("T01")
                .bgUntAbusC("BU")
                .tmnYn("Y")
                .abusTc("20")
                .bseYy("2027")
                .cncdRfrNo("COST-PREV")
                .build();
    }

    private static Btermm historyTerminal(String terminalNo, int costSno, String managerId) {
        return Btermm.builder()
                .tmnMngNo(terminalNo)
                .sno(1)
                .termBgNo("COST-HISTORY")
                .termBgSno(costSno)
                .cgprId(managerId)
                .tmnClsfC("SVC")
                .tmnKdTc("KIND")
                .dfrCleC("M")
                .build();
    }

    private void stubCodes() {
        given(codeRepository.findByCIdWithValidDate(any(), any()))
                .willAnswer(
                        invocation -> {
                            String group = invocation.getArgument(0);
                            return switch (group) {
                                case CommonCodeGroups.IOE ->
                                        List.of(
                                                Ccodem.builder()
                                                        .cId(group)
                                                        .cdva("101")
                                                        .cdvaNm("개발비")
                                                        .cTp("IOE_DVC")
                                                        .build());
                                case CommonCodeGroups.ABUS_UNIT ->
                                        List.of(code(group, "BU", "본부사업"));
                                case CommonCodeGroups.DFR_CLE -> List.of(code(group, "M", "매월"));
                                case CommonCodeGroups.TMN_YN -> List.of(code(group, "1", "단말기 있음"));
                                case CommonCodeGroups.ABUS -> List.of(code(group, "20", "계속"));
                                case CommonCodeGroups.TERM_SERVICE ->
                                        List.of(code(group, "SVC", "금융망"));
                                case CommonCodeGroups.TERM_KIND ->
                                        List.of(code(group, "KIND", "전용"));
                                default -> List.of();
                            };
                        });
        given(codeRepository.findByCIdAndCdvaWithValidDate(any(), any(), eq(null)))
                .willAnswer(
                        invocation -> {
                            String group = invocation.getArgument(0);
                            String value = invocation.getArgument(1);
                            return codeRepository.findByCIdWithValidDate(group, null).stream()
                                    .filter(code -> value.equals(code.getCdva()))
                                    .findFirst();
                        });
    }

    private static Ccodem code(String group, String value, String name) {
        return Ccodem.builder().cId(group).cdva(value).cdvaNm(name).build();
    }
}
