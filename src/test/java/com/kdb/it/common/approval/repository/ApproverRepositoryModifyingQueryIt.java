package com.kdb.it.common.approval.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.config.QuerydslConfig;
import com.kdb.it.support.OracleAvailableCondition;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** PK 구성요소를 바꾸는 네이티브 UPDATE가 영속성 컨텍스트를 비우는지 검증한다. */
@Tag("it")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
@ActiveProfiles("test-it")
@org.junit.jupiter.api.extension.ExtendWith(OracleAvailableCondition.class)
class ApproverRepositoryModifyingQueryIt {

    @Autowired private ApproverRepository approverRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void updateSequence는_PK구성요소변경뒤_영속성컨텍스트를비운다() {
        String dcdMngNo = "APF-2026-T2-UPDATE";
        entityManager.persist(decision(dcdMngNo, 1, "E0001"));
        entityManager.flush();
        entityManager.clear();

        Cdecim managed = approverRepository.findByDcdMngNoAndDcrSqnSno(dcdMngNo, 1).orElseThrow();

        assertThat(entityManager.contains(managed)).isTrue();

        int updated = approverRepository.updateSequence(dcdMngNo, 1, 9);

        assertThat(updated).isEqualTo(1);
        assertThat(entityManager.contains(managed)).isFalse();
        assertThat(approverRepository.findByDcdMngNoAndDcrSqnSno(dcdMngNo, 1)).isEmpty();
        assertThat(approverRepository.findByDcdMngNoAndDcrSqnSno(dcdMngNo, 9)).isPresent();
    }

    @Test
    void shiftPendingSequences는_PK구성요소일괄변경뒤_영속성컨텍스트를비운다() {
        String dcdMngNo = "APF-2026-T2-SHIFT";
        entityManager.persist(decision(dcdMngNo, 1, "E0001"));
        entityManager.persist(decision(dcdMngNo, 2, "E0002"));
        entityManager.persist(decision(dcdMngNo, 3, "E0003"));
        entityManager.flush();
        entityManager.clear();

        Cdecim managed = approverRepository.findByDcdMngNoAndDcrSqnSno(dcdMngNo, 1).orElseThrow();

        assertThat(entityManager.contains(managed)).isTrue();

        int updated = approverRepository.shiftPendingSequences(dcdMngNo, List.of(1, 2), 100);

        assertThat(updated).isEqualTo(2);
        assertThat(entityManager.contains(managed)).isFalse();
        assertThat(approverRepository.findByDcdMngNoOrderByDcrSqnSnoAsc(dcdMngNo))
                .extracting(Cdecim::getDcrSqnSno)
                .containsExactly(3, 101, 102);
    }

    private Cdecim decision(String apfMngNo, int sequence, String eno) {
        return Cdecim.builder()
                .dcdMngNo(apfMngNo)
                .dcrSqnSno(sequence)
                .dcrEno(eno)
                .itPtlDcdStsC("1")
                .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                .dcdDtm(LocalDate.of(2026, 8, 29))
                .dcrOpnnCone("의견-" + sequence)
                .lstDcdYn("N")
                .fstEnrDtm(LocalDateTime.of(2026, 8, 29, 9, 0))
                .fstEnrUsid("BE-T2")
                .lstChgDtm(LocalDateTime.of(2026, 8, 29, 9, 0))
                .lstChgUsid("BE-T2")
                .delYn("N")
                .build();
    }
}
