package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@link ProjectService} 의 XCR 표준 조회 통합 테스트.
 *
 * <p>CONTEXT.md 결정 E / R3.7: 외화 품목(Bitemm) 저장 시 클라이언트 {@code xcr} 을
 * {@link XcrLookupService} 결과로 덮어쓴 뒤 gclAmt 가 재계산되는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceXcrLookupTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ApplicationMapRepository capplaRepository;
    @Mock private ApplicationRepository capplmRepository;
    @Mock private ProjectItemRepository bitemmRepository;
    @Mock private CodeRepository ccodemRepository;
    @Mock private CodeService codeService;
    @Mock private OrganizationRepository corgnIRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private ApproverRepository cdecimRepository;
    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private XcrLookupService xcrLookupService;
    @Mock private ProjectBudgetSummaryService projectBudgetSummaryService;
    @Mock private BprojaSyncService bprojaSyncService;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void setUpSecurity() {
        CustomUserDetails adminUser = new CustomUserDetails(
                "10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(adminUser);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createProject 외화 USD 품목: 클라 xcr=999 무시, Ccodem 1400으로 gclAmt 재계산")
    void createProject_외화품목_Ccodem환율로재계산() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(xcrLookupService.resolveXcr(eq("USD"), any(LocalDate.class)))
                .willReturn(new BigDecimal("1400"));

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("외화 SW");
        item.setCurC("USD");
        item.setFcAmt(new BigDecimal("500.000"));
        item.setXcr(new BigDecimal("999"));            // 클라이언트 위조값
        item.setAmt(new BigDecimal("0"));           // 클라이언트 위조값

        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .abusNm("외화 사업")
                .bseYy("2026")
                .items(List.of(item))
                .build();

        // when
        projectService.createProject(request);

        // then: 저장된 Bitemm 캡처 후 검증
        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(captor.capture());
        Bitemm saved = captor.getValue();
        assertThat(saved.getAmt()).isEqualByComparingTo(new BigDecimal("700000.000"));
        assertThat(saved.getXcr()).isEqualByComparingTo(new BigDecimal("1400"));
        assertThat(saved.getFcAmt()).isEqualByComparingTo(new BigDecimal("500.000"));
        assertThat(saved.getCurC()).isEqualTo("USD");
    }

    @Test
    @DisplayName("createProject 외화 미등록: IllegalStateException, bitemmRepository.save 미호출")
    void createProject_외화미등록_IllegalStateException발생() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(xcrLookupService.resolveXcr(eq("XYZ"), any(LocalDate.class)))
                .willThrow(new IllegalStateException("환율 미등록: XYZ (기준일: 2026-05-24)"));

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("미등록 통화 품목");
        item.setCurC("XYZ");
        item.setFcAmt(new BigDecimal("500"));
        item.setXcr(new BigDecimal("999"));
        item.setAmt(new BigDecimal("0"));

        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .abusNm("미등록 통화 사업")
                .bseYy("2026")
                .items(List.of(item))
                .build();

        // when / then
        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환율 미등록:");
        verify(bitemmRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject KRW 품목: resolveXcr null 반환, gclAmt 클라값 보존, fcAmt=null")
    void createProject_KRW_xcrNull저장() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(xcrLookupService.resolveXcr(eq("KRW"), any(LocalDate.class))).willReturn(null);

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("원화 품목");
        item.setCurC("KRW");
        item.setAmt(new BigDecimal("1000000"));
        item.setFcAmt(null);

        ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                .abusNm("원화 사업")
                .bseYy("2026")
                .items(List.of(item))
                .build();

        // when
        projectService.createProject(request);

        // then: KRW → xcr/fcAmt NULL, gclAmt 클라값 보존
        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        verify(bitemmRepository).save(captor.capture());
        Bitemm saved = captor.getValue();
        assertThat(saved.getXcr()).isNull();
        assertThat(saved.getFcAmt()).isNull();
        assertThat(saved.getAmt()).isEqualByComparingTo(new BigDecimal("1000000"));
    }
}
