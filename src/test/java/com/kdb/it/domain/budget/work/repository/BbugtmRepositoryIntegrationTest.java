package com.kdb.it.domain.budget.work.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * BbugtmRepository 벌크 soft-delete UPDATE 통합 테스트 (P1 #1).
 *
 * <p>실 로컬 Oracle(ITPOWN)에 @DataJpaTest로 연결하며, 모든 픽스처 INSERT/UPDATE는 테스트 종료 시 트랜잭션 롤백되어 dev 데이터에 영향을
 * 주지 않는다. 벌크 UPDATE는 1차 캐시를 우회하므로, clear 후 TestEntityManager로 재조회하여 DB 상태를 검증한다.
 */
@DisplayName("BbugtmRepository 벌크 soft-delete UPDATE (P1 #1)")
class BbugtmRepositoryIntegrationTest extends AbstractOracleRepositoryTest {

    private static final String YEAR = "9999";
    private static final String OTHER_YEAR = "9998";

    @Autowired private BbugtmRepository bbugtmRepository;

    @Autowired private TestEntityManager em;

    /**
     * 테스트 픽스처 행 생성 — 고유 bgNo/sno로 PK 충돌 방지.
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 SecurityContext가 없어 JPA Auditing(@CreatedBy)이 NOT NULL인
     * FST_ENR_USID/FST_ENR_DTM을 채우지 못한다(ORA-01400). 따라서 감사컬럼을 픽스처에서 직접 세팅한다. 이는 테스트 픽스처 한정이며 운영 로직과
     * 무관하다.
     */
    private Bbugtm insertRow(String bgNo, int sno, String bseYy, String delYn) {
        Bbugtm row =
                Bbugtm.builder()
                        .bgNo(bgNo)
                        .sno(sno)
                        .bseYy(bseYy)
                        .fntTbNm("BITEMM")
                        .pkColNm("PK-" + bgNo)
                        .fntTbCrySno(sno)
                        .ioeC("001")
                        .bgDupAmt(BigDecimal.valueOf(1000))
                        .asgRt(100)
                        .delYn(delYn)
                        .fstEnrUsid("FIXTURE")
                        .fstEnrDtm(LocalDateTime.now())
                        .lstChgUsid("FIXTURE")
                        .lstChgDtm(LocalDateTime.now())
                        .build();
        return em.persist(row);
    }

    @Test
    @DisplayName("해당 연도의 미삭제 행만 DEL_YN='Y'로 전환하고 감사컬럼을 세팅한다")
    void softDeleteByBseYy_marksOnlyTargetYearActiveRows() {
        // Arrange: 대상연도 미삭제 2건 + 대상연도 이미삭제 1건 + 타연도 미삭제 1건
        Bbugtm active1 = insertRow("BG-T9999-001", 1, YEAR, "N");
        Bbugtm active2 = insertRow("BG-T9999-002", 2, YEAR, "N");
        Bbugtm alreadyDeleted = insertRow("BG-T9999-003", 3, YEAR, "Y");
        Bbugtm otherYear = insertRow("BG-T9998-001", 1, OTHER_YEAR, "N");
        em.flush();
        em.clear();

        LocalDateTime now = LocalDateTime.now();

        // Act
        int updated = bbugtmRepository.softDeleteByBseYy(YEAR, "TESTUSER", now);

        // Assert: 미삭제 2건만 영향
        assertThat(updated).isEqualTo(2);

        em.clear(); // 벌크 UPDATE 우회분 반영 — 1차 캐시 비우고 DB 재조회
        Bbugtm reloaded = em.find(Bbugtm.class, idOf(active1));
        assertThat(reloaded.getDelYn()).isEqualTo("Y");
        assertThat(reloaded.getLstChgUsid()).isEqualTo("TESTUSER");
        assertThat(reloaded.getLstChgDtm()).isNotNull();
        assertThat(em.find(Bbugtm.class, idOf(active2)).getDelYn()).isEqualTo("Y");
        // 이미 삭제된 행과 타연도 행은 불변
        assertThat(em.find(Bbugtm.class, idOf(alreadyDeleted)).getLstChgUsid())
                .isNotEqualTo("TESTUSER");
        assertThat(em.find(Bbugtm.class, idOf(otherYear)).getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("대상 연도에 미삭제 행이 없으면 0을 반환한다")
    void softDeleteByBseYy_noActiveRows_returnsZero() {
        insertRow("BG-T9999-009", 9, YEAR, "Y");
        em.flush();
        em.clear();

        int updated = bbugtmRepository.softDeleteByBseYy(YEAR, "TESTUSER", LocalDateTime.now());

        assertThat(updated).isZero();
    }

    /** Bbugtm 복합키(BbugtmId) 생성 헬퍼. */
    private com.kdb.it.domain.budget.work.entity.BbugtmId idOf(Bbugtm row) {
        return new com.kdb.it.domain.budget.work.entity.BbugtmId(row.getBgNo(), row.getSno());
    }
}
