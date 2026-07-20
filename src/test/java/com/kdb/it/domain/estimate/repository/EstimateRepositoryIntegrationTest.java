package com.kdb.it.domain.estimate.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("EstimateRepository Oracle 통합 테스트")
class EstimateRepositoryIntegrationTest extends AbstractOracleRepositoryTest {

    @Autowired
    private EstimateRepository estimateRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("search는 Oracle 스키마에서 예외 없이 실행된다")
    void search_executesWithoutThrowing() {
        assertThatCode(() -> estimateRepository.search(null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("search는 개발비 일반(103) 사업만 노출하고 감리/컨설팅(104) 단독 사업은 제외한다")
    void search_excludesConsultingOnlyDevelopmentCost() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String generalProjectNo = "PRJ-TST-" + suffix + "A";
        String consultingProjectNo = "PRJ-TST-" + suffix + "B";

        persistProjectWithItem(generalProjectNo, "103");
        persistProjectWithItem(consultingProjectNo, "104");
        entityManager.flush();
        entityManager.clear();

        assertThat(estimateRepository.search(null, generalProjectNo, null))
                .extracting(item -> item.cncdRfrNo())
                .contains(generalProjectNo);
        assertThat(estimateRepository.search(null, consultingProjectNo, null)).isEmpty();
    }

    private void persistProjectWithItem(String projectNo, String ioeC) {
        LocalDateTime now = LocalDateTime.now();
        Bprojm project = Bprojm.builder()
                .abusMngNo(projectNo)
                .sno(1)
                .abusNm("테스트 사업 " + ioeC)
                .svnDpmC("18001")
                .svnDpmNm("IT기획부")
                .sttDtm(LocalDate.of(2026, 1, 1))
                .endDtm(LocalDate.of(2026, 12, 31))
                .lstYn("Y")
                .fstEnrDtm(now)
                .fstEnrUsid("TEST")
                .lstChgDtm(now)
                .lstChgUsid("TEST")
                .build();
        Bitemm item = Bitemm.builder()
                .gclMngNo(("GCL" + UUID.randomUUID().toString().replace("-", "")).substring(0, 16))
                .sno(1)
                .abusMngNo(projectNo)
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .lstYn("Y")
                .amt(new BigDecimal("100000000"))
                .mplAmt(BigDecimal.ZERO)
                .fstEnrDtm(now)
                .fstEnrUsid("TEST")
                .lstChgDtm(now)
                .lstChgUsid("TEST")
                .build();
        entityManager.persist(project);
        entityManager.persist(item);
    }
}
