package com.kdb.it.domain.estimate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.entity.Besttm;
import com.kdb.it.domain.estimate.repository.EstimateLineRepository;
import com.kdb.it.domain.estimate.repository.EstimateRepository;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.service.EaiService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
class EstimateServiceTest {

    @Mock EstimateRepository estimateRepository;
    @Mock EstimateLineRepository lineRepository;
    @Mock ProjectRepository projectRepository;
    @Mock BprojaSyncService bprojaSyncService;
    @Mock EaiService eaiService;

    EstimateService service;

    /** 일반 사용자 (18001 부서) */
    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    /** 관리자 사용자 */
    CustomUserDetails admin() {
        return new CustomUserDetails("E0099", List.of("ITPAD001"), "18001");
    }

    @BeforeEach
    void setUp() {
        service =
                new EstimateService(
                        estimateRepository,
                        lineRepository,
                        projectRepository,
                        bprojaSyncService,
                        eaiService,
                        new GweProperties("TEST00000001"));
    }

    /** 타인 (소유자가 아닌 일반 사용자) */
    CustomUserDetails other() {
        return new CustomUserDetails("E0002", List.of("ITPZZ001"), "18001");
    }

    @Nested
    @DisplayName("소유권 검증 — 타인 차단")
    class OwnershipTests {

        private Bestim draftOwnedByE0001() {
            return Bestim.builder()
                    .rqmBgReqDocNo("REQ-2026-0001")
                    .docVrsSno(1)
                    .lstYn("Y")
                    .cncdRfrNo("PRJ-2026-0001")
                    .stsTc("51")
                    .reqCone("내용")
                    .fstEnrUsid("E0001")
                    .build();
        }

        @Test
        @DisplayName("타인이 수정하면 AccessDeniedException")
        void update_deniedForOther() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "REQ-2026-0001",
                                            new EstimateDto.UpdateRequest("x"),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("관리자는 타인 문서도 수정 가능")
        void update_allowedForAdmin() {
            Bestim e = draftOwnedByE0001();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            service.update("REQ-2026-0001", new EstimateDto.UpdateRequest("수정"), admin());
            assertThat(e.getReqCone()).isEqualTo("수정");
        }

