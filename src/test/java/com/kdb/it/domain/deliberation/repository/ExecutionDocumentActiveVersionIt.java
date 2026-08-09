package com.kdb.it.domain.deliberation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.payment.entity.Bpaymm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 집행 문서 3종(BDELIM·BCONTM·BPAYMM)의 활성 최신 버전 단일성을 실제 Oracle에서 확인한다 (BE-24).
 *
 * <p>세 도메인의 서비스는 생성 시 {@code docVrsSno}를 <b>1 상수</b>로 넣고, {@code lstYn}을 {@code 'N'}으로 내리는 코드가 어디에도
 * 없다. 그래서 물리 PK {@code (DOC_MNG_NO, DOC_VRS_SNO)}가 이미 "문서당 활성행 1건"을 강제한다 — 리포지토리의 {@code
 * fetchOne()}이 {@code NonUniqueResultException}을 낼 수 없는 이유가 이것이다.
 *
 * <p>이 테스트는 그 전제를 <b>실측으로 고정</b>한다. 나아가 {@code docVrsSno}만 다른 두 번째 활성행도 거부되는지 함께 확인한다 — PK가 막는 것은
 * "같은 버전의 중복"뿐이라 버전 기능이 추가되면 활성행이 둘이 될 수 있었고, 그 구멍을 막으려고 {@code
 * V20260809_001__AddExecutionDocumentActiveVersionUniqueIndex.sql}이 세 테이블에 함수 기반 UNIQUE 인덱스 ({@code
 * CASE WHEN LST_YN='Y' AND DEL_YN='N' THEN DOC_MNG_NO END})를 추가했다.
 *
 * <p>영속성 컨텍스트를 비운 뒤 다시 persist하므로 JPA 레벨의 단축 경로가 아니라 실제 INSERT가 DB 제약에 걸린다. 단언은 예외 종류가 아니라 {@code
 * ORA-00001}을 직접 확인한다 — 종류만 보면 NOT NULL 누락 같은 다른 이유로 실패해도 통과하기 때문이다.
 */
