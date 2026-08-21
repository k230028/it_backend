package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectQueryAssemblerTest {

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

    private record ApplicationSummaryView(
            String apfMngNo,
            String itPtlApfPrgStsC,
            String dcdReqTtl,
            String dcdReqUsid,
            LocalDate dcdReqDtm,
            String rgprDcdReqCone)
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
            return dcdReqTtl;
        }

        @Override
        public String getDcdReqUsid() {
            return dcdReqUsid;
        }

        @Override
        public LocalDate getDcdReqDtm() {
            return dcdReqDtm;
        }

        @Override
        public String getRgprDcdReqCone() {
            return rgprDcdReqCone;
        }
    }

    private record ApproverReadView(
            String dcdMngNo,
            Integer dcrSqnSno,
            String dcrEno,
            String itPtlDcdStsC,
            LocalDate dcdDtm,
            String dcrOpnnCone,
            String lstDcdYn)
            implements ApproverRepository.ApproverReadView {
        @Override
        public String getDcdMngNo() {
            return dcdMngNo;
        }

        @Override
        public Integer getDcrSqnSno() {
            return dcrSqnSno;
        }

        @Override
        public String getDcrEno() {
            return dcrEno;
        }

        @Override
        public String getItPtlDcdStsC() {
            return itPtlDcdStsC;
        }

        @Override
        public LocalDate getDcdDtm() {
            return dcdDtm;
        }

        @Override
        public String getDcrOpnnCone() {
            return dcrOpnnCone;
        }

        @Override
        public String getLstDcdYn() {
            return lstDcdYn;
        }
    }

    private record OrgNameView(String prlmOgzCCone, String bbrNm)
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

    private record UserNameView(String eno, String usrNm, String ptCNm)
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

    private record ItemBudgetView(
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

    private ApplicationMapRepository applicationMapRepository;
    private ApplicationRepository applicationRepository;
    private ProjectItemRepository itemRepository;
    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private ApproverRepository approverRepository;
    private CodeRepository codeRepository;
    private BbugtmRepository budgetRepository;
    private CodeService codeService;
    private BprojaRepository bprojaRepository;
    private ProjectRepository projectRepository;
    private ProjectQueryAssembler assembler;

    @BeforeEach
    void setUp() {
        applicationMapRepository = mock(ApplicationMapRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        itemRepository = mock(ProjectItemRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        approverRepository = mock(ApproverRepository.class);
        codeRepository = mock(CodeRepository.class);
        budgetRepository = mock(BbugtmRepository.class);
        codeService = mock(CodeService.class);
        bprojaRepository = mock(BprojaRepository.class);
        projectRepository = mock(ProjectRepository.class);
        assembler =
                new ProjectQueryAssembler(
                        applicationMapRepository,
                        applicationRepository,
                        itemRepository,
                        organizationRepository,
                        userRepository,
                        approverRepository,
                        codeRepository,
                        budgetRepository,
                        codeService,
                        new ProjectBudgetSummaryService(codeService),
                        bprojaRepository,
                        new CodeNameMapBuilder(codeRepository),
                        projectRepository);
    }

    @Test
    @DisplayName("상세 조립: 최신 신청서와 결재선을 상세 정보에 반영한다")
    void assembleDetail_최신신청서와결재선_반영() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-APP-001").sno(1).delYn("N").build();
        ApplicationMapView latest = new ApplicationMapView("APF-002", "PRJ-APP-001", 1);
        ApplicationMapView old = new ApplicationMapView("APF-001", "PRJ-APP-001", 1);
        ApplicationSummaryView application =
                new ApplicationSummaryView(
                        "APF-002",
                        ApprovalStatus.IN_PROGRESS.code(),
                        "최신 결재 요청",
                        "10001",
                        LocalDate.of(2026, 8, 5),
                        "검토 요청");
        given(
                        applicationMapRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BPROJM", "PRJ-APP-001", 1))
                .willReturn(List.of(latest, old));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-002")))
                .willReturn(List.of(application));
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-002"))
                .willReturn(
                        List.of(
                                new ApproverReadView(
                                        "APF-002",
                                        1,
                                        "20001",
                                        "2",
                                        LocalDate.of(2026, 8, 5),
                                        "승인",
                                        "Y")));

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getApfMngNo()).isEqualTo("APF-002");
        assertThat(result.getApfSts()).isEqualTo("결재중");
        assertThat(result.getApplicationInfo().getApfNm()).isEqualTo("최신 결재 요청");
        assertThat(result.getApplicationInfo().getApprovers())
                .singleElement()
                .satisfies(
                        approver -> {
                            assertThat(approver.getDcdEno()).isEqualTo("20001");
                            assertThat(approver.getDcdSts()).isEqualTo("승인");
                        });
    }

    @Test
    @DisplayName("담당자 이름이 저장된 사업은 이름 필드에 두고 사번 필드를 비운다")
    void assembleDetail_separatesStoredManagerNameFromEmployeeId() {
        Bprojm project =
                Bprojm.builder().abusMngNo("PRJ-NAME-001").sno(1).delYn("N").usid("홍길동").build();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getUsidNm()).isEqualTo("홍길동");
        assertThat(result.getUsid()).isNull();
    }

    @Test
    @DisplayName("상세 조립: 조직·사용자·직위와 공통 코드명을 응답에 반영한다")
    void assembleDetail_조직사용자코드명_반영() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-NAME-001")
                        .sno(1)
                        .dvmDpmC("D001")
                        .svnDpmC("D002")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .rprStsTc("R1")
                        .exePttYn("Y")
                        .abusTc("A1")
                        .edrtTc("E1")
                        .delYn("N")
                        .build();
        given(organizationRepository.findNameViewByPrlmOgzCCone("D001"))
                .willReturn(Optional.of(new OrgNameView("D001", "IT부")));
        given(organizationRepository.findNameViewByPrlmOgzCCone("D002"))
                .willReturn(Optional.of(new OrgNameView("D002", "현업부")));
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new UserNameView("10001", "IT담당", "차장")));
        given(userRepository.findNameViewByEno("10002"))
                .willReturn(Optional.of(new UserNameView("10002", "IT팀장", "팀장")));
        given(userRepository.findNameViewByEno("10003"))
                .willReturn(Optional.of(new UserNameView("10003", "현업담당", "대리")));
        given(userRepository.findNameViewByEno("10004"))
                .willReturn(Optional.of(new UserNameView("10004", "현업팀장", "부장")));
        stubCodeName(CommonCodeGroups.REPORT_STS, "R1", "보고완료");
        stubCodeName(CommonCodeGroups.EXE_POSSIBLE, "Y", "실행가능");
        stubCodeName(CommonCodeGroups.ABUS, "A1", "신규사업");
        stubCodeName(CommonCodeGroups.EDRT, "E1", "편집대상");

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
        assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
        assertThat(result.getDvmUsidNm()).isEqualTo("IT담당");
        assertThat(result.getDvmUsidPtCNm()).isEqualTo("차장");
        assertThat(result.getDvmTlrUsidPtCNm()).isEqualTo("팀장");
        assertThat(result.getUsidPtCNm()).isEqualTo("대리");
        assertThat(result.getTlrUsidPtCNm()).isEqualTo("부장");
        assertThat(result.getRprStsTcNm()).isEqualTo("보고완료");
        assertThat(result.getExePttYnNm()).isEqualTo("실행가능");
        assertThat(result.getAbusTcNm()).isEqualTo("신규사업");
        assertThat(result.getEdrtTcNm()).isEqualTo("편집대상");
    }

    @Test
    @DisplayName("상세 조립: 대표상태·활성 품목·코드명과 예산 합계를 함께 반영한다")
    void assembleDetail_대표상태품목예산_반영() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-ITEM-001").sno(1).delYn("N").build();
        // 자기 행(cncdRfrNo=사업관리번호)이 대표상태를 정한다 — STEP-2는 다른 단계 문서다 (BE-33).
        Bproja writing =
                Bproja.builder()
                        .abusMngNo("PRJ-ITEM-001")
                        .cncdRfrNo("PRJ-ITEM-001")
                        .stsTc("01")
                        .build();
        Bproja approved =
                Bproja.builder().abusMngNo("PRJ-ITEM-001").cncdRfrNo("STEP-2").stsTc("09").build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-001")
                        .abusMngNo("PRJ-ITEM-001")
                        .fntTbCrySno(1)
                        .ioeC("101")
                        .gclNm("서버")
                        .amt(BigDecimal.valueOf(100))
                        .mplAmt(BigDecimal.valueOf(30))
                        .build();
        given(bprojaRepository.findByAbusMngNoAndDelYn("PRJ-ITEM-001", "N"))
                .willReturn(List.of(writing, approved));
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-ITEM-001", 1, "N"))
                .willReturn(List.of(item));
        stubIoeCode();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getStsTc()).isEqualTo("01");
        assertThat(result.getBprojaStsCodes()).containsExactly("01", "09");
        assertThat(result.getItems())
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getGclNm()).isEqualTo("서버");
                            assertThat(value.getIoeCNm()).isEqualTo("개발비");
                        });
        assertThat(result.getAssetBg()).isEqualByComparingTo("100");
        assertThat(result.getDvcBg()).isEqualByComparingTo("100");
        assertThat(result.getMplCpitAmt()).isEqualByComparingTo("30");
        assertThat(result.getTotRqmAmt()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("응답 동등성: 단건·목록·bulk가 신청서·상태·품목·예산 규칙을 공유한다")
    void assembleModes_공통응답규칙_동등() {
        String projectId = "PRJ-PARITY-001";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(projectId)
                        .sno(1)
                        .dvmDpmC("D001")
                        .svnDpmC("D002")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .rprStsTc("R1")
                        .exePttYn("Y")
                        .abusTc("A1")
                        .edrtTc("E1")
                        .delYn("N")
                        .build();
        ApplicationMapView latestApplicationMap =
                new ApplicationMapView("APF-PARITY-LATEST", projectId, 1);
        ApplicationMapView olderApplicationMap =
                new ApplicationMapView("APF-PARITY-OLDER", projectId, 1);
        ApplicationSummaryView latestApplication =
                new ApplicationSummaryView(
                        "APF-PARITY-LATEST",
                        ApprovalStatus.IN_PROGRESS.code(),
                        "최신 동등성 결재",
                        "10001",
                        LocalDate.of(2026, 8, 5),
                        "동등성 검증");
        ApproverReadView approver =
                new ApproverReadView("APF-PARITY-LATEST", 1, "20001", "2", null, "승인", "Y");
        Bproja status =
                Bproja.builder().abusMngNo(projectId).cncdRfrNo("STEP-1").stsTc("09").build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-PARITY")
                        .abusMngNo(projectId)
                        .fntTbCrySno(1)
                        .ioeC("101")
                        .gclNm("동등성 품목")
                        .amt(BigDecimal.valueOf(100))
                        .mplAmt(BigDecimal.valueOf(30))
                        .build();
        given(
                        applicationMapRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BPROJM", projectId, 1))
                .willReturn(List.of(latestApplicationMap, olderApplicationMap));
        given(
                        applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                "BPROJM", List.of(projectId)))
                .willReturn(List.of(latestApplicationMap, olderApplicationMap));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-PARITY-LATEST")))
                .willReturn(List.of(latestApplication));
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-PARITY-LATEST"))
                .willReturn(List.of(approver));
        given(
                        approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(
                                List.of("APF-PARITY-LATEST")))
                .willReturn(List.of(approver));
        given(bprojaRepository.findByAbusMngNoAndDelYn(projectId, "N")).willReturn(List.of(status));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of(projectId), "N"))
                .willReturn(List.of(status));
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(projectId, 1, "N"))
                .willReturn(List.of(item));
        given(itemRepository.findByAbusMngNoInAndDelYn(List.of(projectId), "N"))
                .willReturn(List.of(item));
        given(itemRepository.findBudgetViewsByAbusMngNoInAndDelYn(List.of(projectId), "N"))
                .willReturn(
                        List.of(
                                new ItemBudgetView(
                                        "GCL-PARITY",
                                        projectId,
                                        "101",
                                        BigDecimal.valueOf(100),
                                        BigDecimal.valueOf(30))));
        given(projectRepository.findBizplanScheduleRange(List.of(projectId))).willReturn(List.of());
        given(organizationRepository.findNameViewByPrlmOgzCCone("D001"))
                .willReturn(Optional.of(new OrgNameView("D001", "IT부")));
        given(organizationRepository.findNameViewByPrlmOgzCCone("D002"))
                .willReturn(Optional.of(new OrgNameView("D002", "현업부")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(
                        List.of(new OrgNameView("D001", "IT부"), new OrgNameView("D002", "현업부")));
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new UserNameView("10001", "IT담당", "차장")));
        given(userRepository.findNameViewByEno("10002"))
                .willReturn(Optional.of(new UserNameView("10002", "IT팀장", "팀장")));
        given(userRepository.findNameViewByEno("10003"))
                .willReturn(Optional.of(new UserNameView("10003", "현업담당", "대리")));
        given(userRepository.findNameViewByEno("10004"))
                .willReturn(Optional.of(new UserNameView("10004", "현업팀장", "부장")));
        given(userRepository.findNameViewsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new UserNameView("10001", "IT담당", "차장"),
                                new UserNameView("10002", "IT팀장", "팀장"),
                                new UserNameView("10003", "현업담당", "대리"),
                                new UserNameView("10004", "현업팀장", "부장")));
        stubCodeName(CommonCodeGroups.REPORT_STS, "R1", "보고완료");
        stubCodeName(CommonCodeGroups.EXE_POSSIBLE, "Y", "실행가능");
        stubCodeName(CommonCodeGroups.ABUS, "A1", "신규사업");
        stubCodeName(CommonCodeGroups.EDRT, "E1", "편집대상");
        stubIoeCode();

        ProjectDto.Response detail = assembler.assembleDetail(project);
        ProjectDto.Response list = assembler.assembleList(List.of(project)).getFirst();
        ProjectDto.Response bulk = assembler.assembleBulk(List.of(project), null).getFirst();

        assertThat(List.of(list, bulk))
                .allSatisfy(
                        result -> {
                            assertThat(result.getApfMngNo()).isEqualTo("APF-PARITY-LATEST");
                            assertThat(result.getApfSts()).isEqualTo(detail.getApfSts());
                            assertThat(result.getApplicationInfo().getApfNm())
                                    .isEqualTo("최신 동등성 결재");
                            assertThat(result.getApplicationInfo().getApprovers())
                                    .extracting(value -> value.getDcdEno())
                                    .containsExactly("20001");
                            assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
                            assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
                            assertThat(result.getDvmUsidNm()).isEqualTo("IT담당");
                            assertThat(result.getDvmUsidPtCNm()).isEqualTo("차장");
                            assertThat(result.getDvmTlrUsidNm()).isEqualTo("IT팀장");
                            assertThat(result.getDvmTlrUsidPtCNm()).isEqualTo("팀장");
                            assertThat(result.getUsidNm()).isEqualTo("현업담당");
                            assertThat(result.getUsidPtCNm()).isEqualTo("대리");
                            assertThat(result.getTlrUsidNm()).isEqualTo("현업팀장");
                            assertThat(result.getTlrUsidPtCNm()).isEqualTo("부장");
                            assertThat(result.getRprStsTcNm()).isEqualTo("보고완료");
                            assertThat(result.getExePttYnNm()).isEqualTo("실행가능");
                            assertThat(result.getAbusTcNm()).isEqualTo("신규사업");
                            assertThat(result.getEdrtTcNm()).isEqualTo("편집대상");
                            assertThat(result.getStsTc()).isEqualTo(detail.getStsTc());
                            assertThat(result.getItems())
                                    .extracting(ProjectDto.BitemmDto::getGclNm)
                                    .containsExactly("동등성 품목");
                            assertThat(result.getItems().getFirst().getIoeCNm())
                                    .isEqualTo(detail.getItems().getFirst().getIoeCNm());
                            assertThat(result.getAssetBg()).isEqualByComparingTo("100");
                            assertThat(result.getMplCpitAmt()).isEqualByComparingTo("30");
                            assertThat(result.getTotRqmAmt()).isEqualByComparingTo("70");
                        });
    }

    private void stubCodeName(String group, String value, String name) {
        Ccodem code = Ccodem.builder().cId(group).cdva(value).cdvaNm(name).build();
        given(codeRepository.findByCIdAndCdvaWithValidDate(group, value, null))
                .willReturn(Optional.of(code));
        given(codeRepository.findByCIdWithValidDate(group, null)).willReturn(List.of(code));
    }

    private void stubIoeCode() {
        Ccodem ioe =
                Ccodem.builder()
                        .cId(CommonCodeGroups.IOE)
                        .cdva("101")
                        .cdvaNm("개발비")
                        .cTp("IOE_DVC")
                        .build();
        given(codeRepository.findByCIdWithValidDate(CommonCodeGroups.IOE, null))
                .willReturn(List.of(ioe));
        given(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .willReturn(List.of(ioe));
    }
}
