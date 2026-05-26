package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.util.MentionExtractor;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시물 서비스
 *
 * <p>게시물 CRUD, 답변글 트리, 권한 검증을 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostService {

    private static final Logger log = LoggerFactory.getLogger(BoardPostService.class);

    private final BoardMetaRepository metaRepository;
    private final BoardPostRepository postRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 게시물 목록 조회
     *
     * @param blbMngNo 게시판관리번호
     * @param cond     검색 조건
     * @param user     인증 사용자
     * @return 게시물 목록
     * @throws CustomGeneralException 게시판 조회 권한 없음
     */
    public List<BoardPostDto.ListItem> searchPosts(
            String blbMngNo,
            BoardPostDto.SearchCondition cond,
            CustomUserDetails user) {

        Cblbmm board = findActiveBoard(blbMngNo);
        verifyCanReadBoard(user, board);

        return postRepository.searchPosts(
            blbMngNo, cond,
            user.isAdmin(),
            user.getBbrC(),
            board.getBbrLmtnUseYn()
        ).stream()
         .map(BoardPostDto.ListItem::from)
         .collect(Collectors.toList());
    }

    /**
     * 게시물 상세 조회 (조회수 +1 포함)
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user     인증 사용자
     * @return 게시물 상세 DTO
     * @throws CustomGeneralException 접근 권한 없음 또는 존재하지 않는 게시물
     */
    @Transactional
    public BoardPostDto.Detail getPostDetail(
            String blbMngNo, String nacMngNo, CustomUserDetails user) {

        Cblbmm board = findActiveBoard(blbMngNo);
        Cblbcm post  = findPost(nacMngNo);

        verifyCanReadPost(user, post, board);
        post.incrementViewCount();

        boolean canModify = user.isAdmin()
            || user.getEno().equals(post.getFstEnrUsid());
        return BoardPostDto.Detail.from(post, canModify);
    }

    /**
     * 게시물 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param request  등록 요청 DTO
     * @param user     인증 사용자
     * @return 생성된 게시물관리번호
     * @throws CustomGeneralException 등록 권한 없음
     */
    @Transactional
    public String createPost(
            String blbMngNo,
            BoardPostDto.CreateRequest request,
            CustomUserDetails user) {

        Cblbmm board = findActiveBoard(blbMngNo);
        verifyCanWrite(user, board);
        verifyBbrC(user, request.getBbrC());

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        Long seq = postRepository.getNextSequenceValue();
        String nacMngNo = String.format("NAC-%d-%04d", LocalDate.now().getYear(), seq);

        Cblbcm post = Cblbcm.builder()
            .nacMngNo(nacMngNo)
            .blbMngNo(blbMngNo)
            .nacNm(request.getNacNm())
            .nacCone(sanitizedCone)
            .nacTp(request.getNacTp())
            .kdC(request.getKdC())
            .pritC(request.getPritC() != null ? request.getPritC() : "PRIT_C_001")
            .hrkFxnYn(request.getHrkFxnYn() != null ? request.getHrkFxnYn() : "N")
            .sreYn(request.getSreYn() != null ? request.getSreYn() : "Y")
            .bbrC(request.getBbrC())
            .sttDt(request.getSttYmd())
            .endDt(request.getEndYmd())
            .nacInqNbr(0)
            .flApgYn("N")
            .flNbr(0)
            .nacGrpNo(nacMngNo)
            .nacGrpSqn(0)
            .nacGrpLev(0)
            .build();
        post.initGroupAsRoot();
        postRepository.save(post);
        publishMentionNotifications(post, user.getEno(), false, request.getMentionedEnos());
        return nacMngNo;
    }

    /**
     * 게시물 수정
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request  수정 요청 DTO
     * @param user     인증 사용자
     * @throws CustomGeneralException 수정 권한 없음
     */
    @Transactional
    public void updatePost(
            String blbMngNo,
            String nacMngNo,
            BoardPostDto.UpdateRequest request,
            CustomUserDetails user) {

        findActiveBoard(blbMngNo);
        Cblbcm post = findPost(nacMngNo);
        verifyCanModify(user, post);
        verifyBbrC(user, request.getBbrC());

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        post.update(request.toUpdateCommand(sanitizedCone));
        publishMentionNotifications(post, user.getEno(), false, request.getMentionedEnos());
    }

    /**
     * 게시물 삭제 — Soft Delete
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user     인증 사용자
     * @throws CustomGeneralException 삭제 권한 없음
     */
    @Transactional
    public void deletePost(String blbMngNo, String nacMngNo, CustomUserDetails user) {
        findActiveBoard(blbMngNo);
        Cblbcm post = findPost(nacMngNo);
        verifyCanModify(user, post);
        post.delete();
    }

    /**
     * 답변글 등록 — 트리 SQN 밀어내기 후 저장
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 부모 게시물관리번호
     * @param request  답변글 등록 요청 DTO
     * @param user     인증 사용자
     * @return 생성된 게시물관리번호
     * @throws CustomGeneralException 게시판이 답변 미지원 / 부모 게시물 접근 불가 / 등록 권한 없음
     */
    @Transactional
    public String createReply(
            String blbMngNo,
            String nacMngNo,
            BoardPostDto.ReplyCreateRequest request,
            CustomUserDetails user) {

        Cblbmm board  = findActiveBoard(blbMngNo);
        Cblbcm parent = findPost(nacMngNo);

        if (!"Y".equals(board.getRepUseYn())) {
            throw new CustomGeneralException("해당 게시판은 답변 기능을 지원하지 않습니다.");
        }
        verifyCanReadPost(user, parent, board);
        verifyCanWrite(user, board);

        postRepository.shiftGroupSqn(
            parent.getNacGrpNo(),
            parent.getNacGrpSqn(),
            parent.getNacGrpLev()
        );

        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacCone());
        Long seq = postRepository.getNextSequenceValue();
        String newNacMngNo = String.format("NAC-%d-%04d", LocalDate.now().getYear(), seq);

        Cblbcm reply = Cblbcm.builder()
            .nacMngNo(newNacMngNo)
            .blbMngNo(blbMngNo)
            .nacNm(request.getNacNm())
            .nacCone(sanitizedCone)
            .pritC(request.getPritC() != null ? request.getPritC() : "PRIT_C_001")
            .hrkFxnYn("N")
            .sreYn("Y")
            .bbrC(request.getBbrC())
            .sttDt(request.getSttYmd())
            .endDt(request.getEndYmd())
            .nacInqNbr(0)
            .flApgYn("N")
            .flNbr(0)
            .nacGrpNo(parent.getNacGrpNo())
            .nacGrpSqn(0)
            .nacGrpLev(0)
            .build();
        reply.initGroupAsReply(
            parent.getNacGrpNo(),
            parent.getNacGrpSqn(),
            parent.getNacGrpLev(),
            parent.getNacMngNo()
        );
        postRepository.save(reply);
        publishMentionNotifications(reply, user.getEno(), false, request.getMentionedEnos());
        return newNacMngNo;
    }

    /**
     * 게시물 본문의 {@code @사번} 멘션을 추출하여 수신자별 알림 이벤트를 발행한다.
     *
     * <p>발행은 {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 처리하므로
     * 본 트랜잭션은 차단되지 않는다. 멘션이 없으면 아무 동작도 하지 않는다.</p>
     *
     * @param post      저장 직후의 게시물 엔티티
     * @param authorEno 작성자 사번 (자기 멘션 제외용)
     * @param isComment true=댓글, false=게시물 — 알림 종류 분기에 사용
     */
    private void publishMentionNotifications(Cblbcm post, String authorEno, boolean isComment,
                                             java.util.List<String> explicitEnos) {
        log.info("[멘션 진단] publishMentionNotifications 진입: nacMngNo={}, author={}, contentLen={}, explicitEnos={}",
            post.getNacMngNo(), authorEno, post.getNacCone() == null ? 0 : post.getNacCone().length(), explicitEnos);
        // 1) 본문 정규식 추출 (사용자가 직접 @K... 타이핑한 경우)
        Set<String> rawEnos = new java.util.LinkedHashSet<>(
            MentionExtractor.extractEnos(post.getNacCone(), authorEno));
        // 2) 프론트 자동완성에서 명시 선택된 사번 union (자기 멘션 제외)
        if (explicitEnos != null) {
            for (String eno : explicitEnos) {
                if (eno != null && !eno.isBlank() && !eno.equals(authorEno)) {
                    rawEnos.add(eno);
                }
            }
        }
        log.info("[멘션 진단] union 결과: nacMngNo={}, rawEnos={}", post.getNacMngNo(), rawEnos);
        if (rawEnos.isEmpty()) {
            log.info("[멘션 진단] 추출+명시 union 0건 → 종료. content snippet={}",
                post.getNacCone() == null ? "<null>" :
                    post.getNacCone().substring(0, Math.min(120, post.getNacCone().length())));
            return;
        }
        // 실제 TPRMPP_CUSERI 에 존재하는 사번만 통과 (batch existence check, 순서 보존)
        Set<String> existingEnos = userRepository.findByEnoIn(rawEnos).stream()
            .map(CuserI::getEno)
            .collect(java.util.stream.Collectors.toSet());
        log.info("[멘션 진단] CUSERI 검증: existingEnos={}", existingEnos);
        Set<String> recipients = new java.util.LinkedHashSet<>();
        for (String eno : rawEnos) {
            if (existingEnos.contains(eno)) recipients.add(eno);
        }
        if (recipients.isEmpty()) {
            log.info("[멘션 진단] 검증 후 수신자 0건 → 종료. rawEnos={}, existingEnos={}", rawEnos, existingEnos);
            return;
        }
        log.info("[멘션 진단] 최종 수신자: {}, isComment={}", recipients, isComment);
        String type    = isComment ? NotificationEvent.TYPE_MENTION_COMMENT : NotificationEvent.TYPE_MENTION_POST;
        String title   = (isComment ? "댓글 멘션: " : "게시물 멘션: ") + safe(post.getNacNm());
        String linkUrl = "/board/" + post.getBlbMngNo() + "?postId=" + post.getNacMngNo();
        for (String eno : recipients) {
            eventPublisher.publishEvent(
                NotificationEvent.builder()
                    .recipientEno(eno)
                    .infmSvcTc(type)
                    .ttl(abbreviate(title, 100))
                    .infmMsgCone(abbreviate(safe(post.getNacNm()), 4000))
                    .infmRcdUrl(linkUrl)
                    .build()
            );
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── 권한 검증 (패키지 접근 허용 — BoardCommentService에서 위임 호출) ──

    /**
     * 게시판 조회 권한 검증
     *
     * @param user  인증 사용자
     * @param board 게시판 엔티티
     * @throws CustomGeneralException 조회 권한 없음
     */
    public void verifyCanReadBoard(CustomUserDetails user, Cblbmm board) {
        if (user.isAdmin()) return;

        boolean roleOk = "ALL".equals(board.getInqAthC())
            || hasSpringRole(user, board.getInqAthC());
        boolean deptOk = board.getBbrLmtnC() == null
            || board.getBbrLmtnC().equals(user.getBbrC());

        if (!roleOk || !deptOk) {
            throw new CustomGeneralException("게시판 접근 권한이 없습니다.");
        }
    }

    /**
     * 게시물 단건 가시성 검증
     *
     * @param user  인증 사용자
     * @param post  게시물 엔티티
     * @param board 게시판 엔티티
     * @throws CustomGeneralException 게시물 접근 권한 없음
     */
    public void verifyCanReadPost(CustomUserDetails user, Cblbcm post, Cblbmm board) {
        verifyCanReadBoard(user, board);
        if (user.isAdmin()) return;

        LocalDate today = LocalDate.now();
        boolean visible = "Y".equals(post.getSreYn())
            && (post.getSttDt() == null || !post.getSttDt().isAfter(today))
            && (post.getEndDt() == null || !post.getEndDt().isBefore(today));
        boolean deptOk = !"Y".equals(board.getBbrLmtnUseYn())
            || post.getBbrC() == null
            || post.getBbrC().equals(user.getBbrC());

        if (!visible || !deptOk) {
            throw new CustomGeneralException("게시물에 접근할 권한이 없습니다.");
        }
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

    private void verifyCanWrite(CustomUserDetails user, Cblbmm board) {
        if (user.isAdmin()) return;
        if ("ALL".equals(board.getEnrAthC())) return;
        if (hasSpringRole(user, board.getEnrAthC())) return;
        throw new CustomGeneralException("게시물 등록 권한이 없습니다.");
    }

    private void verifyCanModify(CustomUserDetails user, Cblbcm post) {
        if (user.isAdmin()) return;
        if (user.getEno().equals(post.getFstEnrUsid())) return;
        throw new CustomGeneralException("본인 게시물만 수정/삭제할 수 있습니다.");
    }

    private void verifyBbrC(CustomUserDetails user, String requestBbrC) {
        if (user.isAdmin() || requestBbrC == null) return;
        if (!requestBbrC.equals(user.getBbrC())) {
            throw new CustomGeneralException("본인 부서코드만 지정할 수 있습니다.");
        }
    }

    private boolean hasSpringRole(CustomUserDetails user, String roleCode) {
        return user.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(roleCode));
    }
}
