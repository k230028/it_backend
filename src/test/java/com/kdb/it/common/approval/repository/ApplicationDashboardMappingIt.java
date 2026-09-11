package com.kdb.it.common.approval.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#6 결재 대시보드 native → DTO 매핑 동등성")
class ApplicationDashboardMappingIt extends AbstractOracleRepositoryTest {

    @Autowired ApplicationRepository applicationRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("findMonthlyTrendByBbrC: Object[] 경로와 LabeledCountRow 경로가 컬럼별로 동일하다")
    void monthlyTrend_objectArray_equals_dto() {
        // 존재하지 않는 부서코드는 결정적으로 빈 목록 → 두 경로 동등. 실데이터가 있으면 컬럼별 비교.
        // 주의: 빈 목록만 반환하므로 컬럼별 매핑 검증은 서비스 단위 테스트의 fromRow fixture에 의존함
        String bbrC = "ZZZZZ";
        List<Object[]> rows = applicationRepository.findMonthlyTrendByBbrC(bbrC);
        List<LabeledCountRow> dtos = applicationRepository.findMonthlyTrendRowsByBbrC(bbrC);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            LabeledCountRow d = dtos.get(i);
            assertThat(d.label()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.count()).isEqualTo(r[1] == null ? 0L : ((Number) r[1]).longValue());
        }
    }

    @Test
    @DisplayName("findPendingListByEno: Object[] 경로와 PendingApprovalRow 경로가 컬럼별로 동일하다")
    void pendingList_objectArray_equals_dto() {
        // 주의: 빈 목록만 반환하므로 컬럼별 매핑 검증은 서비스 단위 테스트의 fromRow fixture에 의존함
        String eno = "00000000";
        List<Object[]> rows = applicationRepository.findPendingListByEno(eno);
        List<PendingApprovalRow> dtos = applicationRepository.findPendingRowsByEno(eno);
        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            PendingApprovalRow d = dtos.get(i);
            assertThat(d.apfDcmNo()).isEqualTo(r[0] == null ? null : r[0].toString());
            assertThat(d.title()).isEqualTo(r[1] == null ? null : r[1].toString());
            assertThat(d.usrNm()).isEqualTo(r[2] == null ? null : r[2].toString());
            assertThat(d.rqsDt()).isEqualTo(r[3] == null ? null : r[3].toString());
        }
    }

    @Test
    @DisplayName("같은 결재자가 두 차수에 있어도 최근 결재 목록은 신청서 한 건만 반환한다")
    void pendingList_deduplicatesRepeatedApprover() {
        String apfMngNo = "APF-PENDING-DUP-IT";
        String requesterEno = "EPENDREQ001";
        String approverEno = "EPENDAPP001";
        LocalDateTime auditAt = LocalDateTime.of(2026, 9, 6, 9, 0);
        entityManager.persist(
                CuserI.builder()
                        .eno(requesterEno)
                        .usrNm("중복 결재 테스트 기안자")
                        .fstEnrDtm(auditAt)
                        .fstEnrUsid("PENDING-IT")
                        .lstChgDtm(auditAt)
                        .lstChgUsid("PENDING-IT")
                        .delYn("N")
                        .build());
        entityManager.persist(
                Capplm.builder()
                        .apfMngNo(apfMngNo)
                        .itPtlApfPrgStsC("1")
                        .dcdReqTtl("동일 결재자 최근 목록 검증")
                        .dcdReqUsid(requesterEno)
                        .dcdReqDtm(LocalDate.of(2026, 9, 6))
                        .fstEnrDtm(auditAt)
                        .fstEnrUsid("PENDING-IT")
                        .lstChgDtm(auditAt)
                        .lstChgUsid("PENDING-IT")
                        .delYn("N")
                        .build());
        entityManager.persist(decision(apfMngNo, 1, approverEno, auditAt));
        entityManager.persist(decision(apfMngNo, 2, approverEno, auditAt));
        entityManager.flush();
        entityManager.clear();

        List<Object[]> rows = applicationRepository.findPendingListByEno(approverEno);

        assertThat(rows).extracting(row -> row[0]).containsExactly(apfMngNo);
        assertThat(applicationRepository.findPendingRowsByEno(approverEno))
                .extracting(PendingApprovalRow::apfDcmNo)
                .containsExactly(apfMngNo);
    }

    @Test
    @DisplayName("Home·pending 부서 조회는 모든 OR 분기에서 타 부서 결재를 제외한다")
    void homeInbox_nativeQuery_executes() {
        String apfMngNo = "APF-HOME-INBOX-IT";
        String eno = "EHOMEIT001";
        LocalDate requestedAt = LocalDate.of(2026, 9, 4);
        LocalDateTime auditAt = LocalDateTime.of(2026, 9, 4, 9, 0);
        entityManager.persist(
                Capplm.builder()
                        .apfMngNo(apfMngNo)
                        .itPtlApfPrgStsC("1")
                        .dcdReqTtl("Home projection 검증")
                        .rgprDcdReqCone("정보화사업 1건 · 경상사업 2건")
                        .dcdReqUsid(eno)
                        .dcdReqBbrC("D01")
                        .dcdReqDtm(requestedAt)
                        .fstEnrDtm(auditAt)
                        .fstEnrUsid("HOME-IT")
                        .lstChgDtm(auditAt)
                        .lstChgUsid("HOME-IT")
                        .delYn("N")
                        .build());
        entityManager.persist(decision(apfMngNo, 1, "EBEFORE001", auditAt));
        entityManager.persist(decision(apfMngNo, 2, eno, auditAt));

        String otherPending = "APF-HOME-OTHER-PENDING";
        entityManager.persist(
                application(otherPending, "1", "EOTHERREQ01", "D02", "타 부서 결재 대기", auditAt));
        entityManager.persist(decision(otherPending, 1, eno, "1", auditAt));

        String otherCompleted = "APF-HOME-OTHER-COMPLETED";
        entityManager.persist(
                application(otherCompleted, "2", "EOTHERREQ02", "D02", "타 부서 결재 완료", auditAt));
        entityManager.persist(decision(otherCompleted, 1, eno, "2", auditAt));

        String otherDraft = "APF-HOME-OTHER-DRAFT";
        entityManager.persist(application(otherDraft, "3", eno, "D02", "타 부서 본인 기안", auditAt));
        entityManager.flush();
        entityManager.clear();

        List<ApplicationRepository.HomeInboxRow> rows =
                applicationRepository.findHomeInboxRowsByEnoAndBbrC(eno, "D01");

        assertThat(rows).hasSize(1);
        ApplicationRepository.HomeInboxRow row = rows.getFirst();
        assertThat(row.getApfMngNo()).isEqualTo(apfMngNo);
        assertThat(row.getTitle()).isEqualTo("Home projection 검증");
        assertThat(row.getRequestNote()).isEqualTo("정보화사업 1건 · 경상사업 2건");
        assertThat(row.getRequestedAt().toLocalDate()).isEqualTo(requestedAt);
        assertThat(row.getStatusCode()).isEqualTo("1");
        assertThat(row.getApprovalPending()).isEqualTo(1);
        assertThat(row.getApprovalCompleted()).isZero();
        assertThat(row.getDraftCategory()).isEqualTo("IN_PROGRESS");
        assertThat(row.getActionable()).isZero();

        assertThat(applicationRepository.findPendingApfMngNosByEnoAndBbrC(eno, "D01"))
                .containsExactly(apfMngNo);
        assertThat(applicationRepository.findPendingApfMngNosByEnoAndBbrC(eno, "D02"))
                .containsExactly(otherPending);
    }

    private Capplm application(
            String apfMngNo,
            String status,
            String requesterEno,
            String bbrC,
            String title,
            LocalDateTime auditAt) {
        return Capplm.builder()
                .apfMngNo(apfMngNo)
                .itPtlApfPrgStsC(status)
                .dcdReqTtl(title)
                .dcdReqUsid(requesterEno)
                .dcdReqBbrC(bbrC)
                .dcdReqDtm(auditAt.toLocalDate())
                .fstEnrDtm(auditAt)
                .fstEnrUsid("HOME-IT")
                .lstChgDtm(auditAt)
                .lstChgUsid("HOME-IT")
                .delYn("N")
                .build();
    }

    private Cdecim decision(String apfMngNo, int sequence, String eno, LocalDateTime auditAt) {
        return decision(apfMngNo, sequence, eno, "1", auditAt);
    }

    private Cdecim decision(
            String apfMngNo, int sequence, String eno, String status, LocalDateTime auditAt) {
        return Cdecim.builder()
                .dcdMngNo(apfMngNo)
                .dcrSqnSno(sequence)
                .dcrEno(eno)
                .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                .itPtlDcdStsC(status)
                .lstDcdYn(sequence == 2 ? "Y" : "N")
                .fstEnrDtm(auditAt)
                .fstEnrUsid("HOME-IT")
                .lstChgDtm(auditAt)
                .lstChgUsid("HOME-IT")
                .delYn("N")
                .build();
    }
}
