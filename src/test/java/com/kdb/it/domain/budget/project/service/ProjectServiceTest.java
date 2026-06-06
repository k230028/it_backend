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
import org.mockito.ArgumentCaptor;
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
        /** 환율 표준 조회 헬퍼 (CONTEXT.md 결정 E / R3.7 — Wave 5 추가 의존성) */
        @Mock
        private com.kdb.it.domain.budget.cost.util.XcrLookupService xcrLookupService;
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
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                // setApplicationInfo 내부의 findBy... 호출 → Mockito 기본값(빈 리스트) 자동 처리
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), anyString(), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), eq(1), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0001");
        }

        @Test
        @DisplayName("getProject - 존재하는 프로젝트 관리번호 조회 시 Response 반환")
        void getProject_존재하는프로젝트_반환() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getAbusMngNo()).isEqualTo(prjMngNo);
        }

        @Test
        @DisplayName("getProject - 미존재 프로젝트 조회 시 IllegalArgumentException 발생")
        void getProject_미존재프로젝트_예외발생() {
                // given
                given(projectRepository.findByAbusMngNoAndDelYn("INVALID", "N"))
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중 신청서 존재
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중 신청서 없음
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, 1)).willReturn(List.of());

                // when
                projectService.deleteProject(prjMngNo);

                // then: Soft Delete 검증
                assertThat(project.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("deleteProject - 미존재 프로젝트 삭제 시 IllegalArgumentException 발생")
        void deleteProject_미존재프로젝트_예외발생() {
                // given
                given(projectRepository.findByAbusMngNoAndDelYn("INVALID", "N"))
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
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
                Bprojm project2 = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0002").sno(1).delYn("N").build();

                given(projectRepository.findAllByDelYn("N"))
                                .willReturn(List.of(project1, project2));
                // 배치 조회: 신청서·부서·사용자 없음
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                anyString(), anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                // 예산 합계 계산용 코드 조회
                given(codeService.findCodeEntitiesByCId(anyString()))
                                .willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0001");
                assertThat(result.get(1).getAbusMngNo()).isEqualTo("PRJ-2026-0002");
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
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();

                given(projectRepository.searchByCondition(condition))
                                .willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                anyString(), anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
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
                                .abusNm("신규 정보화사업")
                                .bseYy("2026")
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                // 결재중/결재완료 신청서 없음 → 수정 허용
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                // 기존 품목 없음
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("수정된 사업명")
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
                                .abusMngNo(existingNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(existingNo, "N"))
                                .willReturn(Optional.of(project));
                given(projectRepository.findByAbusMngNoAndDelYn(missingNo, "N"))
                                .willReturn(Optional.empty());

                // 단건 조회 경로 내부 mock
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(existingNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(existingNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(existingNo, missingNo));

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then: 존재하는 1건만 반환
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getAbusMngNo()).isEqualTo(existingNo);
        }

        // ───────────────────────────────────────────────────────
        // updateProject — 결재중 예외 경로
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 결재중/결재완료 상태이면 IllegalStateException을 던진다")
        void updateProject_결재중상태_예외발생() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(true);

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("수정 시도").build();

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
                                .abusMngNo(prjMngNo)
                                .bseYy("2026")
                                .build();

                given(projectRepository.existsByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(true);

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
                item.setIoeC("IOE-237-0700");
                item.setGclNm("소프트웨어 구매");
                item.setAmt(java.math.BigDecimal.valueOf(1_000_000));

                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("품목포함 사업")
                                .bseYy("2026")
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(bitemmRepository.getNextSequenceValue()).willReturn(2L);
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                ProjectDto.BitemmDto newItem = new ProjectDto.BitemmDto();
                newItem.setIoeC("IOE-351-0100");
                newItem.setGclNm("신규 품목");
                newItem.setAmt(java.math.BigDecimal.valueOf(500_000));

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("수정 사업명")
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                // 기존 품목 1건 (gclMngNo="GCL-0001")
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                        com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                .gclMngNo("GCL-0001").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-237-0700").gclNm("기존 품목").delYn("N")
                                .build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                // 요청에 품목 없음 → 기존 품목 전부 soft-delete
                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("수정 사업명")
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
                                .abusMngNo(prjMngNo)
                                .abusNm("수기 관리번호 사업")
                                .abusCone("<script>alert(1)</script><p>설명</p>")
                                .abusRngCone("<b>범위</b>")
                                .build();
                given(projectRepository.existsByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(false);

                String result = projectService.createProject(request);

                assertThat(result).isEqualTo(prjMngNo);
                assertThat(request.getAbusCone()).doesNotContain("<script>");
                verify(projectRepository).save(any(Bprojm.class));
        }

        @Test
        @DisplayName("createProject: 사업연도가 없으면 현재 연도로 채번한다")
        void createProject_사업연도없음_현재연도채번() {
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("연도 기본값 사업")
                                .build();
                given(projectRepository.getNextSequenceValue()).willReturn(3L);

                String result = projectService.createProject(request);

                assertThat(result).matches("PRJ-\\d{4}-0003");
                assertThat(request.getBseYy()).matches("\\d{4}");
        }

        @Test
        @DisplayName("updateProject: 기존 품목이 변경되면 이전 품목을 삭제하고 새 버전을 저장한다")
        void updateProject_기존품목변경_버저닝저장() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                                .gclMngNo("GCL-0001").sno(1)
                                                .abusMngNo(prjMngNo).sno(1)
                                                .ioeC("IOE-237-0700")
                                                .gclNm("기존 품목")
                                                .qty(java.math.BigDecimal.ONE)
                                                .curC("KRW")
                                                .xcr(java.math.BigDecimal.ONE)
                                                .cncdFdtnCone("기존 근거")
                                                .sectSysUtzYn("N")
                                                .itrInfrYn("N")
                                                .amt(java.math.BigDecimal.valueOf(1000))
                                                .delYn("N")
                                                .build();
                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .ioeC("IOE-237-0700")
                                .gclNm("변경 품목")
                                .qty(java.math.BigDecimal.ONE)
                                .curC("KRW")
                                .xcr(java.math.BigDecimal.ONE)
                                .cncdFdtnCone("기존 근거")
                                .sectSysUtzYn(null)
                                .itrInfrYn(null)
                                .amt(java.math.BigDecimal.valueOf(1000))
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .abusNm("수정 사업")
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                                .gclMngNo("GCL-0001").sno(1)
                                                .abusMngNo(prjMngNo).sno(1)
                                                .ioeC("IOE-237-0700")
                                                .gclNm("동일 품목")
                                                .qty(java.math.BigDecimal.ONE)
                                                .curC("KRW")
                                                .xcr(java.math.BigDecimal.ONE)
                                                .sectSysUtzYn("N")
                                                .itrInfrYn("N")
                                                .amt(java.math.BigDecimal.valueOf(1000))
                                                .delYn("N")
                                                .build();
                ProjectDto.BitemmDto sameItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .ioeC("IOE-237-0700")
                                .gclNm("동일 품목")
                                .qty(java.math.BigDecimal.ONE)
                                .curC("KRW")
                                .xcr(java.math.BigDecimal.ONE)
                                .sectSysUtzYn(null)
                                .itrInfrYn(null)
                                .amt(java.math.BigDecimal.valueOf(1000))
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
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
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId("IOE_CPIT"))
                                .willReturn(List.of(Ccodem.builder().cId("IOE-ASSET").cdvaDes("개발비").build()));
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
                request.setBseYy("2026");

                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getDupBgAmt()).isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
                assertThat(result.get(0).getAssetDupBg()).isEqualByComparingTo(java.math.BigDecimal.valueOf(700));
                assertThat(result.get(0).getCostDupBg()).isEqualByComparingTo(java.math.BigDecimal.valueOf(300));
        }

        @Test
        @DisplayName("getProject: 신청서, 코드명, 예산 합계를 함께 채운다")
        void getProject_상세보강정보_함께반환() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo)
                                .sno(1)
                                .dvmDpmC("101")
                                .svnDpmC("102")
                                .dvmUsid("10001")
                                .dvmTlrUsid("10002")
                                .usid("10003")
                                .tlrUsid("10004")
                                .delYn("N")
                                .build();
                Cappla cappla = Cappla.builder()
                                .apfDcmNo("APF-001")
                                .pkColNm(prjMngNo)
                                .fntTbCrySno(1)
                                .build();
                Capplm capplm = Capplm.builder()
                                .apfMngNo("APF-001")
                                .dcdReqTtl("결재")
                                .apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code())
                                .build();
                Cdecim decision = Cdecim.builder()
                                .dcdMngNo("APF-001")
                                .dcrSqnSno(1)
                                .dcrEno("10002")
                                .build();
                Bitemm devItem = Bitemm.builder()
                                .ioeC("101")
                                .amt(BigDecimal.valueOf(100))
                                .xcr(BigDecimal.TEN)
                                .build();
                Bitemm machItem = Bitemm.builder()
                                .ioeC("102")
                                .amt(BigDecimal.valueOf(200))
                                .xcr(BigDecimal.ZERO)
                                .build();
                Bitemm costItem = Bitemm.builder()
                                .ioeC("103")
                                .amt(BigDecimal.valueOf(300))
                                .xcr(null)
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                "BPROJM", prjMngNo, 1)).willReturn(List.of(cappla));
                given(capplmRepository.findById("APF-001")).willReturn(Optional.of(capplm));
                given(cdecimRepository.findByDcdMngNoOrderByDcrSqnSnoAsc("APF-001")).willReturn(List.of(decision));
                given(corgnIRepository.findById("101")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("101").bbrNm("IT부").build()));
                given(corgnIRepository.findById("102")).willReturn(Optional.of(CorgnI.builder().prlmOgzCCone("102").bbrNm("현업부").build()));
                given(cuserIRepository.findById("10001")).willReturn(Optional.of(CuserI.builder().eno("10001").usrNm("담당자").build()));
                given(cuserIRepository.findById("10002")).willReturn(Optional.of(CuserI.builder().eno("10002").usrNm("팀장").build()));
                given(cuserIRepository.findById("10003")).willReturn(Optional.of(CuserI.builder().eno("10003").usrNm("현업담당").build()));
                given(cuserIRepository.findById("10004")).willReturn(Optional.of(CuserI.builder().eno("10004").usrNm("현업팀장").build()));
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(devItem, machItem, costItem,
                                                Bitemm.builder().ioeC(null).amt(BigDecimal.ONE).build(),
                                                Bitemm.builder().ioeC("IOE-NULL").amt(null).build()));
                given(codeService.findCodeEntitiesByCId("IOE_C"))
                                .willReturn(List.of(
                                                Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build(),
                                                Ccodem.builder().cId("IOE_C").cdva("102").cdvaNm("기계장치").cTp("IOE_HW").build(),
                                                Ccodem.builder().cId("IOE_C").cdva("103").cTp("IOE_IDR").build()));
                given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                                .willReturn(List.of(
                                                Ccodem.builder().cId("IOE_C").cdva("101").cdvaNm("개발비").cTp("IOE_DVC").build(),
                                                Ccodem.builder().cId("IOE_C").cdva("102").cdvaNm("기계장치").cTp("IOE_HW").build(),
                                                Ccodem.builder().cId("IOE_C").cdva("103").cTp("IOE_IDR").build()));

                ProjectDto.Response result = projectService.getProject(prjMngNo);

                assertThat(result.getApfMngNo()).isEqualTo("APF-001");
                assertThat(result.getApfSts()).isEqualTo("결재중");
                assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
                assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
                assertThat(result.getDvmUsidNm()).isEqualTo("담당자");
                assertThat(result.getAssetBg()).isEqualByComparingTo("1200");
                assertThat(result.getDvcBg()).isEqualByComparingTo("1000");
                assertThat(result.getHwBg()).isEqualByComparingTo("200");
                assertThat(result.getCostBg()).isEqualByComparingTo("300");
                assertThat(result.getItems().get(0).getIoeCNm()).isEqualTo("개발비");
                assertThat(result.getItems().get(1).getIoeCNm()).isEqualTo("기계장치");
        }

        @Test
        @DisplayName("updateProject: 일반사용자가 타인 작성 프로젝트를 수정하면 거부된다")
        void updateProject_일반사용자_타인작성수정거부() {
                CustomUserDetails user = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "999");
                given(authentication.getPrincipal()).willReturn(user);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .fstEnrUsid("10001")
                                .svnDpmC("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                assertThatThrownBy(() -> projectService.updateProject("PRJ-2026-0001",
                                ProjectDto.UpdateRequest.builder().abusNm("수정").build()))
                                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("updateProject: 부서관리자가 다른 부서 프로젝트를 수정하면 거부된다")
        void updateProject_부서관리자_타부서수정거부() {
                CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
                given(authentication.getPrincipal()).willReturn(manager);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .fstEnrUsid("10001")
                                .svnDpmC("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                assertThatThrownBy(() -> projectService.updateProject("PRJ-2026-0001",
                                ProjectDto.UpdateRequest.builder().abusNm("수정").build()))
                                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("deleteProject: 품목이 있으면 프로젝트와 품목을 함께 논리삭제한다")
        void deleteProject_품목포함_함께논리삭제() {
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .delYn("N")
                                .build();
                Bitemm item = Bitemm.builder()
                                .gclMngNo("GCL-0001")
                                .sno(1)
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1)).willReturn(List.of(item));

                projectService.deleteProject("PRJ-2026-0001");

                assertThat(project.getDelYn()).isEqualTo("Y");
                assertThat(item.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("getProjectList: 배치 보강으로 신청서, 부서명, 담당자명을 설정한다")
        void getProjectList_배치보강정보설정() {
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .dvmDpmC("101")
                                .svnDpmC("102")
                                .dvmUsid("10001")
                                .dvmTlrUsid("10002")
                                .usid("10003")
                                .tlrUsid("10004")
                                .delYn("N")
                                .build();
                Cappla latest = Cappla.builder()
                                .apfDcmNo("APF-001")
                                .pkColNm("PRJ-2026-0001")
                                .fntTbCrySno(1)
                                .build();
                Cappla old = Cappla.builder()
                                .apfDcmNo("APF-OLD")
                                .pkColNm("PRJ-2026-0001")
                                .fntTbCrySno(1)
                                .build();
                Capplm capplm = Capplm.builder()
                                .apfMngNo("APF-001")
                                .apfPrgStsC(com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code())
                                .build();
                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc("BPROJM", List.of("PRJ-2026-0001")))
                                .willReturn(List.of(latest, old));
                given(capplmRepository.findAllById(List.of("APF-001"))).willReturn(List.of(capplm));
                given(cdecimRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(List.of("APF-001")))
                                .willReturn(List.of(Cdecim.builder().dcdMngNo("APF-001").dcrSqnSno(1).dcrEno("10002").build()));
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
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                List<ProjectDto.Response> result = projectService.getProjectList();

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-001");
                assertThat(result.get(0).getApfSts()).isEqualTo("결재중");
                assertThat(result.get(0).getDvmDpmCNm()).isEqualTo("IT부");
                assertThat(result.get(0).getSvnDpmCNm()).isEqualTo("현업부");
                assertThat(result.get(0).getDvmUsidNm()).isEqualTo("IT담당");
                assertThat(result.get(0).getTlrUsidNm()).isEqualTo("현업팀장");
        }

        @Test
        @DisplayName("updateProject: 인증 주체가 CustomUserDetails가 아니면 거부된다")
        void updateProject_인증주체비정상_거부() {
                given(authentication.getPrincipal()).willReturn("anonymous");
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
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
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .svnDpmC("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
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
                                .abusMngNo("PRJ-2026-0001")
                                .sno(1)
                                .fstEnrUsid("10001")
                                .svnDpmC("101")
                                .delYn("N")
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                                .willReturn(List.of());

                String result = projectService.updateProject("PRJ-2026-0001", ProjectDto.UpdateRequest.builder().build());

                assertThat(result).isEqualTo("PRJ-2026-0001");
        }

        // ───────────────────────────────────────────────────────
        // buildCodeNameMap — cdvas 필터 + merge 람다 커버
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectList: 배치 보강 시 buildCodeNameMap이 지정 cdvas만 필터링하여 코드명을 반환한다")
        void buildCodeNameMap_cdvas필터와merge람다커버() {
                // given: 두 개의 프로젝트 (prjTp="A" 중복 → merge lambda 트리거)
                Bprojm project1 = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").bzTpC("A").build();
                Bprojm project2 = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0002").sno(2).delYn("N").bzTpC("A").build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project1, project2));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                                .willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
                // ccodemRepository: prjTp="A" 코드명 반환 (cdva 필터 대상)
                given(ccodemRepository.findByCIdWithValidDate(anyString(), any()))
                                .willReturn(List.of(
                                                Ccodem.builder().cdva("A").cNm("일반사업").build(),
                                                Ccodem.builder().cdva("B").cNm("제외대상").build()));
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then: 두 프로젝트 모두 반환되며 prjTpNm이 "일반사업"으로 설정됨
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getBzTpCNm()).isEqualTo("일반사업");
                assertThat(result.get(1).getBzTpCNm()).isEqualTo("일반사업");
        }

        // ───────────────────────────────────────────────────────
        // getProjectsByIds — bgYy 없음 분기 (편성예산 계산 skip)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectsByIds: bgYy가 null이면 편성예산 계산을 건너뛰고 응답만 반환한다")
        void getProjectsByIds_bgYy없음_편성예산skip() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(prjMngNo));
                request.setBseYy(null); // bgYy 없음 → 편성예산 skip 분기

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then: 프로젝트 반환, bbugtmRepository.sumDupBgByPrjMngNos 미호출
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getAbusMngNo()).isEqualTo(prjMngNo);
                org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                                .sumDupBgByPrjMngNos(anyList(), anyString());
        }

        // ───────────────────────────────────────────────────────
        // enrichProjectListBatch — orgCodes/userEnos 없는 경우 분기 커버
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectList: cappla 있고 capplm 없으면 apfSts만 설정하지 않고 응답을 반환한다")
        void getProjectList_cappla있고capplm없음_apfMngNo만설정() {
                // given: cappla 있음, capplmRepository 반환 없음 → capplm == null 분기 커버
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
                Cappla cappla = Cappla.builder()
                                .apfDcmNo("APF-NOCAPLM")
                                .pkColNm("PRJ-2026-0001")
                                .fntTbCrySno(1)
                                .build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                                .willReturn(List.of(cappla));
                // capplmRepository.findAllById → 빈 목록 → capplmMap.get() == null → capplm null 분기
                given(capplmRepository.findAllById(anyList())).willReturn(List.of());
                given(cdecimRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then: apfMngNo는 설정, apfSts는 null
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-NOCAPLM");
                assertThat(result.get(0).getApfSts()).isNull();
        }

        @Test
        @DisplayName("getProjectList: bzDtt/tchnTp/mnUsr/rprSts/prjPulPtt/pulDtt 있는 프로젝트의 코드명을 배치 조회한다")
        void getProjectList_추가코드필드_배치코드명조회() {
                // given: 코드 필드가 모두 있는 프로젝트 → buildCodeNameMap 분기 다수 커버
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N")
                                .bzTpC("A").bzDttNm("B1").sklTpTc("C1")
                                .cstTpTc("D1").rprStsTc("E1").exePttYn("F1").abusTc("G1")
                                .build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                                .willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
                // ccodemRepository: 각 코드 반환
                given(ccodemRepository.findByCIdWithValidDate(anyString(), any()))
                                .willReturn(List.of(
                                                Ccodem.builder().cdva("A").cNm("사업유형A").build(),
                                                Ccodem.builder().cdva("B1").cNm("업무구분B1").build(),
                                                Ccodem.builder().cdva("C1").cNm("기술유형C1").build(),
                                                Ccodem.builder().cdva("D1").cNm("주요사용자D1").build(),
                                                Ccodem.builder().cdva("E1").cNm("보고상태E1").build(),
                                                Ccodem.builder().cdva("F1").cNm("추진가능F1").build(),
                                                Ccodem.builder().cdva("G1").cNm("사업구분G1").build()));
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then: 프로젝트 1건 반환
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getBzTpCNm()).isEqualTo("사업유형A");
                // 업무구분 코드명은 bzDttNmNm(코드명 필드)에 저장; bzDttNm은 원본 코드값 필드
                assertThat(result.get(0).getBzDttNmNm()).isEqualTo("업무구분B1");
        }

        @Test
        @DisplayName("getProjectList: itDpm/svnDpm 없는 프로젝트도 정상 처리된다")
        void getProjectList_부서정보없음_정상처리() {
                // given: 부서/담당자 정보가 null인 프로젝트 (enrichProjectListBatch null-check 분기 커버)
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0009").sno(1).delYn("N")
                                .dvmDpmC(null).svnDpmC(null)
                                .dvmUsid(null).dvmTlrUsid(null)
                                .usid(null).tlrUsid(null)
                                .bzTpC(null).bzDttNm(null).sklTpTc(null).cstTpTc(null)
                                .rprStsTc(null).exePttYn(null).abusTc(null)
                                .build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                                .willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(anyString(), any(), anyString()))
                                .willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0009");
        }

        @Test
        @DisplayName("updateProject: 기존 품목의 마지막 금액 필드만 달라도 변경으로 판단한다")
        void updateProject_기존품목_금액만변경_버저닝저장() {
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo)
                                .sno(1)
                                .delYn("N")
                                .build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0001")
                                .sno(1)
                                .abusMngNo(prjMngNo)
                                .sno(1)
                                .ioeC("IOE-237")
                                .gclNm("동일")
                                .qty(BigDecimal.ONE)
                                .curC("KRW")
                                .xcr(null)
                                .xcrBseDt("20260101")
                                .cncdFdtnCone("근거")
                                .bseYm("2026-02")
                                .dfrCleC("매월")
                                .sectSysUtzYn("Y")
                                .itrInfrYn("Y")
                                .amt(BigDecimal.valueOf(100))
                                .delYn("N")
                                .build();
                ProjectDto.BitemmDto changed = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .ioeC("IOE-237")
                                .gclNm("동일")
                                .qty(BigDecimal.ONE)
                                .curC("KRW")
                                .xcr(null)
                                .xcrBseDt("20260101")
                                .cncdFdtnCone("근거")
                                .bseYm("2026-02")
                                .dfrCleC("매월")
                                .sectSysUtzYn("Y")
                                .itrInfrYn("Y")
                                .amt(BigDecimal.valueOf(200))
                                .build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
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

        // ───────────────────────────────────────────────────────
        // getProjectsByIds — bgYy null/blank (편성예산 조회 미실행)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectsByIds: bgYy가 null이면 편성예산 조회 없이 기본 정보만 반환한다")
        void getProjectsByIds_bgYyNull_기본정보만반환() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(prjMngNo));
                request.setBseYy(null); // bgYy = null → 편성예산 조회 건너뜀

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then
                assertThat(result).hasSize(1);
                org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                                .sumDupBgByPrjMngNos(any(), any());
        }

        @Test
        @DisplayName("getProjectsByIds: bgYy가 공백이면 편성예산 조회 없이 기본 정보만 반환한다")
        void getProjectsByIds_bgYyBlank_기본정보만반환() {
                // given
                String prjMngNo = "PRJ-2026-0002";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of(prjMngNo));
                request.setBseYy("   "); // 공백 → isBlank() true

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then
                assertThat(result).hasSize(1);
                org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                                .sumDupBgByPrjMngNos(any(), any());
        }

        @Test
        @DisplayName("getProjectsByIds: 요청 목록이 모두 미존재이면 빈 목록을 반환한다")
        void getProjectsByIds_모두미존재_빈목록() {
                // given
                given(projectRepository.findByAbusMngNoAndDelYn("INVALID-1", "N"))
                                .willReturn(Optional.empty());
                given(projectRepository.findByAbusMngNoAndDelYn("INVALID-2", "N"))
                                .willReturn(Optional.empty());

                ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
                request.setPrjMngNos(List.of("INVALID-1", "INVALID-2"));
                request.setBseYy("2026");

                // when
                List<ProjectDto.Response> result = projectService.getProjectsByIds(request);

                // then: bbugtmRepository 미호출 (responses가 empty)
                assertThat(result).isEmpty();
                org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                                .sumDupBgByPrjMngNos(any(), any());
        }

        // ───────────────────────────────────────────────────────
        // deleteProject — RBAC 권한 검증 (일반사용자/부서관리자)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("deleteProject: 일반사용자가 본인 작성 프로젝트를 삭제할 수 있다")
        void deleteProject_일반사용자_본인작성_삭제허용() {
                // given
                CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "999");
                given(authentication.getPrincipal()).willReturn(user);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1)
                                .fstEnrUsid("10001") // 본인 작성
                                .svnDpmC("101").delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1))
                                .willReturn(List.of());

                // when
                projectService.deleteProject("PRJ-2026-0001");

                // then
                assertThat(project.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("deleteProject: 일반사용자가 타인 작성 프로젝트를 삭제하면 거부된다")
        void deleteProject_일반사용자_타인작성_거부() {
                // given
                CustomUserDetails user = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "999");
                given(authentication.getPrincipal()).willReturn(user);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1)
                                .fstEnrUsid("10001") // 타인 작성
                                .svnDpmC("101").delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                // when & then
                assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }

        @Test
        @DisplayName("deleteProject: 부서관리자가 같은 부서 프로젝트를 삭제할 수 있다")
        void deleteProject_부서관리자_동일부서_삭제허용() {
                // given
                CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "101");
                given(authentication.getPrincipal()).willReturn(manager);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1)
                                .fstEnrUsid("10001")
                                .svnDpmC("101") // 같은 부서
                                .delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1))
                                .willReturn(List.of());

                // when
                projectService.deleteProject("PRJ-2026-0001");

                // then
                assertThat(project.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("deleteProject: 부서관리자가 다른 부서 프로젝트를 삭제하면 거부된다")
        void deleteProject_부서관리자_타부서_거부() {
                // given
                CustomUserDetails manager = new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
                given(authentication.getPrincipal()).willReturn(manager);
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1)
                                .fstEnrUsid("10001")
                                .svnDpmC("101") // 다른 부서
                                .delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                // when & then
                assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }

        @Test
        @DisplayName("deleteProject: 인증 주체가 비정상이면 거부된다")
        void deleteProject_인증주체비정상_거부() {
                // given
                given(authentication.getPrincipal()).willReturn("anonymous");
                Bprojm project = Bprojm.builder()
                                .abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                                .willReturn(Optional.of(project));

                // when & then
                assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                                .hasMessageContaining("인증 정보");
        }

        // ───────────────────────────────────────────────────────
        // getProject — 신청서 없는/CAPPLM 없는 경우
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProject: 신청서가 없으면 apfMngNo가 null이다")
        void getProject_신청서없음_apfMngNoNull() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of()); // 신청서 없음
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getApfMngNo()).isNull();
        }

        @Test
        @DisplayName("getProject: CAPPLA는 있지만 CAPPLM이 없으면 apfSts가 null이다")
        void getProject_capplaExist_capplmMissing_apfStsNull() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Cappla cappla = Cappla.builder()
                                .apfDcmNo("APF-001").pkColNm(prjMngNo).fntTbCrySno(1).build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of(cappla));
                given(capplmRepository.findById("APF-001")).willReturn(Optional.empty()); // CAPPLM 없음
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then
                assertThat(result.getApfMngNo()).isEqualTo("APF-001");
                assertThat(result.getApfSts()).isNull();
        }

        // ───────────────────────────────────────────────────────
        // getProject — IOE_CPIT cdvaDes 기반 자본예산 세부 분류
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProject: IOE_CPIT 코드의 cdvaDes에 따라 단말기/기계장치/기타무형자산으로 분류된다")
        void getProject_ioeCpit_cdvaDes분류() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm devItem = Bitemm.builder()
                                .ioeC("DEV-001").amt(BigDecimal.valueOf(100)).xcr(null).build();
                Bitemm machItem = Bitemm.builder()
                                .ioeC("MACH-001").amt(BigDecimal.valueOf(200)).xcr(BigDecimal.ZERO).build();
                Bitemm intanItem = Bitemm.builder()
                                .ioeC("INTAN-001").amt(BigDecimal.valueOf(300)).xcr(BigDecimal.ONE).build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(devItem, machItem, intanItem));
                given(codeService.findCodeEntitiesByCId("IOE_C")).willReturn(List.of(
                                Ccodem.builder().cId("IOE_C").cdva("DEV-001").cTp("IOE_CPIT").cdvaDes("단말기").build(),
                                Ccodem.builder().cId("IOE_C").cdva("MACH-001").cTp("IOE_CPIT").cdvaDes("기계장치").build(),
                                Ccodem.builder().cId("IOE_C").cdva("INTAN-001").cTp("IOE_CPIT").cdvaDes("기타무형자산").build()
                ));
                given(ccodemRepository.findByCIdWithValidDate(eq("IOE_C"), any())).willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then
                assertThat(result.getAssetBg()).isEqualByComparingTo("600");
                assertThat(result.getDvcBg()).isEqualByComparingTo("100");
                assertThat(result.getHwBg()).isEqualByComparingTo("200");
        }

        // ───────────────────────────────────────────────────────
        // searchProjectList — 빈 결과
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("searchProjectList: 조건에 맞는 프로젝트가 없으면 빈 목록을 반환한다")
        void searchProjectList_조건불일치_빈목록() {
                // given
                ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
                given(projectRepository.searchByCondition(condition)).willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.searchProjectList(condition);

                // then
                assertThat(result).isEmpty();
        }

        // ───────────────────────────────────────────────────────
        // updateProject — items null (품목 동기화 건너뜀)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: items가 null이면 품목 동기화를 건너뛴다")
        void updateProject_itemsNull_품목동기화건너뜀() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("수정 사업명")
                                .items(null) // null → 동기화 건너뜀
                                .build();

                // when
                String result = projectService.updateProject(prjMngNo, request);

                // then: bitemmRepository 조회 미호출
                assertThat(result).isEqualTo(prjMngNo);
                org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.never())
                                .findByAbusMngNoAndFntTbCrySnoAndDelYn(any(), any(), any());
        }

        // ───────────────────────────────────────────────────────
        // updateProject — 요청 품목의 gclMngNo가 기존에 없는 경우
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: 요청 품목의 gclMngNo가 기존에 없는 값이면 기존 품목이 soft-delete된다")
        void updateProject_요청품목_기존없는gclMngNo_무시() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0001").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-001").gclNm("기존품목").delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                // 존재하지 않는 gclMngNo로 수정 요청 (existingItems에 없음)
                ProjectDto.BitemmDto notFoundItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-NOTEXIST")
                                .ioeC("IOE-002").gclNm("없는품목")
                                .amt(BigDecimal.valueOf(100))
                                .build();

                // when
                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .abusNm("수정").items(List.of(notFoundItem)).build());

                // then: processedGclMngNos에 없는 existingItem은 soft-delete
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
        }

        // ───────────────────────────────────────────────────────
        // createProject — XSS 새니타이징
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: XSS 스크립트가 포함된 prjDes/prjRng는 새니타이징된다")
        void createProject_xss새니타이징() {
                // given
                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("XSS 테스트 사업")
                                .bseYy("2026")
                                .abusCone("<script>alert('xss')</script><p>설명</p>")
                                .abusRngCone("<script>alert('xss2')</script><b>범위</b>")
                                .build();
                given(projectRepository.getNextSequenceValue()).willReturn(10L);

                // when
                String result = projectService.createProject(request);

                // then: script 태그 제거됨
                assertThat(result).matches("PRJ-2026-\\d{4}");
                assertThat(request.getAbusCone()).doesNotContain("<script>");
                assertThat(request.getAbusRngCone()).doesNotContain("<script>");
        }

        // ───────────────────────────────────────────────────────
        // updateProject — XSS 새니타이징
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: XSS 스크립트가 포함된 prjDes/prjRng는 수정 시 새니타이징된다")
        void updateProject_xss새니타이징() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("XSS 수정 테스트")
                                .abusCone("<script>alert('xss')</script><p>설명</p>")
                                .abusRngCone("<script>alert('xss2')</script><b>범위</b>")
                                .build();

                // when
                String result = projectService.updateProject(prjMngNo, request);

                // then
                assertThat(result).isEqualTo(prjMngNo);
                assertThat(request.getAbusCone()).doesNotContain("<script>");
                assertThat(request.getAbusRngCone()).doesNotContain("<script>");
        }

        // ───────────────────────────────────────────────────────
        // getProject — setCodeNames 코드명 필드 전체 분기 커버
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProject: prjTp/bzDtt/tchnTp/mnUsr/rprSts/prjPulPtt/pulDtt 코드명이 설정된다")
        void getProject_setCodeNames_모든코드명필드설정() {
                // given: 모든 코드명 필드가 설정된 프로젝트
                String prjMngNo = "PRJ-2026-CODE";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1)
                                .bzTpC("TP01").bzDttNm("BZ01").sklTpTc("TC01")
                                .cstTpTc("MN01").rprStsTc("RS01").exePttYn("PP01").abusTc("PD01")
                                .dvmDpmC("101").svnDpmC("102")
                                .dvmUsid("10001").dvmTlrUsid("10002")
                                .usid("10003").tlrUsid("10004")
                                .delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                // 부서명/사용자명
                given(corgnIRepository.findById("101")).willReturn(Optional.of(
                                CorgnI.builder().prlmOgzCCone("101").bbrNm("IT부").build()));
                given(corgnIRepository.findById("102")).willReturn(Optional.of(
                                CorgnI.builder().prlmOgzCCone("102").bbrNm("현업부").build()));
                given(cuserIRepository.findById("10001")).willReturn(Optional.of(
                                CuserI.builder().eno("10001").usrNm("담당자1").build()));
                given(cuserIRepository.findById("10002")).willReturn(Optional.of(
                                CuserI.builder().eno("10002").usrNm("팀장1").build()));
                given(cuserIRepository.findById("10003")).willReturn(Optional.of(
                                CuserI.builder().eno("10003").usrNm("담당자2").build()));
                given(cuserIRepository.findById("10004")).willReturn(Optional.of(
                                CuserI.builder().eno("10004").usrNm("팀장2").build()));

                // 공통코드 코드명 설정
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("PRJ_TP", "TP01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("PRJ_TP").cdva("TP01").cNm("신규개발").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("BZ_DTT", "BZ01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("BZ_DTT").cdva("BZ01").cNm("금융").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("IT_PTL_TCHN_TP_TC", "TC01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("IT_PTL_TCHN_TP_TC").cdva("TC01").cNm("AI").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("CST_TP_TC", "MN01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("CST_TP_TC").cdva("MN01").cNm("직접관리").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("IT_PTL_RPR_STS_TC", "RS01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("IT_PTL_RPR_STS_TC").cdva("RS01").cNm("검토중").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("EXE_PTT_YN", "PP01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("EXE_PTT_YN").cdva("PP01").cNm("정규").build()));
                given(ccodemRepository.findByCIdAndCdvaWithValidDate("ABUS_TC", "PD01", null))
                                .willReturn(Optional.of(Ccodem.builder().cId("ABUS_TC").cdva("PD01").cNm("연초").build()));

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then: 코드명 필드 확인
                assertThat(result.getBzTpCNm()).isEqualTo("신규개발");
                assertThat(result.getBzDttNmNm()).isEqualTo("금융");
                assertThat(result.getSklTpTcNm()).isEqualTo("AI");
                assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
                assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
                assertThat(result.getDvmUsidNm()).isEqualTo("담당자1");
        }

        @Test
        @DisplayName("getProject: 코드값이 null이면 코드명 조회를 건너뛴다")
        void getProject_setCodeNames_코드값null_건너뜀() {
                // given: 코드명 필드 모두 null
                String prjMngNo = "PRJ-2026-NULLCODES";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1)
                                .bzTpC(null).bzDttNm(null).sklTpTc(null)
                                .cstTpTc(null).rprStsTc(null).exePttYn(null).abusTc(null)
                                .dvmDpmC(null).svnDpmC(null)
                                .dvmUsid(null).dvmTlrUsid(null)
                                .usid(null).tlrUsid(null)
                                .delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1))).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of());
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                // when
                ProjectDto.Response result = projectService.getProject(prjMngNo);

                // then: ccodemRepository 미호출
                assertThat(result).isNotNull();
                org.mockito.Mockito.verify(ccodemRepository, org.mockito.Mockito.never())
                                .findByCIdAndCdvaWithValidDate(any(), any(), any());
        }

        // ───────────────────────────────────────────────────────
        // setBudgetSummary — items가 이미 설정된 경우 (목록 조회 분기)
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("getProjectList: 품목이 있는 경우 items DTO를 설정하고 예산 합계를 계산한다")
        void getProjectList_품목있음_예산합계계산() {
                // given: 품목 1건이 있는 프로젝트
                String prjMngNo = "PRJ-2026-ITEM";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm item = Bitemm.builder()
                                .ioeC("IOE-001").amt(BigDecimal.valueOf(500))
                                .xcr(BigDecimal.ONE).build();

                given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
                given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                anyString(), anyList())).willReturn(List.of());
                given(corgnIRepository.findAllById(anyList())).willReturn(List.of());
                given(cuserIRepository.findAllById(anyList())).willReturn(List.of());
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(item));
                // IOE 코드 없음 → assetBg=0, costBg=0
                given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

                // when
                List<ProjectDto.Response> result = projectService.getProjectList();

                // then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getItems()).isNotNull();
        }

        // ───────────────────────────────────────────────────────
        // isItemChanged — 개별 필드별 변경 감지 분기
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("updateProject: ioeC만 변경되면 버저닝이 발생한다")
        void updateProject_ioeC변경_버저닝() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0001").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-OLD")
                                .gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .sectSysUtzYn("N").itrInfrYn("N")
                                .amt(BigDecimal.valueOf(100)).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0001")
                                .ioeC("IOE-NEW") // ioeC만 변경
                                .gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .sectSysUtzYn(null).itrInfrYn(null)
                                .amt(BigDecimal.valueOf(100)).build();

                // when
                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(changedItem)).build());

                // then: 변경 감지 → soft-delete + save
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(Bitemm.class));
        }

        @Test
        @DisplayName("updateProject: cur만 변경되면 버저닝이 발생한다")
        void updateProject_cur변경_버저닝() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0002").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW") // 기존 KRW
                                .xcr(BigDecimal.ONE).sectSysUtzYn("N").itrInfrYn("N")
                                .amt(BigDecimal.valueOf(100)).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0002")
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("USD") // curC 변경
                                .xcr(BigDecimal.ONE).sectSysUtzYn(null).itrInfrYn(null)
                                .amt(BigDecimal.valueOf(100)).build();

                // when
                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(changedItem)).build());

                // then
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(Bitemm.class));
        }

        @Test
        @DisplayName("updateProject: xcrBseDt만 변경되면 버저닝이 발생한다")
        void updateProject_xcrBseDt변경_버저닝() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0003").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .xcrBseDt("20260101") // 기존 날짜
                                .sectSysUtzYn("N").itrInfrYn("N")
                                .amt(BigDecimal.valueOf(100)).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0003")
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .xcrBseDt("20260601") // 날짜 변경
                                .sectSysUtzYn(null).itrInfrYn(null)
                                .amt(BigDecimal.valueOf(100)).build();

                // when
                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(changedItem)).build());

                // then
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(Bitemm.class));
        }

        @Test
        @DisplayName("updateProject: bgFdtnCone만 변경되면 버저닝이 발생한다")
        void updateProject_bgFdtnCone변경_버저닝() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-0004").sno(1)
                                .abusMngNo(prjMngNo).sno(1)
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .cncdFdtnCone("기존근거")
                                .sectSysUtzYn("N").itrInfrYn("N")
                                .amt(BigDecimal.valueOf(100)).delYn("N").build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));

                ProjectDto.BitemmDto changedItem = ProjectDto.BitemmDto.builder()
                                .gclMngNo("GCL-0004")
                                .ioeC("IOE-001").gclNm("동일").qty(BigDecimal.ONE)
                                .curC("KRW").xcr(BigDecimal.ONE)
                                .cncdFdtnCone("변경근거") // bgFdtnCone 변경
                                .sectSysUtzYn(null).itrInfrYn(null)
                                .amt(BigDecimal.valueOf(100)).build();

                // when
                projectService.updateProject(prjMngNo, ProjectDto.UpdateRequest.builder()
                                .items(List.of(changedItem)).build());

                // then
                assertThat(existingItem.getDelYn()).isEqualTo("Y");
                verify(bitemmRepository).save(any(Bitemm.class));
        }

        // ───────────────────────────────────────────────────────
        // createProject — 품목 여러 건 저장
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: 품목 여러 건이 포함되면 각각 save 호출된다")
        void createProject_품목여러건_모두저장() {
                // given
                given(projectRepository.getNextSequenceValue()).willReturn(5L);
                given(bitemmRepository.getNextSequenceValue())
                                .willReturn(1L).willReturn(2L);

                ProjectDto.BitemmDto item1 = new ProjectDto.BitemmDto();
                item1.setIoeC("IOE-001");
                item1.setGclNm("품목1");
                item1.setAmt(BigDecimal.valueOf(100_000));

                ProjectDto.BitemmDto item2 = new ProjectDto.BitemmDto();
                item2.setIoeC("IOE-002");
                item2.setGclNm("품목2");
                item2.setAmt(BigDecimal.valueOf(200_000));

                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("다중품목 사업")
                                .bseYy("2026")
                                .items(List.of(item1, item2))
                                .build();

                // when
                String result = projectService.createProject(request);

                // then: 프로젝트 1회 + 품목 2회 save
                assertThat(result).matches("PRJ-2026-\\d{4}");
                org.mockito.Mockito.verify(projectRepository).save(any(Bprojm.class));
                org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.times(2))
                                .save(any(Bitemm.class));
        }

        // ───────────────────────────────────────────────────────
        // plan 03-04: 외화 서버 재계산 (Bitemm) — 신규 3건
        // ───────────────────────────────────────────────────────

        @Test
        @DisplayName("createProject: 외화 품목 입력 시 gclAmt = fcAmt × xcr로 서버 재계산되어 저장된다")
        void createProject_외화품목_gclAmt_서버재계산() {
                // given
                given(projectRepository.getNextSequenceValue()).willReturn(1L);
                given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
                item.setIoeC("IOE-237-0700");
                item.setGclNm("외화 라이선스");
                item.setCurC("USD");
                item.setFcAmt(new BigDecimal("500.000"));
                item.setXcr(new BigDecimal("1300.0000"));
                item.setAmt(new BigDecimal("999")); // 클라 위조 — 무시되어야 함

                // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다 (CONTEXT.md 결정 E)
                given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                                .willReturn(new BigDecimal("1300.0000"));

                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("외화 품목 사업")
                                .bseYy("2026")
                                .items(List.of(item))
                                .build();

                // when
                projectService.createProject(request);

                // then: 저장된 Bitemm 캡처 — 서버 재계산값 검증
                ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
                org.mockito.Mockito.verify(bitemmRepository).save(captor.capture());
                assertThat(captor.getValue().getAmt())
                                .as("서버 재계산: 500.000 × 1300.0000 = 650000.0000")
                                .isEqualByComparingTo(new BigDecimal("650000.0000"));
                assertThat(captor.getValue().getFcAmt())
                                .isEqualByComparingTo(new BigDecimal("500.000"));
        }

        @Test
        @DisplayName("createProject: 원화(KRW) 품목 입력 시 fcAmt=null로 강제되고 gclAmt는 클라값 그대로 저장된다")
        void createProject_원화품목_fcAmt_null_저장() {
                given(projectRepository.getNextSequenceValue()).willReturn(1L);
                given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
                item.setIoeC("IOE-237-0700");
                item.setGclNm("원화 소프트웨어");
                item.setCurC("KRW");
                item.setAmt(new BigDecimal("1000000"));
                item.setFcAmt(null);
                item.setXcr(null);

                ProjectDto.CreateRequest request = ProjectDto.CreateRequest.builder()
                                .abusNm("원화 품목 사업")
                                .bseYy("2026")
                                .items(List.of(item))
                                .build();

                projectService.createProject(request);

                ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
                org.mockito.Mockito.verify(bitemmRepository).save(captor.capture());
                assertThat(captor.getValue().getFcAmt()).isNull();
                assertThat(captor.getValue().getAmt())
                                .isEqualByComparingTo(new BigDecimal("1000000"));
        }

        @Test
        @DisplayName("updateProject: 기존 품목의 fcAmt만 변경되어도 isItemChanged가 true로 판정되어 신규 버전 저장된다 (null-safe 포함)")
        void updateProject_fcAmt만변경_변경검출_신규버전저장() {
                // given
                String prjMngNo = "PRJ-2026-0001";
                Bprojm project = Bprojm.builder()
                                .abusMngNo(prjMngNo).sno(1).delYn("N").build();

                // 기존 품목: 외화 USD 500.000 (fcAmt 있음)
                Bitemm existingItem = Bitemm.builder()
                                .gclMngNo("GCL-2026-0001")
                                .sno(1)
                                .abusMngNo(prjMngNo)
                                .sno(1)
                                .ioeC("IOE-237-0700")
                                .gclNm("외화 라이선스")
                                .curC("USD")
                                .fcAmt(new BigDecimal("500.000"))
                                .xcr(new BigDecimal("1300.0000"))
                                .amt(new BigDecimal("650000.000"))
                                .sectSysUtzYn("N")
                                .itrInfrYn("N")
                                .delYn("N")
                                .lstYn("Y")
                                .build();

                given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                                .willReturn(Optional.of(project));
                given(capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                                .willReturn(false);
                given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                                .willReturn(List.of(existingItem));
                given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

                // 동일 품목, fcAmt만 500.000 → 600.000으로 변경 (xcr 동일)
                ProjectDto.BitemmDto changed = new ProjectDto.BitemmDto();
                changed.setGclMngNo("GCL-2026-0001"); // 기존 식별자
                changed.setIoeC("IOE-237-0700");
                changed.setGclNm("외화 라이선스");
                changed.setCurC("USD");
                changed.setFcAmt(new BigDecimal("600.000")); // 변경
                changed.setXcr(new BigDecimal("1300.0000"));
                changed.setAmt(new BigDecimal("650000.000")); // 클라가 동일하게 보냄 — 서버 재계산

                // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다 (CONTEXT.md 결정 E)
                given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                                .willReturn(new BigDecimal("1300.0000"));

                ProjectDto.UpdateRequest request = ProjectDto.UpdateRequest.builder()
                                .abusNm("외화 품목 사업")
                                .bseYy("2026")
                                .items(List.of(changed))
                                .build();

                // when
                projectService.updateProject(prjMngNo, request);

                // then: isItemChanged → true 판정되어 신규 Bitemm 저장 + 서버 재계산
                ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
                org.mockito.Mockito.verify(bitemmRepository).save(captor.capture());
                assertThat(captor.getValue().getFcAmt())
                                .isEqualByComparingTo(new BigDecimal("600.000"));
                assertThat(captor.getValue().getAmt())
                                .as("서버 재계산: 600.000 × 1300.0000 = 780000.0000")
                                .isEqualByComparingTo(new BigDecimal("780000.0000"));
        }
}
