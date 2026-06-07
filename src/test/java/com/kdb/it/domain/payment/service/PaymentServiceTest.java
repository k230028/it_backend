package com.kdb.it.domain.payment.service;

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
import com.kdb.it.domain.payment.dto.PaymentDto;
import com.kdb.it.domain.payment.entity.Bpaymm;
import com.kdb.it.domain.payment.entity.Bpaymt;
import com.kdb.it.domain.payment.repository.PaymentLineRepository;
import com.kdb.it.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentLineRepository lineRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;

    PaymentService service;

    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, lineRepository, projectRepository, costRepository);
    }

    @Test
    @DisplayName("사업 대상으로 신규 의뢰 생성 시 PAY-YYYY-0001 문서번호를 채번한다")
    void create_project_assignsDocNo() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(paymentRepository.nextDocSeq()).thenReturn(1L);
        when(paymentRepository.save(any(Bpaymm.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new PaymentDto.CreateRequest("100", "PRJ-1", "요청합니다", "테스트계약", BigDecimal.valueOf(1000000)),
                requester());

        assertThat(docNo).matches("PAY-\\d{4}-0001");
    }

    @Test
    @DisplayName("전산업무비 대상으로 신규 의뢰 생성이 정상 처리된다")
    void create_cost_assignsDocNo() {
        when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N")).thenReturn(true);
        when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(false);
        when(paymentRepository.nextDocSeq()).thenReturn(1L);
        when(paymentRepository.save(any(Bpaymm.class))).thenAnswer(inv -> inv.getArgument(0));

        String docNo = service.create(
                new PaymentDto.CreateRequest("200", "BG-1", null, null, null),
                requester());

        assertThat(docNo).matches("PAY-\\d{4}-0001");
    }

    @Test
    @DisplayName("대상이 존재하지 않으면 신규 의뢰를 거부한다")
    void create_rejectsWhenTargetMissing() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-NONE", "Y", "N")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new PaymentDto.CreateRequest("100", "PRJ-NONE", null, null, null), requester()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상");
    }

    @Test
    @DisplayName("동일 대상에 진행 중인 문서가 있으면 신규 의뢰를 거부한다")
    void create_rejectsDuplicate() {
        when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
        when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                anyString(), anyString(), any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new PaymentDto.CreateRequest("100", "PRJ-1", null, null, null), requester()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("작성중(71)→진행중(72) 상태 전이를 허용한다")
    void changeStatus_draftToInProgress_allowed() {
        Bpaymm e = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        service.changeStatus("PAY-2026-0001", new PaymentDto.StatusRequest("72"), requester());

        assertThat(e.getStsTc()).isEqualTo("72");
    }

    @Test
    @DisplayName("완료(79)→진행중(72) 역전이는 거부한다")
    void changeStatus_doneToInProgress_rejected() {
        Bpaymm e = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("79").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.changeStatus(
                "PAY-2026-0001", new PaymentDto.StatusRequest("72"), requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(72) 상태에서 마스터 수정을 시도하면 거부한다")
    void update_notDraft_rejected() {
        Bpaymm e = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.update(
                "PAY-2026-0001",
                new PaymentDto.UpdateRequest("수정요청", "수정계약명", BigDecimal.valueOf(500000)),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("작성중(71) 상태에서 지급 명세 저장을 시도하면 거부한다")
    void savePayments_notInProgress_rejected() {
        Bpaymm e = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.savePayments(
                "PAY-2026-0001",
                new PaymentDto.LinesRequest(List.of(
                        new PaymentDto.LineRequest(1, BigDecimal.valueOf(100000), "20260601", "20260630", null))),
                requester()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중(72) 상태에서 명세 저장 시 요청에 없는 기존 활성 회차는 soft-delete된다")
    void savePayments_inProgress_existingLineDeleted() {
        Bpaymm master = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(master));

        // 기존 활성 1회차 (delYn 기본값 null → 서비스에서 non-"Y"는 활성으로 처리)
        Bpaymt existing = Bpaymt.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(1)
                .dfrAmt(BigDecimal.valueOf(100000)).dfrDt("20260601").dfrMplDt("20260630").build();
        when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                .thenReturn(new ArrayList<>(List.of(existing)));

        // 2회차만 요청 (1회차는 포함하지 않음 → soft-delete 대상)
        service.savePayments(
                "PAY-2026-0001",
                new PaymentDto.LinesRequest(List.of(
                        new PaymentDto.LineRequest(2, BigDecimal.valueOf(200000), "20260701", "20260731", null))),
                requester());

        // 기존 1회차는 delete()가 호출되어 delYn='Y'가 되어야 함
        assertThat(existing.getDelYn()).isEqualTo("Y");
        // 2회차는 신규이므로 save가 호출되어야 함
        verify(lineRepository).save(any(Bpaymt.class));
    }

    @Test
    @DisplayName("soft-delete된 회차를 재추가하면 복원되고 신규 save는 호출되지 않는다")
    void savePayments_restoresDeletedLine() {
        Bpaymm master = Bpaymm.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").build();
        when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                .thenReturn(Optional.of(master));

        // 기존 soft-deleted 1회차: delete() 호출로 delYn='Y' 설정
        Bpaymt deleted = Bpaymt.builder()
                .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(1)
                .dfrAmt(BigDecimal.valueOf(100000)).dfrDt("20260601").dfrMplDt("20260630").build();
        deleted.delete(); // delYn = 'Y'
        when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                .thenReturn(new ArrayList<>(List.of(deleted)));

        // 1회차 재추가 요청
        service.savePayments(
                "PAY-2026-0001",
                new PaymentDto.LinesRequest(List.of(
                        new PaymentDto.LineRequest(1, BigDecimal.valueOf(150000), "20260601", "20260630", "복원의견"))),
                requester());

        // 복원 후 delYn='N'
        assertThat(deleted.getDelYn()).isEqualTo("N");
        // 금액 업데이트 확인
        assertThat(deleted.getDfrAmt()).isEqualByComparingTo(BigDecimal.valueOf(150000));
        // 기존 행 복원이므로 새 save 호출 없음
        verify(lineRepository, never()).save(any(Bpaymt.class));
    }
}
