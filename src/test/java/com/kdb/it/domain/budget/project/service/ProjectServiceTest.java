package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.access.AccessDeniedException;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
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
        private BbugtmRepository bbugtmRepository;
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
                given(codeService.findCodeEntitiesByCId(anyString()))
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
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
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
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(existingNo, missingNo));

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then: 존재하는 1건만 반환
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrjMngNo()).isEqualTo(existingNo);
        }

        // ───────────────────────────────────────────────────────
        // updateProject — 결재중 예외 경로
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 결재중/결재완료 상태이면 IllegalStateException을 던진다")
        void updateProject_결재중상태_예외발생() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(true);

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .prjNm("수정 시도").build();

                assertThatThrownBy(() -> projectService.updateProject(prjMngNo, request))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("결재중이거나 결재완료된 프로젝트는 수정할 수 없습니다");
        }

        // ───────────────────────────────────────────────────────
        // createProject — 기존 관리번호 중복 예외
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: 제공된 관리번호가 이미 존재하면 IllegalArgumentException을 던진다")
        void createProject_기존관리번호_중복예외발생() {
                String prjMngNo = "PRJ-2026-EXIST";
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .prjMngNo(prjMngNo)
                                .bgYy("2026")
                                .build();

                given(projectRepository.existsByPrjMngNoAndDelYn(prjMngNo, "N")).willReturn(true);

                assertThatThrownBy(() -> projectService.createProject(request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Project already exists");
        }

        // ───────────────────────────────────────────────────────
        // createProject — 품목 포함 생성
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: 품목이 포함된 요청이면 품목도 함께 저장한다")
        void createProject_품목포함_save호출() {
                // given
                given(projectRepository.getNextSequenceValue()).willReturn(1L);
                given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
                item.setGclDtt("IOE-237-0700");
                item.setGclNm("소프트웨어 구매");
                item.setGclAmt(java.math.BigDecimal.valueOf(1_000_000));

                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .prjNm("품목포함 사업")
                                .bgYy("2026")
                                .items(List.of(item))
                                .build();

                // when
                String result = projectService.createProject(request);

                // then: 프로젝트 + 품목 각 1회 save
                assertThat(result).matches("PRJ-2026-\\d{4}");
                org.mockito.Mockito.verify(projectRepository).save(any(Bprojm.class));
                org.mockito.Mockito.verify(bitemmRepository).save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
        }

        // ───────────────────────────────────────────────────────
        // updateProject — 신규 품목 추가
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 신규 품목(gclMngNo=null)이 포함된 요청이면 품목을 save한다")
        void updateProject_신규품목추가_save호출() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(bitemmRepository.getNextSequenceValue()).willReturn(2L);
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                ProjectDto.BitemmDto newItem = new ProjectDto.BitemmDto();
                newItem.setGclDtt("IOE-351-0100");
                newItem.setGclNm("신규 품목");
                newItem.setGclAmt(java.math.BigDecimal.valueOf(500_000));

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .prjNm("수정 사업명")
                                .items(List.of(newItem))
                                .build();

                // when
                String result = projectService.updateProject(prjMngNo, request);

                // then: 신규 품목 save 호출
                assertThat(result).isEqualTo(prjMngNo);
                org.mockito.Mockito.verify(bitemmRepository).save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
        }

        // ───────────────────────────────────────────────────────
        // updateProject — 기존 품목 삭제(요청에 없는 항목 soft-delete)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 요청에 없는 기존 품목은 Soft Delete 된다")
        void updateProject_기존품목삭제_SoftDelete() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();

                // 기존 품목 1건 (gclMngNo="GCL-0001")
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                        com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                .gclMngNo("GCL-0001").gclSno(1)
                                .prjMngNo(prjMngNo).prjSno(1)
                                .gclDtt("IOE-237-0700").gclNm("기존 품목").delYn("N")
                                .build();

                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                // 요청에 품목 없음 → 기존 품목 전부 soft-delete
                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .prjNm("수정 사업명")
                                .items(List.of())
                                .build();

                // when
                projectService.updateProject(prjMngNo, request);

                // then: 기존 품목이 DEL_YN='Y'로 soft-delete 됨
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("createProject: 관리번호가 있고 중복이 없으면 제공된 관리번호로 저장한다")
        void createProject_제공관리번호_중복없음_저장() {
                String prjMngNo = "PRJ-2026-MANUAL";
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .prjMngNo(prjMngNo)
                                .prjNm("수기 관리번호 사업")
                                .prjDes("<script>alert(1)</script><p>설명</p>")
                                .prjRng("<b>범위</b>")
                                .build();
                given(projectRepository.existsByPrjMngNoAndDelYn(prjMngNo, "N")).willReturn(false);

                String result = projectService.createProject(request);

                assertThat(result).isEqualTo(prjMngNo);
                assertThat(request.getPrjDes()).doesNotContain("<script>");
                verify(projectRepository).save(any(Bprojm.class));
        }

        @Test
        @DisplayName("createProject: 사업연도가 없으면 현재 연도로 채번한다")
        void createProject_사업연도없음_현재연도채번() {
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .prjNm("연도 기본값 사업")
                                .build();
                given(projectRepository.getNextSequenceValue()).willReturn(3L);

                String result = projectService.createProject(request);

                assertThat(result).matches("PRJ-\\d{4}-0003");
                assertThat(request.getBgYy()).matches("\\d{4}");
        }

        @Test
        @DisplayName("updateProject: 기존 품목이 변경되면 이전 품목을 삭제하고 새 버전을 저장한다")
        void updateProject_기존품목변경_버저닝저장() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                                .gclMngNo("GCL-0001").gclSno(1)
                                                .prjMngNo(prjMngNo).prjSno(1)
                                                .gclDtt("IOE-237-0700")
                                                .gclNm("기존 품목")
                                                .gclQtt(java.math.BigDecimal.ONE)
                                                .cur("KRW")
                                                .xcr(java.math.BigDecimal.ONE)
                                                .bgFdtn("기존 근거")
                                                .infPrtYn("N")
                                                .itrInfrYn("N")
                                                .gclAmt(java.math.BigDecimal.valueOf(1000))
                                                .delYn("N")
                                                .build();
                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .gclDtt("IOE-237-0700")
                                .gclNm("변경 품목")
                                .gclQtt(java.math.BigDecimal.ONE)
                                .cur("KRW")
                                .xcr(java.math.BigDecimal.ONE)
                                .bgFdtn("기존 근거")
                                .infPrtYn(null)
                                .itrInfrYn(null)
                                .gclAmt(java.math.BigDecimal.valueOf(1000))
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .prjNm("수정 사업")
                                .items(List.of(changedItem))
                                .build());

                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
        }

        @Test
        @DisplayName("updateProject: 기존 품목이 변경되지 않으면 버저닝 저장하지 않는다")
        void updateProject_기존품목변경없음_저장없음() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                                .gclMngNo("GCL-0001").gclSno(1)
                                                .prjMngNo(prjMngNo).prjSno(1)
                                                .gclDtt("IOE-237-0700")
                                                .gclNm("동일 품목")
                                                .gclQtt(java.math.BigDecimal.ONE)
                                                .cur("KRW")
                                                .xcr(java.math.BigDecimal.ONE)
                                                .infPrtYn("N")
                                                .itrInfrYn("N")
                                                .gclAmt(java.math.BigDecimal.valueOf(1000))
                                                .delYn("N")
                                                .build();
                ProjectDto.BitemmDto sameItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .gclDtt("IOE-237-0700")
                                .gclNm("동일 품목")
                                .gclQtt(java.math.BigDecimal.ONE)
                                .cur("KRW")
                                .xcr(java.math.BigDecimal.ONE)
                                .infPrtYn(null)
                                .itrInfrYn(null)
                                .gclAmt(java.math.BigDecimal.valueOf(1000))
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(sameItem))
                                .build());

                assertThat(existingItem.getDelYn()).isEqualTo("N");
                org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.never())
                                .save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
        }

        @Test
        @DisplayName("getProjectsByIds: 배경연도가 있으면 편성예산을 자본/경상으로 분류한다")
        void getProjectsByIds_배경연도있음_편성예산분류() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo).prjSno(1).delYn("N").build();
                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_CPIT"))
                                .willReturn(List.of(Ccodem.builder().cId("IOE-ASSET").cDes("개발비").build()));
                given(codeService.findCodeEntitiesByCId("IOE_IDR"))
                                .willReturn(List.of(Ccodem.builder().cId("IOE-COST").build()));
                given(codeService.findCodeEntitiesByCId("IOE_SEVS")).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_XPN")).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_LEAFE")).willReturn(List.of());
                given(bbugtmRepository.sumDupBgByPrjMngNos(List.of(prjMngNo), "2026"))
                                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(1000)));
                given(bbugtmRepository.sumAssetDupBgByPrjMngNos(eq(List.of(prjMngNo)), eq("2026"), any()))
                                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(700)));
                given(bbugtmRepository.sumCostDupBgByPrjMngNos(eq(List.of(prjMngNo)), eq("2026"), any()))
                                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(300)));
                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(prjMngNo));
                request.setBgYy("2026");

                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getDupBg()).isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
                assertThat(result.get(0).getAssetDupBg()).isEqualByComparingTo(java.math.BigDecimal.valueOf(700));
                assertThat(result.get(0).getCostDupBg()).isEqualByComparingTo(java.math.BigDecimal.valueOf(300));
        }

        @Test
        @DisplayName("getProject: 신청서, 코드명, 예산 합계를 함께 채운다")
        void getProject_상세보강정보_함께반환() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo)
                                .prjSno(1)
                                .itDpm("101")
                                .svnDpm("102")
                                .itDpmCgpr("10001")
                                .itDpmTlr("10002")
                                .svnDpmCgpr("10003")
                                .svnDpmTlr("10004")
                                .delYn("N")
                                .build();
                Cappla cappla = Cappla.builder()
                                .apfMngNo("APF-001")
                                .orcPkVl(prjMngNo)
                                .orcSnoVl(1)
                                .build();
                Capplm capplm = Capplm.builder()
                                .apfMngNo("APF-001")
                                .apfNm("결재")
                                .apfSts("결재중")
                                .build();
                Cdecim decision = Cdecim.builder()
                                .dcdMngNo("APF-001")
                                .dcdSqn(1)
                                .dcdEno("10002")
                                .build();
                Bitemm devItem = Bitemm.builder()
                                .gclDtt("IOE-DEV")
                                .gclAmt(BigDecimal.valueOf(100))
                                .xcr(BigDecimal.TEN)
                                .build();
                Bitemm machItem = Bitemm.builder()
                                .gclDtt("IOE-MACH")
                                .gclAmt(BigDecimal.valueOf(200))
                                .xcr(BigDecimal.ZERO)
                                .build();
                Bitemm costItem = Bitemm.builder()
                                .gclDtt("IOE-COST")
                                .gclAmt(BigDecimal.valueOf(300))
                                .xcr(null)
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc(
                                "BPROJM", prjMngNo, 1)).willReturn(List.of(cappla));
                given(capplmRepository.findById("APF-001")).willReturn(Optional.of(capplm));
                given(cdecimRepository.findByDcdMngNoOrderByDcdSqnAsc("APF-001")).willReturn(List.of(decision));
                given(corgnIRepository.findById("101")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("101").bbrNm("IT부").build()));
                given(corgnIRepository.findById("102")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("102").bbrNm("현업부").build()));
                given(cuserIRepository.findById("10001")).willReturn(Optional.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
                given(cuserIRepository.findById("10002")).willReturn(Optional.of(CuserI.builder().eno("10002").usrNm("팀장").build()));
                given(cuserIRepository.findById("10003")).willReturn(Optional.of(CuserI.builder().eno("10003").usrNm("현업담당").build()));
                given(cuserIRepository.findById("10004")).willReturn(Optional.of(CuserI.builder().eno("10004").usrNm("현업팀장").build()));
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(devItem, machItem, costItem,
                                                Bitemm.builder().gclDtt(null).gclAmt(BigDecimal.ONE).build(),
                                                Bitemm.builder().gclDtt("IOE-NULL").gclAmt(null).build()));
                given(codeService.findCodeEntitiesByCId("IOE_CPIT"))
                                .willReturn(List.of(
                                                Ccodem.builder().cdva("IOE-DEV").cDes("개발비").build(),
                                                Ccodem.builder().cdva("IOE-MACH").cDes("기계장치").build()));
                given(codeService.findCodeEntitiesByCId("IOE_IDR"))
                                .willReturn(List.of(Ccodem.builder().cdva("IOE-COST").build()));
                given(codeService.findCodeEntitiesByCId("IOE_SEVS")).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_XPN")).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_LEAFE")).willReturn(List.of());

                ProjectDto.Response result = projectService.getProject(prjMngNo);

                assertThat(result.getApfMngNo()).isEqualTo("APF-001");
                assertThat(result.getApfSts()).isEqualTo("결재중");
                assertThat(result.getItDpmNm()).isEqualTo("IT부");
                assertThat(result.getSvnDpmNm()).isEqualTo("현업부");
                assertThat(result.getItDpmCgprNm()).isEqualTo("담당자");
                assertThat(result.getAssetBg()).isEqualByComparingTo("1200");
                assertThat(result.getDevBg()).isEqualByComparingTo("1000");
                assertThat(result.getMachBg()).isEqualByComparingTo("200");
                assertThat(result.getCostBg()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("updateProject: 일반사용자가 타인 작성 프로젝트를 수정하면 거부된다")
        void updateProject_일반사용자_타인작성수정거부() {
                CustomUserDetails user = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "999");
                given(authentication.getPrincipal()).willReturn(user);
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .fstEnrUsid("10001")
                                .svnDpm("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                assertThatThrownBy(() -> projectService.updateProject("PRJ-2026-0001",
                                ProjectDto.UpdateRequest.builder().prjNm("수정").build()))
                                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("updateProject: 부서관리자가 다른 부서 프로젝트를 수정하면 거부된다")
        void updateProject_부서관리자_타부서수정거부() {
                CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
                given(authentication.getPrincipal()).willReturn(manager);
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .fstEnrUsid("10001")
                                .svnDpm("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                assertThatThrownBy(() -> projectService.updateProject("PRJ-2026-0001",
                                ProjectDto.UpdateRequest.builder().prjNm("수정").build()))
                                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("deleteProject: 품목이 있으면 프로젝트와 품목을 함께 논리삭제한다")
        void deleteProject_품목포함_함께논리삭제() {
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .delYn("N")
                                .build();
                Bitemm item = Bitemm.builder()
                                .gclMngNo("GCL-0001")
                                .gclSno(1)
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSno("PRJ-2026-0001", 1)).willReturn(List.of(item));

                projectService.deleteProject("PRJ-2026-0001");

                assertThat(project.getDelYn()).isEqualTo("Y");
                assertThat(item.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("getProjectList: 배치 보강으로 신청서, 부서명, 담당자명을 설정한다")
        void getProjectList_배치보강정보설정() {
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .itDpm("101")
                                .svnDpm("102")
                                .itDpmCgpr("10001")
                                .itDpmTlr("10002")
                                .svnDpmCgpr("10003")
                                .svnDpmTlr("10004")
                                .delYn("N")
                                .build();
                Cappla latest = Cappla.builder()
                                .apfMngNo("APF-001")
                                .orcPkVl("PRJ-2026-0001")
                                .orcSnoVl(1)
                                .build();
                Cappla old = Cappla.builder()
                                .apfMngNo("APF-OLD")
                                .orcPkVl("PRJ-2026-0001")
                                .orcSnoVl(1)
                                .build();
                Capplm capplm = Capplm.builder()
                                .apfMngNo("APF-001")
                                .apfSts("결재중")
                                .build();
                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc("BPROJM", List.of("PRJ-2026-0001")))
                                .willReturn(List.of(latest, old));
                given(capplmRepository.findAllById(List.of("APF-001"))).willReturn(List.of(capplm));
                given(cdecimRepository.findByDcdMngNoInOrderByDcdSqnAsc(List.of("APF-001")))
                                .willReturn(List.of(Cdecim.builder().dcdMngNo("APF-001").dcdSqn(1).dcdEno("10002").build()));
                given(corgnIRepository.findAllById(any()))
                                .willReturn(List.of(
                                                CorgnI.builder().prlmOgzCCone("101").bbrNm("IT부").build(),
                                                CorgnI.builder().prlmOgzCCone("102").bbrNm("현업부").build()));
                given(cuserIRepository.findAllById(any()))
                                .willReturn(List.of(
                                                CuserI.builder().eno("10001").usrNm("IT담당").build(),
                                                CuserI.builder().eno("10002").usrNm("IT팀장").build(),
                                                CuserI.builder().eno("10003").usrNm("현업담당").build(),
                                                CuserI.builder().eno("10004").usrNm("현업팀장").build()));
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn("PRJ-2026-0001", 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                List<ProjectDto.Response> result = projectService.getProjectList();

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-001");
                assertThat(result.get(0).getApfSts()).isEqualTo("결재중");
                assertThat(result.get(0).getItDpmNm()).isEqualTo("IT부");
                assertThat(result.get(0).getSvnDpmNm()).isEqualTo("현업부");
                assertThat(result.get(0).getItDpmCgprNm()).isEqualTo("IT담당");
                assertThat(result.get(0).getSvnDpmTlrNm()).isEqualTo("현업팀장");
        }

        @Test
        @DisplayName("updateProject: 인증 주체가 CustomUserDetails가 아니면 거부된다")
        void updateProject_인증주체비정상_거부() {
                given(authentication.getPrincipal()).willReturn("anonymous");
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                assertThatThrownBy(() -> projectService.updateProject("PRJ-2026-0001",
                                ProjectDto.UpdateRequest.builder().build()))
                                .isInstanceOf(AccessDeniedException.class)
                                .hasMessageContaining("인증 정보");
        }

        @Test
        @DisplayName("updateProject: 부서관리자는 같은 부서 프로젝트를 수정할 수 있다")
        void updateProject_부서관리자_동일부서허용() {
                CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "101");
                given(authentication.getPrincipal()).willReturn(manager);
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .svnDpm("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn("PRJ-2026-0001", 1, "N"))
                                .willReturn(List.of());

                String result = projectService.updateProject("PRJ-2026-0001", ProjectDto.UpdateRequest.builder().build());

                assertThat(result).isEqualTo("PRJ-2026-0001");
        }

        @Test
        @DisplayName("updateProject: 일반사용자는 본인 작성 프로젝트를 수정할 수 있다")
        void updateProject_일반사용자_본인작성허용() {
                CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "999");
                given(authentication.getPrincipal()).willReturn(user);
                Bprojm project = Bprojm.builder()
                                .prjMngNo("PRJ-2026-0001")
                                .prjSno(1)
                                .fstEnrUsid("10001")
                                .svnDpm("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn("PRJ-2026-0001", 1, "N"))
                                .willReturn(List.of());

                String result = projectService.updateProject("PRJ-2026-0001", ProjectDto.UpdateRequest.builder().build());

                assertThat(result).isEqualTo("PRJ-2026-0001");
        }

        @Test
        @DisplayName("updateProject: 기존 품목의 마지막 금액 필드만 달라도 변경으로 판단한다")
        void updateProject_기존품목_금액만변경_버저닝저장() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .prjMngNo(prjMngNo)
                                .prjSno(1)
                                .delYn("N")
                                .build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0001")
                                .gclSno(1)
                                .prjMngNo(prjMngNo)
                                .prjSno(1)
                                .gclDtt("IOE-237")
                                .gclNm("동일")
                                .gclQtt(BigDecimal.ONE)
                                .cur("KRW")
                                .xcr(null)
                                .xcrBseDt(LocalDate.of(2026, 1, 1))
                                .bgFdtn("근거")
                                .itdDt("2026-02")
                                .dfrCle("매월")
                                .infPrtYn("Y")
                                .itrInfrYn("Y")
                                .gclAmt(BigDecimal.valueOf(100))
                                .delYn("N")
                                .build();
                ProjectDto.BitemmDto changed = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .gclDtt("IOE-237")
                                .gclNm("동일")
                                .gclQtt(BigDecimal.ONE)
                                .cur("KRW")
                                .xcr(null)
                                .xcrBseDt(LocalDate.of(2026, 1, 1))
                                .bgFdtn("근거")
                                .itdDt("2026-02")
                                .dfrCle("매월")
                                .infPrtYn("Y")
                                .itrInfrYn("Y")
                                .gclAmt(BigDecimal.valueOf(200))
                                .build();
                given(projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(changed))
                                .build());

                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(Bitemm.class));
        }

        @Test
        @DisplayName("getProjectList: 프로젝트가 없으면 배치 보강 없이 빈 목록을 반환한다")
        void getProjectList_빈목록_빈목록반환() {
                given(projectRepository.findAllByDelYn("N")).willReturn(List.of());

                List<ProjectDto.Response> result = projectService.getProjectList();

                assertThat(result).isEmpty();
        }
}
