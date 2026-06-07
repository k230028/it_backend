package com.kdb.it.domain.estimate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Bestid;
import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.repository.EstimateLineRepository;
import com.kdb.it.domain.estimate.repository.EstimateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstimateServiceTest {

    @Mock EstimateRepository estimateRepository;
    @Mock EstimateLineRepository lineRepository;
    @Mock ProjectRepository projectRepository;

    EstimateService service;

    CustomUserDetails requester() { return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001"); }

    @BeforeEach
    void setUp() {
        service = new EstimateService(estimateRepository, lineRepository, projectRepository);
    }

    @Test
    @DisplayName("신규 신청 생성 시 문서번호를 채번하고 상태 41, 대상구분 100으로 저장한다")
    void create_assignsDocNoAndStatus41() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N")).thenReturn(true);
        when(estimateRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(estimateRepository.nextDocSeq()).thenReturn(1L);
        when(estimateRepository.save(any(Bestim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(new EstimateDto.CreateRequest("PRJ-2026-0001", "요청합니다"), requester());

        assertThat(docNo).isEqualTo("REQ-2026-0001");
    }

    @Test
    @DisplayName("대상 사업이 없으면 신규 신청을 거부한다")
    void create_rejectsWhenTargetMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new EstimateDto.CreateRequest("PRJ-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("대상");
    }

    @Test
    @DisplayName("동일 대상에 진행중 문서가 있으면 신규 신청을 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N")).thenReturn(true);
        when(estimateRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(anyString(), anyString(), any(), anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new EstimateDto.CreateRequest("PRJ-2026-0001", null), requester()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("작성중(41)→진행중(42) 제출 전이를 허용한다")
    void changeStatus_submitAllowed() {
        Bestim e = Bestim.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .lstYn("Y").bgPrnTc("100").cncdRfrNo("PRJ-2026-0001").stsTc("41").build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N")).thenReturn(Optional.of(e));
        service.changeStatus("REQ-2026-0001", new EstimateDto.StatusRequest("42"), requester());
        assertThat(e.getStsTc()).isEqualTo("42");
    }

    @Test
    @DisplayName("완료(49)에서 역행 전이를 거부한다")
    void changeStatus_rejectsBackward() {
        Bestim e = Bestim.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .lstYn("Y").bgPrnTc("100").cncdRfrNo("PRJ-2026-0001").stsTc("49").build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N")).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.changeStatus("REQ-2026-0001", new EstimateDto.StatusRequest("42"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중이 아닐 때 마스터 수정을 거부한다")
    void update_rejectsWhenNotDraft() {
        Bestim e = Bestim.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .lstYn("Y").bgPrnTc("100").cncdRfrNo("PRJ-2026-0001").stsTc("42").build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N")).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.update("REQ-2026-0001", new EstimateDto.UpdateRequest("x"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(42)에서 팀별 산정행 저장 — 신규행 추가, 요청에 없는 기존행 soft delete")
    void saveLines_upsertAndSoftDeleteMissing() {
        Bestim e = Bestim.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .lstYn("Y").bgPrnTc("100").cncdRfrNo("PRJ-2026-0001").stsTc("42").build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N")).thenReturn(Optional.of(e));
        Bestid existing = Bestid.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .svnTemC("12004").ioeC("DEV").rqmBgAmt(new java.math.BigDecimal("100")).build();
        when(lineRepository.findByRqmBgReqDocNoAndDocVrsSnoAndDelYn("REQ-2026-0001", 1, "N"))
                .thenReturn(new java.util.ArrayList<>(List.of(existing)));
        var lines = List.of(new EstimateDto.LineRequest("18010", "HW", new java.math.BigDecimal("200"), "HW 산정"));
        service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(lines), requester());
        assertThat(existing.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("진행중이 아닐 때 명세 저장을 거부한다")
    void saveLines_rejectsWhenNotInProgress() {
        Bestim e = Bestim.builder().rqmBgReqDocNo("REQ-2026-0001").docVrsSno(1)
                .lstYn("Y").bgPrnTc("100").cncdRfrNo("PRJ-2026-0001").stsTc("41").build();
        when(estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn("REQ-2026-0001", "Y", "N")).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.saveLines("REQ-2026-0001", new EstimateDto.LinesRequest(List.of()), requester()))
                .isInstanceOf(IllegalStateException.class);
    }
}
