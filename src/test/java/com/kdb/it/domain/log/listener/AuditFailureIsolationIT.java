package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.support.OracleAvailableCondition;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 감사 저장 실패와 원 업무 트랜잭션의 격리를 실제 로컬 Oracle로 검증하는 통합 테스트(ERR-06).
 *
 * <p>대상 엔티티는 외래키가 없는 전산관리비 마스터 {@link Bcostm}(테이블 {@code TPRMPP_BCOSTM})이며,
 * 대응 감사 로그는 {@code TPRMPP_BCOSTL}(시퀀스 {@code SQ_TPRMPP_BCOSTL_1})이다. 각 테스트는 {@code ZZIT}로
 * 시작하는 고유 업무 PK({@code BG_NO})를 직접 생성하고 종료 시 두 테이블에서 자신의 행만 삭제한다.</p>
 *
 * <p>검증 시나리오</p>
 * <ul>
 *   <li>원 업무 커밋 + writer 성공 → 업무 행과 감사 행이 모두 존재한다.</li>
 *   <li>원 업무 롤백 → 업무 행과 감사 행이 모두 없다({@code afterCommit} 미발화).</li>
 *   <li>{@code afterCommit} 감사 쓰기 강제 실패 → 업무 행은 남고 감사 행은 없으며
 *       {@code audit.log.write.failure{stage=afterCommit}} 카운터가 정확히 1 증가한다.</li>
 *   <li>같은 스레드의 다음 정상 저장 성공 → 재진입 ThreadLocal 잔존이 없음을 증명한다.</li>
 * </ul>
 *
 * <p>{@code afterCommit} 콜백은 커밋 스레드에서 동기로 실행되므로 별도 대기(Awaitility) 없이
 * {@code execute(...)} 반환 직후 결과를 단언한다. 로컬 Oracle이 꺼져 있으면
 * {@link OracleAvailableCondition}이 컨텍스트 로드 전에 테스트를 깨끗하게 스킵한다.</p>
 */
