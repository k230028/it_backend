package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #4 평가자별 제출항목 수 배치 COUNT 통합 테스트.
 *
 * <p>per-evaluator COUNT 루프(N+1)를 대체하는 {@code countByEnoForCouncil}이
 * 협의회ID당 1회 GROUP BY로 평가자별 제출 항목 수를 정확히 집계함을 로컬 Oracle로 검증한다.
 * {@code @DataJpaTest} 트랜잭션 롤백으로 픽스처는 테스트 종료 시 사라진다.</p>
 */
class EvaluationRepositoryBatchCountIT extends AbstractOracleRepositoryTest {

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String ASCT_ID = "ASCT-TEST-NPLUS1";

    @Test
    @DisplayName("countByEnoForCouncil: 협의회ID당 1회 GROUP BY로 평가자별 제출 항목 수를 반환한다")
    void countByEnoForCouncil_평가자별_제출항목수_집계() {
        // Arrange — eno A는 6항목(완료), eno B는 4항목(미완료) 제출
        for (String itm : List.of("01", "02", "03", "04", "05", "06")) {
            entityManager.persist(fixture("ENOA", itm, 5));
        }
        for (String itm : List.of("01", "02", "03", "04")) {
            entityManager.persist(fixture("ENOB", itm, 4));
        }
        entityManager.flush();
        entityManager.clear();

        // Act
        Map<String, Long> countByEno = evaluationRepository.countByEnoForCouncil(ASCT_ID, "N").stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));

        // Assert
        assertThat(countByEno).containsEntry("ENOA", 6L).containsEntry("ENOB", 4L);
    }

    /**
     * 테스트 픽스처 행 생성.
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 SecurityContext가 없어 JPA Auditing(@CreatedBy)이
     * NOT NULL인 FST_ENR_USID/FST_ENR_DTM 등을 채우지 못한다(ORA-01400). 따라서 감사컬럼과
     * delYn을 픽스처에서 직접 세팅한다. 이는 테스트 픽스처 한정이며 운영 로직과 무관하다.
     * ({@code BbugtmRepositoryIntegrationTest} 동일 패턴.)</p>
     */
    private Bevalm fixture(String eno, String itPtlCkgItmTc, int quelRcrd) {
        return Bevalm.builder()
                .itPtlAsctId(ASCT_ID)
                .eno(eno)
                .itPtlCkgItmTc(itPtlCkgItmTc)
                .quelRcrd(quelRcrd)
                .delYn("N")
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
    }
}
