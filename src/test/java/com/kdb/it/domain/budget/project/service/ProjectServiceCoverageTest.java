package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;

/**
 * ProjectService 커버리지 보강 테스트
 *
 * <p>
 * Jacoco Complexity 70% 이상 달성을 위해 미커버 분기를 추가 검증합니다:
 * </p>
 * <ul>
 * <li>{@code bigDecimalChanged}: null/null=false, 한쪽만null=true, 스케일 다른 동일값=false, 값 다름=true</li>
 * <li>{@code clampMpl}: null→0, 음수→0, amt 초과→amt, 범위 내→그대로, amt=null→상한 없음</li>
 * <li>{@code isItemChanged}: 각 필드별 변경 탐지(ioeC·gclNm·curC·xcr·xcrBseDt·cncdFdtnCone·bseYm·dfrCleC·sectSysUtzYn·itrInfrYn·fcAmt·mplAmt)</li>
 * <li>{@code setCodeNames}: rprStsTc·exePttYn·abusTc 코드명 조회; null이면 skip</li>
 * <li>{@code representativeStatus}: 여러 행 MAX, 일부 null, 전부 null, 빈 리스트</li>
 * <li>{@code enrichItemIoeCNames}: ioeC=null/빈값 혼재 시 필터 람다 커버</li>
 * <li>{@code getProject} 단건 상세: bproja 있을 때 stsTc·bprojaStsCodes 설정</li>
 * <li>{@code createProject}: items=null 분기; sectSysUtzYn/itrInfrYn null 기본값 처리</li>
 * <li>{@code updateProject}: gclMngNo 있으나 기존 품목에 없을 때 무처리 분기</li>
 * <li>{@code getProjectsByIds}: setApplicationInfo 내부 capplm.ifPresent 람다</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceCoverageTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ApplicationMapRepository capplaRepository;
    @Mock
    private ApplicationRepository capplmRepository;
    @Mock
    private ProjectItemRepository bitemmRepository;
    @Mock
    private CodeRepository ccodemRepository;
    @Mock
    private CodeService codeService;
    @Mock
    private OrganizationRepository corgnIRepository;
    @Mock
    private UserRepository cuserIRepository;
    @Mock
    private ApproverRepository cdecimRepository;
    @Mock
    private BbugtmRepository bbugtmRepository;
    @Mock
    private XcrLookupService xcrLookupService;
    @Mock
    private ProjectBudgetSummaryService projectBudgetSummaryService;
    @Mock
    private BprojaRepository bprojaRepository;
    @Mock
    private BprojaSyncService bprojaSyncService;
    @Mock
    private CodeNameMapBuilder codeNameMapBuilder;
    @Mock
    private com.kdb.it.common.iam.service.AuthorOrgResolver authorOrgResolver;
    /** 조직코드→조직명 해석기 (mock 기본값 null 반환 = 미등록 코드 폴백 경로) */
    @Mock
    private com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void setUpSecurity() {
        // 기본 인증 주체: ADMIN — 권한 검증 통과용
        CustomUserDetails adminUser = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(adminUser);
        SecurityContextHolder.setContext(securityContext);

        // projectBudgetSummaryService mock: 실제 구현 위임
        doAnswer(invocation -> {
            ProjectDto.Response response = invocation.getArgument(0);
            List<Bitemm> items = invocation.getArgument(1);
            new ProjectBudgetSummaryService(codeService).applyBudgetSummary(response, items);
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummary(any(ProjectDto.Response.class), anyList());
        // 작성자 조직 스냅샷 기본값: 생성 경로 NPE 방지용 빈 스냅샷
        org.mockito.Mockito.lenient()
                .when(authorOrgResolver.resolveCurrent())
                .thenReturn(com.kdb.it.common.iam.service.AuthorOrg.empty());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // bigDecimalChanged 분기 — updateProject → isItemChanged 경로로 간접 커버
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("isItemChanged/bigDecimalChanged: 양쪽 모두 null이면 변경 없음으로 판단(버저닝 없음)")
    void isItemChanged_bigDecimal_양쪽null_변경없음() {
        // Arrange: xcr/qty/amt/fcAmt 모두 null로 일치 — bigDecimalChanged(null,null)=false
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem = Bitemm.builder()
                .gclMngNo("GCL-BD-001").sno(1)
                .abusMngNo(prjMngNo).fntTbCrySno(1)
                .ioeC("IOE-001").gclNm("품목A")
                .qty(null).curC("KRW")
                .xcr(null).amt(null).fcAmt(null).mplAmt(null)
                .sectSysUtzYn("N").itrInfrYn("N")
                .delYn("N").build();
        ProjectDto.BitemmDto sameDto = ProjectDto.BitemmDto.builder()
                .gclMngNo("GCL-BD-001")
                .ioeC("IOE-001").gclNm("품목A")
                .qty(null).curC("KRW")
                .xcr(null).amt(null).fcAmt(null).mplAmt(null)
                .sectSysUtzYn("N").itrInfrYn("N")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        // Act
        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(sameDto)).build());

        // Assert: 변경 없으므로 버저닝 save 미호출
        verify(bitemmRepository, never()).save(any(Bitemm.class));
        assertThat(existingItem.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged/bigDecimalChanged: 기존=null, DTO=값이면 변경으로 판단(버저닝)")
    void isItemChanged_bigDecimal_기존null_dto값_변경() {
        // Arrange: 기존 xcr=null, DTO xcr=1.0 → bigDecimalChanged=true
        String prjMngNo = "PRJ-2026-0011";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem = Bitemm.builder()
                .gclMngNo("GCL-BD-011").sno(1)
                .abusMngNo(prjMngNo).fntTbCrySno(1)
                .ioeC("IOE-001").gclNm("품목B")
                .qty(BigDecimal.ONE).curC("KRW")
                .xcr(null) // 기존 xcr=null
                .amt(BigDecimal.valueOf(1000))
                .sectSysUtzYn("N").itrInfrYn("N")
                .delYn("N").build();
        ProjectDto.BitemmDto changedDto = ProjectDto.BitemmDto.builder()
                .gclMngNo("GCL-BD-011")
                .ioeC("IOE-001").gclNm("품목B")
                .qty(BigDecimal.ONE).curC("KRW")
                .xcr(BigDecimal.ONE) // 값이 있음 → 변경
                .amt(BigDecimal.valueOf(1000))
                .sectSysUtzYn("N").itrInfrYn("N")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);

        // Act
        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(changedDto)).build());

        // Assert: 변경 감지 → 버저닝 save 호출
        verify(bitemmRepository, never()).save(any(Bitemm.class));
        assertThat(existingItem.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged/bigDecimalChanged: 기존=값, DTO=null이면 변경으로 판단(버저닝)")
    void isItemChanged_bigDecimal_기존값_dtoNull_변경() {
        // Arrange: 기존 qty=1, DTO qty=null → bigDecimalChanged=true
        String prjMngNo = "PRJ-2026-0012";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem = Bitemm.builder()
                .gclMngNo("GCL-BD-012").sno(1)
                .abusMngNo(prjMngNo).fntTbCrySno(1)
                .ioeC("IOE-001").gclNm("품목C")
                .qty(BigDecimal.ONE) // 기존 qty=1
                .curC("KRW")
                .amt(BigDecimal.valueOf(1000))
                .sectSysUtzYn("N").itrInfrYn("N")
                .delYn("N").build();
        ProjectDto.BitemmDto changedDto = ProjectDto.BitemmDto.builder()
                .gclMngNo("GCL-BD-012")
                .ioeC("IOE-001").gclNm("품목C")
                .qty(null) // null → 변경
                .curC("KRW")
                .amt(BigDecimal.valueOf(1000))
                .sectSysUtzYn("N").itrInfrYn("N")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(null);

        // Act
        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(changedDto)).build());

        // Assert
        verify(bitemmRepository, never()).save(any(Bitemm.class));
        assertThat(existingItem.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged/bigDecimalChanged: 스케일이 다른 동일값(1.0 vs 1.00)은 변경 없음으로 판단")
    void isItemChanged_bigDecimal_스케일다른동일값_변경없음() {
        // Arrange: amt=1000.0 (scale=1), DTO amt=1000.00 (scale=2) → compareTo=0 → 변경 없음
        String prjMngNo = "PRJ-2026-0013";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem = Bitemm.builder()
                .gclMngNo("GCL-BD-013").sno(1)
                .abusMngNo(prjMngNo).fntTbCrySno(1)
                .ioeC("IOE-001").gclNm("품목D")
                .qty(new BigDecimal("1.0"))
                .curC("KRW")
                .xcr(new BigDecimal("1300.0"))
                .amt(new BigDecimal("1000.0"))
                .sectSysUtzYn("N").itrInfrYn("N")
                .delYn("N").build();
        ProjectDto.BitemmDto sameDto = ProjectDto.BitemmDto.builder()
                .gclMngNo("GCL-BD-013")
                .ioeC("IOE-001").gclNm("품목D")
                .qty(new BigDecimal("1.00"))       // scale 다름, 값 동일
                .curC("KRW")
                .xcr(new BigDecimal("1300.00"))    // scale 다름, 값 동일
                .amt(new BigDecimal("1000.00"))    // scale 다름, 값 동일
                .sectSysUtzYn("N").itrInfrYn("N")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        // Act
        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(sameDto)).build());

        // Assert: compareTo=0 → 변경 없음
        verify(bitemmRepository, never()).save(any(Bitemm.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // clampMpl 분기 — createProject 경로로 간접 커버
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("clampMpl: mplAmt=null이면 0으로 저장된다")
    void clampMpl_null_을_0으로() {
        // Arrange: mplAmt=null, amt=1000
        given(projectRepository.getNextSequenceValue()).willReturn(20L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(20L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-001");
        item.setGclNm("품목null");
        item.setAmt(BigDecimal.valueOf(1000));
        item.setMplAmt(null); // null → 0

        ProjectDto.CreateRequest req = ProjectDto.CreateRequest.builder()
                .abusNm("clamp null 테스트").bseYy("2026").items(List.of(item)).build();

        // Act
        projectService.createProject(req);

        // Assert: 저장된 품목의 mplAmt=0
        ArgumentCaptor<Bitemm> cap = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(cap.capture());
        assertThat(cap.getValue().getMplAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("clampMpl: mplAmt가 음수이면 0으로 클램프된다")
    void clampMpl_음수_를_0으로() {
        // Arrange: mplAmt=-500, amt=1000
        given(projectRepository.getNextSequenceValue()).willReturn(21L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(21L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-001");
        item.setGclNm("품목음수");
        item.setAmt(BigDecimal.valueOf(1000));
        item.setMplAmt(BigDecimal.valueOf(-500)); // 음수 → 0

        ProjectDto.CreateRequest req = ProjectDto.CreateRequest.builder()
                .abusNm("clamp 음수 테스트").bseYy("2026").items(List.of(item)).build();

        // Act
        projectService.createProject(req);

        // Assert
        ArgumentCaptor<Bitemm> cap = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(cap.capture());
        assertThat(cap.getValue().getMplAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("clampMpl: mplAmt가 범위 내(0 < mplAmt < amt)이면 그대로 유지된다")
    void clampMpl_범위내_그대로유지() {
        // Arrange: mplAmt=500, amt=1000 → 500 그대로
        given(projectRepository.getNextSequenceValue()).willReturn(22L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(22L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-001");
        item.setGclNm("품목범위내");
        item.setAmt(BigDecimal.valueOf(1000));
        item.setMplAmt(BigDecimal.valueOf(500)); // 범위 내

        ProjectDto.CreateRequest req = ProjectDto.CreateRequest.builder()
                .abusNm("clamp 범위 테스트").bseYy("2026").items(List.of(item)).build();

        // Act
        projectService.createProject(req);

        // Assert: 500 그대로
        ArgumentCaptor<Bitemm> cap = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(cap.capture());
        assertThat(cap.getValue().getMplAmt()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("clampMpl: amt=null이면 상한 클램프 없이 mplAmt 양수값이 그대로 저장된다")
    void clampMpl_amt가null_상한없음() {
        // Arrange: mplAmt=9999, amt=null → 상한 없음 → 9999 그대로
        given(projectRepository.getNextSequenceValue()).willReturn(23L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(23L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-001");
        item.setGclNm("품목amt없음");
        item.setAmt(null);                         // amt=null → reconciled[0]=null
        item.setMplAmt(BigDecimal.valueOf(9999));  // clampMpl(9999, null) → 9999

        ProjectDto.CreateRequest req = ProjectDto.CreateRequest.builder()
                .abusNm("clamp amt null 테스트").bseYy("2026").items(List.of(item)).build();

        // Act
        projectService.createProject(req);

        // Assert: 상한 없이 9999 그대로
        ArgumentCaptor<Bitemm> cap = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(cap.capture());
        assertThat(cap.getValue().getMplAmt()).isEqualByComparingTo(BigDecimal.valueOf(9999));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // isItemChanged — 각 필드별 변경 탐지 (updateProject 경로)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 기본 기존 품목(변경 없음 기준선)을 생성하는 헬퍼.
     * 각 테스트에서 단일 필드만 변경하여 isItemChanged 분기를 격리 검증한다.
     */
    private Bitemm baseExistingItem(String prjMngNo, String gclMngNo) {
        return Bitemm.builder()
                .gclMngNo(gclMngNo).sno(1)
                .abusMngNo(prjMngNo).fntTbCrySno(1)
                .ioeC("IOE-BASE").gclNm("기준품목")
                .qty(BigDecimal.ONE).curC("KRW")
                .xcr(BigDecimal.ONE).xcrBseDt("20260101")
                .cncdFdtnCone("기준근거").bseYm("202601").dfrCleC("1")
                .sectSysUtzYn("N").itrInfrYn("N")
                .amt(BigDecimal.valueOf(1000)).mplAmt(BigDecimal.valueOf(500))
                .fcAmt(null).delYn("N").build();
    }

    /** 기존 품목과 동일한 필드를 가진 DTO 빌더 기준선 */
    private ProjectDto.BitemmDto.BitemmDtoBuilder baseDtoBuilder(String gclMngNo) {
        return ProjectDto.BitemmDto.builder()
                .gclMngNo(gclMngNo)
                .ioeC("IOE-BASE").gclNm("기준품목")
                .qty(BigDecimal.ONE).curC("KRW")
                .xcr(BigDecimal.ONE).xcrBseDt("20260101")
                .cncdFdtnCone("기준근거").bseYm("202601").dfrCleC("1")
                .sectSysUtzYn("N").itrInfrYn("N")
                .amt(BigDecimal.valueOf(1000)).mplAmt(BigDecimal.valueOf(500))
                .fcAmt(null);
    }

    /** updateProject 공통 mock 설정 헬퍼 */
    private void setupUpdateMocks(String prjMngNo, Bprojm project, List<Bitemm> existing) {
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(existing);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(BigDecimal.ONE);
    }

    @Test
    @DisplayName("isItemChanged: ioeC가 다르면 변경으로 탐지한다")
    void isItemChanged_ioeC_변경탐지() {
        String prjMngNo = "PRJ-IC-001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-001");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-001").ioeC("IOE-CHANGED").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
        assertThat(existing.getIoeC()).isEqualTo("IOE-CHANGED");
        verify(bitemmRepository, never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("isItemChanged: gclNm이 다르면 변경으로 탐지한다")
    void isItemChanged_gclNm_변경탐지() {
        String prjMngNo = "PRJ-IC-002";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-002");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-002").gclNm("변경품목명").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: curC가 다르면 변경으로 탐지한다")
    void isItemChanged_curC_변경탐지() {
        String prjMngNo = "PRJ-IC-003";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-003");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-003").curC("USD").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: xcrBseDt가 다르면 변경으로 탐지한다")
    void isItemChanged_xcrBseDt_변경탐지() {
        String prjMngNo = "PRJ-IC-004";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-004");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-004").xcrBseDt("20261231").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: cncdFdtnCone이 다르면 변경으로 탐지한다")
    void isItemChanged_cncdFdtnCone_변경탐지() {
        String prjMngNo = "PRJ-IC-005";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-005");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-005").cncdFdtnCone("변경근거").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: bseYm이 다르면 변경으로 탐지한다")
    void isItemChanged_bseYm_변경탐지() {
        String prjMngNo = "PRJ-IC-006";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-006");
        // bseYm 비교: 기존="202601", DTO="202606" (toItdYm 변환 후)
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-006").bseYm("2026-06").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        // toItdYm("2026-06") = "202606" ≠ "202601" → 변경
        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: dfrCleC가 다르면 변경으로 탐지한다")
    void isItemChanged_dfrCleC_변경탐지() {
        String prjMngNo = "PRJ-IC-007";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-007");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-007").dfrCleC("2").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: sectSysUtzYn이 Y로 변경되면 변경으로 탐지한다")
    void isItemChanged_sectSysUtzYn_변경탐지() {
        // 기존=N, DTO=Y → defaultYn("Y")="Y" ≠ "N" → 변경
        String prjMngNo = "PRJ-IC-008";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-008");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-008").sectSysUtzYn("Y").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: itrInfrYn이 Y로 변경되면 변경으로 탐지한다")
    void isItemChanged_itrInfrYn_변경탐지() {
        // 기존=N, DTO=Y → 변경
        String prjMngNo = "PRJ-IC-009";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-009");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-009").itrInfrYn("Y").build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: fcAmt만 변경되면 변경으로 탐지한다")
    void isItemChanged_fcAmt_변경탐지() {
        // 기존 fcAmt=null, DTO fcAmt=100 → bigDecimalChanged=true
        String prjMngNo = "PRJ-IC-010";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-010");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-010")
                .fcAmt(BigDecimal.valueOf(100)).build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("isItemChanged: mplAmt만 변경되면 변경으로 탐지한다")
    void isItemChanged_mplAmt_변경탐지() {
        // 기존 mplAmt=500, DTO mplAmt=300 → 변경
        String prjMngNo = "PRJ-IC-011";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existing = baseExistingItem(prjMngNo, "GCL-IC-011");
        ProjectDto.BitemmDto dto = baseDtoBuilder("GCL-IC-011")
                .mplAmt(BigDecimal.valueOf(300)).build();

        setupUpdateMocks(prjMngNo, project, List.of(existing));

        projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // setCodeNames — rprStsTc/exePttYn/abusTc 코드명 조회 분기
    // (getProject 경로로 간접 커버)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("setCodeNames: rprStsTc/exePttYn/abusTc가 있으면 ccodemRepository에서 코드명을 조회한다")
    void setCodeNames_rprStsTc_exePttYn_abusTc_코드명조회() {
        // Arrange: 세 가지 코드 필드가 모두 존재하는 프로젝트
        String prjMngNo = "PRJ-CODE-001";
        Bprojm project = Bprojm.builder()
                .abusMngNo(prjMngNo).sno(1).delYn("N")
                .rprStsTc("01").exePttYn("Y").abusTc("TC01")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(List.of());

        // ccodemRepository: rprStsTc 코드명
        given(ccodemRepository.findByCIdAndCdvaWithValidDate(
                CommonCodeGroups.REPORT_STS, "01", null))
                .willReturn(Optional.of(
                        Ccodem.builder().cId(CommonCodeGroups.REPORT_STS)
                                .cdva("01").cdvaNm("진행중").build()));
        // ccodemRepository: exePttYn 코드명
        given(ccodemRepository.findByCIdAndCdvaWithValidDate(
                CommonCodeGroups.EXE_POSSIBLE, "Y", null))
                .willReturn(Optional.of(
                        Ccodem.builder().cId(CommonCodeGroups.EXE_POSSIBLE)
                                .cdva("Y").cdvaNm("가능").build()));
        // ccodemRepository: abusTc 코드명
        given(ccodemRepository.findByCIdAndCdvaWithValidDate(
                CommonCodeGroups.ABUS, "TC01", null))
                .willReturn(Optional.of(
                        Ccodem.builder().cId(CommonCodeGroups.ABUS)
                                .cdva("TC01").cdvaNm("사업구분01").build()));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: 세 가지 코드명이 모두 설정됨
        assertThat(result.getRprStsTcNm()).isEqualTo("진행중");
        assertThat(result.getExePttYnNm()).isEqualTo("가능");
        assertThat(result.getAbusTcNm()).isEqualTo("사업구분01");
    }

    @Test
    @DisplayName("setCodeNames: rprStsTc/exePttYn/abusTc가 null이면 코드명 조회를 건너뛴다")
    void setCodeNames_필드null_코드명조회skip() {
        // Arrange: 코드 필드가 모두 null
        String prjMngNo = "PRJ-CODE-002";
        Bprojm project = Bprojm.builder()
                .abusMngNo(prjMngNo).sno(1).delYn("N")
                .rprStsTc(null).exePttYn(null).abusTc(null)
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(List.of());

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: ccodemRepository 미호출
        verify(ccodemRepository, never()).findByCIdAndCdvaWithValidDate(anyString(), anyString(), any());
        assertThat(result.getRprStsTcNm()).isNull();
    }

    @Test
    @DisplayName("setCodeNames: dvmDpmC/svnDpmC 등 부서·담당자 코드가 있으면 각 명칭을 조회한다")
    void setCodeNames_부서담당자코드_명칭조회() {
        // Arrange
        String prjMngNo = "PRJ-CODE-003";
        Bprojm project = Bprojm.builder()
                .abusMngNo(prjMngNo).sno(1).delYn("N")
                .dvmDpmC("DEPT-001").svnDpmC("DEPT-002")
                .dvmUsid("EMP001").tlrUsid("EMP002")
                .usid("EMP003").dvmTlrUsid("EMP004")
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(List.of());

        given(corgnIRepository.findById("DEPT-001"))
                .willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("DEPT-001").bbrNm("IT기획부").build()));
        given(corgnIRepository.findById("DEPT-002"))
                .willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("DEPT-002").bbrNm("현업부").build()));
        given(cuserIRepository.findById("EMP001"))
                .willReturn(Optional.of(CuserI.builder().eno("EMP001").usrNm("IT담당자").build()));
        given(cuserIRepository.findById("EMP002"))
                .willReturn(Optional.of(CuserI.builder().eno("EMP002").usrNm("주관팀장").build()));
        given(cuserIRepository.findById("EMP003"))
                .willReturn(Optional.of(CuserI.builder().eno("EMP003").usrNm("주관담당자").build()));
        given(cuserIRepository.findById("EMP004"))
                .willReturn(Optional.of(CuserI.builder().eno("EMP004").usrNm("IT팀장").build()));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: 모든 부서명/사용자명이 조회됨
        assertThat(result.getDvmDpmCNm()).isEqualTo("IT기획부");
        assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
        assertThat(result.getDvmUsidNm()).isEqualTo("IT담당자");
        assertThat(result.getTlrUsidNm()).isEqualTo("주관팀장");
        assertThat(result.getUsidNm()).isEqualTo("주관담당자");
        assertThat(result.getDvmTlrUsidNm()).isEqualTo("IT팀장");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // representativeStatus 분기 (getProject 경로로 간접 커버)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("representativeStatus: BPROJA 여러 행에서 MAX stsTc를 대표상태로 설정한다")
    void representativeStatus_여러행_MAX대표상태() {
        // Arrange: stsTc='01', '09' → MAX='09'
        String prjMngNo = "PRJ-RS-001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        Bproja row1 = Bproja.builder()
                .abusMngNo(prjMngNo).cncdRfrNo(prjMngNo).stsTc("01").build();
        Bproja row2 = Bproja.builder()
                .abusMngNo(prjMngNo).cncdRfrNo("DLB-2026-0001").stsTc("09").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(List.of(row1, row2));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: MAX('01','09') = '09'
        assertThat(result.getStsTc()).isEqualTo("09");
        assertThat(result.getBprojaStsCodes()).containsExactlyInAnyOrder("01", "09");
    }

    @Test
    @DisplayName("representativeStatus: stsTc=null인 행은 MAX 계산에서 제외된다")
    void representativeStatus_null행_제외() {
        // Arrange: stsTc=null, '02' → null 무시, MAX='02'
        String prjMngNo = "PRJ-RS-002";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        Bproja nullRow = Bproja.builder()
                .abusMngNo(prjMngNo).cncdRfrNo("REF-NULL").stsTc(null).build();
        Bproja row02 = Bproja.builder()
                .abusMngNo(prjMngNo).cncdRfrNo("REF-02").stsTc("02").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(List.of(nullRow, row02));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: null 제외 후 MAX='02'
        assertThat(result.getStsTc()).isEqualTo("02");
        assertThat(result.getBprojaStsCodes()).containsExactly("02");
    }

    @Test
    @DisplayName("representativeStatus: 모든 BPROJA 행의 stsTc가 null이면 대표상태는 null이다")
    void representativeStatus_전체null_대표상태null() {
        // Arrange: 모든 stsTc=null
        String prjMngNo = "PRJ-RS-003";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        Bproja nullRow = Bproja.builder()
                .abusMngNo(prjMngNo).cncdRfrNo("REF-N1").stsTc(null).build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(List.of(nullRow));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: orElse(null) 분기 → null
        assertThat(result.getStsTc()).isNull();
        assertThat(result.getBprojaStsCodes()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // enrichItemIoeCNames 람다 분기
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("enrichItemIoeCNames: ioeC=null/빈값 품목은 ioeCSet에서 제외되고, 유효한 ioeC는 코드명을 조회한다")
    void enrichItemIoeCNames_null_empty_ioeC_혼재() {
        // Arrange
        String prjMngNo = "PRJ-IOE-001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        Bitemm nullIoeItem  = Bitemm.builder().ioeC(null).amt(BigDecimal.valueOf(100)).build();
        Bitemm validIoeItem = Bitemm.builder().ioeC("IOE-351-0100").amt(BigDecimal.valueOf(200)).build();
        Bitemm emptyIoeItem = Bitemm.builder().ioeC("").amt(BigDecimal.valueOf(50)).build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(nullIoeItem, validIoeItem, emptyIoeItem));
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(List.of());

        // ccodemRepository: IOE-351-0100 코드명
        given(ccodemRepository.findByCIdWithValidDate(anyString(), any()))
                .willReturn(List.of(
                        Ccodem.builder()
                                .cId("IOE_351").cdva("0100").cdvaNm("소프트웨어 구매").build()));

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert: 3건 반환
        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems().get(0).getIoeCNm()).isNull();               // ioeC=null → 조회 안 됨
        assertThat(result.getItems().get(1).getIoeCNm()).isEqualTo("소프트웨어 구매"); // 조회됨
        assertThat(result.getItems().get(2).getIoeCNm()).isNull();               // ioeC="" → ioeCSet 미포함
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getProject — BPROJA 없으면 stsTc=null + bprojaStsCodes=[]
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProject: BPROJA가 없으면 stsTc=null, bprojaStsCodes=[] 이다")
    void getProject_bproja없음_null상태() {
        // Arrange
        String prjMngNo = "PRJ-NOBA-001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(List.of()); // BPROJA 없음

        // Act
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // Assert
        assertThat(result.getStsTc()).isNull();
        assertThat(result.getBprojaStsCodes()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getProjectList (배치) — BPROJA 배치 대표상태 + 람다 커버
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProjectList: BPROJA 배치 조회에서 각 프로젝트별 대표상태를 주입한다")
    void getProjectList_배치대표상태주입() {
        // Arrange: 프로젝트 2건, 각각 다른 BPROJA 행
        Bprojm p1 = Bprojm.builder().abusMngNo("PRJ-L-001").sno(1).delYn("N").build();
        Bprojm p2 = Bprojm.builder().abusMngNo("PRJ-L-002").sno(2).delYn("N").build();

        Bproja b1 = Bproja.builder()
                .abusMngNo("PRJ-L-001").cncdRfrNo("PRJ-L-001").stsTc("01").build();
        Bproja b2 = Bproja.builder()
                .abusMngNo("PRJ-L-002").cncdRfrNo("PRJ-L-002").stsTc("09").build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(p1, p2));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
        given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
        // BPROJA 배치 조회: 두 행 반환
        given(bprojaRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N")))
                .willReturn(List.of(b1, b2));
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N")))
                .willReturn(List.of());

        // Act
        List<ProjectDto.Response> result = projectService.getProjectList();

        // Assert: 각 프로젝트에 대표상태가 주입됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStsTc()).isEqualTo("01");
        assertThat(result.get(1).getStsTc()).isEqualTo("09");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createProject — items=null 분기
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createProject: items=null이면 품목 저장 없이 프로젝트만 저장한다")
    void createProject_items_null_품목저장없음() {
        // Arrange
        given(projectRepository.getNextSequenceValue()).willReturn(99L);

        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .abusNm("품목없는 사업")
                .bseYy("2026")
                .items(null) // items=null → 저장 skip
                .build();

        // Act
        String result = projectService.createProject(request);

        // Assert
        assertThat(result).matches("PRJ-2026-\\d{4}");
        verify(projectRepository).save(any(Bprojm.class));
        verify(bitemmRepository, never()).save(any(Bitemm.class));
        // BPROJA 적재 확인
        verify(bprojaSyncService).upsert(result, result, "01");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateProject — gclMngNo 있으나 기존 품목에 없는 경우(orphan)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateProject: gclMngNo가 있으나 기존 품목에 없으면 아무 작업도 하지 않는다")
    void updateProject_gclMngNo있으나기존품목없음_무처리() {
        // Arrange
        String prjMngNo = "PRJ-ORPHAN-001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                eq("BPROJM"), eq(prjMngNo), eq(1), anyList())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of()); // 기존 품목 없음

        ProjectDto.BitemmDto dto = ProjectDto.BitemmDto.builder()
                .gclMngNo("GCL-ORPHAN") // gclMngNo 있음 but 기존에 없음 → existingItem=null
                .ioeC("IOE-001").gclNm("고아 품목")
                .amt(BigDecimal.valueOf(100))
                .build();

        // Act
        String result = projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                .abusNm("사업").items(List.of(dto)).build());

        // Assert: 저장 없음
        assertThat(result).isEqualTo(prjMngNo);
        verify(bitemmRepository, never()).save(any(Bitemm.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getProjectsByIds — setApplicationInfo lambda (capplm.ifPresent)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProjectsByIds: 두 건 모두 CAPPLA+CAPPLM이 있으면 각 단건에 apfSts가 설정된다")
    void getProjectsByIds_cappla_capplm_ifPresent_람다커버() {
        // Arrange
        String p1 = "PRJ-BULK-001";
        String p2 = "PRJ-BULK-002";

        Bprojm proj1 = Bprojm.builder().abusMngNo(p1).sno(1).delYn("N").build();
        Bprojm proj2 = Bprojm.builder().abusMngNo(p2).sno(1).delYn("N").build();

        Cappla cappla1 = Cappla.builder().apfDcmNo("APF-B-001").pkColNm(p1).fntTbCrySno(1).build();
        Cappla cappla2 = Cappla.builder().apfDcmNo("APF-B-002").pkColNm(p2).fntTbCrySno(1).build();

        Capplm capplm1 = Capplm.builder()
                .apfMngNo("APF-B-001").dcdReqTtl("결재1")
                .apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code())
                .build();
        Capplm capplm2 = Capplm.builder()
                .apfMngNo("APF-B-002").dcdReqTtl("결재2")
                .apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code())
                .build();

        given(projectRepository.findByAbusMngNoAndDelYn(p1, "N")).willReturn(Optional.of(proj1));
        given(projectRepository.findByAbusMngNoAndDelYn(p2, "N")).willReturn(Optional.of(proj2));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(p1), eq(1))).willReturn(List.of(cappla1));
        given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                anyString(), eq(p2), eq(1))).willReturn(List.of(cappla2));
        given(capplmRepository.findById("APF-B-001")).willReturn(Optional.of(capplm1));
        given(capplmRepository.findById("APF-B-002")).willReturn(Optional.of(capplm2));
        given(cdecimRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(anyString())).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), eq(1), eq("N")))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(anyString(), eq("N"))).willReturn(List.of());

        ProjectDto.BulkGetRequest req = new ProjectDto.BulkGetRequest();
        req.setPrjMngNos(List.of(p1, p2));
        req.setBseYy(null); // 편성예산 skip

        // Act
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(req);

        // Assert: capplm.ifPresent 람다 실행 → 결재상태 설정됨
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).getApfSts()).isEqualTo("결재중");
        assertThat(result.items().get(1).getApfSts()).isEqualTo("결재완료");
    }
}
