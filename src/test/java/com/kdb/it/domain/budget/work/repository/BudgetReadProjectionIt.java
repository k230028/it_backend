package com.kdb.it.domain.budget.work.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BudgetReadProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired BbugtmRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 예산읽기프로젝션은집계에필요한일곱필드를엔티티와동일하게반환한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String year = "97" + suffix.substring(0, 2);
        String bgNo = "B3-" + suffix;
        LocalDateTime now = LocalDateTime.of(2097, 12, 31, 23, 40);
        Bbugtm entity =
                Bbugtm.builder()
                        .bgNo(bgNo)
                        .sno(17)
                        .bseYy(year)
                        .pkColNm("BE03-PK-" + suffix)
                        .fntTbNm("BITEMM")
                        .fntTbCrySno(3)
                        .ioeC("B3IOE01")
                        .bgDupAmt(new BigDecimal("123456.789"))
                        .asgRt(new BigDecimal("73"))
                        .delYn("N")
                        .fstEnrDtm(now)
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(now)
                        .lstChgUsid("BE03-TEST")
                        .build();
        entityManager.persist(entity);
        entityManager.flush();
        entityManager.clear();

        Bbugtm loaded =
                repository.findByBseYyAndDelYn(year, "N").stream()
                        .filter(row -> bgNo.equals(row.getBgNo()))
                        .findFirst()
                        .orElseThrow();
        BudgetReadView view =
                repository.findReadViewsByBseYyAndDelYn(year, "N").stream()
                        .filter(row -> bgNo.equals(row.getBgNo()))
                        .findFirst()
                        .orElseThrow();

        assertThat(view.getBgNo()).isEqualTo(loaded.getBgNo());
        assertThat(view.getSno()).isEqualTo(loaded.getSno());
        assertThat(view.getPkColNm()).isEqualTo(loaded.getPkColNm());
        assertThat(view.getFntTbNm()).isEqualTo(loaded.getFntTbNm());
        assertThat(view.getIoeC()).isEqualTo(loaded.getIoeC());
        assertThat(view.getBgDupAmt()).isEqualByComparingTo(loaded.getBgDupAmt());
        assertThat(view.getAsgRt()).isEqualByComparingTo(loaded.getAsgRt());
        assertThat(BudgetReadView.class.getDeclaredMethods()).hasSize(7);
    }
}
