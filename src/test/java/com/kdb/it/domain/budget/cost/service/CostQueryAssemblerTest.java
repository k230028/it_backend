package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.approval.domain.ApprovalStatus;
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

    private record ApproverView(String dcdMngNo) implements ApproverRepository.ApproverReadView {
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
            return "10002";
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

    private CostQueryAssembler assembler;

    @BeforeEach
    void setUp() {
        CodeNameMapBuilder codeNameMapBuilder = new CodeNameMapBuilder(codeRepository);
        assembler =
                new CostQueryAssembler(
                        applicationMapRepository,
                        applicationRepository,
                        organizationRepository,
                        userRepository,
                        approverRepository,
                        codeRepository,
                        budgetRepository,
                        costRepository,
                        codeNameMapBuilder,
                        new CostTerminalAssembler(
                                terminalRepository, userRepository, codeNameMapBuilder));
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
                .willReturn(List.of(new ApproverView("APF-1")));
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
        given(costRepository.sumPrevBgByCostBgNos(List.of("COST-PREV"), "2026"))
                .willReturn(java.util.Map.of("COST-PREV", BigDecimal.valueOf(900)));

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
    @DisplayName("목록 조립: 배치 경계에서 이전예산과 이전 편성예산을 행 기준연도로 채운다")
    void assembleList_이전예산과편성예산_배치조립() {
        Bcostm cost = enrichedCost("COST-LIST", 1);
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(new OrgView("D01", "정보기술부"), new OrgView("T01", "개발팀")));
        given(userRepository.findNameViewsByEnoIn(any()))
                .willReturn(List.of(new UserView("10001", "담당자", "과장")));
        stubCodes();
        given(costRepository.sumPrevBgByCostBgNos(List.of("COST-PREV"), "2026"))
                .willReturn(java.util.Map.of("COST-PREV", BigDecimal.valueOf(900)));
        given(budgetRepository.sumDupBgByItMngcNos(List.of("COST-PREV"), "2026"))
                .willReturn(java.util.Map.of("COST-PREV", BigDecimal.valueOf(700)));

        CostDto.Response result = assembler.assembleList(List.of(cost)).getFirst();

        assertThat(result.getCostSvnDpmNm()).isEqualTo("정보기술부");
        assertThat(result.getCgprNm()).isEqualTo("담당자");
        assertThat(result.getPrevBgAmt()).isEqualByComparingTo("900");
        assertThat(result.getPrevDupBg()).isEqualByComparingTo("700");
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
