package com.kdb.it.common.speeddial.service;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.board.service.BoardTypeResolver;
import com.kdb.it.common.speeddial.dto.SpeedDialDto;
import com.kdb.it.common.speeddial.event.QnaRegisteredEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 전역 스피드다이얼의 FAQ 조회와 Q&A 등록을 범용 게시판에 연결합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeedDialService {

    private static final int FAQ_LIMIT = 50;

    /** 문의 구분 코드와 표시명. 화면 체크박스 노출 순서와 같게 유지합니다. */
    private static final Map<String, String> CATEGORY_NAMES =
            Map.of(
                    "BUDGET", "예산",
                    "PROJECT", "사업",
                    "IMPROVEMENT", "기능 개선",
                    "BUG", "오류/결함",
                    "OTHER", "기타");

    private final BoardTypeResolver boardTypeResolver;
    private final BoardPostService boardPostService;
    private final BoardPostRepository boardPostRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** FAQ 유형 게시판의 최신 공개 게시글을 반환합니다. */
    public List<SpeedDialDto.FaqResponse> getFaqs() {
        Cblbmm board = boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.FAQ_BOARD_TYPE);
        LocalDate today = LocalDate.now();
        return boardPostRepository
                .findTop50ByBlbMngNoAndDelYnAndXpoYnOrderByFstEnrDtmDescNacMngNoDesc(
                        board.getBlbMngNo(), "N", "Y")
                .stream()
                .filter(post -> isWithinPublicationPeriod(post, today))
                .limit(FAQ_LIMIT)
                .map(
                        post ->
                                new SpeedDialDto.FaqResponse(
                                        post.getNacMngNo(),
                                        post.getNacNm(),
                                        post.getNacCone(),
                                        post.getFstEnrDtm()))
                .toList();
    }

    /** Q&A 유형 게시판에 스피드다이얼 문의를 저장합니다. */
    @Transactional
    public String createQna(SpeedDialDto.QnaCreateRequest request, CustomUserDetails user) {
        validateRequest(request);
        Cblbmm board = boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE);
        String categoryName = CATEGORY_NAMES.get(request.category());
        String title = "[문의] (" + categoryName + ") " + request.title().trim();
        String writerBbrC = requireWriterDepartment(user);
        String content =
                "<p>화면(URL): "
                        + escape(request.screenName().trim())
                        + " ("
                        + escape(request.screenUrl().trim())
                        + ")</p><p>구분: "
                        + escape(categoryName)
                        + "</p><p>문의 및 요청 내용:</p>"
                        + request.content().trim();
        BoardPostDto.CreateRequest boardRequest =
                new BoardPostDto.CreateRequest(
                        title,
                        content,
                        "N",
                        request.privatePost() ? "N" : "Y",
                        writerBbrC,
                        null,
                        null,
                        List.of());
        String postId = boardPostService.createPost(board.getBlbMngNo(), boardRequest, user);
        eventPublisher.publishEvent(
                new QnaRegisteredEvent(
                        postId,
                        title,
                        categoryName,
                        request.title().trim(),
                        user.getEno(),
                        request.screenName().trim(),
                        request.screenUrl().trim(),
                        "/board/" + board.getBlbMngNo() + "?postId=" + postId));
        return postId;
    }

    private void validateRequest(SpeedDialDto.QnaCreateRequest request) {
        if (request == null) throw new CustomGeneralException("문의 내용을 입력하세요.");
        if (!StringUtils.hasText(request.title()) || request.title().length() > 90) {
            throw new CustomGeneralException("문의 제목을 확인하세요.");
        }
        if (!CATEGORY_NAMES.containsKey(request.category())) {
            throw new CustomGeneralException("문의 구분이 올바르지 않습니다.");
        }
        if (!StringUtils.hasText(request.screenName()) || request.screenName().length() > 200) {
            throw new CustomGeneralException("화면명을 확인하세요.");
        }
        String screenUrl = request.screenUrl() == null ? "" : request.screenUrl().trim();
        if (!screenUrl.matches("^/(?!/)(?!.*://).*$") || screenUrl.length() > 300) {
            throw new CustomGeneralException("화면 URL은 내부 경로만 입력할 수 있습니다.");
        }
        if (!StringUtils.hasText(request.content()) || isEmptyTiptapHtml(request.content())) {
            throw new CustomGeneralException("문의 및 요청 내용을 입력하세요.");
        }
    }

    /** 비공개 문의의 부서 범위와 작성 시점 부서 스냅샷에 사용할 로그인 부서를 확인합니다. */
    private String requireWriterDepartment(CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(user.getBbrC())) {
            throw new CustomGeneralException("소속 부서 정보를 확인할 수 없어 문의를 등록할 수 없습니다.");
        }
        return user.getBbrC();
    }

    private boolean isEmptyTiptapHtml(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim().isEmpty();
    }

    private boolean isWithinPublicationPeriod(Cblbcm post, LocalDate today) {
        return (post.getSttDt() == null || !post.getSttDt().isAfter(today))
                && (post.getEndDt() == null || !post.getEndDt().isBefore(today));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
