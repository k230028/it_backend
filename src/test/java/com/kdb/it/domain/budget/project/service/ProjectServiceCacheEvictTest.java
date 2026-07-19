package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.kdb.it.config.CacheConfig;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;

/**
 * ProjectService 쓰기 경로의 tiptapMetadata 캐시 무효화 검증.
 *
 * <p>실제 {@link CacheConfig}(Caffeine)와 Spring 캐시 프록시를 띄워, create/update/delete 호출이
 * {@code @CacheEvict(cacheNames="tiptapMetadata", allEntries=true)}를 발화시키는지 확인합니다.
 * DB·협력 서비스는 Mockito로 대체하므로 DB 미접속이며 {@code @Tag("it")}를 부착하지 않습니다.</p>
 *
 * <p>update/delete는 {@code validateModifyPermission}이 SecurityContext의 {@link CustomUserDetails}를
 * 요구하므로, 메서드가 예외 없이 정상 반환해 evict가 발화하도록 ADMIN 컨텍스트를 심습니다(계획 ⚠️ 주석 반영).</p>
 */
// 주의: 트랜잭션 매니저 빈이 없어 @Transactional은 비활성 — @CacheEvict 발화 자체만 검증하며,
//       커밋 후 지연(TransactionAwareCacheManagerProxy) 경로는 검증 대상이 아님.
@SpringBootTest(classes = {CacheConfig.class, ProjectService.class})
class ProjectServiceCacheEvictTest {

    @Autowired
    private ProjectService projectService;

    // 시드/검증은 위임 대상 Caffeine 매니저를 직접 사용 — 프록시 지연과 무관하게 백킹 스토어를 명시 타깃팅.
    @Autowired
    private CaffeineCacheManager caffeineCacheManager;

    // ProjectService 협력 빈 — 캐시 발화만 검증하므로 동작은 최소 스텁
    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private ApplicationMapRepository capplaRepository;
    @MockitoBean private ApplicationRepository capplmRepository;
    @MockitoBean private ProjectItemRepository bitemmRepository;
    @MockitoBean private OrganizationRepository corgnIRepository;
    @MockitoBean private UserRepository cuserIRepository;
    @MockitoBean private ApproverRepository cdecimRepository;
    @MockitoBean private CodeService codeService;
    @MockitoBean private CodeRepository ccodemRepository;
    @MockitoBean private BbugtmRepository bbugtmRepository;
    @MockitoBean private XcrLookupService xcrLookupService;
    @MockitoBean private ProjectBudgetSummaryService projectBudgetSummaryService;
    @MockitoBean private BprojaRepository bprojaRepository;
    @MockitoBean private BprojaSyncService bprojaSyncService;
    @MockitoBean private CodeNameMapBuilder codeNameMapBuilder;
    @MockitoBean private com.kdb.it.common.iam.service.AuthorOrgResolver authorOrgResolver;
    /** 조직코드→조직명 해석기 (mock 기본값 null 반환 = 미등록 코드 폴백 경로) */
    @MockitoBean private com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;

    private Cache tiptapCache;

    @BeforeEach
    void seedCacheAndSecurity() {
        tiptapCache = caffeineCacheManager.getCache("tiptapMetadata");
        assertThat(tiptapCache).isNotNull();
        // evict 검증을 위해 임의 엔트리를 미리 적재
        tiptapCache.put("ALL", "stale");
        tiptapCache.put("D001", "stale");

        // validateModifyPermission(update/delete)이 요구하는 인증 컨텍스트 — ADMIN으로 즉시 통과
        CustomUserDetails admin = new CustomUserDetails("ADMIN01", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));

    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createProject 호출 시 tiptapMetadata 전체 evict")
    void createProject_evictsTiptapMetadata() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);

        projectService.createProject(newCreateRequest());

        assertThat(tiptapCache.get("ALL")).isNull();
        assertThat(tiptapCache.get("D001")).isNull();
    }

    @Test
    @DisplayName("updateProject 호출 시 tiptapMetadata 전체 evict")
    void updateProject_evictsTiptapMetadata() {
        Bprojm project = org.mockito.Mockito.mock(Bprojm.class);
        given(project.getSno()).willReturn(1);
        given(project.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(projectRepository.findByAbusMngNoAndDelYn(anyString(), anyString()))
                .willReturn(java.util.Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                anyString(), anyString(), any(), any())).willReturn(false);

        projectService.updateProject("PRJ-2026-0001", newUpdateRequest());

        assertThat(tiptapCache.get("ALL")).isNull();
        assertThat(tiptapCache.get("D001")).isNull();
    }

    @Test
    @DisplayName("deleteProject 호출 시 tiptapMetadata 전체 evict")
    void deleteProject_evictsTiptapMetadata() {
        Bprojm project = org.mockito.Mockito.mock(Bprojm.class);
        given(project.getSno()).willReturn(1);
        given(projectRepository.findByAbusMngNoAndDelYn(anyString(), anyString()))
                .willReturn(java.util.Optional.of(project));
        given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                anyString(), anyString(), any(), any())).willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(anyString(), any()))
                .willReturn(java.util.List.of());

        projectService.deleteProject("PRJ-2026-0001");

        assertThat(tiptapCache.get("ALL")).isNull();
        assertThat(tiptapCache.get("D001")).isNull();
    }

    private ProjectDto.CreateRequest newCreateRequest() {
        ProjectDto.CreateRequest req = new ProjectDto.CreateRequest();
        req.setAbusNm("테스트사업");
        req.setBseYy("2026");
        return req;
    }

    private ProjectDto.UpdateRequest newUpdateRequest() {
        ProjectDto.UpdateRequest req = new ProjectDto.UpdateRequest();
        req.setAbusNm("수정사업");
        return req;
    }
}
