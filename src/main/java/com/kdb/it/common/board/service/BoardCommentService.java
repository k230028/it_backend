package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentRepository;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.MentionExtractor;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시판 댓글 서비스
 *
 * <p>원댓글·대댓글 CRUD, 트리 알고리즘을 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardCommentService {

    private final BoardMetaRepository    metaRepository;
    private final BoardPostRepository    postRepository;
    private final BoardCommentRepository commentRepository;
    private final BoardPostService       postService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시물의 댓글 목록 조회 (트리 정렬)
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user     인증 사용자
     * @return 트리 정렬된 댓글 목록
     */
    public List<BoardCommentDto.Response> getComments(
            String blbMngNo, String nacMngNo, CustomUserDetails user) {

        Cblbmm board = findActiveBoard(blbMngNo);
        Cblbcm post  = findPost(nacMngNo);
        postService.verifyCanReadPost(user, post, board);

        return commentRepository.findCommentsByPost(nacMngNo).stream()
            .map(c -> BoardCommentDto.Response.from(c, canModify(user, c)))
            .collect(Collectors.toList());
    }

    /**
     * 댓글 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request  댓글 등록 요청 DTO
     * @param user     인증 사용자
     * @return 생성된 댓글관리번호
     * @throws CustomGeneralException 게시판이 댓글 미지원 / 게시물 접근 불가
     */
    @Transactional
    public String createComment(
            String blbMngNo, String nacMngNo,
            BoardCommentDto.CreateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findActiveBoard(blbMngNo);
        Cblbcm post  = findPost(nacMngNo);

        if (!"Y".equals(board.getCmmtUseYn())) {
            throw new CustomGeneralException("해당 게시판은 댓글 기능을 지원하지 않습니다.");
        }
        postService.verifyCanReadPost(user, post, board);

        String sanitized = HtmlSanitizer.sanitize(request.getCmmtCone());
        String cmmtMngNo = generateCmmtId();

        Ccmmtm comment = Ccmmtm.builder()
            .cmmtMngNo(cmmtMngNo)
            .nacMngNo(nacMngNo)
            .cmmtCone(sanitized)
            .sreYn("Y")
            .cmmtGrpNo(cmmtMngNo)
            .cmmtGrpSqn(0)
            .cmmtGrpLev(0)
            .build();
        comment.initGroupAsRoot();
        commentRepository.save(comment);
        publishMentionNotifications(comment, post, user.getEno());
        return cmmtMngNo;
    }

    /**
     * 대댓글 등록 — 트리 SQN 밀어내기 후 저장
     *
     * @param blbMngNo      게시판관리번호
     * @param nacMngNo      게시물관리번호
     * @param hrkCmmtMngNo  부모 댓글관리번호
     * @param request       댓글 등록 요청 DTO
     * @param user          인증 사용자
     * @return 생성된 댓글관리번호
     * @throws CustomGeneralException 게시판이 댓글 미지원 / 게시물 접근 불가
     */
    @Transactional
    public String createReply(
            String blbMngNo, String nacMngNo, String hrkCmmtMngNo,
            BoardCommentDto.CreateRequest request,
            CustomUserDetails user) {

        Cblbmm board  = findActiveBoard(blbMngNo);
        Cblbcm post   = findPost(nacMngNo);
        Ccmmtm parent = findComment(hrkCmmtMngNo);

        if (!"Y".equals(board.getCmmtUseYn())) {
            throw new CustomGeneralException("해당 게시판은 댓글 기능을 지원하지 않습니다.");
        }
        postService.verifyCanReadPost(user, post, board);

        commentRepository.shiftGroupSqn(
            parent.getCmmtGrpNo(),
            parent.getCmmtGrpSqn(),
            parent.getCmmtGrpLev()
        );

        String sanitized = HtmlSanitizer.sanitize(request.getCmmtCone());
        String cmmtMngNo = generateCmmtId();

        Ccmmtm reply = Ccmmtm.builder()
            .cmmtMngNo(cmmtMngNo)
            .nacMngNo(nacMngNo)
            .cmmtCone(sanitized)
            .sreYn("Y")
            .cmmtGrpNo(parent.getCmmtGrpNo())
            .cmmtGrpSqn(0)
            .cmmtGrpLev(0)
            .build();
        reply.initGroupAsReply(
            parent.getCmmtGrpNo(),
            parent.getCmmtGrpSqn(),
            parent.getCmmtGrpLev(),
            parent.getCmmtMngNo()
        );
        commentRepository.save(reply);
        publishMentionNotifications(reply, post, user.getEno());
        return cmmtMngNo;
    }

    /**
     * 댓글 수정
     *
     * @param cmmtMngNo 댓글관리번호
     * @param request   수정 요청 DTO
     * @param user      인증 사용자
     * @throws CustomGeneralException 수정 권한 없음
     */
    @Transactional
    public void updateComment(
            String cmmtMngNo,
            BoardCommentDto.UpdateRequest request,
            CustomUserDetails user) {

        Ccmmtm comment = findComment(cmmtMngNo);
        verifyCanModify(user, comment);
        comment.updateContent(HtmlSanitizer.sanitize(request.getCmmtCone()));
        Cblbcm post = findPost(comment.getNacMngNo());
        publishMentionNotifications(comment, post, user.getEno());
    }

    /**
     * 댓글 삭제 — Soft Delete
     *
     * <p>자식 댓글이 있으면 본문이 "삭제된 댓글입니다."로 표시되고 트리 구조는 유지된다.</p>
     *
     * @param cmmtMngNo 댓글관리번호
     * @param user      인증 사용자
     * @throws CustomGeneralException 삭제 권한 없음
     */
    @Transactional
    public void deleteComment(String cmmtMngNo, CustomUserDetails user) {
        Ccmmtm comment = findComment(cmmtMngNo);
        verifyCanModify(user, comment);
        comment.delete();
    }

    // ── 내부 헬퍼 ──

    private Cblbmm findActiveBoard(String blbMngNo) {
        return metaRepository.findByBlbMngNoAndDelYn(blbMngNo, "N")
            .orElseThrow(() -> new CustomGeneralException("게시판을 찾을 수 없습니다: " + blbMngNo));
    }

    private Cblbcm findPost(String nacMngNo) {
        return postRepository.findByNacMngNoAndDelYn(nacMngNo, "N")
            .orElseThrow(() -> new CustomGeneralException("게시물을 찾을 수 없습니다: " + nacMngNo));
    }

    private Ccmmtm findComment(String cmmtMngNo) {
        return commentRepository.findByCmmtMngNoAndDelYn(cmmtMngNo, "N")
            .orElseThrow(() -> new CustomGeneralException("댓글을 찾을 수 없습니다: " + cmmtMngNo));
    }

    private void verifyCanModify(CustomUserDetails user, Ccmmtm comment) {
        if (user.isAdmin()) return;
        if (user.getEno().equals(comment.getFstEnrUsid())) return;
        throw new CustomGeneralException("본인 댓글만 수정/삭제할 수 있습니다.");
    }

    private boolean canModify(CustomUserDetails user, Ccmmtm comment) {
        return user.isAdmin() || user.getEno().equals(comment.getFstEnrUsid());
    }

    private String generateCmmtId() {
        Long seq = commentRepository.getNextSequenceValue();
        return String.format("CMMT-%d-%04d", LocalDate.now().getYear(), seq);
    }

    /**
     * 댓글 본문의 {@code @사번} 멘션을 추출하여 수신자별 알림 이벤트를 발행한다.
     *
     * <p>발행은 {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로
     * 본 트랜잭션은 차단되지 않는다. 멘션이 없으면 아무 동작도 하지 않는다.</p>
     *
     * @param comment   저장 직후의 댓글 엔티티
     * @param post      댓글이 속한 게시물 (linkUrl 구성에 필요)
     * @param authorEno 작성자 사번 (자기 멘션 제외용)
     */
    private void publishMentionNotifications(Ccmmtm comment, Cblbcm post, String authorEno) {
        Set<String> recipients = MentionExtractor.extractEnos(comment.getCmmtCone(), authorEno);
        if (recipients.isEmpty()) return;
        String title   = "댓글 멘션: " + safe(post.getNacNm());
        String linkUrl = "/board/" + post.getBlbMngNo()
            + "?postId=" + post.getNacMngNo()
            + "&commentId=" + comment.getCmmtMngNo();
        for (String eno : recipients) {
            eventPublisher.publishEvent(
                NotificationEvent.builder()
                    .recipientEno(eno)
                    .infTpC(NotificationEvent.TYPE_MENTION_COMMENT)
                    .infTtl(abbreviate(title, 100))
                    .infCone(abbreviate(safe(post.getNacNm()), 300))
                    .infLnkUrl(linkUrl)
                    .build()
            );
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