@DisplayName("집행 문서 3종: 문서당 활성행 1건을 DB가 강제한다 (BE-24)")
class ExecutionDocumentActiveVersionIt extends AbstractOracleRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 14, 0);
    private static final String ACTOR = "EDA-TEST";

    @Autowired EntityManager entityManager;

    /** 테스트마다 새로 만드는 식별자 접미사 — 팩토리 람다에서 참조하려고 필드로 둔다. */
    private String fixedSuffix;

    @Test
    @DisplayName("BDELIM: 같은 (문서번호, 버전)으로 두 번째 행을 넣으면 DB가 거부한다")
    void bdelim_같은PK중복_거부() {
        assertDuplicateRejected("BDELIM", vrs -> deliberation("EDA-DLB-" + fixedSuffix, vrs));
    }

    @Test
    @DisplayName("BCONTM: 같은 (문서번호, 버전)으로 두 번째 행을 넣으면 DB가 거부한다")
    void bcontm_같은PK중복_거부() {
        assertDuplicateRejected("BCONTM", vrs -> contract("EDA-CTR-" + fixedSuffix, vrs));
    }

    @Test
    @DisplayName("BPAYMM: 같은 (문서번호, 버전)으로 두 번째 행을 넣으면 DB가 거부한다")
    void bpaymm_같은PK중복_거부() {
        assertDuplicateRejected("BPAYMM", vrs -> payment("EDA-PAY-" + fixedSuffix, vrs));
    }

    @Test
    @DisplayName("버전이 달라도 같은 문서의 두 번째 활성행은 UNIQUE 인덱스가 거부한다")
    void 버전이달라도_두번째활성행_거부() {
        fixedSuffix = suffix();
        String docMngNo = "EDA-GAP-" + fixedSuffix;

        entityManager.persist(deliberation(docMngNo, 1));
        entityManager.flush();
        entityManager.clear();

        // PK는 (DOC_MNG_NO, DOC_VRS_SNO)라 버전이 다르면 통과한다. 여기서 막는 주체는
        // IX_TPRMPP_BDELIM_03(활성행만 색인하는 함수 기반 UNIQUE 인덱스)이다.
        assertThatThrownBy(
                        () -> {
                            entityManager.persist(deliberation(docMngNo, 2));
                            entityManager.flush();
                        })
                .as("버전이 달라도 활성행은 문서당 1건만 허용해야 한다")
                .hasStackTraceContaining("ORA-00001");
    }

    @Test
    @DisplayName("이전 버전을 LST_YN='N'으로 내리면 새 활성 버전을 만들 수 있다")
    void 이전버전을_내리면_새활성버전_허용() {
        fixedSuffix = suffix();
        String docMngNo = "EDA-ROLL-" + fixedSuffix;

        entityManager.persist(deliberation(docMngNo, 1));
        entityManager.flush();

        // 활성 플래그를 내린 행은 인덱스 식이 NULL이 되어 색인 대상에서 빠진다.
        entityManager
                .createQuery(
                        "UPDATE Bdelim d SET d.lstYn = 'N'"
                                + " WHERE d.docMngNo = :no AND d.docVrsSno = 1")
                .setParameter("no", docMngNo)
                .executeUpdate();
        entityManager.clear();

        entityManager.persist(deliberation(docMngNo, 2));
        entityManager.flush();
        entityManager.clear();

        Long activeRows =
                entityManager
                        .createQuery(
                                "SELECT COUNT(d) FROM Bdelim d WHERE d.docMngNo = :no"
                                        + " AND d.lstYn = 'Y' AND d.delYn = 'N'",
                                Long.class)
                        .setParameter("no", docMngNo)
                        .getSingleResult();

        // 인덱스가 버전 전환 자체를 막지는 않는다 — 막는 것은 "활성행이 둘인 상태"뿐이다.
        assertThat(activeRows).isEqualTo(1);
    }

    /**
     * 같은 PK로 두 번째 행을 넣으면 DB가 거부하는지 확인한다.
     *
     * @param table 대상 테이블명 (단언 메시지용)
     * @param factory 버전을 받아 엔티티를 만드는 팩토리
     */
    private void assertDuplicateRejected(String table, IntFunction<Object> factory) {
        fixedSuffix = suffix();

        entityManager.persist(factory.apply(1));
        entityManager.flush();
        // 컨텍스트를 비워야 두 번째 persist가 JPA 단축 경로가 아닌 실제 INSERT로 나간다.
        entityManager.clear();

        assertThatThrownBy(
                        () -> {
                            entityManager.persist(factory.apply(1));
                            entityManager.flush();
                        })
                // 예외 종류만 보면 다른 이유(NOT NULL 누락 등)로 실패해도 통과한다.
                // Oracle이 PK 제약으로 거부했다는 사실 자체를 고정한다.
                .as("%s: 같은 (DOC_MNG_NO, DOC_VRS_SNO)는 PK가 거부해야 한다", table)
                .hasStackTraceContaining("ORA-00001");
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Bdelim deliberation(String docMngNo, int docVrsSno) {
        return Bdelim.builder()
                .docMngNo(docMngNo)
                .docVrsSno(docVrsSno)
                .ioeC("100")
                .cncdRfrNo("EDA-REF-" + fixedSuffix)
                .stsTc("01")
                .taskDbrTc("1")
                .taskDbrRltTc("1")
                .taskDbrTod("1")
                .lstYn("Y")
                .delYn("N")
                .fstEnrDtm(CREATED_AT)
                .fstEnrUsid(ACTOR)
                .lstChgDtm(CREATED_AT)
                .lstChgUsid(ACTOR)
                .build();
    }

    private Bcontm contract(String docMngNo, int docVrsSno) {
        return Bcontm.builder()
                .docMngNo(docMngNo)
                .docVrsSno(docVrsSno)
                .ioeC("100")
                .cncdRfrNo("EDA-REF-" + fixedSuffix)
                .stsTc("01")
                .lstYn("Y")
                .delYn("N")
                .fstEnrDtm(CREATED_AT)
                .fstEnrUsid(ACTOR)
                .lstChgDtm(CREATED_AT)
                .lstChgUsid(ACTOR)
                .build();
    }

    private Bpaymm payment(String docMngNo, int docVrsSno) {
        return Bpaymm.builder()
                .docMngNo(docMngNo)
                .docVrsSno(docVrsSno)
                .ioeC("100")
                .cncdRfrNo("EDA-REF-" + fixedSuffix)
                .stsTc("01")
                .lstYn("Y")
                .delYn("N")
                .fstEnrDtm(CREATED_AT)
                .fstEnrUsid(ACTOR)
                .lstChgDtm(CREATED_AT)
                .lstChgUsid(ACTOR)
                .build();
    }
}
