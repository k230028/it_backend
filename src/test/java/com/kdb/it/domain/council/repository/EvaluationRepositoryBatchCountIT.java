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
 * <p>per-evaluator COUNT 루프(N+1)를 대체하는 {@code countByEnoForCouncil}이 협의회ID당 1회 GROUP BY로 평가자별 제출 항목
 * 수를 정확히 집계함을 로컬 Oracle로 검증한다. {@code @DataJpaTest} 트랜잭션 롤백으로 픽스처는 테스트 종료 시 사라진다.
 */
class EvaluationRepositoryBatchCountIT extends AbstractOracleRepositoryTest {

    @Autowired private EvaluationRepository evaluationRepository;

    @Autowired private EntityManager entityManager;

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
        Map<String, Long> countByEno =
                evaluationRepository.countByEnoForCouncil(ASCT_ID, "N").stream()
                        .collect(
                                Collectors.toMap(
                                        r -> (String) r[0], r -> ((Number) r[1]).longValue()));

        // Assert
        assertThat(countByEno).containsEntry("ENOA", 6L).containsEntry("ENOB", 4L);
    }

    @Test
    @DisplayName("countByEnoForCouncil: delYn=Y 행은 집계에서 제외된다")
    void countByEnoForCouncil_delYnY행_집계제외() {
        // Arrange — 동일 평가자(ENOC)에 활성 3건 + 소프트삭제 1건. WHERE delYn='N'이 삭제 행을 제외해야 한다.
        for (String itm : List.of("01", "02", "03")) {
            entityManager.persist(fixture("ENOC", itm, 5, "N"));
        }
        entityManager.persist(fixture("ENOC", "04", 5, "Y"));
        entityManager.flush();
        entityManager.clear();

        // Act
        Map<String, Long> countByEno =
                evaluationRepository.countByEnoForCouncil(ASCT_ID, "N").stream()
                        .collect(
                                Collectors.toMap(
                                        r -> (String) r[0], r -> ((Number) r[1]).longValue()));

        // Assert — 활성 3건만 집계되고 소프트삭제 행은 카운트되지 않는다
        assertThat(countByEno).containsEntry("ENOC", 3L);
    }

    /**
     * 테스트 픽스처 행 생성 (활성 행, delYn="N").
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 SecurityContext가 없어 JPA Auditing(@CreatedBy)이 NOT NULL인
     * FST_ENR_USID/FST_ENR_DTM 등을 채우지 못한다(ORA-01400). 따라서 감사컬럼과 delYn을 픽스처에서 직접 세팅한다. 이는 테스트 픽스처
     * 한정이며 운영 로직과 무관하다. ({@code BbugtmRepositoryIntegrationTest} 동일 패턴.)
     */
    private Bevalm fixture(String eno, String itPtlCkgItmTc, int quelRcrd) {
        return fixture(eno, itPtlCkgItmTc, quelRcrd, "N");
    }

    /** 테스트 픽스처 행 생성 (delYn 지정). 소프트삭제 제외 검증에서 delYn="Y" 행을 만든다. */
    private Bevalm fixture(String eno, String itPtlCkgItmTc, int quelRcrd, String delYn) {
        return Bevalm.builder()
                .itPtlAsctId(ASCT_ID)
                .eno(eno)
                .itPtlCkgItmTc(itPtlCkgItmTc)
                .quelRcrd(quelRcrd)
                .delYn(delYn)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
    }
}
