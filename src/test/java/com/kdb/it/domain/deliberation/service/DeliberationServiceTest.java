package com.kdb.it.domain.deliberation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.repository.DeliberationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliberationServiceTest {

    @Mock DeliberationRepository deliberationRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;

    DeliberationService service;

    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    @BeforeEach
    void setUp() {
        service = new DeliberationService(deliberationRepository, projectRepository, costRepository);
    }

    @Test
    @DisplayName("사업 대상 신규 신청 생성 시 문서번호를 채번하고 상태 51, 대상구분 100으로 저장한다")
    void create_project_assignsDocNoAndStatus51() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(deliberationRepository.nextDocSeq()).thenReturn(1L);
        when(deliberationRepository.save(any(Bdelim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-1", "심의 요청합니다"), requester());

        assertThat(docNo).matches("DLB-\\d{4}-0001");
    }

    @Test
    @DisplayName("전산업무비 대상 신규 신청 생성 시 문서번호를 채번하고 상태 51, 대상구분 200으로 저장한다")
    void create_cost_assignsDocNoAndStatus51() {
        when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(deliberationRepository.nextDocSeq()).thenReturn(1L);
        when(deliberationRepository.save(any(Bdelim.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new DeliberationDto.CreateRequest("200", "BG-1", "전산업무비 심의 요청"), requester());

        assertThat(docNo).matches("DLB-\\d{4}-0001");
    }

    @Test
    @DisplayName("대상 사업이 없으면 신규 신청을 거부한다")
    void create_rejectsWhenTargetProjectMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("동일 대상에 진행중 문서가 있으면 신규 신청을 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new DeliberationDto.CreateRequest("100", "PRJ-1", null), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("작성중(51)→진행중(52) 전이를 허용한다")
    void changeStatus_submitAllowed() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("51").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.changeStatus("DLB-2026-0001", new DeliberationDto.StatusRequest("52"), requester());

        assertThat(e.getStsTc()).isEqualTo("52");
    }

    @Test
    @DisplayName("완료(59)에서 역행 전이(59→52)를 거부한다")
    void changeStatus_rejectsBackward() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("59").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.changeStatus(
                "DLB-2026-0001", new DeliberationDto.StatusRequest("52"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중이 아닐 때(52) 마스터 수정을 거부한다")
    void update_rejectsWhenNotDraft() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("52").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.update(
                "DLB-2026-0001", new DeliberationDto.UpdateRequest("수정 내용"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중이 아닐 때(51) 심의 결과 입력을 거부한다")
    void saveResult_rejectsWhenNotInProgress() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("51").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, null, null),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(52)에서 심의 결과 입력 시 결과 필드가 반영된다")
    void saveResult_inProgress_appliesResult() {
        Bdelim e = Bdelim.builder()
                .docMngNo("DLB-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("52").taskDbrOmtYn("N").build();
        when(deliberationRepository.findByDocMngNoAndLstYnAndDelYn("DLB-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.saveResult(
                "DLB-2026-0001",
                new DeliberationDto.ResultRequest("01", "01", "20260601", "01", "N", null, "의견없음", null),
                requester());

        assertThat(e.getTaskDbrRltTc()).isEqualTo("01");
    }
}
