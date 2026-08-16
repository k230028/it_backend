package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.NotFoundException;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 게시물 조회수 갱신의 소속·감사·동시성 계약을 실제 Oracle로 검증합니다. */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class BoardPostViewAuditIT {

    private static final String AUDITOR = "ITEST15";

    @Autowired private BoardPostService service;
    @Autowired private BoardMetaRepository metaRepository;
    @Autowired private BoardPostRepository postRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private String boardId;
    private String otherBoardId;
    private String postId;
    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        boardId = "ZB" + suffix;
        otherBoardId = "ZO" + suffix;
        postId = "ZP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        user = new CustomUserDetails(AUDITOR, List.of("ITPZZ001"), "100");
        authenticate();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            metaRepository.save(board(boardId));
                            metaRepository.save(board(otherBoardId));
                            postRepository.save(post());
                        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCL WHERE NAC_NO = ?", postId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", postId);
        jdbcTemplate.update(
                "DELETE FROM TPRMPP_CBLBML WHERE BLB_ID IN (?, ?)", boardId, otherBoardId);
        jdbcTemplate.update(
                "DELETE FROM TPRMPP_CBLBMM WHERE BLB_ID IN (?, ?)", boardId, otherBoardId);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("조회수 POST는 managed update 감사필드와 수정 스냅샷을 각각 한 번 남긴다")
    void incrementView_persistsAuditFieldsAndSingleUpdateSnapshot() throws Exception {
        LocalDateTime before = queryLastChangedAt();
        // Oracle 실 스키마의 감사시각 정밀도가 초 단위이므로 생성 시각과 다른 초에서 갱신한다.
        Thread.sleep(1100);

        service.incrementPostView(boardId, postId, user);

        assertThat(queryViewCount()).isOne();
        assertThat(queryLastChangedAt()).isAfter(before);
        assertThat(queryLastChangedBy()).isEqualTo(AUDITOR);
        assertThat(queryUpdateAuditCount()).isOne();
        assertThat(queryUpdateAuditViewCount()).isOne();
    }

    @Test
    @DisplayName("다른 게시판 경로는 404이며 조회수와 감사 로그를 변경하지 않는다")
    void incrementView_wrongBoard_doesNotMutate() {
        assertThatThrownBy(() -> service.incrementPostView(otherBoardId, postId, user))
                .isInstanceOf(NotFoundException.class);

        assertThat(queryViewCount()).isZero();
        assertThat(queryUpdateAuditCount()).isZero();
    }

    @Test
    @DisplayName("동시 조회수 POST 두 건은 비관적 잠금으로 손실 없이 +2 된다")
    void incrementView_concurrently_updatesTwice() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> incrementAfterBarrier(ready, start));
            var second = executor.submit(() -> incrementAfterBarrier(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        assertThat(queryViewCount()).isEqualTo(2);
        assertThat(queryUpdateAuditCount()).isEqualTo(2);
    }

    private void incrementAfterBarrier(CountDownLatch ready, CountDownLatch start) {
        authenticate();
        try {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            service.incrementPostView(boardId, postId, user);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AUDITOR, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Cblbmm board(String id) {
        return Cblbmm.builder()
                .blbMngNo(id)
                .blbNm("SEC-15 조회수 통합 테스트")
                .itPtlBlbTc("002")
                .repUseYn("Y")
                .cmmtUseYn("Y")
                .flEsnYn("N")
                .hedTagUseYn("N")
                .sreSqnNo(999)
                .useYn("Y")
                .build();
    }

    private Cblbcm post() {
        return Cblbcm.builder()
                .nacMngNo(postId)
                .blbMngNo(boardId)
                .nacNm("SEC-15 조회수")
                .nacCone("본문")
                .nacInqNbr(0)
                .nacUnqId(postId)
                .ancYn("N")
                .xpoYn("Y")
                .bbrC("100")
                .flApgYn("N")
                .flNbr(0)
                .nacGrpSqn(0)
                .nacGrpLev(0)
                .build();
    }

    private int queryViewCount() {
        return jdbcTemplate.queryForObject(
                "SELECT NAC_INQ_NBR FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", Integer.class, postId);
    }

    private LocalDateTime queryLastChangedAt() {
        Timestamp value =
                jdbcTemplate.queryForObject(
                        "SELECT LST_CHG_DTM FROM TPRMPP_CBLBCM WHERE NAC_NO = ?",
                        Timestamp.class,
                        postId);
        return value.toLocalDateTime();
    }

    private String queryLastChangedBy() {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_USID FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", String.class, postId);
    }

    private int queryUpdateAuditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CBLBCL WHERE NAC_NO = ? AND CHG_DTT_YN = 'U'",
                Integer.class,
                postId);
    }

    private int queryUpdateAuditViewCount() {
        return jdbcTemplate.queryForObject(
                "SELECT NAC_INQ_NBR FROM TPRMPP_CBLBCL WHERE NAC_NO = ? AND CHG_DTT_YN = 'U'",
                Integer.class,
                postId);
    }
}
