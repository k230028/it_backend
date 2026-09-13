package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                        new ProjectBudgetSummaryService(codeService, new ProjectAmountCalculator()),
                        bprojaRepository,
                        new CodeNameMapBuilder(codeRepository),
                        projectRepository,
                        org.mockito.Mockito.mock(ProjectConcurrencyStamper.class));
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
    @DisplayName("상세 조립: 신청서 진행상태 코드가 없으면 상태 표시명을 null로 둔다")
    void assembleDetail_신청서진행상태코드없음_상태표시명null() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-NOSTS-001").sno(1).delYn("N").build();
        ApplicationMapView map = new ApplicationMapView("APF-NOSTS", "PRJ-NOSTS-001", 1);
        // 진행상태 코드가 비어 있는 신청서 — ApprovalStatus.ofCode를 타지 않아야 한다
        ApplicationSummaryView application =
                new ApplicationSummaryView("APF-NOSTS", null, "상태 없는 결재", "10001", null, null);
        given(
                        applicationMapRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BPROJM", "PRJ-NOSTS-001", 1))
                .willReturn(List.of(map));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-NOSTS")))
                .willReturn(List.of(application));
        given(approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-NOSTS"))
                .willReturn(List.of());

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getApfMngNo()).isEqualTo("APF-NOSTS");
        assertThat(result.getApfSts()).isNull();
        assertThat(result.getApfStsC()).isNull();
        assertThat(result.getApplicationInfo()).isNotNull();
        assertThat(result.getApplicationInfo().getApfNm()).isEqualTo("상태 없는 결재");
        assertThat(result.getApplicationInfo().getApfSts()).isNull();
    }

    @Test
    @DisplayName("상세 조립: 저장된 현업부서명 스냅샷이 있으면 조직 조회보다 우선한다")
    void assembleDetail_현업부서명스냅샷_조직조회보다우선() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-SVN-001")
                        .sno(1)
                        .delYn("N")
                        .svnDpmC("D002")
                        .svnDpmNm("현업부명 스냅샷")
                        .build();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getSvnDpmCNm()).isEqualTo("현업부명 스냅샷");
        // 스냅샷이 있으므로 현업부서 코드로 조직명을 재조회하지 않는다
        then(organizationRepository).shouldHaveNoInteractions();
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
    @DisplayName("상세 조립: 행번이 빈 사업은 담당자명 스냅샷(USR_NM·TLR_NM)을 유지한다")
    void assembleDetail_행번빈사업_담당자명스냅샷유지() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-EMPTY-ID")
                        .sno(1)
                        .delYn("N")
                        .usid(null)
                        .usrNm("홍길동")
                        .tlrUsid(null)
                        .tlrNm("김팀장")
                        .build();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getUsidNm()).isEqualTo("홍길동");
        assertThat(result.getTlrUsidNm()).isEqualTo("김팀장");
        assertThat(result.getUsid()).isNull();
        assertThat(result.getTlrUsid()).isNull();
    }

    @Test
    @DisplayName("상세 조립: 최초 작성자·최근 수정자 사번을 배치 조회 한 번으로 이름으로 채운다")
    void assembleDetail_감사자명_배치조회로채움() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-AUDIT")
                        .sno(1)
                        .delYn("N")
                        .fstEnrUsid("K100001")
                        .lstChgUsid("K100002")
                        .build();
        given(userRepository.findNameViewsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new UserNameView("K100001", "작성자", "대리"),
                                new UserNameView("K100002", "수정자", "과장")));

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getFstEnrUsid()).isEqualTo("K100001");
        assertThat(result.getFstEnrUsNm()).isEqualTo("작성자");
        assertThat(result.getLstChgUsid()).isEqualTo("K100002");
        assertThat(result.getLstChgUsNm()).isEqualTo("수정자");
        then(userRepository)
                .should()
                .findNameViewsByEnoIn(
                        argThat(enos -> enos.containsAll(List.of("K100001", "K100002"))));
    }

    @Test
    @DisplayName("상세 조립: 감사자 사번이 미해석(DB 기본값·퇴직)이면 이름은 비우고 사번은 그대로 둔다")
    void assembleDetail_감사자미해석_사번유지_이름null() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-AUDIT-LEGACY")
                        .sno(1)
                        .delYn("N")
                        .fstEnrUsid("00000000000000")
                        .lstChgUsid("K999999")
                        .build();
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getFstEnrUsid()).isEqualTo("00000000000000");
        assertThat(result.getFstEnrUsNm()).isNull();
        assertThat(result.getLstChgUsid()).isEqualTo("K999999");
        assertThat(result.getLstChgUsNm()).isNull();
    }

    @Test
    @DisplayName("상세 조립: 미해석 행번(퇴직 등)이라도 담당자명 스냅샷을 null로 덮지 않는다")
    void assembleDetail_미해석행번_담당자명스냅샷유지() {
        /* 사번 형태(K999999)지만 사용자 조회가 비는 행 — 종전에는 이름이 null로 덮여 사라졌다 */
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-RETIRED")
                        .sno(1)
                        .delYn("N")
                        .usid("K999999")
                        .usrNm("퇴직자")
                        .build();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getUsidNm()).isEqualTo("퇴직자");
        assertThat(result.getUsid()).isEqualTo("K999999");
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
        assertThat(result.getTyyBgAmt()).isEqualByComparingTo("100");
        assertThat(result.getMplAmt()).isEqualByComparingTo("30");
        assertThat(result.getPrjBgAmt()).isEqualByComparingTo("130");
    }

    @Test
    @DisplayName("목록 조립: 품목 금액은 사업번호의 다른 개정본과 합산하지 않는다")
    void assembleList_품목금액_다른개정본제외() {
        String projectNo = "PRJ-2027-0600";
        Bprojm original =
                Bprojm.builder()
                        .abusMngNo(projectNo)
                        .sno(1)
                        .lstYn("Y")
                        .totRqmAmt(new BigDecimal("100000000"))
                        .mplAmt(BigDecimal.ZERO)
                        .dfrAmt(BigDecimal.ZERO)
                        .delYn("N")
                        .build();
        Bitemm originalItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0932")
                        .abusMngNo(projectNo)
                        .fntTbCrySno(1)
                        .ioeC("101")
                        .amt(new BigDecimal("100000000"))
                        .mplAmt(BigDecimal.ZERO)
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(projectNo, 1, "N"))
                .willReturn(List.of(originalItem));
        given(itemRepository.findBudgetViewsByAbusMngNoInAndDelYn(List.of(projectNo), "N"))
                .willReturn(
                        List.of(
                                new ItemBudgetView(
                                        "GCL-2026-0932",
                                        projectNo,
                                        "101",
                                        new BigDecimal("100000000"),
                                        BigDecimal.ZERO,
                                        "KRW",
                                        null),
                                new ItemBudgetView(
                                        "GCL-2026-0934",
                                        projectNo,
                                        "101",
                                        new BigDecimal("200000000"),
                                        BigDecimal.ZERO,
                                        "KRW",
                                        null)));
        given(projectRepository.findBizplanScheduleRange(List.of(projectNo))).willReturn(List.of());
        stubIoeCode();

        ProjectDto.Response result = assembler.assembleList(List.of(original)).getFirst();

        assertThat(result.getItems())
                .extracting(ProjectDto.BitemmDto::getAmt)
                .containsExactly(new BigDecimal("100000000"));
        assertThat(result.getAssetBg()).isEqualByComparingTo("100000000");
        assertThat(result.getPrjBgAmt()).isEqualByComparingTo("100000000");
    }

    @Test
    @DisplayName("상세 조립: 품목 비목 코드가 전부 비어 있으면 코드명 조회 없이 품목을 반환한다")
    void assembleDetail_품목비목코드전부빈값_코드명조회생략() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-NOIOE-001").sno(1).delYn("N").build();
        // 비목 코드가 null인 품목만 있는 사업 — 코드명 조회 자체를 건너뛰어야 한다
        Bitemm uncoded =
                Bitemm.builder()
                        .gclMngNo("GCL-NOIOE")
                        .abusMngNo("PRJ-NOIOE-001")
                        .fntTbCrySno(1)
                        .gclNm("코드 없는 품목")
                        .amt(BigDecimal.valueOf(10))
                        .build();
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-NOIOE-001", 1, "N"))
                .willReturn(List.of(uncoded));

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getItems())
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getGclNm()).isEqualTo("코드 없는 품목");
                            assertThat(value.getIoeCNm()).isNull();
                        });
        then(codeRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상세 조립: 비목 코드 없는 품목이 섞여 있으면 코드 있는 품목만 코드명을 채운다")
    void assembleDetail_비목코드없는품목혼재_코드있는품목만채움() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-MIX-001").sno(1).delYn("N").build();
        Bitemm coded =
                Bitemm.builder()
                        .gclMngNo("GCL-MIX-1")
                        .abusMngNo("PRJ-MIX-001")
                        .fntTbCrySno(1)
                        .ioeC("101")
                        .gclNm("코드 있는 품목")
                        .amt(BigDecimal.valueOf(100))
                        .build();
        Bitemm uncoded =
                Bitemm.builder()
                        .gclMngNo("GCL-MIX-2")
                        .abusMngNo("PRJ-MIX-001")
                        .fntTbCrySno(1)
                        .gclNm("코드 없는 품목")
                        .amt(BigDecimal.valueOf(20))
                        .build();
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-MIX-001", 1, "N"))
                .willReturn(List.of(coded, uncoded));
        stubIoeCode();

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getItems())
                .extracting(ProjectDto.BitemmDto::getIoeCNm)
                .containsExactly("개발비", null);
    }

    @Test
    @DisplayName("품목 비목 코드명 보강: 품목 목록이 null이면 예외 없이 종료한다")
    void enrichItemIoeNames_null품목목록_예외없이종료() throws Exception {
        // 공개 API는 null 품목 목록을 만들지 않으므로 방어 분기를 리플렉션으로 직접 검증한다
        Method enrich =
                ProjectQueryAssembler.class.getDeclaredMethod("enrichItemIoeNames", List.class);
        enrich.setAccessible(true);

        assertThatCode(() -> enrich.invoke(assembler, new Object[] {null}))
                .doesNotThrowAnyException();

        then(codeRepository).shouldHaveNoInteractions();
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
                        // 편성요청서 반입 사업처럼 저장 스냅샷이 품목 합계(100/30)와 다른 경우
                        .totRqmAmt(BigDecimal.valueOf(500))
                        .mplAmt(BigDecimal.valueOf(200))
                        .dfrAmt(BigDecimal.valueOf(50))
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
                                        BigDecimal.valueOf(30),
                                        "KRW",
                                        null)));
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

        assertThat(List.of(detail, list, bulk))
                .allSatisfy(
                        result -> {
                            // 총 예산·익년 이후 예산은 품목 합계가 아니라 저장 스냅샷을 쓴다
                            assertThat(result.getPrjBgAmt()).isEqualByComparingTo("500");
                            assertThat(result.getMplAmt()).isEqualByComparingTo("200");
                            // 당해예산 = 500 − 200 − 50 (품목 기준 70이 아니다)
                            assertThat(result.getTyyBgAmt()).isEqualByComparingTo("250");
                        });
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
                        });
    }

    @Test
    @DisplayName("bulk 조립: 비목 유형으로 편성예산을 자본·경상 예산으로 분류한다")
    void assembleBulk_비목유형별_편성예산분류() {
        String projectId = "PRJ-BUDGET-001";
        Bprojm project = Bprojm.builder().abusMngNo(projectId).sno(1).delYn("N").build();
        Ccodem assetCode =
                Ccodem.builder().cId(CommonCodeGroups.IOE).cdva("101").cTp("IOE_DVC").build();
        Ccodem costCode =
                Ccodem.builder().cId(CommonCodeGroups.IOE).cdva("201").cTp("IOE_XPN").build();
        Ccodem unrelatedCode =
                Ccodem.builder().cId(CommonCodeGroups.IOE).cdva("999").cTp("IOE_OTHER").build();
        given(codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE))
                .willReturn(List.of(assetCode, costCode, unrelatedCode));
        given(budgetRepository.sumDupBgByPrjMngNos(List.of(projectId), "2027"))
                .willReturn(Map.of(projectId, new BigDecimal("1000")));
        given(budgetRepository.sumAssetDupBgByPrjMngNos(List.of(projectId), "2027", Set.of("101")))
                .willReturn(Map.of(projectId, new BigDecimal("700")));
        given(budgetRepository.sumCostDupBgByPrjMngNos(List.of(projectId), "2027", Set.of("201")))
                .willReturn(Map.of(projectId, new BigDecimal("300")));

        ProjectDto.Response result = assembler.assembleBulk(List.of(project), "2027").getFirst();

        assertThat(result.getDupBgAmt()).isEqualByComparingTo("1000");
        assertThat(result.getAssetDupBg()).isEqualByComparingTo("700");
        assertThat(result.getCostDupBg()).isEqualByComparingTo("300");
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

    @Test
    @DisplayName("목록 조립: 미상신 초안 행이 구버전의 결재완료 정보를 물려받지 않는다")
    void assembleList_초안행은_구버전_결재정보를_물려받지_않는다() {
        Bprojm draft =
                Bprojm.builder().abusMngNo("PRJ-VER-001").sno(2).lstYn("N").delYn("N").build();
        // 같은 관리번호의 v1이 결재완료 상태로 남아 있다 — 초안 v2는 아직 상신 전이다.
        ApplicationMapView v1Link = new ApplicationMapView("APF-010", "PRJ-VER-001", 1);
        given(
                        applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                "BPROJM", List.of("PRJ-VER-001")))
                .willReturn(List.of(v1Link));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-010")))
                .willReturn(
                        List.of(
                                new ApplicationSummaryView(
                                        "APF-010",
                                        ApprovalStatus.COMPLETED.code(),
                                        "v1 결재",
                                        "10001",
                                        LocalDate.of(2026, 8, 5),
                                        "승인 요청")));

        ProjectDto.Response result = assembler.assembleList(List.of(draft)).get(0);

        assertThat(result.getApfSts()).isNull();
        assertThat(result.getApfMngNo()).isNull();
        assertThat(result.getApplicationInfo()).isNull();
    }

    @Test
    @DisplayName("배치 조립: 빈 목록은 연관 조회 없이 빈 응답을 반환한다")
    void assembleBatch_빈목록_빈응답() {
        assertThat(assembler.assembleList(List.of())).isEmpty();
        assertThat(assembler.assembleBulk(List.of(), "2027")).isEmpty();

        then(applicationMapRepository).shouldHaveNoInteractions();
        then(itemRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배치 조립: null 목록은 계약 위반으로 즉시 실패한다")
    void assembleBatch_null목록_실패() {
        assertThatThrownBy(() -> assembler.assembleList(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> assembler.assembleBulk(null, "2027"))
                .isInstanceOf(NullPointerException.class);
    }
}
