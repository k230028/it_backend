package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.kdb.it.infra.file.repository.FileRepository;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 게시물 첨부파일 캐시 동기화의 부모 행 잠금과 재집계 순서를 실제 로컬 Oracle 트랜잭션으로 검증합니다.
 *
 * <p>첫 스레드가 활성 파일 수를 읽은 직후 repository spy에서 대기하는 동안 두 번째 파일을 커밋하고 신규 동기화를 시작합니다. count-before-lock
 * 구현이면 신규 값 2가 먼저 커밋된 뒤 구 값 1이 덮어쓰지만, lock-before-count 구현이면 첫 스레드가 이미 부모 잠금을 보유하므로 신규 동기화가 대기했다가
 * 파일 수 2를 다시 읽어 최종 캐시를 2로 유지합니다.
 */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class BoardPostFileCacheConcurrencyIT {

    private static final String PREFIX = "BRD11";

    @Autowired private BoardPostFileCacheService fileCacheService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private FileRepository fileRepository;

    private String nacMngNo;
    private String uid;

    @BeforeEach
    void setUp() {
        uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        nacMngNo = PREFIX + uid;
        cleanup();
        insertBoardPost();
        insertFile("A");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("구 count 동기화가 신 count 뒤에 커밋되어 게시물 캐시를 덮어쓰지 못한다")
    void concurrentSync_구Count지연_최종캐시는최신활성파일수() {
        CountDownLatch olderCountRead = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);

        doAnswer(
                        invocation -> {
                            Long count =
                                    jdbcTemplate.queryForObject(
                                            "SELECT COUNT(*) FROM TPRMPP_CFILEM "
                                                    + "WHERE PK_COL_NM = ? AND PK_CONE = ? AND DEL_YN = ?",
                                            Long.class,
                                            "공통게시판",
                                            nacMngNo,
                                            "N");
                            if (Thread.currentThread().getName().equals("older-cache-sync")) {
                                olderCountRead.countDown();
                                if (!releaseOlder.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("구 count 동기화 대기 제한시간을 초과했습니다.");
                                }
                            }
                            return count;
                        })
                .when(fileRepository)
                .countByPkColNmAndPkConeAndDelYn(eq("공통게시판"), eq(nacMngNo), eq("N"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(20),
                    () -> {
                        Future<?> older =
                                executor.submit(
                                        () -> {
                                            Thread.currentThread().setName("older-cache-sync");
                                            fileCacheService.syncFromActiveFiles(nacMngNo);
                                        });
                        if (!olderCountRead.await(5, TimeUnit.SECONDS)) {
                            older.get(1, TimeUnit.SECONDS);
                            throw new IllegalStateException("구 count 동기화가 count 지점에 도달하지 못했습니다.");
                        }

                        insertFile("B");
                        Future<?> newer =
                                executor.submit(
                                        () -> {
                                            Thread.currentThread().setName("newer-cache-sync");
                                            fileCacheService.syncFromActiveFiles(nacMngNo);
                                        });

                        releaseOlder.countDown();
                        older.get(10, TimeUnit.SECONDS);
                        newer.get(10, TimeUnit.SECONDS);
                    });
        } finally {
            releaseOlder.countDown();
            executor.shutdownNow();
        }

        Integer cachedCount =
                jdbcTemplate.queryForObject(
                        "SELECT APG_FL_NBR FROM TPRMPP_CBLBCM WHERE NAC_NO = ?",
                        Integer.class,
                        nacMngNo);
        String hasFile =
                jdbcTemplate.queryForObject(
                        "SELECT FL_APG_YN FROM TPRMPP_CBLBCM WHERE NAC_NO = ?",
                        String.class,
                        nacMngNo);
        assertThat(cachedCount).isEqualTo(2);
        assertThat(hasFile).isEqualTo("Y");
    }

    private void insertBoardPost() {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CBLBCM (NAC_NO, BLB_ID, NAC_TTL, ANC_YN, XPO_YN, NAC_INQ_NBR, "
                        + "APG_FL_NBR, FL_APG_YN, GRP_SQN_SNO, NAC_LEV_MNG_SNO, NAC_UNQ_ID, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, 'BRD11BLB', '동시성 캐시 테스트', 'N', 'Y', 0, 0, 'N', 0, 0, ?, "
                        + "'BRD11TEST', SYSDATE, 'N', ?, 1, 'BRD11TEST', SYSDATE)",
                nacMngNo,
                nacMngNo,
                UUID.randomUUID().toString());
    }

    private void insertFile(String suffix) {
        String fileId = PREFIX + suffix + uid;
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CFILEM (FL_MPN_ID, FL_NM, FL_PYS_NM, FL_KPN_PTH, FL_TP_CONE, "
                        + "PK_COL_NM, PK_CONE, FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, "
                        + "LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, ?, '/data/files/brd11', '첨부파일', '공통게시판', ?, "
                        + "'BRD11TEST', SYSDATE, 'N', ?, 1, 'BRD11TEST', SYSDATE)",
                fileId,
                fileId + ".pdf",
                fileId + "_physical.pdf",
                nacMngNo,
                UUID.randomUUID().toString());
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CFILEM WHERE FL_MPN_ID LIKE '" + PREFIX + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE NAC_NO LIKE '" + PREFIX + "%'");
    }
}
