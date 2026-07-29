package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentRepository;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.NotFoundException;
import com.kdb.it.support.OracleAvailableCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 게시판 쓰기와 답글 순서의 비관적 잠금 계약을 실제 Oracle 트랜잭션으로 검증합니다. */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
class BoardWriteConcurrencyIT {

    private static final String AUDITOR = "ITEST15";

    @Autowired private BoardPostService postService;
    @Autowired private BoardCommentService commentService;
    @Autowired private BoardMetaRepository metaRepository;
    @MockitoSpyBean private BoardPostRepository postRepository;
    @MockitoSpyBean private BoardCommentRepository commentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private String boardId;
    private String postId;
    private String replyRootId;
    private String replyParentId;
    private String replyChildId;
    private String replySiblingId;
    private Long commentRootId;
    private Long commentParentId;
    private Long commentChildId;
    private Long commentSiblingId;
    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        boardId = "R5" + suffix;
        postId = "R5P0" + suffix;
        replyRootId = "R5R0" + suffix;
        replyParentId = "R5R1" + suffix;
        replyChildId = "R5R2" + suffix;
        replySiblingId = "R5R3" + suffix;
        commentRootId = nextCommentId();
        commentParentId = nextCommentId();
        commentChildId = nextCommentId();
        commentSiblingId = nextCommentId();
        user = new CustomUserDetails(AUDITOR, List.of("ITPZZ001"), "100");
        authenticate();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            metaRepository.save(board());
                            postRepository.saveAll(
                                    List.of(
                                            post(postId, postId, 0, 0, null, "동시성 대상"),
                                            post(replyRootId, replyRootId, 0, 0, null, "답글 루트"),
                                            post(
                                                    replyParentId,
                                                    replyRootId,
                                                    1,
                                                    1,
                                                    replyRootId,
                                                    "답글 부모"),
                                            post(
                                                    replyChildId,
                                                    replyRootId,
                                                    2,
                                                    2,
                                                    replyParentId,
                                                    "답글 자식"),
                                            post(
                                                    replySiblingId,
                                                    replyRootId,
                                                    3,
                                                    1,
                                                    replyRootId,
                                                    "답글 형제")));
                            commentRepository.saveAll(
                                    List.of(
                                            comment(
                                                    commentRootId,
                                                    commentRootId,
                                                    0,
                                                    0,
                                                    null,
                                                    "댓글 루트"),
                                            comment(
                                                    commentParentId,
                                                    commentRootId,
                                                    1,
                                                    1,
                                                    commentRootId,
                                                    "댓글 부모"),
                                            comment(
                                                    commentChildId,
                                                    commentRootId,
                                                    2,
                                                    2,
                                                    commentParentId,
                                                    "댓글 자식"),
                                            comment(
                                                    commentSiblingId,
                                                    commentRootId,
                                                    3,
                                                    1,
                                                    commentRootId,
                                                    "댓글 형제")));
                        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTL WHERE NAC_NO = ?", postId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CCMMTM WHERE NAC_NO = ?", postId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCL WHERE BLB_ID = ?", boardId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE BLB_ID = ?", boardId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBML WHERE BLB_ID = ?", boardId);
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBMM WHERE BLB_ID = ?", boardId);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("조회수 증가와 게시물 수정이 경합해도 제목과 조회수를 모두 보존한다")
    void viewIncrement_concurrentWithUpdate_preservesBothChanges() throws Exception {
        CountDownLatch viewLocked = new CountDownLatch(1);
        CountDownLatch releaseView = new CountDownLatch(1);
        CountDownLatch updateReachedRepository = new CountDownLatch(1);

        doAnswer(
                        invocation -> {
                            if ("view-writer".equals(Thread.currentThread().getName())) {
                                Object result = findPostAssociation(true);
                                viewLocked.countDown();
                                await(releaseView, "조회수 쓰기 해제");
                                return result;
                            }
                            if ("content-writer".equals(Thread.currentThread().getName())) {
                                updateReachedRepository.countDown();
                            }
                            return findPostAssociation(true);
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYnForUpdate(eq(boardId), eq(postId), eq("N"));
        doAnswer(
                        invocation -> {
                            Object result = findPostAssociation(false);
                            if ("content-writer".equals(Thread.currentThread().getName())) {
                                updateReachedRepository.countDown();
                            }
                            return result;
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYn(eq(boardId), eq(postId), eq("N"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> view =
                    executor.submit(
                            () ->
                                    runAs(
                                            "view-writer",
                                            () ->
                                                    postService.incrementPostView(
                                                            boardId, postId, user)));
            assertThat(viewLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> update =
                    executor.submit(
                            () ->
                                    runAs(
                                            "content-writer",
                                            () ->
                                                    postService.updatePost(
                                                            boardId,
                                                            postId,
                                                            updateRequest("동시 수정"),
                                                            user)));
            assertThat(updateReachedRepository.await(10, TimeUnit.SECONDS)).isTrue();
            releaseView.countDown();
            view.get(20, TimeUnit.SECONDS);
            update.get(20, TimeUnit.SECONDS);
        } finally {
            releaseView.countDown();
        }

        assertThat(queryPostTitle(postId)).isEqualTo("동시 수정");
        assertThat(queryViewCount(postId)).isOne();
        assertThat(queryPostUpdateAuditCount(postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("잠금 후 대기 중인 게시물 수정은 삭제 완료 행을 되살리지 않는다")
    void delete_concurrentWithStaleUpdate_doesNotResurrectPost() throws Exception {
        CountDownLatch updateLoaded = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        CountDownLatch deleteReachedRepository = new CountDownLatch(1);

        doAnswer(
                        invocation -> {
                            if ("stale-update".equals(Thread.currentThread().getName())) {
                                Object result = findPostAssociation(true);
                                updateLoaded.countDown();
                                await(releaseUpdate, "게시물 수정 해제");
                                return result;
                            }
                            if ("delete-writer".equals(Thread.currentThread().getName())) {
                                deleteReachedRepository.countDown();
                            }
                            return findPostAssociation(true);
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYnForUpdate(eq(boardId), eq(postId), eq("N"));
        doAnswer(
                        invocation -> {
                            Object result = findPostAssociation(false);
                            if ("stale-update".equals(Thread.currentThread().getName())) {
                                updateLoaded.countDown();
                                await(releaseUpdate, "게시물 수정 해제");
                            } else if ("delete-writer".equals(Thread.currentThread().getName())) {
                                deleteReachedRepository.countDown();
                            }
                            return result;
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYn(eq(boardId), eq(postId), eq("N"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> update =
                    executor.submit(
                            () ->
                                    runAs(
                                            "stale-update",
                                            () ->
                                                    postService.updatePost(
                                                            boardId,
                                                            postId,
                                                            updateRequest("오래된 수정"),
                                                            user)));
            assertThat(updateLoaded.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> delete =
                    executor.submit(
                            () ->
                                    runAs(
                                            "delete-writer",
                                            () -> postService.deletePost(boardId, postId, user)));
            assertThat(deleteReachedRepository.await(10, TimeUnit.SECONDS)).isTrue();
            if (completesWithin(delete, 2, TimeUnit.SECONDS)) {
                assertThat(queryPostDeleted(postId)).isEqualTo("Y");
            }
            releaseUpdate.countDown();
            update.get(20, TimeUnit.SECONDS);
            delete.get(20, TimeUnit.SECONDS);
        } finally {
            releaseUpdate.countDown();
        }

        assertThat(queryPostDeleted(postId)).isEqualTo("Y");
        assertThat(queryPostUpdateAuditCount(postId)).isOne();
        assertThat(queryPostDeleteAuditCount(postId)).isOne();
    }

    @Test
    @DisplayName("분기된 게시물 트리의 부모 답글은 모든 후속 행을 이동하고 감사 delta를 정확히 남긴다")
    void branchyPostReply_shiftsEveryFollowingRowAndAuditsExactDelta() throws Exception {
        int beforeCreate = queryPostGroupAuditCount("C");
        int beforeUpdate = queryPostGroupAuditCount("U");
        LocalDateTime childChangedBefore = queryPostLastChangedAt(replyChildId);
        LocalDateTime siblingChangedBefore = queryPostLastChangedAt(replySiblingId);
        Thread.sleep(1100);

        String createdId =
                postService.createReply(boardId, replyParentId, replyRequest("분기 게시물 답글"), user);

        assertThat(queryPostGroupOrder())
                .containsExactly(
                        replyRootId + ":0",
                        replyParentId + ":1",
                        createdId + ":2",
                        replyChildId + ":3",
                        replySiblingId + ":4");
        assertThat(queryPostGroupAuditCount("C") - beforeCreate).isOne();
        assertThat(queryPostGroupAuditCount("U") - beforeUpdate).isEqualTo(2);
        assertThat(queryPostLastChangedAt(replyChildId)).isAfter(childChangedBefore);
        assertThat(queryPostLastChangedAt(replySiblingId)).isAfter(siblingChangedBefore);
        assertThat(queryPostLastChangedBy(replyChildId)).isEqualTo(AUDITOR);
        assertThat(queryPostLastChangedBy(replySiblingId)).isEqualTo(AUDITOR);
    }

    @Test
    @DisplayName("분기된 댓글 트리의 부모 대댓글은 모든 후속 행을 이동하고 감사 delta를 정확히 남긴다")
    void branchyCommentReply_shiftsEveryFollowingRowAndAuditsExactDelta() throws Exception {
        int beforeCreate = queryCommentGroupAuditCount("C");
        int beforeUpdate = queryCommentGroupAuditCount("U");
        LocalDateTime childChangedBefore = queryCommentLastChangedAt(commentChildId);
        LocalDateTime siblingChangedBefore = queryCommentLastChangedAt(commentSiblingId);
        Thread.sleep(1100);

        Long createdId =
                commentService.createReply(
                        boardId,
                        postId,
                        commentParentId,
                        new BoardCommentDto.CreateRequest("분기 댓글 대댓글"),
                        user);

        assertThat(queryCommentGroupOrder())
                .containsExactly(
                        commentRootId + ":0",
                        commentParentId + ":1",
                        createdId + ":2",
                        commentChildId + ":3",
                        commentSiblingId + ":4");
        assertThat(queryCommentGroupAuditCount("C") - beforeCreate).isOne();
        assertThat(queryCommentGroupAuditCount("U") - beforeUpdate).isEqualTo(2);
        assertThat(queryCommentLastChangedAt(commentChildId)).isAfter(childChangedBefore);
        assertThat(queryCommentLastChangedAt(commentSiblingId)).isAfter(siblingChangedBefore);
        assertThat(queryCommentLastChangedBy(commentChildId)).isEqualTo(AUDITOR);
        assertThat(queryCommentLastChangedBy(commentSiblingId)).isEqualTo(AUDITOR);
    }

    @ParameterizedTest(name = "게시물 삭제 경합 시 댓글 {0}은 삭제된 게시물 아래에 반영되지 않는다")
    @EnumSource(CommentWriteOperation.class)
    void commentWrite_concurrentWithPostDelete_doesNotMutateDeletedPost(
            CommentWriteOperation operation) throws Exception {
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch commentReachedPost = new CountDownLatch(1);
        CountDownLatch releaseComment = new CountDownLatch(1);
        int commentsBefore = queryActiveCommentCount();
        String contentBefore = queryCommentContent(commentParentId);
        String deletedBefore = queryCommentDeleted(commentParentId);

        doAnswer(
                        invocation -> {
                            if ("post-delete".equals(Thread.currentThread().getName())) {
                                Object result = findPostAssociation(true);
                                deleteLocked.countDown();
                                await(releaseDelete, "게시물 삭제 해제");
                                return result;
                            }
                            if ("comment-write".equals(Thread.currentThread().getName())) {
                                commentReachedPost.countDown();
                            }
                            return findPostAssociation(true);
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYnForUpdate(eq(boardId), eq(postId), eq("N"));
        doAnswer(
                        invocation -> {
                            Object result = findPostAssociation(false);
                            if ("comment-write".equals(Thread.currentThread().getName())) {
                                commentReachedPost.countDown();
                                await(releaseComment, "댓글 쓰기 해제");
                            }
                            return result;
                        })
                .when(postRepository)
                .findByBlbMngNoAndNacMngNoAndDelYn(eq(boardId), eq(postId), eq("N"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> delete =
                    executor.submit(
                            () ->
                                    runAs(
                                            "post-delete",
                                            () -> postService.deletePost(boardId, postId, user)));
            assertThat(deleteLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> commentWrite =
                    executor.submit(
                            () ->
                                    runAsCatching(
                                            "comment-write", () -> executeCommentWrite(operation)));
            assertThat(commentReachedPost.await(10, TimeUnit.SECONDS)).isTrue();
            releaseDelete.countDown();
            delete.get(20, TimeUnit.SECONDS);
            releaseComment.countDown();
            Throwable failure = commentWrite.get(20, TimeUnit.SECONDS);

            assertThat(failure).isInstanceOf(NotFoundException.class);
        } finally {
            releaseDelete.countDown();
            releaseComment.countDown();
        }

        assertThat(queryPostDeleted(postId)).isEqualTo("Y");
        switch (operation) {
            case CREATE, REPLY -> assertThat(queryActiveCommentCount()).isEqualTo(commentsBefore);
            case UPDATE ->
                    assertThat(queryCommentContent(commentParentId)).isEqualTo(contentBefore);
            case DELETE ->
                    assertThat(queryCommentDeleted(commentParentId)).isEqualTo(deletedBefore);
        }
    }

    @Test
    @DisplayName("같은 게시물 그룹의 서로 다른 부모 답글은 고유하고 연속된 순서를 가진다")
    void concurrentPostReplies_sameGroup_haveDistinctOrderedSequence() throws Exception {
        CountDownLatch childLockedTail = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch parentLockedTail = new CountDownLatch(1);
        int beforeCreate = queryPostGroupAuditCount("C");
        int beforeUpdate = queryPostGroupAuditCount("U");

        doAnswer(
                        invocation -> {
                            List<Cblbcm> tail =
                                    findPostGroupTailForUpdate(
                                            invocation.getArgument(0),
                                            invocation.getArgument(1),
                                            invocation.getArgument(2));
                            if ("child-post-reply".equals(Thread.currentThread().getName())) {
                                childLockedTail.countDown();
                                await(releaseChild, "자식 게시물 답글 해제");
                            } else if ("parent-post-reply"
                                    .equals(Thread.currentThread().getName())) {
                                parentLockedTail.countDown();
                            }
                            return tail;
                        })
                .when(postRepository)
                .findActiveGroupTailForUpdate(
                        eq(boardId), eq(replyRootId), org.mockito.ArgumentMatchers.anyInt());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> childReply =
                    executor.submit(
                            () ->
                                    runAs(
                                            "child-post-reply",
                                            () ->
                                                    postService.createReply(
                                                            boardId,
                                                            replyChildId,
                                                            replyRequest("자식의 답글"),
                                                            user)));
            assertThat(childLockedTail.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> parentReply =
                    executor.submit(
                            () ->
                                    runAs(
                                            "parent-post-reply",
                                            () ->
                                                    postService.createReply(
                                                            boardId,
                                                            replyParentId,
                                                            replyRequest("부모의 답글"),
                                                            user)));
            if (parentLockedTail.await(2, TimeUnit.SECONDS)) {
                parentReply.get(10, TimeUnit.SECONDS);
            }
            releaseChild.countDown();
            childReply.get(20, TimeUnit.SECONDS);
            parentReply.get(20, TimeUnit.SECONDS);
        } finally {
            releaseChild.countDown();
        }

        assertThat(queryPostGroupSequences())
                .containsExactly(0, 1, 2, 3, 4, 5)
                .doesNotHaveDuplicates();
        assertThat(queryPostGroupAuditCount("C") - beforeCreate).isEqualTo(2);
        assertThat(queryPostGroupAuditCount("U") - beforeUpdate).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 댓글 그룹의 서로 다른 부모 대댓글은 고유하고 연속된 순서를 가진다")
    void concurrentCommentReplies_sameGroup_haveDistinctOrderedSequence() throws Exception {
        CountDownLatch childLockedTail = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch parentLockedTail = new CountDownLatch(1);
        int beforeCreate = queryCommentGroupAuditCount("C");
        int beforeUpdate = queryCommentGroupAuditCount("U");

        doAnswer(
                        invocation -> {
                            List<Ccmmtm> tail =
                                    findCommentGroupTailForUpdate(
                                            invocation.getArgument(0),
                                            invocation.getArgument(1),
                                            invocation.getArgument(2));
                            if ("child-comment-reply".equals(Thread.currentThread().getName())) {
                                childLockedTail.countDown();
                                await(releaseChild, "자식 댓글 답글 해제");
                            } else if ("parent-comment-reply"
                                    .equals(Thread.currentThread().getName())) {
                                parentLockedTail.countDown();
                            }
                            return tail;
                        })
                .when(commentRepository)
                .findActiveGroupTailForUpdate(
                        eq(postId), eq(commentRootId), org.mockito.ArgumentMatchers.anyInt());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> childReply =
                    executor.submit(
                            () ->
                                    runAs(
                                            "child-comment-reply",
                                            () ->
                                                    commentService.createReply(
                                                            boardId,
                                                            postId,
                                                            commentChildId,
                                                            new BoardCommentDto.CreateRequest(
                                                                    "자식의 대댓글"),
                                                            user)));
            assertThat(childLockedTail.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> parentReply =
                    executor.submit(
                            () ->
                                    runAs(
                                            "parent-comment-reply",
                                            () ->
                                                    commentService.createReply(
                                                            boardId,
                                                            postId,
                                                            commentParentId,
                                                            new BoardCommentDto.CreateRequest(
                                                                    "부모의 대댓글"),
                                                            user)));
            if (parentLockedTail.await(2, TimeUnit.SECONDS)) {
                parentReply.get(10, TimeUnit.SECONDS);
            }
            releaseChild.countDown();
            childReply.get(20, TimeUnit.SECONDS);
            parentReply.get(20, TimeUnit.SECONDS);
        } finally {
            releaseChild.countDown();
        }

        assertThat(queryCommentGroupSequences())
                .containsExactly(0, 1, 2, 3, 4, 5)
                .doesNotHaveDuplicates();
        assertThat(queryCommentGroupAuditCount("C") - beforeCreate).isEqualTo(2);
        assertThat(queryCommentGroupAuditCount("U") - beforeUpdate).isEqualTo(4);
    }

    private Optional<Cblbcm> findPostAssociation(boolean forUpdate) {
        var query =
                entityManager
                        .createQuery(
                                """
                                SELECT c
                                  FROM Cblbcm c
                                 WHERE c.blbMngNo = :blbMngNo
                                   AND c.nacMngNo = :nacMngNo
                                   AND c.delYn = 'N'
                                """,
                                Cblbcm.class)
                        .setParameter("blbMngNo", boardId)
                        .setParameter("nacMngNo", postId);
        if (forUpdate) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return query.getResultStream().findFirst();
    }

    private List<Cblbcm> findPostGroupTailForUpdate(
            String targetBoardId, String groupId, int parentSequence) {
        return entityManager
                .createQuery(
                        """
                        SELECT c
                          FROM Cblbcm c
                         WHERE c.blbMngNo = :blbMngNo
                           AND c.nacUnqId = :groupId
                           AND c.nacGrpSqn > :parentSqn
                           AND c.delYn = 'N'
                         ORDER BY c.nacGrpSqn DESC, c.nacMngNo DESC
                        """,
                        Cblbcm.class)
                .setParameter("blbMngNo", targetBoardId)
                .setParameter("groupId", groupId)
                .setParameter("parentSqn", parentSequence)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    private List<Ccmmtm> findCommentGroupTailForUpdate(
            String targetPostId, Long groupId, int parentSequence) {
        return entityManager
                .createQuery(
                        """
                        SELECT c
                          FROM Ccmmtm c
                         WHERE c.nacMngNo = :nacMngNo
                           AND c.cmmtGrpNo = :groupId
                           AND c.cmmtGrpSqn > :parentSqn
                           AND c.delYn = 'N'
                         ORDER BY c.cmmtGrpSqn DESC, c.cmmtMngNo DESC
                        """,
                        Ccmmtm.class)
                .setParameter("nacMngNo", targetPostId)
                .setParameter("groupId", groupId)
                .setParameter("parentSqn", parentSequence)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    private void runAs(String threadName, Runnable action) {
        Thread.currentThread().setName(threadName);
        authenticate();
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Throwable runAsCatching(String threadName, Runnable action) {
        try {
            runAs(threadName, action);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void executeCommentWrite(CommentWriteOperation operation) {
        switch (operation) {
            case CREATE ->
                    commentService.createComment(
                            boardId, postId, new BoardCommentDto.CreateRequest("삭제 경합 댓글"), user);
            case REPLY ->
                    commentService.createReply(
                            boardId,
                            postId,
                            commentParentId,
                            new BoardCommentDto.CreateRequest("삭제 경합 대댓글"),
                            user);
            case UPDATE ->
                    commentService.updateComment(
                            boardId,
                            postId,
                            commentParentId,
                            new BoardCommentDto.UpdateRequest("삭제 경합 수정"),
                            user);
            case DELETE -> commentService.deleteComment(boardId, postId, commentParentId, user);
        }
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AUDITOR, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(description + " 대기 제한시간을 초과했습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private boolean completesWithin(Future<?> future, long timeout, TimeUnit unit)
            throws Exception {
        try {
            future.get(timeout, unit);
            return true;
        } catch (TimeoutException expected) {
            return false;
        }
    }

    private BoardPostDto.UpdateRequest updateRequest(String title) {
        BoardPostDto.UpdateRequest request = new BoardPostDto.UpdateRequest();
        request.setNacNm(title);
        request.setNacCone("동시성 수정 본문");
        request.setAncYn("N");
        request.setXpoYn("Y");
        request.setBbrC("100");
        return request;
    }

    private BoardPostDto.ReplyCreateRequest replyRequest(String title) {
        BoardPostDto.ReplyCreateRequest request = new BoardPostDto.ReplyCreateRequest();
        request.setNacNm(title);
        request.setNacCone("동시성 답글 본문");
        request.setBbrC("100");
        return request;
    }

    private Cblbmm board() {
        return Cblbmm.builder()
                .blbMngNo(boardId)
                .blbNm("SEC-15 쓰기 동시성")
                .itPtlBlbTc("002")
                .repUseYn("Y")
                .cmmtUseYn("Y")
                .flEsnYn("N")
                .hedTagUseYn("N")
                .sreSqnNo(999)
                .useYn("Y")
                .build();
    }

    private Cblbcm post(
            String id,
            String groupId,
            int groupSequence,
            int groupLevel,
            String parentId,
            String title) {
        return Cblbcm.builder()
                .nacMngNo(id)
                .blbMngNo(boardId)
                .nacNm(title)
                .nacCone("동시성 본문")
                .nacInqNbr(0)
                .nacUnqId(groupId)
                .ancYn("N")
                .xpoYn("Y")
                .bbrC("100")
                .flApgYn("N")
                .flNbr(0)
                .nacGrpSqn(groupSequence)
                .nacGrpLev(groupLevel)
                .hrkNacNo(parentId)
                .build();
    }

    private Ccmmtm comment(
            Long id,
            Long groupId,
            int groupSequence,
            int groupLevel,
            Long parentId,
            String content) {
        return Ccmmtm.builder()
                .cmmtMngNo(id)
                .nacMngNo(postId)
                .cmmtCone(content)
                .cmmtGrpNo(groupId)
                .cmmtGrpSqn(groupSequence)
                .cmmtGrpLev(groupLevel)
                .hrkCmmtMngNo(parentId)
                .build();
    }

    private Long nextCommentId() {
        return jdbcTemplate.queryForObject(
                "SELECT SQ_TPRMPP_CCMMTM_1.NEXTVAL FROM DUAL", Long.class);
    }

    private String queryPostTitle(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT NAC_TTL FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", String.class, id);
    }

    private int queryViewCount(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT NAC_INQ_NBR FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", Integer.class, id);
    }

    private String queryPostDeleted(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT DEL_YN FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", String.class, id);
    }

    private int queryPostUpdateAuditCount(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CBLBCL WHERE NAC_NO = ? AND CHG_DTT_YN = 'U'",
                Integer.class,
                id);
    }

    private int queryPostDeleteAuditCount(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CBLBCL WHERE NAC_NO = ? AND CHG_DTT_YN = 'D'",
                Integer.class,
                id);
    }

    private List<String> queryPostGroupOrder() {
        return jdbcTemplate.queryForList(
                "SELECT NAC_NO || ':' || TO_CHAR(GRP_SQN_SNO) "
                        + "FROM TPRMPP_CBLBCM "
                        + "WHERE BLB_ID = ? AND NAC_UNQ_ID = ? AND DEL_YN = 'N' "
                        + "ORDER BY GRP_SQN_SNO, NAC_NO",
                String.class,
                boardId,
                replyRootId);
    }

    private int queryPostGroupAuditCount(String changeType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CBLBCL "
                        + "WHERE BLB_ID = ? AND NAC_UNQ_ID = ? AND CHG_DTT_YN = ?",
                Integer.class,
                boardId,
                replyRootId,
                changeType);
    }

    private LocalDateTime queryPostLastChangedAt(String id) {
        Timestamp value =
                jdbcTemplate.queryForObject(
                        "SELECT LST_CHG_DTM FROM TPRMPP_CBLBCM WHERE NAC_NO = ?",
                        Timestamp.class,
                        id);
        return value.toLocalDateTime();
    }

    private String queryPostLastChangedBy(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_USID FROM TPRMPP_CBLBCM WHERE NAC_NO = ?", String.class, id);
    }

    private List<Integer> queryPostGroupSequences() {
        return jdbcTemplate.queryForList(
                "SELECT GRP_SQN_SNO FROM TPRMPP_CBLBCM "
                        + "WHERE BLB_ID = ? AND NAC_UNQ_ID = ? AND DEL_YN = 'N' "
                        + "ORDER BY GRP_SQN_SNO",
                Integer.class,
                boardId,
                replyRootId);
    }

    private List<String> queryCommentGroupOrder() {
        return jdbcTemplate.queryForList(
                "SELECT TO_CHAR(CMMT_SNO) || ':' || TO_CHAR(CMMT_SQN_SNO) "
                        + "FROM TPRMPP_CCMMTM "
                        + "WHERE NAC_NO = ? AND CMMT_TGT_SNO = ? AND DEL_YN = 'N' "
                        + "ORDER BY CMMT_SQN_SNO, CMMT_SNO",
                String.class,
                postId,
                commentRootId);
    }

    private int queryCommentGroupAuditCount(String changeType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CCMMTL "
                        + "WHERE NAC_NO = ? AND CMMT_TGT_SNO = ? AND CHG_DTT_YN = ?",
                Integer.class,
                postId,
                commentRootId,
                changeType);
    }

    private LocalDateTime queryCommentLastChangedAt(Long id) {
        Timestamp value =
                jdbcTemplate.queryForObject(
                        "SELECT LST_CHG_DTM FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?",
                        Timestamp.class,
                        id);
        return value.toLocalDateTime();
    }

    private String queryCommentLastChangedBy(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT LST_CHG_USID FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?", String.class, id);
    }

    private int queryActiveCommentCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TPRMPP_CCMMTM WHERE NAC_NO = ? AND DEL_YN = 'N'",
                Integer.class,
                postId);
    }

    private String queryCommentContent(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT CMMT_CONE FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?", String.class, id);
    }

    private String queryCommentDeleted(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT DEL_YN FROM TPRMPP_CCMMTM WHERE CMMT_SNO = ?", String.class, id);
    }

    private List<Integer> queryCommentGroupSequences() {
        return jdbcTemplate.queryForList(
                "SELECT CMMT_SQN_SNO FROM TPRMPP_CCMMTM "
                        + "WHERE NAC_NO = ? AND CMMT_TGT_SNO = ? AND DEL_YN = 'N' "
                        + "ORDER BY CMMT_SQN_SNO",
                Integer.class,
                postId,
                commentRootId);
    }

    private enum CommentWriteOperation {
        CREATE,
        REPLY,
        UPDATE,
        DELETE
    }
}