@Tag("it")
@SpringBootTest(properties = {
        // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를 대체(EnvironmentValidator 통과).
        "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
class AuditFailureIsolationIT {

    /** 실패 카운터 이름(제한된 태그 entity/chgTp/stage). */
    private static final String FAILURE_METRIC = "audit.log.write.failure";
    /** 테스트가 생성하는 업무 PK 접두어 — 실 운영 데이터(COST-*)와 충돌하지 않음. */
    private static final String TEST_PK_PREFIX = "ZZIT";
    /** 감사 대상 엔티티 종류명(메트릭 태그 값). */
    private static final String ENTITY_NAME = "Bcostm";

    @Autowired
    private CostRepository costRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    /** 감사 쓰기를 강제로 실패시키기 위한 spy — 실제 REQUIRES_NEW 트랜잭션 프록시를 감싼다. */
    @MockitoSpyBean
    private AuditLogWriter auditLogWriter;

    private TransactionTemplate transactionTemplate;
    /** 정리 대상 업무 PK — 커밋/롤백과 무관하게 종료 시 두 테이블에서 삭제. */
    private final List<String> createdBgNos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // FST_ENR_USID/LST_CHG_USID(NOT NULL)를 JPA Auditing이 채우도록 인증 컨텍스트를 심는다.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "ITEST01", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        // 커밋된 업무/감사 행을 남기지 않도록 자신이 만든 PK만 정리한다(자식 로그 먼저).
        for (String bgNo : createdBgNos) {
            jdbcTemplate.update("DELETE FROM TPRMPP_BCOSTL WHERE BG_NO = ?", bgNo);
            jdbcTemplate.update("DELETE FROM TPRMPP_BCOSTM WHERE BG_NO = ?", bgNo);
        }
        createdBgNos.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("원 업무 커밋 + 감사 성공 → 업무 행과 감사 행이 모두 존재")
    void commitWithWriterSuccess_persistsBusinessAndAuditRows() {
        String bgNo = uniqueBgNo();

        insertCommitting(newBcostm(bgNo));

        assertThat(countBusinessRows(bgNo)).isEqualTo(1);
        assertThat(countAuditRows(bgNo)).isEqualTo(1);
    }

    @Test
    @DisplayName("원 업무 롤백 → 업무 행과 감사 행이 모두 없음")
    void businessRollback_persistsNeitherBusinessNorAuditRow() {
        String bgNo = uniqueBgNo();

        insertRollingBack(newBcostm(bgNo));

        assertThat(countBusinessRows(bgNo)).isZero();
        assertThat(countAuditRows(bgNo)).isZero();
    }

    @Test
    @DisplayName("afterCommit 감사 실패 → 업무 행 유지·감사 행 없음·실패 카운터 +1, 다음 저장은 성공")
    void auditWriteFailure_isolatedFromBusiness_andThreadLocalDoesNotLeak() {
        // 첫 감사 쓰기만 예외를 던지고 이후 호출은 실제 동작(REQUIRES_NEW INSERT)한다.
        doThrow(new DataIntegrityViolationException("강제 감사 INSERT 실패 (지연 DB 오류 모사)"))
                .doCallRealMethod()
                .when(auditLogWriter).writeInNewTransaction(any());

        double before = failureCount("afterCommit");

        // 1) 감사 실패 케이스 — 원 업무는 커밋되어야 하고 감사 행은 없어야 한다.
        String failingBgNo = uniqueBgNo();
        insertCommitting(newBcostm(failingBgNo));

        assertThat(countBusinessRows(failingBgNo)).isEqualTo(1);
        assertThat(countAuditRows(failingBgNo)).isZero();
        assertThat(failureCount("afterCommit")).isEqualTo(before + 1.0);
        // 실패 처리 후 재진입 가드가 반드시 해제되어 있어야 한다.
        assertThat(AuditFailureRecorder.isHandlingFailure()).isFalse();

        // 2) 같은 테스트 스레드의 다음 정상 저장 — ThreadLocal 잔존이 없으므로 감사 행이 정상 적재된다.
        String recoveredBgNo = uniqueBgNo();
        insertCommitting(newBcostm(recoveredBgNo));

        assertThat(countBusinessRows(recoveredBgNo)).isEqualTo(1);
        assertThat(countAuditRows(recoveredBgNo)).isEqualTo(1);
    }

    /**
     * 업무 엔티티를 커밋 트랜잭션 안에서 저장한다.
     *
     * <p>{@code saveAndFlush}로 트랜잭션 내 INSERT와 {@code @PrePersist}를 확정해 커밋 후
     * {@code afterCommit} 감사 쓰기를 예약한다. 정상 반환 시 커밋되어 콜백이 동기로 실행된다.</p>
     *
     * @param entity 저장할 업무 엔티티
     */
    private void insertCommitting(Bcostm entity) {
        transactionTemplate.execute(status -> {
            costRepository.saveAndFlush(entity);
            return null;
        });
    }

    /**
     * 업무 엔티티를 저장한 뒤 트랜잭션을 롤백한다.
     *
     * <p>{@code @PrePersist}로 감사 동기화가 예약된 상태에서 롤백해도 {@code afterCommit}이
     * 발화하지 않아 감사 INSERT가 실행되지 않음을 검증하기 위한 경로다. 의도된 롤백 신호는
     * 콜백 밖에서 삼킨다.</p>
     *
     * @param entity 저장 후 롤백할 업무 엔티티
     */
    private void insertRollingBack(Bcostm entity) {
        try {
            transactionTemplate.execute(status -> {
                costRepository.saveAndFlush(entity);
                throw new RollbackSignal();
            });
        } catch (RollbackSignal ignored) {
            // 의도된 롤백 — afterCommit 미발화 검증용 신호.
        }
    }

    /**
     * 테스트용 최소 전산관리비 엔티티를 생성한다.
     *
     * <p>복합 PK({@code BG_NO}, {@code BG_SNO})만 지정하고 감사 공통값(DEL_YN/GUID/등록자·시각)은
     * {@code @PrePersist}·JPA Auditing이 채운다.</p>
     *
     * @param bgNo 고유 업무 PK
     * @return 저장 대상 엔티티
     */
    private Bcostm newBcostm(String bgNo) {
        return Bcostm.builder()
                .costBgNo(bgNo)
                .bgSno(1)
                .lstYn("Y")
                .bseYy("2026")
                .cttNm("ERR-06 격리 통합 테스트")
                .build();
    }

    /**
     * 정리 목록에 등록된 고유 업무 PK를 발급한다({@code BG_NO} 최대 15자 준수).
     *
     * @return {@code ZZIT} 접두 15자 고유 문자열
     */
    private String uniqueBgNo() {
        String bgNo = (TEST_PK_PREFIX + UUID.randomUUID().toString().replace("-", "")).substring(0, 15);
        createdBgNos.add(bgNo);
        return bgNo;
    }

    /**
     * 업무 테이블에서 해당 PK의 행 수를 센다.
     *
     * @param bgNo 업무 PK
     * @return 행 수(0 또는 1)
     */
    private int countBusinessRows(String bgNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_BCOSTM WHERE BG_NO = ?", Integer.class, bgNo);
        return count == null ? 0 : count;
    }

    /**
     * 감사 로그 테이블에서 해당 업무 PK의 행 수를 센다.
     *
     * @param bgNo 업무 PK
     * @return 감사 행 수
     */
    private int countAuditRows(String bgNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_BCOSTL WHERE BG_NO = ?", Integer.class, bgNo);
        return count == null ? 0 : count;
    }

    /**
     * 지정 단계의 감사 실패 카운터 값을 읽는다(미생성 시 0).
     *
     * @param stage 실패 단계(direct/afterCommit/schedule)
     * @return 카운터 누적값
     */
    private double failureCount(String stage) {
        Counter counter = meterRegistry.find(FAILURE_METRIC)
                .tags("entity", ENTITY_NAME, "chgTp", "C", "stage", stage)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    /** 의도된 롤백을 트리거하기 위한 내부 신호 예외. */
    private static final class RollbackSignal extends RuntimeException {
    }
}
