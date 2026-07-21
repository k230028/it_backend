package com.kdb.it.domain.budget.document.repository;

import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRequestDocVersionProjectionIt extends AbstractOracleRepositoryTest {

    private static final String DOC_MNG_NO = "DOC-BE03-0001";

    @Autowired ServiceRequestDocRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 활성버전만버전내림차순으로조회하고대용량본문은읽지않는다() {
        entityManager.persist(document(100, "N", LocalDateTime.of(2026, 7, 21, 9, 0),
                LocalDateTime.of(2026, 7, 21, 9, 5)));
        entityManager.persist(document(101, "N", LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 10, 5)));
        entityManager.persist(document(200, "N", LocalDateTime.of(2026, 7, 21, 11, 0),
                LocalDateTime.of(2026, 7, 21, 11, 5)));
        entityManager.persist(document(300, "Y", LocalDateTime.of(2026, 7, 21, 12, 0),
                LocalDateTime.of(2026, 7, 21, 12, 5)));
        entityManager.flush();
        entityManager.clear();

        List<ServiceRequestDocRepository.VersionHistoryView> views = repository
                .findAllProjectedByDocMngNoAndDelYnOrderByDocVrsSnoDesc(DOC_MNG_NO, "N");

        assertThat(views).hasSize(3);
        assertThat(views).extracting(ServiceRequestDocRepository.VersionHistoryView::getDocMngNo)
                .containsOnly(DOC_MNG_NO);
        assertThat(views).extracting(ServiceRequestDocRepository.VersionHistoryView::getDocVrsSno)
                .containsExactly(new BigDecimal("200"), new BigDecimal("101"), new BigDecimal("100"));
        assertThat(views).extracting(ServiceRequestDocRepository.VersionHistoryView::getFstEnrDtm)
                .containsExactly(
                        LocalDateTime.of(2026, 7, 21, 11, 0),
                        LocalDateTime.of(2026, 7, 21, 10, 0),
                        LocalDateTime.of(2026, 7, 21, 9, 0));
        assertThat(views).extracting(ServiceRequestDocRepository.VersionHistoryView::getLstChgDtm)
                .containsExactly(
                        LocalDateTime.of(2026, 7, 21, 11, 5),
                        LocalDateTime.of(2026, 7, 21, 10, 5),
                        LocalDateTime.of(2026, 7, 21, 9, 5));
        assertThat(views).extracting(ServiceRequestDocRepository.VersionHistoryView::getDelYn)
                .containsOnly("N");
        assertThat(ServiceRequestDocRepository.VersionHistoryView.class.getDeclaredMethods()).hasSize(5);
    }

    private Brdocm document(
            int storedVersion,
            String delYn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return Brdocm.builder()
                .docMngNo(DOC_MNG_NO)
                .docVrsSno(new BigDecimal(storedVersion))
                .reqTtl("BE-03 프로젝션 검증")
                .redtConeInf("가".repeat(12_000))
                .delYn(delYn)
                .fstEnrDtm(createdAt)
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(updatedAt)
                .lstChgUsid("BE03-TEST")
                .build();
    }
}
