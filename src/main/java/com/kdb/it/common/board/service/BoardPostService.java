package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.speeddial.event.FaqRegisteredEvent;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.MentionExtractor;
import com.kdb.it.common.notification.util.NotificationMessageFormatter;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 게시물 서비스
 *
 * <p>게시물 CRUD, 답변글 트리, 권한 검증을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostService {

    private static final Logger log = LoggerFactory.getLogger(BoardPostService.class);

    /**
     * 일정 게시판 구분코드(공통코드 {@code IT_PTL_BLB_TC}의 {@code 003}). 이 유형은 시작·종료일자가 공개기간이 아니라 일정이라 공개기간 필터를
     * 적용하지 않는다.
     */
    private static final String SCHEDULE_BOARD_TYPE = "003";

    private final BoardMetaRepository metaRepository;
    private final BoardPostRepository postRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시물 목록 조회
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건
     * @param user 인증 사용자
     * @return 게시물 페이지
     * @throws NotFoundException 사용 중인 게시판을 찾을 수 없는 경우
     * @throws CustomGeneralException 검색어가 최소 길이보다 짧은 경우
     */
    public Page<BoardPostDto.ListItem> searchPosts(
            String blbMngNo, BoardPostDto.SearchCondition cond, CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo); // 사용 중인 게시판만 사용자 목록 조회 허용
        BoardPostDto.SearchCondition effectiveCond =
                cond == null ? new BoardPostDto.SearchCondition() : cond;
        validateSearchCondition(effectiveCond);
        if (SCHEDULE_BOARD_TYPE.equals(board.getItPtlBlbTc())) {
            effectiveCond.ignorePublicationPeriod();
        }

        return postRepository
                .searchPostRows(blbMngNo, effectiveCond, user.isAdmin())
                .map(BoardPostDto.ListItem::from);
    }

    /**
     * 게시물 상세 조회
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @return 게시물 상세 DTO
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws CustomGeneralException 비공개 또는 공개기간 외 게시물에 대한 접근 권한이 없는 경우
     */
    public BoardPostDto.Detail getPostDetail(
            String blbMngNo, String nacMngNo, CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoard(blbMngNo, nacMngNo);

        verifyCanReadPost(user, post, board);

        boolean canModify = user.isAdmin() || user.getEno().equals(post.getFstEnrUsid());
        // 작성자 이름·부서명은 사번으로 조회하며, 조회되지 않는 사번은 이름 없이 응답한다.
        CuserI writer = userRepository.findByEno(post.getFstEnrUsid()).orElse(null);
        return BoardPostDto.Detail.from(post, canModify, writer);
    }

    /**
     * 게시물 조회수를 증가시킵니다.
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws CustomGeneralException 비공개 또는 공개기간 외 게시물에 대한 접근 권한이 없는 경우
     */
    @Transactional
    public void incrementPostView(String blbMngNo, String nacMngNo, CustomUserDetails user) {
        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCanReadPost(user, post, board);
        post.incrementViewCount();
    }

    /**
     * 게시물 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param request 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 게시물관리번호
     * @throws NotFoundException 사용 중인 게시판을 찾을 수 없는 경우
     * @throws CustomGeneralException 등록 권한이 없거나 요청 부서가 사용자 부서와 다른 경우
     */
    @Transactional
    public String createPost(
            String blbMngNo, BoardPostDto.CreateRequest request, CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        verifyCanWrite(user, board);
        verifyBbrC(user, request.getBbrC());
        validateSchedulePeriod(board, request.getSttYmd(), request.getEndYmd());

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        Long seq = postRepository.getNextSequenceValue();
        String nacMngNo = String.format("NAC-%d-%04d", LocalDate.now().getYear(), seq);

        Cblbcm post =
                Cblbcm.builder()
                        .nacMngNo(nacMngNo)
                        .blbMngNo(blbMngNo)
                        .nacNm(request.getNacNm())
                        .nacCone(sanitizedCone)
                        .ancYn(request.getAncYn() != null ? request.getAncYn() : "N")
                        .xpoYn(request.getXpoYn() != null ? request.getXpoYn() : "Y")
                        .bbrC(request.getBbrC())
                        .sttDt(request.getSttYmd())
                        .endDt(request.getEndYmd())
                        .nacInqNbr(0)
                        .flApgYn("N")
                        .flNbr(0)
                        .nacGrpSqn(0)
                        .nacGrpLev(0)
                        .build();
        post.initGroupAsRoot();
        postRepository.save(post);
        if (BoardTypeResolver.FAQ_BOARD_TYPE.equals(board.getItPtlBlbTc())) {
            String authorName =
                    userRepository.findByEno(user.getEno()).map(value -> value.getUsrNm()).orElse(user.getEno());
            eventPublisher.publishEvent(
                    new FaqRegisteredEvent(
                            post.getNacMngNo(),
                            post.getNacNm(),
                            post.getNacCone(),
                            user.getEno(),
                            authorName,
                            "/board/" + blbMngNo + "?postId=" + post.getNacMngNo()));
        }
        publishMentionNotifications(post, user.getEno(), false, request.getMentionedEnos());
        return nacMngNo;
    }

    /**
     * 게시물 수정
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request 수정 요청 DTO
     * @param user 인증 사용자
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws CustomGeneralException 요청 부서가 사용자 부서와 다른 경우
     * @throws org.springframework.security.access.AccessDeniedException 게시물 수정 권한이 없는 경우
     */
    @Transactional
    public void updatePost(
            String blbMngNo,
            String nacMngNo,
            BoardPostDto.UpdateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCanModify(user, post);
        verifyBbrC(user, request.getBbrC());
        validateSchedulePeriod(board, request.getSttYmd(), request.getEndYmd());

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        post.update(request.toUpdateCommand(sanitizedCone));
        publishMentionNotifications(post, user.getEno(), false, request.getMentionedEnos());
    }

    /**
     * 게시물 삭제 — Soft Delete
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @throws NotFoundException 게시판·게시물이 존재하지 않거나 게시물이 해당 게시판 소속이 아닌 경우
     * @throws org.springframework.security.access.AccessDeniedException 게시물 삭제 권한이 없는 경우
     */
    @Transactional
    public void deletePost(String blbMngNo, String nacMngNo, CustomUserDetails user) {
        findUserActiveBoard(blbMngNo);
        Cblbcm post = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCanModify(user, post);
        post.delete();
    }

    /**
     * 답변글 등록 — 트리 SQN 밀어내기 후 저장
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 부모 게시물관리번호
     * @param request 답변글 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 게시물관리번호
     * @throws NotFoundException 게시판·부모 게시물·답글 그룹을 찾을 수 없거나 소속이 다른 경우
     * @throws CustomGeneralException 게시판이 답변 미지원 / 부모 게시물 접근 불가 / 등록 권한 없음
     */
    @Transactional
    public String createReply(
            String blbMngNo,
            String nacMngNo,
            BoardPostDto.ReplyCreateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findUserActiveBoard(blbMngNo);
        if (!"Y".equals(board.getRepUseYn())) {
            throw new CustomGeneralException("해당 게시판은 답변 기능을 지원하지 않습니다.");
        }
        verifyCanWrite(user, board);

        String groupId = findReplyGroupId(blbMngNo, nacMngNo);
        lockReplyGroup(blbMngNo, groupId);
        Cblbcm parent = findPostInBoardForUpdate(blbMngNo, nacMngNo);
        verifyCanReadPost(user, parent, board);

        postRepository
                .findActiveGroupTailForUpdate(blbMngNo, parent.getNacUnqId(), parent.getNacGrpSqn())
                .forEach(Cblbcm::shiftGroupSequence);

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        Long seq = postRepository.getNextSequenceValue();
        String newNacMngNo = String.format("NAC-%d-%04d", LocalDate.now().getYear(), seq);

        Cblbcm reply =
                Cblbcm.builder()
                        .nacMngNo(newNacMngNo)
                        .blbMngNo(blbMngNo)
                        .nacNm(request.getNacNm())
                        .nacCone(sanitizedCone)
                        .ancYn("N")
                        .xpoYn("Y")
                        .bbrC(request.getBbrC())
                        .sttDt(request.getSttYmd())
                        .endDt(request.getEndYmd())
                        .nacInqNbr(0)
                        .flApgYn("N")
                        .flNbr(0)
                        .nacGrpSqn(0)
                        .nacGrpLev(0)
                        .build();
        reply.initGroupAsReply(
                parent.getNacUnqId(),
                parent.getNacGrpSqn(),
                parent.getNacGrpLev(),
                parent.getNacMngNo());
        postRepository.save(reply);
        publishMentionNotifications(reply, user.getEno(), false, request.getMentionedEnos());
        return newNacMngNo;
    }

    /**
     * 게시물 본문의 {@code @사번} 멘션과 프론트에서 명시 선택한 사번을 합쳐 수신자별 알림 이벤트를 발행한다.
     *
     * <p>발행은 {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로 본 트랜잭션은 차단되지 않는다. 두 경로
     * 모두에서 수신자가 나오지 않으면 아무 동작도 하지 않는다.
     *
     * @param post 저장 직후의 게시물 엔티티
     * @param authorEno 작성자 사번 (자기 멘션 제외용)
     * @param isComment true=댓글, false=게시물 — 알림 종류 분기에 사용
     * @param explicitEnos 프론트 자동완성에서 명시 선택한 사번. null이면 본문 추출 결과만 사용한다
     */
    private void publishMentionNotifications(
            Cblbcm post, String authorEno, boolean isComment, java.util.List<String> explicitEnos) {
        log.debug(
                "[멘션 진단] publishMentionNotifications 진입: nacMngNo={}, author={}, contentLen={},"
                        + " explicitEnos={}",
                post.getNacMngNo(),
                authorEno,
                post.getNacCone() == null ? 0 : post.getNacCone().length(),
                explicitEnos);
        // 1) 본문 정규식 추출 (사용자가 직접 @K... 타이핑한 경우)
        Set<String> rawEnos =
                new java.util.LinkedHashSet<>(
                        MentionExtractor.extractEnos(post.getNacCone(), authorEno));
        // 2) 프론트 자동완성에서 명시 선택된 사번 union (자기 멘션 제외)
        if (explicitEnos != null) {
            for (String eno : explicitEnos) {
                if (eno != null && !eno.isBlank() && !eno.equals(authorEno)) {
                    rawEnos.add(eno);
                }
            }
        }
        log.debug("[멘션 진단] union 결과: nacMngNo={}, rawEnos={}", post.getNacMngNo(), rawEnos);
        if (rawEnos.isEmpty()) {
            log.debug(
                    "[멘션 진단] 추출+명시 union 0건 → 종료. content snippet={}",
                    post.getNacCone() == null
                            ? "<null>"
                            : post.getNacCone()
                                    .substring(0, Math.min(120, post.getNacCone().length())));
            return;
        }
        // 실제 TPRMPP_CUSERI 에 존재하는 사번만 통과 (batch existence check, 순서 보존)
        Set<String> existingEnos =
                userRepository.findByEnoIn(rawEnos).stream()
                        .map(value -> value.getEno())
                        .collect(java.util.stream.Collectors.toSet());
        log.debug("[멘션 진단] CUSERI 검증: existingEnos={}", existingEnos);
        Set<String> recipients = new java.util.LinkedHashSet<>();
        for (String eno : rawEnos) {
            if (existingEnos.contains(eno)) recipients.add(eno);
        }
        if (recipients.isEmpty()) {
            log.debug(
                    "[멘션 진단] 검증 후 수신자 0건 → 종료. rawEnos={}, existingEnos={}", rawEnos, existingEnos);
            return;
        }
        log.debug("[멘션 진단] 최종 수신자: {}, isComment={}", recipients, isComment);
        String type =
                isComment
                        ? NotificationEvent.TYPE_MENTION_COMMENT
                        : NotificationEvent.TYPE_MENTION_POST;
        String title = (isComment ? "댓글 멘션: " : "게시물 멘션: ") + safe(post.getNacNm());
        String linkUrl = "/board/" + post.getBlbMngNo() + "?postId=" + post.getNacMngNo();
        for (String eno : recipients) {
            eventPublisher.publishEvent(
                    NotificationEvent.builder()
                            .recipientEno(eno)
                            .itPtlInfmSvcTc(type)
                            .ttl(NotificationMessageFormatter.abbreviate(title, 100))
                            .infmMsgCone(
                                    NotificationMessageFormatter.abbreviate(
                                            safe(post.getNacNm()), 4000))
                            .infmRcdUrl(linkUrl)
                            .build());
        }
    }

    private static String safe(String s) {
        return BoardLookupSupport.safe(s);
    }

    // ── 권한 검증 (패키지 접근 허용 — BoardCommentService에서 위임 호출) ──

    /**
     * 게시물 단건 가시성 검증
     *
     * <p>게시판 조회는 인증된 모든 사용자에게 공개되므로 게시판 단위 권한 검증은 없으며, 게시물의 화면노출여부와 공개기간만 비관리자 대상으로 확인합니다. 다만 일정
     * 게시판({@code SCHEDULE_BOARD_TYPE})은 시작·종료일자가 공개기간이 아니라 일정 자체이므로 공개기간 검사에서 제외합니다.
     *
     * @param user 인증 사용자
     * @param post 게시물 엔티티
     * @param board 게시판 엔티티
     * @throws CustomGeneralException 게시물 접근 권한 없음
     */
    public void verifyCanReadPost(CustomUserDetails user, Cblbcm post, Cblbmm board) {
        if (user.isAdmin()) return;

        LocalDate today = LocalDate.now();
        boolean checkPublicationPeriod = !SCHEDULE_BOARD_TYPE.equals(board.getItPtlBlbTc());
        boolean visible =
                "Y".equals(post.getXpoYn())
                        && (!checkPublicationPeriod
                                || ((post.getSttDt() == null || !post.getSttDt().isAfter(today))
                                        && (post.getEndDt() == null
                                                || !post.getEndDt().isBefore(today))));

        if (!visible) {
            throw new CustomGeneralException("게시물에 접근할 권한이 없습니다.");
        }
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

    private String findReplyGroupId(String blbMngNo, String nacMngNo) {
        return postRepository
                .findReplyGroupId(blbMngNo, nacMngNo, "N")
                .orElseThrow(() -> new NotFoundException("게시물을 찾을 수 없습니다: " + nacMngNo));
    }

    /**
     * 답글 쓰기 잠금 순서의 첫 행인 그룹 루트를 잠급니다.
     *
     * <p>모든 답글 경로는 {@code 그룹 루트 → 부모 게시물 → 후속 그룹 행} 순서로만 잠급니다. 일반 수정·삭제·조회수 갱신은 대상 게시물 한 행만 잠그며 그룹
     * 루트를 추가로 기다리지 않으므로 역순 대기 사이클이 생기지 않습니다.
     */
    private void lockReplyGroup(String blbMngNo, String groupId) {
        postRepository
                .findReplyGroupAnchorForUpdate(blbMngNo, groupId)
                .orElseThrow(() -> new NotFoundException("게시물 답글 그룹을 찾을 수 없습니다: " + groupId));
    }

    private void validateSearchCondition(BoardPostDto.SearchCondition cond) {
        if (cond == null) {
            return;
        }
        if (StringUtils.hasText(cond.getKeyword()) && cond.getKeyword().trim().length() < 2) {
            throw new CustomGeneralException("검색어는 2자 이상 입력하세요.");
        }
    }

    private void validateSchedulePeriod(Cblbmm board, LocalDate startDate, LocalDate endDate) {
        if (!SCHEDULE_BOARD_TYPE.equals(board.getItPtlBlbTc())) return;
        if (startDate == null || endDate == null) {
            throw new CustomGeneralException("일정 게시판은 시작일자와 종료일자를 모두 입력해야 합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new CustomGeneralException("종료일자는 시작일자보다 빠를 수 없습니다.");
        }
    }

    /**
     * 게시물 등록 권한 검증
     *
     * <p>공지사항(IT_PTL_BLB_TC='001')과 FAQ(IT_PTL_BLB_TC='004') 게시판은 관리자만 등록할 수 있으며, 그 외 게시판은 인증된 모든 사용자가 등록할 수 있습니다.
     */
    private void verifyCanWrite(CustomUserDetails user, Cblbmm board) {
        if (user.isAdmin()) return;
        if ("001".equals(board.getItPtlBlbTc())) {
            throw new CustomGeneralException("공지사항은 관리자만 등록할 수 있습니다.");
        }
        if (BoardTypeResolver.FAQ_BOARD_TYPE.equals(board.getItPtlBlbTc())) {
            throw new CustomGeneralException("FAQ는 관리자만 등록할 수 있습니다.");
        }
    }

    private void verifyCanModify(CustomUserDetails user, Cblbcm post) {
        OwnershipVerifier.verifyOwnerOrAdmin(post.getFstEnrUsid(), user);
    }

    private void verifyBbrC(CustomUserDetails user, String requestBbrC) {
        if (user.isAdmin() || requestBbrC == null) return;
        if (!requestBbrC.equals(user.getBbrC())) {
            throw new CustomGeneralException("본인 부서코드만 지정할 수 있습니다.");
        }
    }
}
