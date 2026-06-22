package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentRepository;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.MentionExtractor;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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
    private final UserRepository         userRepository;
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
            .toList();
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
    public Long createComment(
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
        Long cmmtMngNo = generateCmmtId();

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
        publishMentionNotifications(comment, post, user.getEno(), request.getMentionedEnos());
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
    public Long createReply(
            String blbMngNo, String nacMngNo, Long hrkCmmtMngNo,
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
        Long cmmtMngNo = generateCmmtId();

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
        publishMentionNotifications(reply, post, user.getEno(), request.getMentionedEnos());
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
            Long cmmtMngNo,
            BoardCommentDto.UpdateRequest request,
            CustomUserDetails user) {

        Ccmmtm comment = findComment(cmmtMngNo);
        verifyCanModify(user, comment);
        comment.updateContent(HtmlSanitizer.sanitize(request.getCmmtCone()));
        Cblbcm post = findPost(comment.getNacMngNo());
        publishMentionNotifications(comment, post, user.getEno(), request.getMentionedEnos());
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
    public void deleteComment(Long cmmtMngNo, CustomUserDetails user) {
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

    private Ccmmtm findComment(Long cmmtMngNo) {
        return commentRepository.findByCmmtMngNoAndDelYn(cmmtMngNo, "N")
            .orElseThrow(() -> new CustomGeneralException("댓글을 찾을 수 없습니다: " + cmmtMngNo));
    }

    private void verifyCanModify(CustomUserDetails user, Ccmmtm comment) {
        OwnershipVerifier.verifyOwnerOrAdmin(comment.getFstEnrUsid(), user);
    }

    private boolean canModify(CustomUserDetails user, Ccmmtm comment) {
        return user.isAdmin() || user.getEno().equals(comment.getFstEnrUsid());
    }

    /** 댓글 식별자 채번 — SEQ_CCMMTM 시퀀스 기반 숫자 일련번호(CMMT_SNO). */
    private Long generateCmmtId() {
        return commentRepository.getNextSequenceValue();
    }

    /**
     * 댓글 본문의 {@code @사번} 멘션을 추출하여 수신자별 알림 이벤트를 발행한다.
     *
     * <p>발행은 {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로
     * 본 트랜잭션은 차단되지 않는다. 멘션이 없으면 아무 동작도 하지 않는다.</p>
     *
     * @param comment      저장 직후의 댓글 엔티티
     * @param post         댓글이 속한 게시물 (linkUrl 구성에 필요)
     * @param authorEno    작성자 사번 (자기 멘션 제외용)
     * @param explicitEnos 프론트 자동완성에서 명시 선택된 사번 목록 (null 허용)
     */
    private void publishMentionNotifications(Ccmmtm comment, Cblbcm post, String authorEno,
                                             java.util.List<String> explicitEnos) {
        // 1) 본문 정규식 추출
        Set<String> rawEnos = new java.util.LinkedHashSet<>(
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
        Set<String> existingEnos = userRepository.findByEnoIn(rawEnos).stream()
            .map(CuserI::getEno)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> recipients = new java.util.LinkedHashSet<>();
        for (String eno : rawEnos) {
            if (existingEnos.contains(eno)) recipients.add(eno);
        }
        if (recipients.isEmpty()) {
            return;
        }
        String title   = "댓글 멘션: " + safe(post.getNacNm());
        String linkUrl = "/board/" + post.getBlbMngNo()
            + "?postId=" + post.getNacMngNo()
            + "&commentId=" + comment.getCmmtMngNo();
        for (String eno : recipients) {
            eventPublisher.publishEvent(
                NotificationEvent.builder()
                    .recipientEno(eno)
                    .infmSvcTc(NotificationEvent.TYPE_MENTION_COMMENT)
                    .ttl(abbreviate(title, 100))
                    .infmMsgCone(abbreviate(safe(post.getNacNm()), 4000))
                    .infmRcdUrl(linkUrl)
                    .build()
            );
        }
    }

    /**
     * null-safe 문자열 반환 헬퍼.
     *
     * @param s 대상 문자열
     * @return null이면 빈 문자열, 아니면 원본 문자열
     */
    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * 문자열을 최대 길이로 말줄임합니다.
     *
     * <p>{@code s}의 길이가 {@code max}를 초과하면 {@code max-1}자로 자르고 {@code "…"}를 추가합니다.</p>
     *
     * @param s   대상 문자열 (null 허용, null이면 null 반환)
     * @param max 최대 허용 길이 (이 길이를 초과하면 말줄임 처리)
     * @return max 이하로 줄인 문자열 (null 입력 시 null)
     */
    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
