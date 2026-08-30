package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentRepository;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.MentionExtractor;
import com.kdb.it.common.notification.util.NotificationMessageFormatter;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 댓글 서비스
 *
 * <p>원댓글·대댓글 CRUD, 트리 알고리즘을 담당한다.
 *
 * <p>댓글 쓰기는 게시물 삭제와 원자성을 보장하기 위해 게시물 → 그룹 루트 → 부모 또는 대상 → 뒤쪽 그룹 행 순서로 잠근다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardCommentService {

    private final BoardMetaRepository metaRepository;
    private final BoardPostRepository postRepository;
    private final BoardCommentRepository commentRepository;
    private final BoardPostService postService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시물의 댓글 목록 조회 (트리 정렬)
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @return 트리 정렬된 댓글 목록
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws CustomGeneralException 댓글 미지원 게시판이거나 게시물 접근 권한이 없는 경우
     */
    public List<BoardCommentDto.Response> getComments(
            String blbMngNo, String nacMngNo, CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoard(blbMngNo, nacMngNo);
        verifyCommentsEnabled(board);
        postService.verifyCanReadPost(user, post, board);

        return commentRepository.findCommentRowsByPost(nacMngNo).stream()
                .map(row -> BoardCommentDto.Response.from(row, canModify(user, row.fstEnrUsid())))
                .toList();
    }

    /**
     * 댓글 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request 댓글 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 댓글관리번호
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws CustomGeneralException 게시판이 댓글 미지원 / 게시물 접근 불가
     */
    @Transactional
    public Long createComment(
            String blbMngNo,
            String nacMngNo,
            BoardCommentDto.CreateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCommentsEnabled(board);
        postService.verifyCanReadPost(user, post, board);

        String sanitized = HtmlSanitizer.sanitize(request.getCmmtCone());
        Long cmmtMngNo = generateCmmtId();

        Ccmmtm comment =
                Ccmmtm.builder()
                        .cmmtMngNo(cmmtMngNo)
                        .nacMngNo(nacMngNo)
                        .cmmtCone(sanitized)
                        .cmmtGrpNo(cmmtMngNo)
                        .cmmtGrpSqn(0)
                        .cmmtGrpLev(0)
                        .build();
        comment.initGroupAsRoot();
        commentRepository.save(comment);
        publishMentionNotifications(comment, post, user.getEno(), request.getMentionedEnos());
        return cmmtMngNo;
    }

    /**
     * 대댓글 등록 — 트리 SQN 밀어내기 후 저장
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param hrkCmmtMngNo 부모 댓글관리번호
     * @param request 댓글 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 댓글관리번호
     * @throws NotFoundException 게시판·게시물·부모 댓글·댓글 그룹이 없거나 소속이 다른 경우
     * @throws CustomGeneralException 게시판이 댓글 미지원 / 게시물 접근 불가
     */
    @Transactional
    public Long createReply(
            String blbMngNo,
            String nacMngNo,
            Long hrkCmmtMngNo,
            BoardCommentDto.CreateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCommentsEnabled(board);
        postService.verifyCanReadPost(user, post, board);

        Long groupId = findReplyGroupId(nacMngNo, hrkCmmtMngNo);
        lockReplyGroup(nacMngNo, groupId);
        Ccmmtm parent = findCommentInPostForUpdate(nacMngNo, hrkCmmtMngNo);

        commentRepository
                .findActiveGroupTailForUpdate(
                        nacMngNo, parent.getCmmtGrpNo(), parent.getCmmtGrpSqn())
                .forEach(Ccmmtm::shiftGroupSequence);

        String sanitized = HtmlSanitizer.sanitize(request.getCmmtCone());
        Long cmmtMngNo = generateCmmtId();

        Ccmmtm reply =
                Ccmmtm.builder()
                        .cmmtMngNo(cmmtMngNo)
                        .nacMngNo(nacMngNo)
                        .cmmtCone(sanitized)
                        .cmmtGrpNo(parent.getCmmtGrpNo())
                        .cmmtGrpSqn(0)
                        .cmmtGrpLev(0)
                        .build();
        reply.initGroupAsReply(
                parent.getCmmtGrpNo(),
                parent.getCmmtGrpSqn(),
                parent.getCmmtGrpLev(),
                parent.getCmmtMngNo());
        commentRepository.save(reply);
        publishMentionNotifications(reply, post, user.getEno(), request.getMentionedEnos());
        return cmmtMngNo;
    }

    /**
     * 댓글 수정
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param cmmtMngNo 댓글관리번호
     * @param request 수정 요청 DTO
     * @param user 인증 사용자
     * @throws NotFoundException 게시판·게시물·댓글이 없거나 부모-자식 소속이 다른 경우
     * @throws CustomGeneralException 댓글 미지원 게시판이거나 게시물 접근 권한이 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 댓글 수정 권한이 없는 경우
     */
    @Transactional
    public void updateComment(
            String blbMngNo,
            String nacMngNo,
            Long cmmtMngNo,
            BoardCommentDto.UpdateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCommentsEnabled(board);
        postService.verifyCanReadPost(user, post, board);
        Ccmmtm comment = findCommentInPostForUpdate(nacMngNo, cmmtMngNo);
        verifyCanModify(user, comment);
        comment.updateContent(HtmlSanitizer.sanitize(request.getCmmtCone()));
        publishMentionNotifications(comment, post, user.getEno(), request.getMentionedEnos());
    }

    /**
     * 댓글 삭제 — Soft Delete
     *
     * <p>자식 댓글이 있으면 본문이 "삭제된 댓글입니다."로 표시되고 트리 구조는 유지된다.
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param cmmtMngNo 댓글관리번호
     * @param user 인증 사용자
     * @throws NotFoundException 게시판·게시물·댓글이 없거나 부모-자식 소속이 다른 경우
     * @throws CustomGeneralException 댓글 미지원 게시판이거나 게시물 접근 권한이 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 댓글 삭제 권한이 없는 경우
     */
    @Transactional
    public void deleteComment(
            String blbMngNo, String nacMngNo, Long cmmtMngNo, CustomUserDetails user) {
        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCommentsEnabled(board);
        postService.verifyCanReadPost(user, post, board);
        Ccmmtm comment = findCommentInPostForUpdate(nacMngNo, cmmtMngNo);
        verifyCanModify(user, comment);
        comment.delete();
    }

    // ── 내부 헬퍼 ──

    private Cblbmm findUserActiveBoard(String blbMngNo) {
        return BoardLookupSupport.findUserActiveBoard(metaRepository, blbMngNo);
    }

    private Cblbcm findPostInBoard(String blbMngNo, String nacMngNo) {
        return BoardLookupSupport.findPost(postRepository, blbMngNo, nacMngNo);
    }

    private Cblbcm findPostInBoardForUpdate(String blbMngNo, String nacMngNo) {
        return BoardLookupSupport.findPostForUpdate(postRepository, blbMngNo, nacMngNo);
    }

    private Ccmmtm findCommentInPostForUpdate(String nacMngNo, Long cmmtMngNo) {
        return commentRepository
                .findByCmmtMngNoAndNacMngNoAndDelYnForUpdate(cmmtMngNo, nacMngNo, "N")
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + cmmtMngNo));
    }

    private Long findReplyGroupId(String nacMngNo, Long cmmtMngNo) {
        return commentRepository
                .findReplyGroupId(cmmtMngNo, nacMngNo, "N")
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다: " + cmmtMngNo));
    }

    /**
     * 대댓글 쓰기 잠금 순서의 첫 행인 그룹 루트를 잠급니다.
     *
     * <p>모든 댓글 쓰기는 게시물 행을 먼저 잠급니다. 대댓글은 이어서 {@code 그룹 루트 → 부모 댓글 → 후속 그룹 행} 순서로 잠급니다. 일반 댓글 수정·삭제는
     * 대상 댓글 한 행만 추가로 잠그므로 게시물 삭제와 역순 대기 사이클이 생기지 않습니다.
     */
    private void lockReplyGroup(String nacMngNo, Long groupId) {
        commentRepository
                .findReplyGroupAnchorForUpdate(nacMngNo, groupId)
                .orElseThrow(() -> new NotFoundException("댓글 그룹을 찾을 수 없습니다: " + groupId));
    }

    private void verifyCommentsEnabled(Cblbmm board) {
        if (!"Y".equals(board.getCmmtUseYn())) {
            throw new CustomGeneralException("해당 게시판은 댓글 기능을 지원하지 않습니다.");
        }
    }

    private void verifyCanModify(CustomUserDetails user, Ccmmtm comment) {
        OwnershipVerifier.verifyOwnerOrAdmin(comment.getFstEnrUsid(), user);
    }

    private boolean canModify(CustomUserDetails user, String fstEnrUsid) {
        return user.isAdmin() || user.getEno().equals(fstEnrUsid);
    }

    /** 댓글 식별자 채번 — SQ_TPRMPP_CCMMTM_1 시퀀스 기반 숫자 일련번호(CMMT_SNO). */
    private Long generateCmmtId() {
        return commentRepository.getNextSequenceValue();
    }

    /**
     * 댓글 본문의 {@code @사번} 멘션을 추출하여 수신자별 알림 이벤트를 발행한다.
     *
     * <p>발행은 {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로 본 트랜잭션은 차단되지 않는다. 멘션이 없으면
     * 아무 동작도 하지 않는다.
     *
     * @param comment 저장 직후의 댓글 엔티티
     * @param post 댓글이 속한 게시물 (linkUrl 구성에 필요)
     * @param authorEno 작성자 사번 (자기 멘션 제외용)
     * @param explicitEnos 프론트 자동완성에서 명시 선택된 사번 목록 (null 허용)
     */
    private void publishMentionNotifications(
            Ccmmtm comment, Cblbcm post, String authorEno, java.util.List<String> explicitEnos) {
        // 1) 본문 정규식 추출
        Set<String> rawEnos =
                new java.util.LinkedHashSet<>(
                        MentionExtractor.extractEnos(comment.getCmmtCone(), authorEno));
        // 2) 프론트 자동완성에서 명시 선택된 사번 union (자기 멘션 제외)
        if (explicitEnos != null) {
            for (String eno : explicitEnos) {
                if (eno != null && !eno.isBlank() && !eno.equals(authorEno)) {
                    rawEnos.add(eno);
                }
            }
        }
        if (rawEnos.isEmpty()) {
            return;
        }
        // 실제 TPRMPP_CUSERI 에 존재하는 사번만 통과 (batch existence check, 순서 보존)
        Set<String> existingEnos =
                userRepository.findByEnoIn(rawEnos).stream()
                        .map(value -> value.getEno())
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> recipients = new java.util.LinkedHashSet<>();
        for (String eno : rawEnos) {
            if (existingEnos.contains(eno)) recipients.add(eno);
        }
        if (recipients.isEmpty()) {
            return;
        }
        String title = "댓글 멘션: " + safe(post.getNacNm());
        String linkUrl =
                "/board/"
                        + post.getBlbMngNo()
                        + "?postId="
                        + post.getNacMngNo()
                        + "&commentId="
                        + comment.getCmmtMngNo();
        for (String eno : recipients) {
            eventPublisher.publishEvent(
                    NotificationEvent.builder()
                            .recipientEno(eno)
                            .itPtlInfmSvcTc(NotificationEvent.TYPE_MENTION_COMMENT)
                            .ttl(NotificationMessageFormatter.abbreviate(title, 100))
                            .infmMsgCone(
                                    NotificationMessageFormatter.abbreviate(
                                            safe(post.getNacNm()), 4000))
                            .infmRcdUrl(linkUrl)
                            .build());
        }
    }

    /**
     * null-safe 문자열 반환 헬퍼.
     *
     * @param s 대상 문자열
     * @return null이면 빈 문자열, 아니면 원본 문자열
     */
    private static String safe(String s) {
        return BoardLookupSupport.safe(s);
    }
}
