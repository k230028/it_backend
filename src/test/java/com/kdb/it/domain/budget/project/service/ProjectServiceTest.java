package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;

/**
 * ProjectService 단위 테스트
 *
 * <p>
 * 모든 Repository를 Mock 처리하여 Oracle DB 없이 비즈니스 로직을 검증합니다.
 * 특히 결재중/결재완료 프로젝트 삭제 거부 등 핵심 비즈니스 제약을 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

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
        private SecurityContext securityContext;
        @Mock
        private Authentication authentication;

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
        @DisplayName("getProjectList - DEL_YN=N 프로젝트 목록 반환")
        void getProjectList_전체목록반환() {
                // given
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001").prjSno(1).delYn("N").build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                // setApplicationInfo 내부의 findBy... 호출 → Mockito 기본값(빈 리스트) 자동 처리
                given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                                anyString(), anyString(), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(anyString(), eq(1), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrjMngNo()).isEqualTo("PRJ-2026-0001");
        }

        @Test
        @DisplayName("getProject - 존재하는 프로젝트 관리번호 조회 시 Response 반환")
        void getProject_존재하는프로젝트_반환() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getPrjMngNo()).isEqualTo(prjMngNo);
        }

        @Test
        @DisplayName("getProject - 미존재 프로젝트 조회 시 IllegalArgumentException 발생")
        void getProject_미존재프로젝트_예외발생() {
                // given
                given(projectRepository.findByPrjMngNoAndDelYn("INVALID", "N"))
                                .willReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> projectService.getProject("INVALID"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Project not found");
        }

        @Test
        @DisplayName("deleteProject - 결재중 신청서 존재 시 IllegalStateException 발생")
        void deleteProject_결재중상태_예외발생() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중 신청서 존재
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(true);

                // when & then
                assertThatThrownBy(() -> projectService.deleteProject(prjMngNo))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("결재중이거나 결재완료된 프로젝트는 삭제할 수 없습니다");
        }

        @Test
        @DisplayName("deleteProject - 정상 상태 프로젝트 삭제 시 project.delete() 호출 (delYn=Y)")
        void deleteProject_정상상태_SoftDelete() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중 신청서 없음
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSno(prjMngNo, 1)).willReturn(List.of());

                // when
                projectService.deleteProject(prjMngNo);

                // then: Soft Delete 검증
                assertThat(project.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("deleteProject - 미존재 프로젝트 삭제 시 IllegalArgumentException 발생")
        void deleteProject_미존재프로젝트_예외발생() {
                // given
                given(projectRepository.findByPrjMngNoAndDelYn("INVALID", "N"))
                                .willReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> projectService.deleteProject("INVALID"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Project not found");
        }

        // ───────────────────────────────────────────────────────
        // getProjectList (신규) — 2건 반환
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectList: DEL_YN=N 프로젝트 2건이 있으면 2건을 반환한다")
        void getProjectList_2건반환() {
                // given: 두 개의 프로젝트 빌더 생성
                Bprojm project1 = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001").prjSno(1).delYn("N").build();
                Bprojm project2 = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0002").prjSno(1).delYn("N").build();

                given(projectRepository.findAllByDelYn("N"))
                                .willReturn(List.of(project1, project2));
                // 배치 조회: 신청서·부서·사용자 없음
                given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(
                                anyString(), anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                // 예산 합계 계산용 코드 조회
                given(codeService.findCodeEntitiesByCttTp(anyString()))
                                .willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getPrjMngNo()).isEqualTo("PRJ-2026-0001");
                assertThat(result.get(1).getPrjMngNo()).isEqualTo("PRJ-2026-0002");
        }

        // ───────────────────────────────────────────────────────
        // searchProjectList (신규) — 검색 조건 전달 확인
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("searchProjectList: 검색 조건을 repository에 전달하고 결과를 반환한다")
        void searchProjectList_검색조건전달확인() {
                // given: 검색 조건 설정
                ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001").prjSno(1).delYn("N").build();

                given(projectRepository.searchByCondition(condition))
                                .willReturn(List.of(project));
                given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc(
                                anyString(), anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCttTp(anyString())).willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.searchProjectList(condition);

                // then: searchByCondition이 호출되었고 결과 1건 반환
                verify(projectRepository).searchByCondition(condition);
                assertThat(result).hasSize(1);
        }

        // ───────────────────────────────────────────────────────
        // createProject (신규) — 관리번호 자동 채번
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: 관리번호가 없으면 Oracle 시퀀스로 자동 채번하여 저장한다")
        void createProject_관리번호자동채번() {
                // given: 관리번호 미입력
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .prjNm("신규 정보화사업")
                                .bgYy("2026")
                                .build();

                given(projectRepository.getNextSequenceValue()).willReturn(1L);

                // when
                String result = projectService.createProject(request);

                // then: 자동 채번된 관리번호 형식 검증 (PRJ-2026-0001)
                assertThat(result).matches("PRJ-2026-\\d{4}");
                // repository.save() 호출 확인
                verify(projectRepository).save(any(Bprojm.class));
        }

        // ───────────────────────────────────────────────────────
        // updateProject (신규) — 정상 수정
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 정상 수정 시 관리번호를 반환한다")
        void updateProject_정상수정_관리번호반환() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중/결재완료 신청서 없음 → 수정 허용
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                // 기존 품목 없음
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .prjNm("수정된 사업명")
                                .build();

                // when
                String result = projectService.updateProject(prjMngNo, request);

                // then
                assertThat(result).isEqualTo(prjMngNo);
        }

        // ───────────────────────────────────────────────────────
        // getProjectsByIds (신규) — 존재+미존재 필터링
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectsByIds: 존재하는 항목만 반환하고 미존재 항목은 제외한다")
        void getProjectsByIds_존재미존재필터링() {
                // given: 첫 번째만 존재, 두 번째는 미존재
                String existingNo = "PRJ-2026-0001";
                String missingNo  = "PRJ-NOTEXIST-9999";

                Bprojm project = Bprojm.builder()
                                .prjMngNo(existingNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(existingNo, "N"))
                                .willReturn(Optional.of(project));
                given(projectRepository.findByPrjMngNoAndDelYn(missingNo, "N"))
                                .willReturn(Optional.empty());

                // 단건 조회 경로 내부 mock
                given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                                anyString(), eq(existingNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(existingNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCttTp(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(existingNo, missingNo));

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then: 존재하는 1건만 반환
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrjMngNo()).isEqualTo(existingNo);
        }
}
