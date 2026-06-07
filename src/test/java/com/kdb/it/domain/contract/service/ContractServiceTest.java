package com.kdb.it.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.domain.contract.repository.ContractRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;

    ContractService service;

    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    @BeforeEach
    void setUp() {
        service = new ContractService(contractRepository, projectRepository, costRepository);
    }

    @Test
    @DisplayName("사업 대상 신규 의뢰 생성 시 문서번호를 채번하고 상태 61, 대상구분 100으로 저장한다")
    void create_project_assignsDocNoAndStatus61() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(contractRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(contractRepository.nextDocSeq()).thenReturn(1L);
        when(contractRepository.save(any(Bcontm.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new ContractDto.CreateRequest("100", "PRJ-1", "입찰 의뢰합니다"), requester());

        assertThat(docNo).matches("CTR-\\d{4}-0001");
    }

    @Test
    @DisplayName("전산업무비 대상 신규 의뢰 생성 시 문서번호를 채번하고 상태 61, 대상구분 200으로 저장한다")
    void create_cost_assignsDocNoAndStatus61() {
        when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N")).thenReturn(true);
        when(contractRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(contractRepository.nextDocSeq()).thenReturn(1L);
        when(contractRepository.save(any(Bcontm.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new ContractDto.CreateRequest("200", "BG-1", "전산업무비 입찰 의뢰"), requester());

        assertThat(docNo).matches("CTR-\\d{4}-0001");
    }

    @Test
    @DisplayName("대상 사업이 없으면 신규 의뢰를 거부한다")
    void create_rejectsWhenTargetProjectMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-X", "Y", "N")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new ContractDto.CreateRequest("100", "PRJ-X", null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("동일 대상에 진행중 문서가 있으면 신규 의뢰를 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(contractRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new ContractDto.CreateRequest("100", "PRJ-1", null), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("작성중(61)→진행중(62) 전이를 허용한다")
    void changeStatus_submitAllowed() {
        Bcontm e = Bcontm.builder()
                .docMngNo("CTR-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("61").build();
        when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.changeStatus("CTR-2026-0001", new ContractDto.StatusRequest("62"), requester());

        assertThat(e.getStsTc()).isEqualTo("62");
    }

    @Test
    @DisplayName("완료(69)에서 역행 전이(69→62)를 거부한다")
    void changeStatus_rejectsBackward() {
        Bcontm e = Bcontm.builder()
                .docMngNo("CTR-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("69").build();
        when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.changeStatus(
                "CTR-2026-0001", new ContractDto.StatusRequest("62"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중이 아닐 때(62) 마스터 수정을 거부한다")
    void update_rejectsWhenNotDraft() {
        Bcontm e = Bcontm.builder()
                .docMngNo("CTR-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("62").build();
        when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.update(
                "CTR-2026-0001", new ContractDto.UpdateRequest("수정 내용"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중이 아닐 때(61) 계약 정보 입력을 거부한다")
    void saveContract_rejectsWhenNotInProgress() {
        Bcontm e = Bcontm.builder()
                .docMngNo("CTR-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("61").build();
        when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.saveContract(
                "CTR-2026-0001",
                new ContractDto.WorkRequest("01", "수의계약 사유", "계약A", new BigDecimal("1000"), "상대처A", "20260601"),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(62)에서 계약 정보 입력 시 계약 필드가 반영된다")
    void saveContract_inProgress_appliesContractFields() {
        Bcontm e = Bcontm.builder()
                .docMngNo("CTR-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("62").build();
        when(contractRepository.findByDocMngNoAndLstYnAndDelYn("CTR-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.saveContract(
                "CTR-2026-0001",
                new ContractDto.WorkRequest("01", "수의계약 사유", "계약A", new BigDecimal("1000"), "상대처A", "20260601"),
                requester());

        assertThat(e.getCttNm()).isEqualTo("계약A");
        assertThat(e.getCttManrC()).isEqualTo("01");
    }
}
