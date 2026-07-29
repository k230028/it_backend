package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentListRow;
import com.kdb.it.common.board.repository.BoardCommentRepository;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BoardCommentServiceTest {

    @Mock BoardMetaRepository metaRepository;
    @Mock BoardPostRepository postRepository;
    @Mock BoardCommentRepository commentRepository;
    @Mock BoardPostService postService;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BoardCommentService service;

    @Captor ArgumentCaptor<Ccmmtm> captor;

    private Cblbmm boardWithComment;
    private Cblbmm boardNoComment;
    private Cblbcm post;
    private CustomUserDetails normalUser;

    @BeforeEach
    void setUp() {
        lenient()
                .when(metaRepository.findByBlbMngNoAndUseYnAndDelYn(anyString(), eq("Y"), eq("N")))
                .thenAnswer(
                        invocation ->
                                metaRepository.findByBlbMngNoAndDelYn(
                                        invocation.getArgument(0), "N"));
        lenient()
                .when(
                        postRepository.findByBlbMngNoAndNacMngNoAndDelYn(
                                anyString(), anyString(), eq("N")))
                .thenAnswer(
                        invocation ->
                                postRepository.findByNacMngNoAndDelYn(
                                        invocation.getArgument(1), "N"));
        lenient()
                .when(
                        commentRepository.findByCmmtMngNoAndNacMngNoAndDelYn(
                                anyLong(), anyString(), eq("N")))
                .thenAnswer(
                        invocation ->
                                commentRepository.findByCmmtMngNoAndDelYn(
                                        invocation.getArgument(0), "N"));

        boardWithComment =
                Cblbmm.builder()
                        .blbMngNo("BLBM-2026-0001")
                        .blbNm("자유게시판")
                        .itPtlBlbTc("002")
                        .repUseYn("N")
                        .cmmtUseYn("Y")
                        .useYn("Y")
                        .delYn("N")
                        .build();

        boardNoComment =
                Cblbmm.builder()
                        .blbMngNo("BLBM-2026-0002")
                        .blbNm("공지사항")
                        .itPtlBlbTc("001")
                        .repUseYn("N")
                        .cmmtUseYn("N")
                        .useYn("Y")
                        .delYn("N")
                        .build();

        post =
                Cblbcm.builder()
                        .nacMngNo("NAC-2026-0001")
                        .blbMngNo("BLBM-2026-0001")
                        .nacNm("테스트 게시물")
                        .delYn("N")
                        .build();

        normalUser = new CustomUserDetails("USER001", List.of("ITPZZ001"), "10002");
    }

    // ── 리플렉션 헬퍼 ──

    private void stubActiveBoardAndPost() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
    }

    /**
     * BaseEntity.fstEnrUsid 를 리플렉션으로 설정하는 공통 헬퍼.
     *
     * @param entity 대상 엔티티 (BaseEntity 하위)
     * @param userId 설정할 사번
     */
    private static void setFstEnrUsid(Object entity, String userId) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("fstEnrUsid");
            field.setAccessible(true);
            field.set(entity, userId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("fstEnrUsid 리플렉션 설정 실패", e);
        }
    }

    @Test
    @DisplayName("사용 중지 게시판은 댓글 사용자 API 5개를 모두 404로 차단한다")
    void inactiveBoard_rejectsAllCommentUserOperations() {
        given(metaRepository.findByBlbMngNoAndUseYnAndDelYn("BLBM-2026-0001", "Y", "N"))
                .willReturn(Optional.empty());
        var create = new BoardCommentDto.CreateRequest();
        var update = new BoardCommentDto.UpdateRequest();

        assertThatThrownBy(() -> service.getComments("BLBM-2026-0001", "NAC-2026-0001", normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.createComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", create, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.createReply(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, create, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, update, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, normalUser))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("다른 게시판 게시물 경로의 댓글 API 5개는 404이고 변경은 없다")
    void commentEndpoints_wrongBoardPost_throwNotFoundWithoutMutation() {
        Cblbcm otherBoardPost =
                Cblbcm.builder()
                        .nacMngNo("NAC-2026-0001")
                        .blbMngNo("BLBM-2026-9999")
                        .nacNm("다른 게시판 게시물")
                        .delYn("N")
                        .build();
        given(metaRepository.findByBlbMngNoAndUseYnAndDelYn("BLBM-2026-0001", "Y", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(otherBoardPost));
        given(
                        postRepository.findByBlbMngNoAndNacMngNoAndDelYn(
                                "BLBM-2026-0001", "NAC-2026-0001", "N"))
                .willReturn(Optional.empty());
        var create = new BoardCommentDto.CreateRequest();
        create.setCmmtCone("변조 댓글");
        var update = new BoardCommentDto.UpdateRequest();
        update.setCmmtCone("변조 수정");

        assertThatThrownBy(() -> service.getComments("BLBM-2026-0001", "NAC-2026-0001", normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.createComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", create, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.createReply(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, create, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, update, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, normalUser))
                .isInstanceOf(NotFoundException.class);

        verify(commentRepository, never()).findCommentRowsByPost(anyString());
        verify(commentRepository, never()).getNextSequenceValue();
        verify(commentRepository, never()).shiftGroupSqn(anyLong(), anyInt(), anyInt());
        verify(commentRepository, never()).save(any(Ccmmtm.class));
    }

    @Test
    @DisplayName("다른 게시물 댓글로 답글·수정·삭제를 요청하면 404이고 변경은 없다")
    void commentMutations_wrongPostComment_throwNotFoundWithoutMutation() {
        stubActiveBoardAndPost();
        Ccmmtm otherPostComment = buildComment(1L);
        setFstEnrUsid(otherPostComment, "USER001");
        ReflectionTestUtils.setField(otherPostComment, "nacMngNo", "NAC-2026-9999");
        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N"))
                .willReturn(Optional.of(otherPostComment));
        given(commentRepository.findByCmmtMngNoAndNacMngNoAndDelYn(1L, "NAC-2026-0001", "N"))
                .willReturn(Optional.empty());
        var create = new BoardCommentDto.CreateRequest();
        create.setCmmtCone("변조 답글");
        var update = new BoardCommentDto.UpdateRequest();
        update.setCmmtCone("변조 수정");

        assertThatThrownBy(
                        () ->
                                service.createReply(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, create, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, update, normalUser))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, normalUser))
                .isInstanceOf(NotFoundException.class);

        assertThat(otherPostComment.getCmmtCone()).isEqualTo("원본 댓글");
        assertThat(otherPostComment.getDelYn()).isEqualTo("N");
        verify(commentRepository, never()).shiftGroupSqn(anyLong(), anyInt(), anyInt());
        verify(commentRepository, never()).save(any(Ccmmtm.class));
    }

    // ── 댓글 생성 ──

    @Test
    @DisplayName("댓글 미지원 게시판에 댓글을 등록하면 예외가 발생한다")
    void createComment_boardNoComment_throws() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0002", "N"))
                .willReturn(Optional.of(boardNoComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));

        var request = new BoardCommentDto.CreateRequest("테스트 댓글 내용");

        assertThatThrownBy(
                        () ->
                                service.createComment(
                                        "BLBM-2026-0002", "NAC-2026-0001", request, normalUser))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("댓글 기능을 지원하지 않습니다");
    }

    @Test
    @DisplayName("댓글 지원 게시판에 댓글을 등록하면 CMMT- 형식의 ID가 반환되고 루트 그룹 정보가 설정된다")
    void createComment_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        // verifyCanReadPost는 void 메서드 — 아무 동작 없이 통과
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.getNextSequenceValue()).willReturn(1L);
        given(commentRepository.save(any(Ccmmtm.class))).willAnswer(inv -> inv.getArgument(0));

        var request = new BoardCommentDto.CreateRequest("테스트 댓글 내용");
        Long result = service.createComment("BLBM-2026-0001", "NAC-2026-0001", request, normalUser);

        assertThat(result).isNotNull();

        // 저장된 엔티티의 루트 그룹 필드를 검증
        verify(commentRepository).save(captor.capture());
        Ccmmtm saved = captor.getValue();
        assertThat(saved.getCmmtGrpNo()).isEqualTo(saved.getCmmtMngNo()); // 루트 댓글
        assertThat(saved.getCmmtGrpSqn()).isZero();
        assertThat(saved.getCmmtGrpLev()).isZero();
    }

    @Test
    @DisplayName("명시 멘션이 유효한 사용자이면 댓글 등록 시 알림 이벤트를 발행한다")
    void createComment_유효멘션_알림발행() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        given(commentRepository.getNextSequenceValue()).willReturn(2L);
        given(userRepository.findByEnoIn(anySet()))
                .willReturn(
                        List.of(com.kdb.it.common.iam.entity.CuserI.builder().eno("E002").build()));

        var request = new BoardCommentDto.CreateRequest("멘션 댓글", List.of("E002", "USER001", " "));
        service.createComment("BLBM-2026-0001", "NAC-2026-0001", request, normalUser);

        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 멘션 사용자이면 댓글 등록 시 알림을 발행하지 않는다")
    void createComment_없는멘션_알림미발행() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        given(commentRepository.getNextSequenceValue()).willReturn(3L);
        given(userRepository.findByEnoIn(anySet())).willReturn(List.of());

        var request = new BoardCommentDto.CreateRequest("멘션 댓글", List.of("E404"));
        service.createComment("BLBM-2026-0001", "NAC-2026-0001", request, normalUser);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── 댓글 수정 ──

    @Test
    @DisplayName("본인 댓글이 아닌 댓글을 수정하려 하면 예외가 발생한다")
    void updateComment_notOwner_throws() {
        stubActiveBoardAndPost();
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "OTHER_USER");

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));

        var request = new BoardCommentDto.UpdateRequest("수정 내용");

        // normalUser(USER001)는 OTHER_USER가 작성한 댓글을 수정할 수 없다
        assertThatThrownBy(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, request, normalUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("본인 댓글을 수정하면 예외 없이 본문이 변경된다")
    void updateComment_ownerSuccess() {
        stubActiveBoardAndPost();
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "USER001"); // normalUser.getEno()

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));

        var request = new BoardCommentDto.UpdateRequest("수정된 댓글 내용");

        // 예외 없이 완료되어야 한다 (JPA Dirty Checking — 명시적 save() 없음)
        assertThatCode(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, request, normalUser))
                .doesNotThrowAnyException();

        // 엔티티 본문이 수정되었는지 확인
        assertThat(comment.getCmmtCone()).isEqualTo("수정된 댓글 내용");
    }

    // ── 대댓글 생성 ──

    @Test
    @DisplayName("부모 댓글이 있는 게시판에 대댓글을 등록하면 CMMT- 형식의 ID와 lev=1이 반환된다")
    void createReply_success() {
        Long parentId = 1L;

        // 부모 댓글 (루트, lev=0)
        Ccmmtm parent =
                Ccmmtm.builder()
                        .cmmtMngNo(parentId)
                        .nacMngNo("NAC-2026-0001")
                        .cmmtCone("부모 댓글")
                        .cmmtGrpNo(parentId)
                        .cmmtGrpSqn(0)
                        .cmmtGrpLev(0)
                        .delYn("N")
                        .build();

        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        given(commentRepository.findByCmmtMngNoAndDelYn(parentId, "N"))
                .willReturn(Optional.of(parent));
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.getNextSequenceValue()).willReturn(2L);
        given(commentRepository.save(any(Ccmmtm.class))).willAnswer(inv -> inv.getArgument(0));

        var request = new BoardCommentDto.CreateRequest("대댓글 내용");
        Long result =
                service.createReply(
                        "BLBM-2026-0001", "NAC-2026-0001", parentId, request, normalUser);

        assertThat(result).isNotNull();

        // 저장된 대댓글의 그룹 레벨이 부모+1 인지 검증
        verify(commentRepository).save(captor.capture());
        Ccmmtm saved = captor.getValue();
        assertThat(saved.getCmmtGrpLev()).isEqualTo(1); // 부모 lev(0) + 1
        assertThat(saved.getCmmtGrpNo()).isEqualTo(parentId);
    }

    // ── 댓글 삭제 ──

    @Test
    @DisplayName("타인의 댓글을 일반 사용자가 삭제하려 하면 예외가 발생한다")
    void deleteComment_notOwner_throws() {
        stubActiveBoardAndPost();
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "OTHER_USER");

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));

        // normalUser(USER001)는 OTHER_USER의 댓글을 삭제할 수 없다
        assertThatThrownBy(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, normalUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("본인 댓글을 삭제하면 예외 없이 소프트 딜리트된다")
    void deleteComment_owner_success() {
        stubActiveBoardAndPost();
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "USER001"); // normalUser.getEno()

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));

        // 예외 없이 완료되어야 한다 (JPA Dirty Checking — 명시적 save() 없음)
        assertThatCode(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, normalUser))
                .doesNotThrowAnyException();

        // Soft Delete: DEL_YN = 'Y' 로 변경되었는지 확인
        assertThat(comment.getDelYn()).isEqualTo("Y");
    }

    // ── 댓글 목록 조회 ──

    @Test
    @DisplayName("getComments — 게시물에 댓글이 없으면 빈 목록을 반환한다")
    void getComments_emptyList() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.findCommentRowsByPost("NAC-2026-0001")).willReturn(List.of());

        // Act
        var result = service.getComments("BLBM-2026-0001", "NAC-2026-0001", normalUser);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getComments — 본인 댓글은 canModify=true로 반환된다")
    void getComments_ownComment_canModifyTrue() {
        // Arrange
        BoardCommentListRow row = buildCommentRow(1L, "USER001"); // normalUser.getEno()

        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.findCommentRowsByPost("NAC-2026-0001")).willReturn(List.of(row));

        // Act
        var result = service.getComments("BLBM-2026-0001", "NAC-2026-0001", normalUser);

        // Assert — 본인 댓글이므로 canModify=true
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCanModify()).isTrue();
    }

    @Test
    @DisplayName("getComments — 타인 댓글은 canModify=false로 반환된다")
    void getComments_otherComment_canModifyFalse() {
        // Arrange
        BoardCommentListRow row = buildCommentRow(1L, "OTHER_USER");

        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.findCommentRowsByPost("NAC-2026-0001")).willReturn(List.of(row));

        // Act
        var result = service.getComments("BLBM-2026-0001", "NAC-2026-0001", normalUser);

        // Assert — 타인 댓글이므로 canModify=false
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCanModify()).isFalse();
    }

    @Test
    @DisplayName("getComments — 관리자는 타인 댓글도 canModify=true로 반환된다")
    void getComments_admin_canModifyTrue() {
        // Arrange
        BoardCommentListRow row = buildCommentRow(1L, "OTHER_USER");

        CustomUserDetails adminUser = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.findCommentRowsByPost("NAC-2026-0001")).willReturn(List.of(row));

        // Act
        var result = service.getComments("BLBM-2026-0001", "NAC-2026-0001", adminUser);

        // Assert — 관리자는 모든 댓글에 canModify=true
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCanModify()).isTrue();
    }

    // ── verifyCanModify 관리자 경로 ──

    @Test
    @DisplayName("관리자는 타인의 댓글도 수정할 수 있다")
    void updateComment_admin_canModifyOthers() {
        stubActiveBoardAndPost();
        // Arrange
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "OTHER_USER");

        CustomUserDetails adminUser = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));

        var request = new BoardCommentDto.UpdateRequest("관리자 수정 내용");

        // Act & Assert — 관리자는 예외 없이 수정 가능
        assertThatCode(
                        () ->
                                service.updateComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, request, adminUser))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자는 타인의 댓글도 삭제할 수 있다")
    void deleteComment_admin_canDeleteOthers() {
        stubActiveBoardAndPost();
        // Arrange
        Ccmmtm comment = buildComment(1L);
        setFstEnrUsid(comment, "OTHER_USER");

        CustomUserDetails adminUser = new CustomUserDetails("ADMIN1", List.of("ITPAD001"), "IT001");

        given(commentRepository.findByCmmtMngNoAndDelYn(1L, "N")).willReturn(Optional.of(comment));

        // Act & Assert — 관리자는 예외 없이 삭제 가능
        assertThatCode(
                        () ->
                                service.deleteComment(
                                        "BLBM-2026-0001", "NAC-2026-0001", 1L, adminUser))
                .doesNotThrowAnyException();

        assertThat(comment.getDelYn()).isEqualTo("Y");
    }

    // ── createReply 댓글 미지원 게시판 경로 ──

    @Test
    @DisplayName("댓글 미지원 게시판에 대댓글 등록 시 예외가 발생한다")
    void createReply_boardNoComment_throws() {
        // Arrange
        Long parentId = 1L;
        Ccmmtm parent =
                Ccmmtm.builder()
                        .cmmtMngNo(parentId)
                        .nacMngNo("NAC-2026-0001")
                        .cmmtCone("부모 댓글")
                        .cmmtGrpNo(parentId)
                        .cmmtGrpSqn(0)
                        .cmmtGrpLev(0)
                        .delYn("N")
                        .build();

        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0002", "N"))
                .willReturn(Optional.of(boardNoComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
                .willReturn(Optional.of(post));
        given(commentRepository.findByCmmtMngNoAndDelYn(parentId, "N"))
                .willReturn(Optional.of(parent));

        var request = new BoardCommentDto.CreateRequest("대댓글 내용");

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                service.createReply(
                                        "BLBM-2026-0002",
                                        "NAC-2026-0001",
                                        parentId,
                                        request,
                                        normalUser))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("댓글 기능을 지원하지 않습니다");
    }

    // ── 내부 헬퍼 ──

    /**
     * 기본 댓글 엔티티 생성 헬퍼.
     *
     * @param cmmtMngNo 댓글관리번호
     * @return 루트 댓글 엔티티
     */
    private Ccmmtm buildComment(Long cmmtMngNo) {
        return Ccmmtm.builder()
                .cmmtMngNo(cmmtMngNo)
                .nacMngNo("NAC-2026-0001")
                .cmmtCone("원본 댓글")
                .cmmtGrpNo(cmmtMngNo)
                .cmmtGrpSqn(0)
                .cmmtGrpLev(0)
                .delYn("N")
                .build();
    }

    /**
     * 목록 프로젝션 테스트 픽스처 생성 헬퍼 — getComments 전용.
     *
     * @param cmmtMngNo 댓글관리번호
     * @param fstEnrUsid 최초등록사용자ID (canModify 판정용)
     * @return {@link BoardCommentListRow} 픽스처
     */
    private BoardCommentListRow buildCommentRow(Long cmmtMngNo, String fstEnrUsid) {
        return new BoardCommentListRow(
                cmmtMngNo,
                "NAC-2026-0001",
                "원본 댓글",
                cmmtMngNo,
                0,
                0,
                null,
                "N",
                fstEnrUsid,
                null,
                null);
    }
}
