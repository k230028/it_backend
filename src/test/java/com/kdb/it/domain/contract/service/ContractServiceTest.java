package com.kdb.it.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.domain.contract.repository.ContractDetailRow;
import com.kdb.it.domain.contract.repository.ContractRepository;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.service.EaiService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;
    @Mock BprojaSyncService bprojaSyncService;
    @Mock EaiService eaiService;

    ContractService service;

    /** 일반 사용자(부서코드 18001) */
    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    /** 관리자(ITPAD001) */
    CustomUserDetails admin() {
        return new CustomUserDetails("A0001", List.of("ITPAD001"), "18001");
    }

    /** 타부서 일반 사용자(소유자 아님) */
    CustomUserDetails other() {
        return new CustomUserDetails("E0002", List.of("ITPZZ001"), "18001");
    }

    /** 공통 테스트용 Bcontm 빌더 헬퍼. 최초등록자(소유자)는 requester()와 동일한 E0001. */
    Bcontm entityWith(String stsTc, String ioeC, String cncdRfrNo) {
        return Bcontm.builder()
                .docMngNo("CTR-2026-0001")
                .docVrsSno(1)
                .lstYn("Y")
                .ioeC(ioeC)
                .cncdRfrNo(cncdRfrNo)
                .stsTc(stsTc)
                .fstEnrUsid("E0001")
                .build();
    }

    ContractDetailRow detailRow(Bcontm e, String targetName) {
        return new ContractDetailRow(
                e.getDocMngNo(),
                e.getDocVrsSno(),
                e.getIoeC(),
                e.getCncdRfrNo(),
                targetName,
                e.getStsTc(),
                e.getReqCone(),
                e.getItPtlCttManrC(),
                e.getCttManrRsn(),
                e.getCttNm(),
                e.getCttAmt(),
                e.getCttOppNm(),
                e.getCttDt(),
                e.getFstEnrUsid(),
                e.getFstEnrDtm());
    }

    @BeforeEach
    void setUp() {
        service =
                new ContractService(
                        contractRepository,
                        projectRepository,
                        costRepository,
                        bprojaSyncService,
                        eaiService,
                        new GweProperties("TEST00000001"));
    }

    // =========================================================================
    // create — 신규 의뢰 생성
    // =========================================================================

    @Nested
    @DisplayName("create — 신규 의뢰 생성")
    class Create {

        @Test
        @DisplayName("사업 대상 신규 의뢰 생성 시 문서번호를 채번하고 상태 61, 대상구분 100으로 저장한다")
        void create_project_assignsDocNoAndStatus61() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N"))
                    .thenReturn(true);
            when(contractRepository.existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
                            anyString(), anyString(), any(), anyString()))
                    .thenReturn(false);
            when(contractRepository.nextDocSeq()).thenReturn(1L);
            when(contractRepository.save(any(Bcontm.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String docNo =
                    service.create(
                            new ContractDto.CreateRequest("100", "PRJ-1", "입찰 의뢰합니다"), requester());

            // Assert
            assertThat(docNo).matches("CTR-\\d{4}-0001");
        }

        @Test
        @DisplayName("전산업무비 대상 신규 의뢰 생성 시 문서번호를 채번하고 상태 61, 대상구분 200으로 저장한다")
        void create_cost_assignsDocNoAndStatus61() {
            // Arrange
            when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N"))
                    .thenReturn(true);
            when(contractRepository.existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
                            anyString(), anyString(), any(), anyString()))
                    .thenReturn(false);
            when(contractRepository.nextDocSeq()).thenReturn(1L);
            when(contractRepository.save(any(Bcontm.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String docNo =
                    service.create(
                            new ContractDto.CreateRequest("200", "BG-1", "전산업무비 입찰 의뢰"),
                            requester());

            // Assert
            assertThat(docNo).matches("CTR-\\d{4}-0001");
        }

        @Test
        @DisplayName("대상 사업이 없으면 신규 의뢰를 거부한다")
        void create_rejectsWhenTargetProjectMissing() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N"))
                    .thenReturn(false);

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new ContractDto.CreateRequest("100", "PRJ-X", null),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("대상");
        }

        @Test
        @DisplayName("전산업무비 대상이 없으면 신규 의뢰를 거부한다")
        void create_rejectsWhenTargetCostMissing() {
            // Arrange — 전산업무비 대상 미존재
            when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-X", "Y", "N"))
                    .thenReturn(false);

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new ContractDto.CreateRequest("200", "BG-X", null),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("대상");
        }

        @Test
        @DisplayName("알 수 없는 대상구분(999)이면 신규 의뢰를 거부한다")
        void create_rejectsUnknownIoeC() {
            // Arrange — 대상구분 코드가 100/200이 아닌 경우
            // validateTarget → IllegalArgumentException("알 수 없는 대상구분: ...")

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new ContractDto.CreateRequest("999", "ANY-1", null),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("알 수 없는 대상구분");
        }

        @Test
        @DisplayName("동일 대상에 진행중 문서가 있으면 신규 의뢰를 거부한다")
        void create_rejectsDuplicate() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N"))
                    .thenReturn(true);
            when(contractRepository.existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
                            anyString(), anyString(), any(), anyString()))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new ContractDto.CreateRequest("100", "PRJ-1", null),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("진행 중");
        }
    }

    // =========================================================================
    // update — 요청내용 수정
    // =========================================================================

    @Nested
    @DisplayName("update — 요청내용 수정")
    class Update {

        @Test
        @DisplayName("작성중(61) 상태에서 수정 시 요청내용이 갱신된다")
        void update_draft_updatesReqCone() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.update("CTR-2026-0001", new ContractDto.UpdateRequest("새 요청내용"), requester());

            // Assert — JPA Dirty Checking 방식; entity 필드 직접 검증
            assertThat(e.getReqCone()).isEqualTo("새 요청내용");
        }

        @Test
        @DisplayName("작성중이 아닐 때(62) 마스터 수정을 거부한다")
        void update_rejectsWhenNotDraft() {
            // Arrange
            Bcontm e = entityWith("75", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "CTR-2026-0001",
                                            new ContractDto.UpdateRequest("수정 내용"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("작성중");
        }

        @Test
        @DisplayName("완료(69) 상태에서 수정을 거부한다")
        void update_rejectsWhenDone() {
            // Arrange — 완료 상태
            Bcontm e = entityWith("79", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "CTR-2026-0001",
                                            new ContractDto.UpdateRequest("변경 시도"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // delete — 논리 삭제
    // =========================================================================

    @Nested
    @DisplayName("delete — 논리 삭제")
    class Delete {

        @Test
        @DisplayName("작성중(61) 상태에서 삭제 시 DEL_YN이 Y로 변경된다")
        void delete_draft_setsDelYn() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.delete("CTR-2026-0001", requester());

            // Assert — BaseEntity.delete()가 delYn을 'Y'로 변경
            assertThat(e.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("작성중이 아닐 때(62) 삭제를 거부한다")
        void delete_rejectsWhenNotDraft() {
            // Arrange
            Bcontm e = entityWith("75", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("CTR-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("작성중");
        }

        @Test
        @DisplayName("완료(69) 상태에서 삭제를 거부한다")
        void delete_rejectsWhenDone() {
            // Arrange
            Bcontm e = entityWith("79", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("CTR-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // changeStatus — 상태 전이
    // =========================================================================

    @Nested
    @DisplayName("changeStatus — 상태 전이")
    class ChangeStatus {

        @Test
        @DisplayName("작성중(61)→진행중(62) 전이를 허용한다")
        void changeStatus_submitAllowed() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.changeStatus("CTR-2026-0001", new ContractDto.StatusRequest("75"), admin());

            // Assert
            assertThat(e.getStsTc()).isEqualTo("75");
        }

        @Test
        @DisplayName("진행중(62)→완료(69) 전이를 허용한다")
        void changeStatus_completeAllowed() {
            // Arrange
            Bcontm e = entityWith("75", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.changeStatus("CTR-2026-0001", new ContractDto.StatusRequest("79"), admin());

            // Assert
            assertThat(e.getStsTc()).isEqualTo("79");
        }

        @Test
        @DisplayName("EAI 발송 실패는 입찰계약 상태 전이를 막지 않는다")
        void changeStatus_eaiFailure_keepsMainWorkflow() {
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            when(eaiService.sendEai(any())).thenThrow(new IllegalStateException("EAI 장애"));

            service.changeStatus("CTR-2026-0001", new ContractDto.StatusRequest("75"), admin());

            assertThat(e.getStsTc()).isEqualTo("75");
            verify(bprojaSyncService).upsert("PRJ-1", "CTR-2026-0001", "75");
        }

        @Test
        @DisplayName("완료(69)에서 역행 전이(69→62)를 거부한다")
        void changeStatus_rejectsBackward() {
            // Arrange
            Bcontm e = entityWith("79", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "CTR-2026-0001",
                                            new ContractDto.StatusRequest("75"),
                                            admin()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("작성중(61)에서 완료(69)로의 비인접 전이를 거부한다")
        void changeStatus_rejectsNonAdjacentTransition() {
            // Arrange — 61→69 는 허용되지 않는 비인접 전이
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "CTR-2026-0001",
                                            new ContractDto.StatusRequest("79"),
                                            admin()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("허용되지 않은 상태 전이");
        }

        @Test
        @DisplayName("진행중(62)→작성중(61) 역행 전이를 거부한다")
        void changeStatus_rejectsReverseFromInProgress() {
            // Arrange
            Bcontm e = entityWith("75", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "CTR-2026-0001",
                                            new ContractDto.StatusRequest("71"),
                                            admin()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // saveContract — 계약 정보 입력
    // =========================================================================

    @Nested
    @DisplayName("saveContract — 계약 정보 입력")
    class SaveContract {

        @Test
        @DisplayName("진행중이 아닐 때(61) 계약 정보 입력을 거부한다")
        void saveContract_rejectsWhenNotInProgress() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.saveContract(
                                            "CTR-2026-0001",
                                            new ContractDto.WorkRequest(
                                                    "01",
                                                    "수의계약 사유",
                                                    "계약A",
                                                    new BigDecimal("1000"),
                                                    "상대처A",
                                                    "20260601"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("진행중");
        }

        @Test
        @DisplayName("완료(69) 상태에서 계약 정보 입력을 거부한다")
        void saveContract_rejectsWhenDone() {
            // Arrange
            Bcontm e = entityWith("79", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.saveContract(
                                            "CTR-2026-0001",
                                            new ContractDto.WorkRequest(
                                                    "02",
                                                    "사유",
                                                    "계약B",
                                                    BigDecimal.ZERO,
                                                    "상대처B",
                                                    "20260602"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("진행중(62)에서 계약 정보 입력 시 계약 필드가 반영된다")
        void saveContract_inProgress_appliesContractFields() {
            // Arrange
            Bcontm e = entityWith("75", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.saveContract(
                    "CTR-2026-0001",
                    new ContractDto.WorkRequest(
                            "01", "수의계약 사유", "계약A", new BigDecimal("1000"), "상대처A", "20260601"),
                    requester());

            // Assert
            assertThat(e.getCttNm()).isEqualTo("계약A");
            assertThat(e.getItPtlCttManrC()).isEqualTo("01");
            assertThat(e.getCttAmt()).isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(e.getCttOppNm()).isEqualTo("상대처A");
            assertThat(e.getCttDt()).isEqualTo("20260601");
            assertThat(e.getCttManrRsn()).isEqualTo("수의계약 사유");
        }
    }

    // =========================================================================
    // OwnershipTests — 쓰기 경로 소유권 검증
    // =========================================================================

    @Nested
    @DisplayName("OwnershipTests — 쓰기 경로 소유권 검증")
    class OwnershipTests {

        @Test
        @DisplayName("소유자가 아닌 사용자가 수정하면 AccessDeniedException이 발생한다")
        void update_rejectsNonOwner() {
            // Arrange — 소유자 E0001, 요청자 E0002(타인), 작성중(61)
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "CTR-2026-0001",
                                            new ContractDto.UpdateRequest("수정 시도"),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 삭제하면 AccessDeniedException이 발생한다")
        void delete_rejectsNonOwner() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("CTR-2026-0001", other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 상태 전이하면 AccessDeniedException이 발생한다")
        void changeStatus_rejectsNonOwner() {
            // Arrange
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "CTR-2026-0001",
                                            new ContractDto.StatusRequest("75"),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 계약 정보를 입력하면 AccessDeniedException이 발생한다")
        void saveContract_rejectsNonOwner() {
            // Arrange — 소유권 검증이 상태 검증보다 먼저이므로 작성중(61)이어도 소유권에서 거부
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.saveContract(
                                            "CTR-2026-0001",
                                            new ContractDto.WorkRequest(
                                                    "01",
                                                    "수의계약 사유",
                                                    "계약A",
                                                    new BigDecimal("1000"),
                                                    "상대처A",
                                                    "20260601"),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("관리자는 소유자가 아니어도 수정할 수 있다")
        void update_allowsAdmin() {
            // Arrange — 소유자 E0001, 요청자는 관리자(A0001/ITPAD001)
            Bcontm e = entityWith("71", "100", "PRJ-1");
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.update("CTR-2026-0001", new ContractDto.UpdateRequest("관리자 수정"), admin());

            // Assert
            assertThat(e.getReqCone()).isEqualTo("관리자 수정");
        }
    }

    // =========================================================================
    // get — 상세 조회 (스칼라 프로젝션 findCurrentDetail: CASE 모든 분기 포함)
    // =========================================================================

    @Nested
    @DisplayName("get — 상세 조회")
    class Get {

        @Test
        @DisplayName("사업(100) 대상 문서 조회 시 단일 쿼리로 사업명이 포함된 상세를 반환하고 대상별 추가 조회는 호출되지 않는다")
        void get_project_returnsDetailWithTargetName() {
            // Arrange — 스칼라 프로젝션(findCurrentDetail)이 상세 필드와 대상명을 함께 반환
            Bcontm e = entityWith("75", "100", "PRJ-1");
            ContractDetailRow row = detailRow(e, "클라우드 전환 사업");
            when(contractRepository.findCurrentDetail("CTR-2026-0001"))
                    .thenReturn(Optional.of(row));

            // Act
            ContractDto.Detail detail = service.get("CTR-2026-0001");

            // Assert
            assertThat(detail.docMngNo()).isEqualTo("CTR-2026-0001");
            assertThat(detail.tgtNm()).isEqualTo("클라우드 전환 사업");
            assertThat(detail.ioeC()).isEqualTo("100");
            // 단일 쿼리로 통합되어 마스터 조회·대상별 조회가 더 이상 호출되지 않음
            verify(contractRepository).findCurrentDetail("CTR-2026-0001");
            verify(contractRepository, never())
                    .findByDocMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
            verify(projectRepository, never())
                    .findByAbusMngNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
            verify(costRepository, never())
                    .findByCostBgNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("사업 대상 이름 조회 결과가 없을 때 tgtNm은 null이다")
        void get_project_returnsNullTargetNameWhenAbsent() {
            // Arrange — LEFT JOIN 미매칭이면 대상명 null
            Bcontm e = entityWith("75", "100", "PRJ-NONE");
            when(contractRepository.findCurrentDetail("CTR-2026-0001"))
                    .thenReturn(Optional.of(detailRow(e, null)));

            // Act
            ContractDto.Detail detail = service.get("CTR-2026-0001");

            // Assert
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("전산업무비(200) 대상 문서 조회 시 단일 쿼리로 계약명이 tgtNm으로 반환된다")
        void get_cost_returnsDetailWithTargetName() {
            // Arrange
            Bcontm e = entityWith("71", "200", "BG-1");
            when(contractRepository.findCurrentDetail("CTR-2026-0001"))
                    .thenReturn(Optional.of(detailRow(e, "서버 유지보수")));

            // Act
            ContractDto.Detail detail = service.get("CTR-2026-0001");

            // Assert
            assertThat(detail.tgtNm()).isEqualTo("서버 유지보수");
            assertThat(detail.ioeC()).isEqualTo("200");
            verify(costRepository, never())
                    .findByCostBgNoAndLstYnAndDelYn(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("전산업무비 대상 이름 조회 결과가 없을 때 tgtNm은 null이다")
        void get_cost_returnsNullTargetNameWhenAbsent() {
            // Arrange — LEFT JOIN 미매칭이면 대상명 null
            Bcontm e = entityWith("71", "200", "BG-NONE");
            when(contractRepository.findCurrentDetail("CTR-2026-0001"))
                    .thenReturn(Optional.of(detailRow(e, null)));

            // Act
            ContractDto.Detail detail = service.get("CTR-2026-0001");

            // Assert
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("알 수 없는 대상구분(999)인 문서 조회 시 tgtNm은 null을 반환한다")
        void get_unknownIoeC_returnsNullTargetName() {
            // Arrange — CASE otherwise(null) 분기
            Bcontm e = entityWith("71", "999", "ANY-1");
            when(contractRepository.findCurrentDetail("CTR-2026-0001"))
                    .thenReturn(Optional.of(detailRow(e, null)));

            // Act
            ContractDto.Detail detail = service.get("CTR-2026-0001");

            // Assert
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("문서가 없으면 조회 시 IllegalArgumentException이 발생한다")
        void get_notFound_throwsIllegalArgumentException() {
            // Arrange — 단일 쿼리 → Optional.empty() → throws
            when(contractRepository.findCurrentDetail("CTR-NONE")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.get("CTR-NONE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("입찰계약 문서를 찾을 수 없습니다");
        }
    }

    // =========================================================================
    // list — 목록 검색
    // =========================================================================

    @Nested
    @DisplayName("list — 목록 검색")
    class ListSearch {

        @Test
        @DisplayName("관리자는 부서코드 null로 전체 목록을 조회한다")
        void list_admin_usesBbrCNull() {
            // Arrange — 관리자: isAdmin() == true → bbrC=null
            when(contractRepository.search(null, null, null, null)).thenReturn(List.of());

            // Act
            List<ContractDto.ListItem> result = service.list(null, null, null, admin());

            // Assert
            assertThat(result).isEmpty();
            verify(contractRepository).search(null, null, null, null);
        }

        @Test
        @DisplayName("일반 사용자는 소속 부서코드로 목록을 조회한다")
        void list_normalUser_usesBbrCFromToken() {
            // Arrange — 일반 사용자의 bbrC="18001"
            ContractDto.ListItem item =
                    new ContractDto.ListItem(
                            "CTR-2026-0001", 1, "100", "PRJ-1", "71", null, null, "E0001", null);
            when(contractRepository.search("71", "100", null, "18001")).thenReturn(List.of(item));

            // Act
            List<ContractDto.ListItem> result = service.list("71", "100", null, requester());

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).docMngNo()).isEqualTo("CTR-2026-0001");
            verify(contractRepository).search("71", "100", null, "18001");
        }

        @Test
        @DisplayName("필터 파라미터(stsTc, ioeC, cncdRfrNo)를 모두 전달하면 그대로 search에 넘긴다")
        void list_allFilters_passedToRepository() {
            // Arrange
            when(contractRepository.search("75", "200", "BG-1", "18001")).thenReturn(List.of());

            // Act
            service.list("75", "200", "BG-1", requester());

            // Assert
            verify(contractRepository).search("75", "200", "BG-1", "18001");
        }
    }

    // =========================================================================
    // loadCurrent — 문서 미존재 시 예외 발생 (공개 메서드 경유)
    // =========================================================================

    @Nested
    @DisplayName("loadCurrent — 문서 미존재 시 예외 발생")
    class LoadCurrent {

        @Test
        @DisplayName("update 호출 시 문서가 없으면 IllegalArgumentException이 발생한다")
        void loadCurrent_update_notFound_throws() {
            // Arrange
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-9999", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "CTR-9999",
                                            new ContractDto.UpdateRequest("내용"),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("입찰계약 문서를 찾을 수 없습니다: CTR-9999");
        }

        @Test
        @DisplayName("delete 호출 시 문서가 없으면 IllegalArgumentException이 발생한다")
        void loadCurrent_delete_notFound_throws() {
            // Arrange
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-8888", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.delete("CTR-8888", requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("입찰계약 문서를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("changeStatus 호출 시 문서가 없으면 IllegalArgumentException이 발생한다")
        void loadCurrent_changeStatus_notFound_throws() {
            // Arrange
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-7777", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "CTR-7777",
                                            new ContractDto.StatusRequest("75"),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("saveContract 호출 시 문서가 없으면 IllegalArgumentException이 발생한다")
        void loadCurrent_saveContract_notFound_throws() {
            // Arrange
            when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-6666", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(
                            () ->
                                    service.saveContract(
                                            "CTR-6666",
                                            new ContractDto.WorkRequest(
                                                    null, null, null, null, null, null),
                                            requester()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
