package com.kdb.it.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * PaymentService 단위 테스트.
 *
 * <p>Mockito로 모든 외부 의존성(Repository)을 대체합니다. 각 테스트는 독립적이며 공유 상태가 없습니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentLineRepository lineRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CostRepository costRepository;
    @Mock BprojaSyncService bprojaSyncService;

    PaymentService service;

    /** 일반 사용자 인증 정보 생성 헬퍼 */
    CustomUserDetails requester() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    /** 관리자 인증 정보 생성 헬퍼 */
    CustomUserDetails adminUser() {
        return new CustomUserDetails("A0001", List.of("ITPAD001"), "18001");
    }

    /** 소유자가 아닌 타인(일반 사용자) 인증 정보 생성 헬퍼 */
    CustomUserDetails other() {
        return new CustomUserDetails("E0002", List.of("ITPZZ001"), "18001");
    }

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, lineRepository, projectRepository, costRepository, bprojaSyncService);
    }

    // =========================================================================
    // create() 테스트
    // =========================================================================

    @Nested
    @DisplayName("create() - 신규 의뢰 생성")
    class CreateTests {

        @Test
        @DisplayName("사업 대상으로 신규 의뢰 생성 시 PAY-YYYY-0001 문서번호를 채번한다")
        void create_project_assignsDocNo() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
            when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                    anyString(), anyString(), any(), anyString())).thenReturn(false);
            when(paymentRepository.nextDocSeq()).thenReturn(1L);
            when(paymentRepository.save(any(Bpaymm.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String docNo = service.create(
                    new PaymentDto.CreateRequest("100", "PRJ-1", "요청합니다", "테스트계약", BigDecimal.valueOf(1000000)),
                    requester());

            // Assert
            assertThat(docNo).matches("PAY-\\d{4}-0001");
        }

        @Test
        @DisplayName("전산업무비 대상으로 신규 의뢰 생성이 정상 처리된다")
        void create_cost_assignsDocNo() {
            // Arrange
            when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N")).thenReturn(true);
            when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                    anyString(), anyString(), any(), anyString())).thenReturn(false);
            when(paymentRepository.nextDocSeq()).thenReturn(1L);
            when(paymentRepository.save(any(Bpaymm.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String docNo = service.create(
                    new PaymentDto.CreateRequest("200", "BG-1", null, null, null),
                    requester());

            // Assert
            assertThat(docNo).matches("PAY-\\d{4}-0001");
        }

        @Test
        @DisplayName("알 수 없는 대상구분(bgPrnTc=999)이면 IllegalArgumentException을 던진다")
        void create_unknownBgPrnTc_throwsIllegalArgument() {
            // Arrange - 알 수 없는 대상구분 "999"
            // validateTarget 내에서 즉시 throw → 중복 체크 Repository는 호출되지 않아야 함

            // Act & Assert
            assertThatThrownBy(() -> service.create(
                    new PaymentDto.CreateRequest("999", "REF-1", null, null, null), requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("알 수 없는 대상구분");

            verify(paymentRepository, never()).existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                    anyString(), anyString(), any(), anyString());
        }

        @Test
        @DisplayName("사업 대상이 존재하지 않으면 신규 의뢰를 거부한다")
        void create_rejectsWhenProjectTargetMissing() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-NONE", "Y", "N")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.create(
                    new PaymentDto.CreateRequest("100", "PRJ-NONE", null, null, null), requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("대상을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("전산업무비 대상이 존재하지 않으면 신규 의뢰를 거부한다")
        void create_rejectsWhenCostTargetMissing() {
            // Arrange
            when(costRepository.existsByCostBgNoAndLstYnAndDelYn("BG-NONE", "Y", "N")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.create(
                    new PaymentDto.CreateRequest("200", "BG-NONE", null, null, null), requester()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("대상을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("동일 대상에 진행 중인 문서가 있으면 신규 의뢰를 거부한다")
        void create_rejectsDuplicate() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
            when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                    anyString(), anyString(), any(), anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.create(
                    new PaymentDto.CreateRequest("100", "PRJ-1", null, null, null), requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("진행 중");
        }

        @Test
        @DisplayName("채번 결과가 10이면 PAY-YYYY-0010 형식(4자리 패딩)으로 반환된다")
        void create_docNoFormattedWithFourDigits() {
            // Arrange
            when(projectRepository.existsByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N")).thenReturn(true);
            when(paymentRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                    anyString(), anyString(), any(), anyString())).thenReturn(false);
            when(paymentRepository.nextDocSeq()).thenReturn(10L);
            when(paymentRepository.save(any(Bpaymm.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String docNo = service.create(
                    new PaymentDto.CreateRequest("100", "PRJ-1", null, null, null), requester());

            // Assert - 4자리 포맷 검증
            assertThat(docNo).matches("PAY-\\d{4}-0010");
        }
    }

    // =========================================================================
    // update() 테스트
    // =========================================================================

    @Nested
    @DisplayName("update() - 마스터 수정")
    class UpdateTests {

        @Test
        @DisplayName("작성중(71) 상태에서 마스터 수정이 정상 처리된다")
        void update_draft_success() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71")
                    .reqCone("기존내용").cttNm("기존계약명").cttAmt(BigDecimal.valueOf(1000000)).fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.update(
                    "PAY-2026-0001",
                    new PaymentDto.UpdateRequest("수정내용", "수정계약명", BigDecimal.valueOf(2000000)),
                    requester());

            // Assert - Dirty Checking으로 엔티티 필드가 변경되어야 함
            assertThat(e.getReqCone()).isEqualTo("수정내용");
            assertThat(e.getCttNm()).isEqualTo("수정계약명");
            assertThat(e.getCttAmt()).isEqualByComparingTo(BigDecimal.valueOf(2000000));
        }

        @Test
        @DisplayName("진행중(72) 상태에서 마스터 수정을 시도하면 거부한다")
        void update_notDraft_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.update(
                    "PAY-2026-0001",
                    new PaymentDto.UpdateRequest("수정요청", "수정계약명", BigDecimal.valueOf(500000)),
                    requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("작성중 상태에서만 수정");
        }

        @Test
        @DisplayName("완료(79) 상태에서 마스터 수정을 시도하면 거부한다")
        void update_done_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("79").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.update(
                    "PAY-2026-0001",
                    new PaymentDto.UpdateRequest(null, null, null),
                    requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // delete() 테스트
    // =========================================================================

    @Nested
    @DisplayName("delete() - 논리 삭제")
    class DeleteTests {

        @Test
        @DisplayName("작성중(71) 상태에서 논리 삭제가 정상 처리된다")
        void delete_draft_success() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.delete("PAY-2026-0001", requester());

            // Assert - Soft Delete: delYn='Y'
            assertThat(e.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("진행중(72) 상태에서 삭제를 시도하면 거부한다")
        void delete_inProgress_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("PAY-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("작성중 상태에서만 삭제");
        }

        @Test
        @DisplayName("완료(79) 상태에서 삭제를 시도하면 거부한다")
        void delete_done_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("79").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("PAY-2026-0001", requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // changeStatus() 테스트
    // =========================================================================

    @Nested
    @DisplayName("changeStatus() - 상태 전이")
    class ChangeStatusTests {

        @Test
        @DisplayName("작성중(71)→진행중(72) 상태 전이를 허용한다")
        void changeStatus_draftToInProgress_allowed() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.changeStatus("PAY-2026-0001", new PaymentDto.StatusRequest("72"), requester());

            // Assert
            assertThat(e.getStsTc()).isEqualTo("72");
        }

        @Test
        @DisplayName("진행중(72)→완료(79) 상태 전이를 허용한다")
        void changeStatus_inProgressToDone_allowed() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.changeStatus("PAY-2026-0001", new PaymentDto.StatusRequest("79"), requester());

            // Assert
            assertThat(e.getStsTc()).isEqualTo("79");
        }

        @Test
        @DisplayName("완료(79)→진행중(72) 역전이는 거부한다")
        void changeStatus_doneToInProgress_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("79").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(
                    "PAY-2026-0001", new PaymentDto.StatusRequest("72"), requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("허용되지 않은 상태 전이");
        }

        @Test
        @DisplayName("작성중(71)→완료(79) 직접 전이는 허용되지 않는다")
        void changeStatus_draftToDone_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(
                    "PAY-2026-0001", new PaymentDto.StatusRequest("79"), requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("허용되지 않은 상태 전이");
        }

        @Test
        @DisplayName("진행중(72)→작성중(71) 역전이는 허용되지 않는다")
        void changeStatus_inProgressToDraft_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(
                    "PAY-2026-0001", new PaymentDto.StatusRequest("71"), requester()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // savePayments() 테스트
    // =========================================================================

    @Nested
    @DisplayName("savePayments() - 회차별 지급 명세 저장")
    class SavePaymentsTests {

        @Test
        @DisplayName("작성중(71) 상태에서 지급 명세 저장을 시도하면 거부한다")
        void savePayments_notInProgress_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of(
                            new PaymentDto.LineRequest(1, BigDecimal.valueOf(100000), "20260601", "20260630", null))),
                    requester()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("진행중 상태에서만");
        }

        @Test
        @DisplayName("완료(79) 상태에서 지급 명세 저장을 시도하면 거부한다")
        void savePayments_done_rejected() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("79").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act & Assert
            assertThatThrownBy(() -> service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of()),
                    requester()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("진행중(72) 상태에서 명세 저장 시 요청에 없는 기존 활성 회차는 soft-delete된다")
        void savePayments_inProgress_existingLineDeleted() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
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

            // Assert - 기존 1회차는 delete()가 호출되어 delYn='Y'가 되어야 함
            assertThat(existing.getDelYn()).isEqualTo("Y");
            // 2회차는 신규이므로 save가 호출되어야 함
            verify(lineRepository).save(any(Bpaymt.class));
        }

        @Test
        @DisplayName("soft-delete된 회차를 재추가하면 복원되고 신규 save는 호출되지 않는다")
        void savePayments_restoresDeletedLine() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // soft-deleted 1회차: delete() 호출로 delYn='Y' 설정
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

            // Assert - 복원 후 delYn='N'
            assertThat(deleted.getDelYn()).isEqualTo("N");
            // 금액 업데이트 확인
            assertThat(deleted.getDfrAmt()).isEqualByComparingTo(BigDecimal.valueOf(150000));
            // 기존 행 복원이므로 새 save 호출 없음
            verify(lineRepository, never()).save(any(Bpaymt.class));
        }

        @Test
        @DisplayName("기존 활성 회차가 요청에 포함되면 updatePayment만 호출되고 삭제되지 않는다")
        void savePayments_existingActiveLineIncluded_updatesOnly() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // 기존 활성 1회차 (delYn=null → !="Y" → 활성)
            Bpaymt existing = Bpaymt.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(1)
                    .dfrAmt(BigDecimal.valueOf(100000)).dfrDt("20260601").dfrMplDt("20260630").opnnCone("기존의견").build();
            when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(existing)));

            // 동일 1회차를 포함한 요청 → soft-delete 없이 updatePayment만 호출
            service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of(
                            new PaymentDto.LineRequest(1, BigDecimal.valueOf(999000), "20260610", "20260615", "수정의견"))),
                    requester());

            // Assert - 업데이트만, 삭제 없음
            assertThat(existing.getDfrAmt()).isEqualByComparingTo(BigDecimal.valueOf(999000));
            assertThat(existing.getOpnnCone()).isEqualTo("수정의견");
            assertThat(existing.getDelYn()).isNotEqualTo("Y"); // 삭제되지 않음
            verify(lineRepository, never()).save(any(Bpaymt.class)); // 신규 INSERT 없음
        }

        @Test
        @DisplayName("빈 요청 목록으로 저장 시 기존 활성 회차가 모두 soft-delete된다")
        void savePayments_emptyLines_deletesAllExisting() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // 기존 활성 1, 2회차
            Bpaymt line1 = Bpaymt.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(1)
                    .dfrAmt(BigDecimal.valueOf(100000)).build();
            Bpaymt line2 = Bpaymt.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(2)
                    .dfrAmt(BigDecimal.valueOf(200000)).build();
            when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(line1, line2)));

            // 빈 목록으로 저장 요청
            service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of()),
                    requester());

            // Assert - 모든 기존 활성 회차가 삭제되어야 함
            assertThat(line1.getDelYn()).isEqualTo("Y");
            assertThat(line2.getDelYn()).isEqualTo("Y");
            verify(lineRepository, never()).save(any(Bpaymt.class));
        }

        @Test
        @DisplayName("기존 DB에 행이 없을 때 신규 요청만 있으면 모두 save된다")
        void savePayments_noExistingLines_savesAllNew() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // 기존 행 없음
            when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                    .thenReturn(new ArrayList<>());

            // 1, 2회차 신규 요청
            service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of(
                            new PaymentDto.LineRequest(1, BigDecimal.valueOf(100000), "20260601", "20260630", null),
                            new PaymentDto.LineRequest(2, BigDecimal.valueOf(200000), "20260701", "20260731", "2차의견"))),
                    requester());

            // Assert - 2번 save 호출
            verify(lineRepository, Mockito.times(2)).save(any(Bpaymt.class));
        }

        @Test
        @DisplayName("이미 soft-delete된 회차는 incoming에 없어도 다시 delete() 호출되지 않는다")
        void savePayments_alreadyDeletedLine_notDeletedAgain() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72").fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // 이미 삭제된 1회차 (delYn='Y')
            Bpaymt alreadyDeleted = Bpaymt.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).dfrTod(1)
                    .dfrAmt(BigDecimal.valueOf(100000)).build();
            alreadyDeleted.delete(); // delYn='Y'
            when(lineRepository.findByDocMngNoAndDocVrsSno("PAY-2026-0001", 1))
                    .thenReturn(new ArrayList<>(List.of(alreadyDeleted)));

            // 1회차를 포함하지 않는 빈 요청
            service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of()),
                    requester());

            // Assert - 이미 'Y'이므로 "Y".equals(row.getDelYn()) → delete() 재호출 없이 'Y' 유지
            assertThat(alreadyDeleted.getDelYn()).isEqualTo("Y");
        }
    }

    // =========================================================================
    // get() 테스트
    // =========================================================================

    @Nested
    @DisplayName("get() - 상세 조회")
    class GetTests {

        @Test
        @DisplayName("사업(100) 대상 문서 상세 조회 시 프로젝트명이 포함된 Detail을 반환한다")
        void get_projectTarget_returnsDetailWithProjectName() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71")
                    .reqCone("요청내용").cttNm("계약명").cttAmt(BigDecimal.valueOf(500000)).build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn("PAY-2026-0001", 1, "N"))
                    .thenReturn(List.of());

            // Bprojm을 직접 빌드하기 어려우므로 Mock으로 이름만 반환
            Bprojm proj = Mockito.mock(Bprojm.class);
            when(proj.getAbusNm()).thenReturn("클라우드 전환 프로젝트");
            when(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N"))
                    .thenReturn(Optional.of(proj));

            // Act
            PaymentDto.Detail detail = service.get("PAY-2026-0001");

            // Assert
            assertThat(detail.docMngNo()).isEqualTo("PAY-2026-0001");
            assertThat(detail.bgPrnTc()).isEqualTo("100");
            assertThat(detail.tgtNm()).isEqualTo("클라우드 전환 프로젝트");
            assertThat(detail.lines()).isEmpty();
        }

        @Test
        @DisplayName("전산업무비(200) 대상 문서 상세 조회 시 전산업무비명과 회차 명세 목록을 반환한다")
        void get_costTarget_returnsDetailWithCostName() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0002").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("200").cncdRfrNo("BG-1").stsTc("72")
                    .cttNm("유지보수계약").cttAmt(BigDecimal.valueOf(1000000)).build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0002", "Y", "N"))
                    .thenReturn(Optional.of(master));

            Bpaymt line = Bpaymt.builder()
                    .docMngNo("PAY-2026-0002").docVrsSno(1).dfrTod(1)
                    .dfrAmt(BigDecimal.valueOf(500000)).dfrDt("20260601").dfrMplDt("20260630").opnnCone("1차지급").build();
            when(lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn("PAY-2026-0002", 1, "N"))
                    .thenReturn(List.of(line));

            Bcostm cost = Mockito.mock(Bcostm.class);
            when(cost.getCttNm()).thenReturn("서버유지보수");
            when(costRepository.findByCostBgNoAndLstYnAndDelYn("BG-1", "Y", "N"))
                    .thenReturn(Optional.of(cost));

            // Act
            PaymentDto.Detail detail = service.get("PAY-2026-0002");

            // Assert
            assertThat(detail.tgtNm()).isEqualTo("서버유지보수");
            assertThat(detail.lines()).hasSize(1);
            assertThat(detail.lines().get(0).dfrTod()).isEqualTo(1);
            assertThat(detail.lines().get(0).dfrAmt()).isEqualByComparingTo(BigDecimal.valueOf(500000));
            assertThat(detail.lines().get(0).opnnCone()).isEqualTo("1차지급");
        }

        @Test
        @DisplayName("사업 대상이지만 프로젝트를 찾을 수 없으면 tgtNm이 null이다")
        void get_projectNotFound_tgtNmIsNull() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0003").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-GONE").stsTc("71").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0003", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn("PAY-2026-0003", 1, "N"))
                    .thenReturn(List.of());
            when(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-GONE", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act
            PaymentDto.Detail detail = service.get("PAY-2026-0003");

            // Assert - orElse(null) → tgtNm은 null
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("전산업무비 대상을 찾을 수 없으면 tgtNm이 null이다")
        void get_costNotFound_tgtNmIsNull() {
            // Arrange
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0005").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("200").cncdRfrNo("BG-GONE").stsTc("71").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0005", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn("PAY-2026-0005", 1, "N"))
                    .thenReturn(List.of());
            when(costRepository.findByCostBgNoAndLstYnAndDelYn("BG-GONE", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act
            PaymentDto.Detail detail = service.get("PAY-2026-0005");

            // Assert
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("알 수 없는 대상구분(bgPrnTc=999) 문서의 tgtNm은 null이다")
        void get_unknownBgPrnTc_tgtNmIsNull() {
            // Arrange - bgPrnTc="999" 같은 비표준 값 → resolveTargetName에서 else null 경로
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0004").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("999").cncdRfrNo("UNKNOWN").stsTc("71").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0004", "Y", "N"))
                    .thenReturn(Optional.of(master));
            when(lineRepository.findByDocMngNoAndDocVrsSnoAndDelYn("PAY-2026-0004", 1, "N"))
                    .thenReturn(List.of());

            // Act
            PaymentDto.Detail detail = service.get("PAY-2026-0004");

            // Assert
            assertThat(detail.tgtNm()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 문서번호로 조회하면 IllegalArgumentException을 던진다")
        void get_documentNotFound_throwsIllegalArgument() {
            // Arrange
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-XXXX-9999", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.get("PAY-XXXX-9999"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("대금지급 문서를 찾을 수 없습니다");
        }
    }

    // =========================================================================
    // list() 테스트
    // =========================================================================

    @Nested
    @DisplayName("list() - 목록 조회")
    class ListTests {

        @Test
        @DisplayName("관리자는 bbrC=null로 전체 목록을 조회한다")
        void list_admin_noBbrCFilter() {
            // Arrange
            List<PaymentDto.ListItem> mockResult = List.of(
                    new PaymentDto.ListItem("PAY-2026-0001", 1, "100", "PRJ-1", "71", "계약1", BigDecimal.valueOf(1000000), "E0001", null),
                    new PaymentDto.ListItem("PAY-2026-0002", 1, "200", "BG-1", "72", "계약2", BigDecimal.valueOf(2000000), "E0002", null)
            );
            when(paymentRepository.search(null, null, null, null)).thenReturn(mockResult);

            // Act
            List<PaymentDto.ListItem> result = service.list(null, null, null, adminUser());

            // Assert - 관리자이므로 bbrC=null로 search 호출
            verify(paymentRepository).search(null, null, null, null);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("일반 사용자는 소속 부서코드(bbrC)로 필터링하여 목록을 조회한다")
        void list_regularUser_bbrCFiltered() {
            // Arrange - 사용자 bbrC="18001"
            List<PaymentDto.ListItem> mockResult = List.of(
                    new PaymentDto.ListItem("PAY-2026-0001", 1, "100", "PRJ-1", "71", "계약1", BigDecimal.valueOf(1000000), "E0001", null)
            );
            when(paymentRepository.search("71", null, null, "18001")).thenReturn(mockResult);

            // Act
            List<PaymentDto.ListItem> result = service.list("71", null, null, requester());

            // Assert - 일반 사용자이므로 bbrC="18001"로 search 호출
            verify(paymentRepository).search("71", null, null, "18001");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("상태코드·대상구분·대상관리번호 필터가 모두 지정된 경우 조건이 그대로 전달된다")
        void list_withAllFilters_passedThrough() {
            // Arrange
            when(paymentRepository.search("72", "100", "PRJ-1", null)).thenReturn(List.of());

            // Act
            service.list("72", "100", "PRJ-1", adminUser());

            // Assert
            verify(paymentRepository).search("72", "100", "PRJ-1", null);
        }

        @Test
        @DisplayName("빈 결과를 반환하면 빈 리스트를 반환한다")
        void list_emptyResult_returnsEmptyList() {
            // Arrange
            when(paymentRepository.search(null, null, null, "18001")).thenReturn(List.of());

            // Act
            List<PaymentDto.ListItem> result = service.list(null, null, null, requester());

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // loadCurrent() 테스트
    // =========================================================================

    @Nested
    @DisplayName("loadCurrent() - 현재 유효 마스터 조회")
    class LoadCurrentTests {

        @Test
        @DisplayName("유효한 문서번호로 조회하면 마스터 엔티티를 반환한다")
        void loadCurrent_found_returnsMaster() {
            // Arrange
            Bpaymm e = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act - package-private 메서드 직접 호출 (동일 패키지)
            Bpaymm result = service.loadCurrent("PAY-2026-0001");

            // Assert
            assertThat(result.getDocMngNo()).isEqualTo("PAY-2026-0001");
            assertThat(result.getStsTc()).isEqualTo("71");
        }

        @Test
        @DisplayName("존재하지 않는 문서번호로 loadCurrent 호출 시 IllegalArgumentException을 던진다")
        void loadCurrent_notFound_throwsIllegalArgument() {
            // Arrange
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-NOTEXIST", "Y", "N"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.loadCurrent("PAY-NOTEXIST"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PAY-NOTEXIST");
        }
    }

    // =========================================================================
    // 소유권 검증 테스트 (OwnershipVerifier 적용)
    // =========================================================================

    @Nested
    @DisplayName("소유권 검증 - 본인 또는 관리자만 쓰기 허용")
    class OwnershipTests {

        /** 소유자(E0001)가 작성한 작성중(71) 마스터 픽스처 */
        private Bpaymm draftOwnedByE0001() {
            return Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("71")
                    .fstEnrUsid("E0001").build();
        }

        @Test
        @DisplayName("타인이 마스터 수정을 시도하면 AccessDeniedException을 던진다")
        void update_byOther_denied() {
            // Arrange
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));

            // Act & Assert
            assertThatThrownBy(() -> service.update(
                    "PAY-2026-0001",
                    new PaymentDto.UpdateRequest("수정내용", "수정계약명", BigDecimal.valueOf(2000000)),
                    other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("타인이 삭제를 시도하면 AccessDeniedException을 던진다")
        void delete_byOther_denied() {
            // Arrange
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));

            // Act & Assert
            assertThatThrownBy(() -> service.delete("PAY-2026-0001", other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("타인이 상태 전이를 시도하면 AccessDeniedException을 던진다")
        void changeStatus_byOther_denied() {
            // Arrange
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(draftOwnedByE0001()));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(
                    "PAY-2026-0001", new PaymentDto.StatusRequest("72"), other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("타인이 지급 명세 저장을 시도하면 AccessDeniedException을 던진다")
        void savePayments_byOther_denied() {
            // Arrange - 진행중(72) 상태 마스터, 소유자 E0001
            Bpaymm master = Bpaymm.builder()
                    .docMngNo("PAY-2026-0001").docVrsSno(1).lstYn("Y")
                    .bgPrnTc("100").cncdRfrNo("PRJ-1").stsTc("72")
                    .fstEnrUsid("E0001").build();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(master));

            // Act & Assert
            assertThatThrownBy(() -> service.savePayments(
                    "PAY-2026-0001",
                    new PaymentDto.LinesRequest(List.of()),
                    other()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("관리자는 타인 문서라도 마스터 수정을 수행할 수 있다")
        void update_byAdmin_allowed() {
            // Arrange - 소유자 E0001 문서를 관리자가 수정
            Bpaymm e = draftOwnedByE0001();
            when(paymentRepository.findByDocMngNoAndLstYnAndDelYn("PAY-2026-0001", "Y", "N"))
                    .thenReturn(Optional.of(e));

            // Act
            service.update(
                    "PAY-2026-0001",
                    new PaymentDto.UpdateRequest("관리자수정", "관리자계약명", BigDecimal.valueOf(3000000)),
                    adminUser());

            // Assert
            assertThat(e.getReqCone()).isEqualTo("관리자수정");
            assertThat(e.getCttNm()).isEqualTo("관리자계약명");
        }
    }
}
