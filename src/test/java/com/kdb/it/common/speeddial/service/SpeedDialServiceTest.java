package com.kdb.it.common.speeddial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.board.service.BoardTypeResolver;
import com.kdb.it.common.speeddial.dto.SpeedDialDto;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpeedDialServiceTest {

    @Mock private BoardTypeResolver boardTypeResolver;
    @Mock private BoardPostService boardPostService;
    @Mock private BoardPostRepository boardPostRepository;
    @Mock private CustomUserDetails user;

    @Test
    void qnaUsesResolvedTypeBoardAndNeverClientBoardId() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE))
                .willReturn(board("BLBM-CHANGED", "005"));
        given(boardPostService.createPost(eq("BLBM-CHANGED"), any(), same(user)))
                .willReturn("NAC-2026-0001");

        String postId = service.createQna(validRequest(), user);

        assertThat(postId).isEqualTo("NAC-2026-0001");
        verify(boardPostService).createPost(eq("BLBM-CHANGED"), any(), same(user));
    }

    @Test
    void rejectsExternalScreenUrl() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);

        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "정보화사업",
                                                "https://example.com",
                                                "OTHER",
                                                "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("화면 URL");
    }

    @Test
    void faqOnlyContainsPostsInsidePublicationPeriod() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.FAQ_BOARD_TYPE))
                .willReturn(board("BLBM-FAQ", "004"));
        LocalDate today = LocalDate.now();
        given(
                        boardPostRepository
                                .findTop50ByBlbMngNoAndDelYnAndXpoYnOrderByFstEnrDtmDescNacMngNoDesc(
                                        "BLBM-FAQ", "N", "Y"))
                .willReturn(
                        List.of(
                                post("NAC-OPEN", "공개", null, null),
                                post("NAC-FUTURE", "예정", today.plusDays(1), null),
                                post("NAC-ENDED", "종료", null, today.minusDays(1))));

        List<SpeedDialDto.FaqResponse> result = service.getFaqs();

        assertThat(result).extracting(SpeedDialDto.FaqResponse::title).containsExactly("공개");
    }

    @Test
    void qnaEscapesScreenMetadataBeforeDelegatingToBoardPostService() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE))
                .willReturn(board("BLBM-QNA", "005"));
        given(boardPostService.createPost(eq("BLBM-QNA"), any(), same(user)))
                .willReturn("NAC-2026-0002");

        service.createQna(
                new SpeedDialDto.QnaCreateRequest(
                        "화면 <이름>", "/info?filter=<all>", "OTHER", "<p>문의</p>"),
                user);

        ArgumentCaptor<BoardPostDto.CreateRequest> captor =
                ArgumentCaptor.forClass(BoardPostDto.CreateRequest.class);
        verify(boardPostService).createPost(eq("BLBM-QNA"), captor.capture(), same(user));
        assertThat(captor.getValue().getNacCone())
                .contains("화면 &lt;이름&gt;")
                .contains("/info?filter=&lt;all&gt;");
    }

    @Test
    void qnaTitleKeepsOnlyInquiryPrefixAndCategoryName() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE))
                .willReturn(board("BLBM-QNA", "005"));
        given(boardPostService.createPost(eq("BLBM-QNA"), any(), same(user)))
                .willReturn("NAC-2026-0003");

        service.createQna(
                new SpeedDialDto.QnaCreateRequest("Q&A", "/board/qna", "IMPROVEMENT", "<p>문의</p>"),
                user);

        ArgumentCaptor<BoardPostDto.CreateRequest> captor =
                ArgumentCaptor.forClass(BoardPostDto.CreateRequest.class);
        verify(boardPostService).createPost(eq("BLBM-QNA"), captor.capture(), same(user));
        assertThat(captor.getValue().getNacNm()).isEqualTo("[문의] 기능 개선");
    }

    @Test
    void qnaAcceptsBudgetAndProjectCategories() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);
        given(boardTypeResolver.requireUniqueActiveBoard(BoardTypeResolver.QNA_BOARD_TYPE))
                .willReturn(board("BLBM-QNA", "005"));
        given(boardPostService.createPost(eq("BLBM-QNA"), any(), same(user)))
                .willReturn("NAC-2026-0004");

        service.createQna(
                new SpeedDialDto.QnaCreateRequest("예산 작성", "/budget/work", "BUDGET", "<p>문의</p>"),
                user);
        service.createQna(
                new SpeedDialDto.QnaCreateRequest(
                        "정보화사업", "/info/projects", "PROJECT", "<p>문의</p>"),
                user);

        ArgumentCaptor<BoardPostDto.CreateRequest> captor =
                ArgumentCaptor.forClass(BoardPostDto.CreateRequest.class);
        verify(boardPostService, times(2)).createPost(eq("BLBM-QNA"), captor.capture(), same(user));
        assertThat(captor.getAllValues())
                .extracting(BoardPostDto.CreateRequest::getNacNm)
                .containsExactly("[문의] 예산", "[문의] 사업");
    }

    @Test
    void rejectsMissingOrMalformedQnaFields() {
        SpeedDialService service =
                new SpeedDialService(boardTypeResolver, boardPostService, boardPostRepository);

        assertThatThrownBy(() -> service.createQna(null, user))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "화면", "/info", "UNKNOWN", "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                " ", "/info", "OTHER", "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "x".repeat(201), "/info", "OTHER", "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "화면", null, "OTHER", "<p>문의</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class);
        assertThatThrownBy(
                        () ->
                                service.createQna(
                                        new SpeedDialDto.QnaCreateRequest(
                                                "화면", "/info", "OTHER", "<p>&nbsp;</p>"),
                                        user))
                .isInstanceOf(CustomGeneralException.class);
    }

    private static SpeedDialDto.QnaCreateRequest validRequest() {
        return new SpeedDialDto.QnaCreateRequest(
                "정보화사업", "/info/projects?tab=active", "IMPROVEMENT", "<p>검색 조건을 저장해주세요.</p>");
    }

    private static Cblbmm board(String id, String type) {
        return Cblbmm.builder()
                .blbMngNo(id)
                .blbNm(type.equals("005") ? "Q&A" : "FAQ")
                .itPtlBlbTc(type)
                .repUseYn("N")
                .cmmtUseYn("N")
                .flEsnYn("N")
                .hedTagUseYn("N")
                .sreSqnNo(1)
                .useYn("Y")
                .delYn("N")
                .build();
    }

    private static Cblbcm post(String id, String title, LocalDate startDate, LocalDate endDate) {
        return Cblbcm.builder()
                .nacMngNo(id)
                .blbMngNo("BLBM-FAQ")
                .nacNm(title)
                .nacCone("<p>본문</p>")
                .nacInqNbr(0)
                .ancYn("N")
                .xpoYn("Y")
                .sttDt(startDate)
                .endDt(endDate)
                .flApgYn("N")
                .flNbr(0)
                .nacGrpSqn(0)
                .nacGrpLev(0)
                .fstEnrDtm(LocalDateTime.of(2026, 8, 30, 10, 0))
                .build();
    }
}