        @Test
        @DisplayName("타인이 삭제하면 AccessDeniedException")
        void delete_deniedForOther() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));
            assertThatThrownBy(() -> service.delete("REQ-2026-0001", other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("타인이 상태전이하면 AccessDeniedException")
        void changeStatus_deniedForOther() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "REQ-2026-0001",
                                            new EstimateDto.StatusRequest("55"),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("소유자라도 ADMIN이 아니면 상태전이 거부")
        void changeStatus_deniedForNonAdminOwner() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));
            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            "REQ-2026-0001",
                                            new EstimateDto.StatusRequest("55"),
                                            requester()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("타인이 명세저장하면 AccessDeniedException")
        void saveLines_deniedForOther() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));
            assertThatThrownBy(
                            () ->
                                    service.saveLines(
                                            "REQ-2026-0001",
                                            new EstimateDto.LinesRequest(List.of()),
                                            other()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // =========================================================================
    // create — 신규 신청 생성
    // =========================================================================

    @Test
    @DisplayName("신규 신청 생성 시 문서번호를 채번하고 작성중 상태로 저장한다")
    void create_assignsDocNoAndStatus41() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .thenReturn(true);
        when(estimateRepository.existsByCncdRfrNoAndStsTcInAndDelYn(
                        anyString(), any(), anyString()))
                .thenReturn(false);
        when(estimateRepository.nextDocSeq()).thenReturn(1L);
        when(estimateRepository.save(any(Bestim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo =
                service.create(
                        new EstimateDto.CreateRequest("PRJ-2026-0001", "요청합니다"), requester());

        assertThat(docNo).matches("REQ-\\d{4}-0001");
    }

    @Test
    @DisplayName("대상 사업이 없으면 신규 신청을 거부한다")
    void create_rejectsWhenTargetMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N"))
                .thenReturn(false);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new EstimateDto.CreateRequest("PRJ-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("동일 대상에 진행중 문서가 있으면 신규 신청을 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .thenReturn(true);
        when(estimateRepository.existsByCncdRfrNoAndStsTcInAndDelYn(
                        anyString(), any(), anyString()))
                .thenReturn(true);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new EstimateDto.CreateRequest("PRJ-2026-0001", null),
                                        requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("시퀀스 번호 99번이면 문서번호를 4자리 포맷으로 채번한다")
    void create_formatsSeqWith4Digits() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .thenReturn(true);
        when(estimateRepository.existsByCncdRfrNoAndStsTcInAndDelYn(
                        anyString(), any(), anyString()))
                .thenReturn(false);
        when(estimateRepository.nextDocSeq()).thenReturn(99L);
        when(estimateRepository.save(any(Bestim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo =
                service.create(new EstimateDto.CreateRequest("PRJ-2026-0001", "요청"), requester());

        assertThat(docNo).endsWith("-0099");
    }

    // =========================================================================
    // update — 마스터 수정
    // =========================================================================

    @Nested
    @DisplayName("update — 마스터 수정")
    class UpdateTests {

        @Test
        @DisplayName("작성중(41) 상태에서 요청내용을 수정할 수 있다")
        void update_succeedsWhenDraft() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("51")
                            .reqCone("기존 내용")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.update("REQ-2026-0001", new EstimateDto.UpdateRequest("수정된 내용"), requester());

            // Assert
            assertThat(e.getReqCone()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("작성중이 아닐 때(42) 마스터 수정을 거부한다")
        void update_rejectsWhenNotDraft() {
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "REQ-2026-0001",
                                            new EstimateDto.UpdateRequest("x"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("완료(49) 상태에서도 수정을 거부한다")
        void update_rejectsWhenDone() {
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("59")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            assertThatThrownBy(
                            () ->
                                    service.update(
                                            "REQ-2026-0001",
                                            new EstimateDto.UpdateRequest("x"),
                                            requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // delete — Soft Delete
    // =========================================================================

    @Nested
    @DisplayName("delete — Soft Delete")
    class DeleteTests {

        @Test
        @DisplayName("작성중(41) 상태에서 삭제 시 delYn이 Y가 된다")
        void delete_succeedsWhenDraft() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("51")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.delete("REQ-2026-0001", requester());

            // Assert
            assertThat(e.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("진행중(42) 상태에서는 삭제를 거부한다")
        void delete_rejectsWhenInProgress() {
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            assertThatThrownBy(() -> service.delete("REQ-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("작성중");
        }

        @Test
        @DisplayName("완료(49) 상태에서도 삭제를 거부한다")
        void delete_rejectsWhenDone() {
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("59")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            assertThatThrownBy(() -> service.delete("REQ-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // changeStatus — 상태 전이
    // =========================================================================

    @Test
    @DisplayName("작성중(41)→진행중(42) 제출 전이를 허용한다")
    void changeStatus_submitAllowed() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("51")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        service.changeStatus("REQ-2026-0001", new EstimateDto.StatusRequest("55"), admin());
        assertThat(e.getStsTc()).isEqualTo("55");
    }

    @Test
    @DisplayName("진행중(42)→완료(49) 완료 전이를 허용한다")
    void changeStatus_completeAllowed() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("55")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        service.changeStatus("REQ-2026-0001", new EstimateDto.StatusRequest("59"), admin());
        assertThat(e.getStsTc()).isEqualTo("59");
    }

    @Test
    @DisplayName("EAI 발송이 실패해도 상태 전이와 사업 진행 동기화는 유지한다")
    void changeStatus_eaiFailure_keepsMainWorkflow() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("51")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        when(eaiService.sendEai(any())).thenThrow(new IllegalStateException("EAI 장애"));

        service.changeStatus("REQ-2026-0001", new EstimateDto.StatusRequest("55"), admin());

        assertThat(e.getStsTc()).isEqualTo("55");
        verify(bprojaSyncService).upsert("PRJ-2026-0001", "REQ-2026-0001", "55");
    }

    @Test
    @DisplayName("완료(49)에서 역행 전이를 거부한다")
    void changeStatus_rejectsBackward() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("59")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        "REQ-2026-0001",
                                        new EstimateDto.StatusRequest("55"),
                                        admin()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중(41)→완료(49) 건너뛰기 전이를 거부한다")
    void changeStatus_rejectsSkipTransition() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("51")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        "REQ-2026-0001",
                                        new EstimateDto.StatusRequest("59"),
                                        admin()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은");
    }

    @Test
    @DisplayName("진행중(42)에서 작성중(41) 역행을 거부한다")
    void changeStatus_rejectsReverseFromInProgress() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("55")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        "REQ-2026-0001",
                                        new EstimateDto.StatusRequest("51"),
                                        admin()))
                .isInstanceOf(IllegalStateException.class);
    }

    // =========================================================================
    // get — 상세 조회
    // =========================================================================

    @Nested
    @DisplayName("get — 상세 조회")
    class GetTests {

        @Test
        @DisplayName("명세 행이 있고 사업명도 조회될 때 상세 DTO를 정상 반환한다")
        void get_returnsDetailWithLinesAndAbusNm() {
            // Arrange: 마스터
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .reqCone("요청내용")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // 명세 행 1건
            Besttm line =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .svnTemC("18010")
                            .ioeC("HW")
                            .rqmBgAmt(new BigDecimal("500"))
                            .opnnCone("HW 산정")
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoAndDelYn("REQ-2026-0001", 1, "N"))
                    .thenReturn(List.of(line));

            // 사업명 조회 성공
            ProjectRepository.ProjectNameView proj =
                    org.mockito.Mockito.mock(ProjectRepository.ProjectNameView.class);
            when(proj.getAbusNm()).thenReturn("사업명");
            when(projectRepository.findNameViewByAbusMngNoAndLstYnAndDelYn(
                            "PRJ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(proj));

            // Act
            EstimateDto.Detail detail = service.get("REQ-2026-0001");

            // Assert
            assertThat(detail.rqmBgReqDocNo()).isEqualTo("REQ-2026-0001");
            assertThat(detail.stsTc()).isEqualTo("55");
            assertThat(detail.lines()).hasSize(1);
            assertThat(detail.lines().get(0).svnTemC()).isEqualTo("18010");
            assertThat(detail.lines().get(0).rqmBgAmt())
                    .isEqualByComparingTo(new BigDecimal("500"));
        }

        @Test
        @DisplayName("사업이 삭제되어 projectRepository가 empty를 반환하면 abusNm이 null이다")
        void get_returnsDetailWithNullAbusNmWhenProjectMissing() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("51")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoAndDelYn("REQ-2026-0001", 1, "N"))
                    .thenReturn(List.of());
            when(projectRepository.findNameViewByAbusMngNoAndLstYnAndDelYn(
                            "PRJ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act
            EstimateDto.Detail detail = service.get("REQ-2026-0001");

            // Assert: abusNm=null (Optional.empty()의 orElse(null) 분기)
            assertThat(detail.abusNm()).isNull();
            assertThat(detail.lines()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 문서번호로 조회 시 IllegalArgumentException을 던진다")
        void get_throwsWhenDocNotFound() {
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("NOT-EXISTS", "Y", "N"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get("NOT-EXISTS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("소요예산");
        }
    }

    // =========================================================================
    // list — 목록 조회
    // =========================================================================

    @Nested
    @DisplayName("list — 목록 조회")
    class ListTests {

        @Test
        @DisplayName("관리자는 bbrC=null로 전체 목록을 조회한다")
        void list_adminGetsAllWithNullBbrC() {
            // Arrange
            List<EstimateDto.ListItem> mockResult =
                    List.of(
                            new EstimateDto.ListItem(
                                    "REQ-2026-0001",
                                    1,
                                    "100",
                                    "PRJ-2026-0001",
                                    "테스트사업",
                                    new BigDecimal("1000"),
                                    LocalDate.of(2026, 1, 1),
                                    LocalDate.of(2026, 12, 31),
                                    "18001",
                                    "IT기획부",
                                    "51",
                                    "E0001",
                                    null),
                            new EstimateDto.ListItem(
                                    "REQ-2026-0002",
                                    1,
                                    "100",
                                    "PRJ-2026-0002",
                                    "다른사업",
                                    new BigDecimal("2000"),
                                    LocalDate.of(2026, 2, 1),
                                    LocalDate.of(2026, 11, 30),
                                    "18002",
                                    "디지털부",
                                    "55",
                                    "E0002",
                                    null));
            when(estimateRepository.search(null, null, null)).thenReturn(mockResult);

            // Act
            List<EstimateDto.ListItem> result = service.list(null, null, admin());

            // Assert: 관리자 → bbrC=null → search(null, null, null) 호출
            assertThat(result).hasSize(2);
            verify(estimateRepository).search(null, null, null);
        }

        @Test
        @DisplayName("일반 사용자는 소속 부서코드(bbrC)로 필터링하여 목록을 조회한다")
        void list_nonAdminFiltersByBbrC() {
            // Arrange
            List<EstimateDto.ListItem> mockResult =
                    List.of(
                            new EstimateDto.ListItem(
                                    "REQ-2026-0001",
                                    1,
                                    "100",
                                    "PRJ-2026-0001",
                                    "테스트사업",
                                    new BigDecimal("1000"),
                                    LocalDate.of(2026, 1, 1),
                                    LocalDate.of(2026, 12, 31),
                                    "18001",
                                    "IT기획부",
                                    "51",
                                    "E0001",
                                    null));
            when(estimateRepository.search("51", "PRJ-2026-0001", "18001")).thenReturn(mockResult);

            // Act
            List<EstimateDto.ListItem> result = service.list("51", "PRJ-2026-0001", requester());

            // Assert: bbrC="18001" 로 제한 조회
            assertThat(result).hasSize(1);
            verify(estimateRepository).search("51", "PRJ-2026-0001", "18001");
        }

        @Test
        @DisplayName("일반 사용자는 필터가 null이어도 bbrC로 범위를 제한한다")
        void list_nonAdminWithNullFiltersStillUsesBbrC() {
            when(estimateRepository.search(null, null, "18001")).thenReturn(List.of());

            service.list(null, null, requester());

            verify(estimateRepository).search(null, null, "18001");
        }
    }

    // =========================================================================
    // saveLines — 팀별 산정 명세 일괄 저장
    // =========================================================================

    @Test
    @DisplayName("진행중(42)에서 팀별 산정행 저장 — 신규행 추가, 요청에 없는 기존행 soft delete")
    void saveLines_upsertAndSoftDeleteMissing() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("55")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        Besttm existing =
                Besttm.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .svnTemC("12004")
                        .ioeC("DEV")
                        .rqmBgAmt(new BigDecimal("100"))
                        .delYn("N")
                        .build();
        when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                .thenReturn(new ArrayList<>(List.of(existing)));
        var lines =
                List.of(new EstimateDto.LineRequest("18010", "HW", new BigDecimal("200"), "HW 산정"));
        service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(lines), requester());
        assertThat(existing.getDelYn()).isEqualTo("Y");
        verify(lineRepository).save(any(Besttm.class));
    }

    @Test
    @DisplayName("진행중(42)에서 soft-deleted 행과 동일 키 재추가 시 새로 insert하지 않고 기존 행을 복원·갱신한다")
    void saveLines_revivesSoftDeletedRowOnReAdd() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("55")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        // 이미 soft-delete된 행 (delYn='Y') — 동일 PK가 물리적으로 존재
        Besttm deleted =
                Besttm.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .svnTemC("12004")
                        .ioeC("DEV")
                        .rqmBgAmt(new BigDecimal("100"))
                        .delYn("Y")
                        .build();
        when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                .thenReturn(new ArrayList<>(List.of(deleted)));
        var lines =
                List.of(new EstimateDto.LineRequest("12004", "DEV", new BigDecimal("300"), "재산정"));
        service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(lines), requester());
        assertThat(deleted.getDelYn()).isEqualTo("N");
        assertThat(deleted.getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("300"));
        verify(lineRepository, never()).save(any(Besttm.class));
    }

    @Test
    @DisplayName("진행중이 아닐 때 명세 저장을 거부한다")
    void saveLines_rejectsWhenNotInProgress() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("51")
                        .fstEnrUsid("E0001")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));
        assertThatThrownBy(
                        () ->
                                service.saveLines(
                                        "REQ-2026-0001",
                                        new EstimateDto.LinesRequest(List.of()),
                                        requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Nested
    @DisplayName("saveLines — 추가 브랜치 커버리지")
    class SaveLinesExtraTests {

        @Test
        @DisplayName("기존 활성 행과 동일 키 요청 시 갱신만 하고 INSERT하지 않는다")
        void saveLines_updatesExistingActiveRow() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // 기존 활성 행 (delYn='N') — 요청과 동일한 (svnTemC, ioeC)
            Besttm active =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .svnTemC("18010")
                            .ioeC("HW")
                            .rqmBgAmt(new BigDecimal("100"))
                            .delYn("N")
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(active)));

            // 동일 키로 금액/의견 변경
            var lines =
                    List.of(
                            new EstimateDto.LineRequest(
                                    "18010", "HW", new BigDecimal("999"), "변경된 의견"));

            // Act
            service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(lines), requester());

            // Assert: 갱신, delYn 'N' 유지, 신규 save 없음
            assertThat(active.getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("999"));
            assertThat(active.getOpnnCone()).isEqualTo("변경된 의견");
            assertThat(active.getDelYn()).isEqualTo("N");
            verify(lineRepository, never()).save(any(Besttm.class));
        }

        @Test
        @DisplayName("이미 삭제된 행이 요청에 없으면 그대로 둔다 (delYn 'Y' 유지)")
        void saveLines_doesNotTouchAlreadyDeletedRowsNotInRequest() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // 이미 삭제된 행 — 요청에 포함되지 않음
            Besttm alreadyDeleted =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .svnTemC("12004")
                            .ioeC("DEV")
                            .rqmBgAmt(new BigDecimal("100"))
                            .delYn("Y")
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(alreadyDeleted)));

            when(lineRepository.save(any(Besttm.class))).thenAnswer(inv -> inv.getArgument(0));
            var lines =
                    List.of(
                            new EstimateDto.LineRequest(
                                    "18010", "HW", new BigDecimal("200"), "HW"));

            // Act
            service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(lines), requester());

            // Assert: 이미 삭제된 행 delYn 'Y' 그대로 유지
            assertThat(alreadyDeleted.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("요청 lines가 빈 리스트이면 모든 활성 행을 soft delete한다")
        void saveLines_emptyRequestSoftDeletesAllActiveRows() {
            // Arrange
            Bestim e =
                    Bestim.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .lstYn("Y")
                            .cncdRfrNo("PRJ-2026-0001")
                            .stsTc("55")
                            .fstEnrUsid("E0001")
                            .build();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            Besttm row1 =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .svnTemC("18010")
                            .ioeC("HW")
                            .rqmBgAmt(new BigDecimal("200"))
                            .delYn("N")
                            .build();
            Besttm row2 =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .svnTemC("12004")
                            .ioeC("DEV")
                            .rqmBgAmt(new BigDecimal("300"))
                            .delYn("N")
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(row1, row2)));

            // Act: 빈 lines
            service.saveLines(
                    "REQ-2026-0001", new EstimateDto.LinesRequest(List.of()), requester());

            // Assert: 모든 활성 행 삭제
            assertThat(row1.getDelYn()).isEqualTo("Y");
            assertThat(row2.getDelYn()).isEqualTo("Y");
            verify(lineRepository, never()).save(any(Besttm.class));
        }
    }

    @Nested
    @DisplayName("명세 저장 — 개선의견일련번호 채번")
    class SaveLinesSnoTests {

        private Bestim inProgress() {
            return Bestim.builder()
                    .rqmBgReqDocNo("REQ-2026-0001")
                    .docVrsSno(1)
                    .lstYn("Y")
                    .cncdRfrNo("PRJ-001")
                    .stsTc("55")
                    .build();
        }

        @Test
        @DisplayName("빈 문서에 2행을 저장하면 일련번호 1, 2가 부여된다")
        void assignsSequentialSnoForNewRows() {
            Bestim master = inProgress();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>());

            List<Besttm> saved = new ArrayList<>();
            when(lineRepository.save(any(Besttm.class)))
                    .thenAnswer(
                            invocation -> {
                                Besttm row = invocation.getArgument(0);
                                saved.add(row);
                                return row;
                            });

            service.saveLines(
                    "REQ-2026-0001",
                    new EstimateDto.LinesRequest(
                            List.of(
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("100"), "의견1"),
                                    new EstimateDto.LineRequest(
                                            "T002", "1020", new BigDecimal("200"), "의견2"))),
                    admin());

            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(Besttm::getIpmOpnnSno).containsExactly(1, 2);
        }

        @Test
        @DisplayName("기존 행이 있으면 최대 일련번호 다음 번호를 이어서 부여한다")
        void continuesFromExistingMaxSno() {
            Bestim master = inProgress();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            Besttm existing =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .ipmOpnnSno(7)
                            .svnTemC("T001")
                            .ioeC("1010")
                            .rqmBgAmt(new BigDecimal("100"))
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(existing)));

            List<Besttm> saved = new ArrayList<>();
            when(lineRepository.save(any(Besttm.class)))
                    .thenAnswer(
                            invocation -> {
                                Besttm row = invocation.getArgument(0);
                                saved.add(row);
                                return row;
                            });

            service.saveLines(
                    "REQ-2026-0001",
                    new EstimateDto.LinesRequest(
                            List.of(
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("150"), "수정"),
                                    new EstimateDto.LineRequest(
                                            "T003", "1030", new BigDecimal("300"), "신규"))),
                    admin());

            // 기존 (T001,1010)은 갱신되므로 save 호출 없음. 신규 1건만 8번으로 채번된다.
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getIpmOpnnSno()).isEqualTo(8);
            assertThat(saved.get(0).getSvnTemC()).isEqualTo("T003");
            assertThat(existing.getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("150"));
        }
    }

    @Nested
    @DisplayName("saveLines — DB에 이미 (팀+비목) 중복 행이 존재하는 혼합 사례")
    class ExistingDuplicateKeyInDatabaseTests {

        private Bestim inProgress() {
            return Bestim.builder()
                    .rqmBgReqDocNo("REQ-2026-0001")
                    .docVrsSno(1)
                    .lstYn("Y")
                    .cncdRfrNo("PRJ-001")
                    .stsTc("55")
                    .build();
        }

        @Test
        @DisplayName(
                "DB에 동일 (팀+비목) 물리행이 이미 2건 있고 요청에도 같은 키가 2번 오면 예외 없이 "
                        + "낮은 일련번호 행이 마지막 요청값으로 갱신된다")
        void existingDuplicateKeyAndRequestDuplicate_mergesWithoutException() {
            // Arrange — 운영 3컬럼 PK(문서번호+버전+개선의견일련번호)는 동일 (팀+비목) 쌍을 가진
            // 물리 행이 2건 이상 존재하는 것을 막지 않는다. 이 상태에서 재저장을 시도하는 시나리오.
            Bestim master = inProgress();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            Besttm existingLow =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .ipmOpnnSno(1)
                            .svnTemC("T001")
                            .ioeC("1010")
                            .rqmBgAmt(new BigDecimal("100"))
                            .delYn("N")
                            .build();
            Besttm existingHigh =
                    Besttm.builder()
                            .rqmBgReqDocNo("REQ-2026-0001")
                            .docVrsSno(1)
                            .ipmOpnnSno(2)
                            .svnTemC("T001")
                            .ioeC("1010")
                            .rqmBgAmt(new BigDecimal("999"))
                            .delYn("N")
                            .build();
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(existingLow, existingHigh)));

            // Act — 동일 (T001, 1010) 키를 값만 다르게 요청에 두 번 포함
            service.saveLines(
                    "REQ-2026-0001",
                    new EstimateDto.LinesRequest(
                            List.of(
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("300"), "첫번째"),
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("777"), "두번째"))),
                    admin());

            // Assert — 예외 없이 저장되고, 색인에서 살아남은 낮은 일련번호(1) 행이 최종 요청값으로 갱신된다.
            assertThat(existingLow.getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("777"));
            assertThat(existingLow.getOpnnCone()).isEqualTo("두번째");
            // 색인에서 밀린 중복 행(2번)은 이번 저장에서 건드리지 않는다 — 요청에 같은 키가 있어
            // soft-delete 대상도 아니고, byKey에서 밀려 갱신 대상도 아니다(알려진 잔여 상태).
            assertThat(existingHigh.getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("999"));
            assertThat(existingHigh.getDelYn()).isEqualTo("N");
            verify(lineRepository, never()).save(any(Besttm.class));
        }
    }

    @Nested
    @DisplayName("saveLines — 요청 내 (팀+비목) 중복 처리")
    class DuplicateKeyWithinRequestTests {

        private Bestim inProgress() {
            return Bestim.builder()
                    .rqmBgReqDocNo("REQ-2026-0001")
                    .docVrsSno(1)
                    .lstYn("Y")
                    .cncdRfrNo("PRJ-001")
                    .stsTc("55")
                    .build();
        }

        @Test
        @DisplayName("한 요청에 동일한 (팀+비목)이 두 번 오면 한 행만 남고 마지막 값이 반영된다")
        void duplicateKeyInSingleRequest_resolvesToOneRowWithLastValueWinning() {
            Bestim master = inProgress();
            when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc("REQ-2026-0001", 1))
                    .thenReturn(new ArrayList<>());

            List<Besttm> saved = new ArrayList<>();
            when(lineRepository.save(any(Besttm.class)))
                    .thenAnswer(
                            invocation -> {
                                Besttm row = invocation.getArgument(0);
                                saved.add(row);
                                return row;
                            });

            // 동일 (T001, 1010) 키를 금액·의견만 다르게 두 번 요청에 포함
            service.saveLines(
                    "REQ-2026-0001",
                    new EstimateDto.LinesRequest(
                            List.of(
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("100"), "첫번째"),
                                    new EstimateDto.LineRequest(
                                            "T001", "1010", new BigDecimal("250"), "두번째"))),
                    admin());

            // Assert: 물리 행은 1건만 생성되고, 요청에서 나중에 온 값이 최종 반영된다
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getRqmBgAmt()).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(saved.get(0).getOpnnCone()).isEqualTo("두번째");
            assertThat(saved.get(0).getIpmOpnnSno()).isEqualTo(1);
            verify(lineRepository, times(1)).save(any(Besttm.class));
        }
    }

    // =========================================================================
    // loadCurrent — 내부 헬퍼 (package-private 직접 테스트)
    // =========================================================================

    @Test
    @DisplayName("loadCurrent: 문서가 없으면 IllegalArgumentException을 던진다")
    void loadCurrent_throwsWhenNotFound() {
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("MISSING", "Y", "N"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadCurrent("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    @DisplayName("loadCurrent: 문서가 있으면 엔티티를 반환한다")
    void loadCurrent_returnsEntityWhenFound() {
        Bestim e =
                Bestim.builder()
                        .rqmBgReqDocNo("REQ-2026-0001")
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-2026-0001")
                        .stsTc("51")
                        .build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        Bestim result = service.loadCurrent("REQ-2026-0001");

        assertThat(result.getRqmBgReqDocNo()).isEqualTo("REQ-2026-0001");
    }
}
