package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BoardPostServiceTest {

    @Mock BoardMetaRepository metaRepository;
    @Mock BoardPostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks BoardPostService service;

    private Cblbmm publicBoard;
    private Cblbmm adminOnlyBoard;
    private CustomUserDetails normalUser;
    private CustomUserDetails adminUser;

    @BeforeEach
    void setUp() {
        // 공지사항(BLB_TC='001') — 조회는 전체 공개, 등록은 관리자 전용
        publicBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0001").blbNm("공지사항").blbTp("001")
            .repUseYn("N").cmmtUseYn("N")
            .useYn("Y").delYn("N")
            .build();

        adminOnlyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0099").blbNm("내부게시판").blbTp("001")
            .useYn("Y").delYn("N")
            .build();

        normalUser = new CustomUserDetails("USER001",  List.of("ITPZZ001"), "10002");
        adminUser = new CustomUserDetails("ADMIN001",  List.of("ITPAD001"), "99999");
    }

    @Test
    @DisplayName("일반 사용자도 모든 게시판 게시물 목록을 조회할 수 있다 (조회 전체 공개)")
    void searchPosts_normalUser_anyBoard_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0099", "N"))
            .willReturn(Optional.of(adminOnlyBoard));
        given(postRepository.searchPosts(any(), any(), anyBoolean()))
            .willReturn(List.of());

        var result = service.searchPosts(
            "BLBM-2026-0099", new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(), normalUser);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("공개 게시판 게시물 목록을 일반 사용자가 조회할 수 있다")
    void searchPosts_publicBoard_normalUser_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard));
        given(postRepository.searchPosts(any(), any(), anyBoolean()))
            .willReturn(List.of());

        var result = service.searchPosts(
            "BLBM-2026-0001",
            new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(),
            normalUser
        );
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("공지사항(BLB_TC='001') 게시판에 일반 사용자가 게시물을 등록하면 예외가 발생한다")
    void createPost_noWritePermission_throwsForbidden() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard)); // blbTp = 001 → 관리자만 등록

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("제목");

        assertThatThrownBy(() -> service.createPost("BLBM-2026-0001", req, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("관리자만");
    }

    @Test
    @DisplayName("전체 등록 가능 게시판에는 일반 사용자가 게시물을 등록할 수 있다")
    void createPost_publicWrite_success() {
        Cblbmm writableBoard = writableBoard();
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard));
        given(postRepository.getNextSequenceValue()).willReturn(9L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("제목");
        req.setNacCone("<script>alert(1)</script><p>본문</p>");
        req.setBbrC("10002");

        String result = service.createPost("BLBM-2026-0003", req, normalUser);

        assertThat(result).startsWith("NAC-");
        verify(postRepository).save(argThat(post ->
            "제목".equals(post.getNacNm())
                && "10002".equals(post.getBbrC())
                && "N".equals(post.getAncYn())
                && "Y".equals(post.getSreYn())
        ));
    }

    @Test
    @DisplayName("게시물 상세 조회는 조회수를 증가시키고 작성자 수정 가능 여부를 반환한다")
    void getPostDetail_owner_success() {
        Cblbmm writableBoard = writableBoard();
        Cblbcm post = post("NAC-2026-0001", "USER001");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(post));

        var result = service.getPostDetail("BLBM-2026-0003", "NAC-2026-0001", normalUser);

        assertThat(result.getNacMngNo()).isEqualTo("NAC-2026-0001");
        assertThat(result.isCanModify()).isTrue();
        assertThat(post.getNacInqNbr()).isEqualTo(1);
    }

    @Test
    @DisplayName("작성자는 게시물을 수정하고 부서 불일치 요청은 차단된다")
    void updatePost_ownerAndInvalidDepartment() {
        Cblbcm post = post("NAC-2026-0001", "USER001");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard()));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(post));
        var ok = new com.kdb.it.common.board.dto.BoardPostDto.UpdateRequest();
        ok.setNacNm("수정 제목");
        ok.setNacCone("<p>수정</p>");
        ok.setAncYn("N");
        ok.setSreYn("Y");
        ok.setBbrC("10002");

        service.updatePost("BLBM-2026-0003", "NAC-2026-0001", ok, normalUser);

        assertThat(post.getNacNm()).isEqualTo("수정 제목");

        var invalid = new com.kdb.it.common.board.dto.BoardPostDto.UpdateRequest();
        invalid.setNacNm("다른 부서");
        invalid.setBbrC("99999");
        assertThatThrownBy(() -> service.updatePost("BLBM-2026-0003", "NAC-2026-0001", invalid, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("부서코드");
    }

    @Test
    @DisplayName("관리자는 타인 게시물을 삭제할 수 있다")
    void deletePost_admin_success() {
        Cblbcm post = post("NAC-2026-0001", "USER001");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard()));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(post));

        service.deletePost("BLBM-2026-0003", "NAC-2026-0001", adminUser);

        assertThat(post.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("답변 지원 게시판은 답변글 그룹 정보를 생성하고 미지원 게시판은 예외를 던진다")
    void createReply_successAndUnsupported() {
        Cblbmm writableBoard = writableBoard();
        Cblbcm parent = post("NAC-2026-0001", "USER001");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(parent));
        given(postRepository.getNextSequenceValue()).willReturn(10L);

        var req = new com.kdb.it.common.board.dto.BoardPostDto.ReplyCreateRequest();
        req.setNacNm("답변");
        req.setNacCone("<p>답변</p>");
        req.setBbrC("10002");

        String result = service.createReply("BLBM-2026-0003", "NAC-2026-0001", req, normalUser);

        assertThat(result).startsWith("NAC-");
        verify(postRepository).shiftGroupSqn(parent.getNacUnqId(), parent.getNacGrpSqn(), parent.getNacGrpLev());
        verify(postRepository).save(argThat(reply -> reply.getNacGrpLev() == parent.getNacGrpLev() + 1));

        Cblbmm noReplyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0004").blbNm("답변 미지원").blbTp("002").repUseYn("N")
            .useYn("Y").delYn("N")
            .build();
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0004", "N"))
            .willReturn(Optional.of(noReplyBoard));
        assertThatThrownBy(() -> service.createReply("BLBM-2026-0004", "NAC-2026-0001", req, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("답변 기능");
    }

    @Test
    @DisplayName("공개 시작 전 게시물은 일반 사용자 접근을 차단한다")
    void verifyCanReadPost_beforePublishStart_throws() {
        Cblbmm board = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0005").blbNm("자유게시판").blbTp("002").repUseYn("Y")
            .useYn("Y").delYn("N")
            .build();
        Cblbcm hidden = post("NAC-2026-0002", "OTHER");
        hidden.update(new Cblbcm.UpdateCommand(
            hidden.getNacNm(), hidden.getNacCone(),
            hidden.getAncYn(), "N", hidden.getBbrC(),
            LocalDate.now().plusDays(1), null
        ));

        assertThatThrownBy(() -> service.verifyCanReadPost(normalUser, hidden, board))
            .isInstanceOf(CustomGeneralException.class);
    }

    // ── verifyCanReadPost: 다양한 날짜/부서 분기 ──

    @Test
    @DisplayName("시작일/종료일이 null인 게시물은 일반 사용자가 접근할 수 있다")
    void verifyCanReadPost_nullDates_allowed() {
        // Arrange: sttDt = null, endDt = null, sreYn = Y
        Cblbmm board = writableBoard();
        Cblbcm openPost = post("NAC-2026-0010", "OTHER");
        // sttDt, endDt은 기본 null

        // Act & Assert: 예외 없이 통과
        service.verifyCanReadPost(normalUser, openPost, board);
    }

    @Test
    @DisplayName("종료일이 지난 게시물은 일반 사용자 접근을 차단한다")
    void verifyCanReadPost_pastEndDt_throws() {
        // Arrange: endDt = 어제
        Cblbmm board = writableBoard();
        Cblbcm expiredPost = post("NAC-2026-0011", "OTHER");
        expiredPost.update(new Cblbcm.UpdateCommand(
            expiredPost.getNacNm(), expiredPost.getNacCone(),
            expiredPost.getAncYn(), "Y", expiredPost.getBbrC(),
            null, LocalDate.now().minusDays(1)
        ));

        // Act & Assert
        assertThatThrownBy(() -> service.verifyCanReadPost(normalUser, expiredPost, board))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("접근할 권한");
    }

    @Test
    @DisplayName("화면여부가 N인 게시물은 일반 사용자 접근을 차단한다")
    void verifyCanReadPost_sreYnN_throws() {
        // Arrange: sreYn = N → 비공개
        Cblbmm board = writableBoard();
        Cblbcm hiddenPost = post("NAC-2026-0012", "OTHER");
        hiddenPost.update(new Cblbcm.UpdateCommand(
            hiddenPost.getNacNm(), hiddenPost.getNacCone(),
            hiddenPost.getAncYn(), "N", hiddenPost.getBbrC(),
            null, null
        ));

        // Act & Assert
        assertThatThrownBy(() -> service.verifyCanReadPost(normalUser, hiddenPost, board))
            .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("관리자는 비공개 게시물도 접근할 수 있다")
    void verifyCanReadPost_admin_canAccessHidden() {
        // Arrange: sttDt = 미래 (공개 전)
        Cblbmm board = writableBoard();
        Cblbcm futurePost = post("NAC-2026-0013", "OTHER");
        futurePost.update(new Cblbcm.UpdateCommand(
            futurePost.getNacNm(), futurePost.getNacCone(),
            futurePost.getAncYn(), "N", futurePost.getBbrC(),
            LocalDate.now().plusDays(5), null
        ));

        // Act & Assert: adminUser는 두 번째 isAdmin() return → 예외 없음
        service.verifyCanReadPost(adminUser, futurePost, board);
    }

    // ── findActiveBoard / findPost: not found 경로 ──

    @Test
    @DisplayName("존재하지 않는 게시판 조회 시 예외가 발생한다")
    void searchPosts_boardNotFound_throws() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-NOT-EXIST", "N"))
            .willReturn(java.util.Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
            service.searchPosts("BLBM-NOT-EXIST",
                new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(), normalUser)
        ).isInstanceOf(CustomGeneralException.class)
         .hasMessageContaining("게시판을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 게시물 상세 조회 시 예외가 발생한다")
    void getPostDetail_postNotFound_throws() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.findByNacMngNoAndDelYn("NAC-NOT-EXIST", "N"))
            .willReturn(java.util.Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
            service.getPostDetail("BLBM-2026-0003", "NAC-NOT-EXIST", normalUser)
        ).isInstanceOf(CustomGeneralException.class)
         .hasMessageContaining("게시물을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("타인 게시물을 수정하려는 일반 사용자는 차단된다")
    void updatePost_nonOwner_throws() {
        // Arrange: 게시물 작성자 OTHER, 요청자 USER001
        Cblbcm post = post("NAC-2026-0099", "OTHER");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0099", "N"))
            .willReturn(java.util.Optional.of(post));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.UpdateRequest();
        req.setNacNm("수정 제목");
        req.setNacCone("<p>수정</p>");
        req.setBbrC("10002");

        // Act & Assert
        assertThatThrownBy(() -> service.updatePost("BLBM-2026-0003", "NAC-2026-0099", req, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("본인 게시물");
    }

    @Test
    @DisplayName("타인 게시물을 삭제하려는 일반 사용자는 차단된다")
    void deletePost_nonOwner_throws() {
        // Arrange
        Cblbcm post = post("NAC-2026-0098", "OTHER");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0098", "N"))
            .willReturn(java.util.Optional.of(post));

        // Act & Assert
        assertThatThrownBy(() -> service.deletePost("BLBM-2026-0003", "NAC-2026-0098", normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("본인 게시물");
    }

    @Test
    @DisplayName("createPost: ancYn/sreYn이 null인 경우 기본값이 적용된다")
    void createPost_nullOptions_defaultsApplied() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.getNextSequenceValue()).willReturn(11L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("기본값 테스트");
        req.setNacCone("<p>본문</p>");
        req.setBbrC("10002");
        req.setAncYn(null); // → 기본값 N
        req.setSreYn(null);    // → 기본값 Y

        // Act
        String result = service.createPost("BLBM-2026-0003", req, normalUser);

        // Assert
        assertThat(result).startsWith("NAC-");
        verify(postRepository).save(argThat(savedPost ->
            "N".equals(savedPost.getAncYn())
                && "Y".equals(savedPost.getSreYn())
        ));
    }

    @Test
    @DisplayName("createPost: ancYn/sreYn이 명시된 경우 해당 값이 사용된다")
    void createPost_explicitOptions_usedAsProvided() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.getNextSequenceValue()).willReturn(12L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("명시값 테스트");
        req.setNacCone("<p>본문</p>");
        req.setBbrC("10002");
        req.setAncYn("Y");
        req.setSreYn("N");

        // Act
        String result = service.createPost("BLBM-2026-0003", req, normalUser);

        // Assert
        assertThat(result).startsWith("NAC-");
        verify(postRepository).save(argThat(savedPost ->
            "Y".equals(savedPost.getAncYn())
                && "N".equals(savedPost.getSreYn())
        ));
    }

    @Test
    @DisplayName("createPost: bbrC가 null인 경우 부서코드 검증을 건너뛴다")
    void createPost_nullBbrC_skipsValidation() {
        // Arrange
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(writableBoard()));
        given(postRepository.getNextSequenceValue()).willReturn(14L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("bbrC null 테스트");
        req.setNacCone("<p>본문</p>");
        req.setBbrC(null); // null → 검증 건너뜀

        // Act
        String result = service.createPost("BLBM-2026-0003", req, normalUser);

        // Assert
        assertThat(result).startsWith("NAC-");
    }

    @Test
    @DisplayName("createPost: 명시 멘션 사용자가 존재하면 알림 이벤트를 발행한다")
    void createPost_유효멘션_알림발행() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard()));
        given(postRepository.getNextSequenceValue()).willReturn(15L);
        given(userRepository.findByEnoIn(anySet())).willReturn(List.of(
            com.kdb.it.common.iam.entity.CuserI.builder().eno("E002").build()));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("멘션 게시물");
        req.setNacCone("본문");
        req.setMentionedEnos(List.of("E002", "USER001", ""));

        service.createPost("BLBM-2026-0003", req, normalUser);

        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("createPost: 존재하지 않는 멘션만 있으면 알림 이벤트를 발행하지 않는다")
    void createPost_없는멘션_알림미발행() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(Optional.of(writableBoard()));
        given(postRepository.getNextSequenceValue()).willReturn(17L);
        given(userRepository.findByEnoIn(anySet())).willReturn(List.of());

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("멘션 게시물");
        req.setNacCone("본문");
        req.setMentionedEnos(List.of("E404"));

        service.createPost("BLBM-2026-0003", req, normalUser);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createPost: 관리자가 등록 시 부서코드 검증을 건너뛴다")
    void createPost_adminSkipsBbrCValidation() {
        // Arrange: 관리자 전용 등록 게시판
        Cblbmm adminWriteBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0099").blbNm("관리자등록").blbTp("001")
            .repUseYn("N").cmmtUseYn("N")
            .useYn("Y").delYn("N")
            .build();
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0099", "N"))
            .willReturn(java.util.Optional.of(adminWriteBoard));
        given(postRepository.getNextSequenceValue()).willReturn(13L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("관리자 게시물");
        req.setNacCone("<p>관리자 본문</p>");
        req.setBbrC("99999"); // 다른 부서코드여도 관리자이면 통과

        // Act
        String result = service.createPost("BLBM-2026-0099", req, adminUser);

        // Assert
        assertThat(result).startsWith("NAC-");
    }

    @Test
    @DisplayName("createReply: 공지사항(BLB_TC='001') 게시판에서 일반 사용자가 답변 등록 시 예외가 발생한다")
    void createReply_noWritePermission_throws() {
        // Arrange: 답변 지원되지만 공지사항(001) → 관리자만 등록 가능
        Cblbmm adminWriteReplyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0020").blbNm("공지답변").blbTp("001")
            .repUseYn("Y").cmmtUseYn("N")
            .useYn("Y").delYn("N")
            .build();
        Cblbcm parent = post("NAC-2026-0060", "ADMIN001");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0020", "N"))
            .willReturn(java.util.Optional.of(adminWriteReplyBoard));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0060", "N"))
            .willReturn(java.util.Optional.of(parent));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.ReplyCreateRequest();
        req.setNacNm("답변 시도");
        req.setNacCone("<p>답변</p>");
        req.setBbrC("10002");

        // Act & Assert
        assertThatThrownBy(() -> service.createReply("BLBM-2026-0020", "NAC-2026-0060", req, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("관리자만");
    }

    @Test
    @DisplayName("getPostDetail: 작성자가 아닌 사람은 canModify=false를 반환한다")
    void getPostDetail_nonOwner_canModifyFalse() {
        // Arrange
        Cblbmm board = writableBoard();
        Cblbcm otherPost = post("NAC-2026-0070", "OTHER");
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0003", "N"))
            .willReturn(java.util.Optional.of(board));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0070", "N"))
            .willReturn(java.util.Optional.of(otherPost));

        // Act
        var result = service.getPostDetail("BLBM-2026-0003", "NAC-2026-0070", normalUser);

        // Assert
        assertThat(result.isCanModify()).isFalse();
    }

    @Test
    @DisplayName("verifyCanWrite: 공지사항이 아닌 게시판은 일반 사용자가 등록할 수 있다")
    void verifyCanWrite_roleMatch_allowed() {
        // Arrange: blbTp = 002 (비공지) → 인증 사용자 등록 허용
        Cblbmm roleWriteBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0030").blbNm("자유게시판").blbTp("002")
            .repUseYn("N").cmmtUseYn("N")
            .useYn("Y").delYn("N")
            .build();
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0030", "N"))
            .willReturn(java.util.Optional.of(roleWriteBoard));
        given(postRepository.getNextSequenceValue()).willReturn(16L);
        given(postRepository.save(any(Cblbcm.class))).willAnswer(inv -> inv.getArgument(0));

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("역할 등록 테스트");
        req.setNacCone("<p>본문</p>");

        // Act
        String result = service.createPost("BLBM-2026-0030", req, normalUser);
        assertThat(result).startsWith("NAC-");
    }

    private Cblbmm writableBoard() {
        // 공지사항이 아닌 일반 게시판(BLB_TC='002') — 인증 사용자 전체 등록 가능
        return Cblbmm.builder()
            .blbMngNo("BLBM-2026-0003").blbNm("자유게시판").blbTp("002")
            .repUseYn("Y").cmmtUseYn("Y")
            .useYn("Y").delYn("N")
            .build();
    }

    private Cblbcm post(String id, String owner) {
        Cblbcm post = Cblbcm.builder()
            .nacMngNo(id)
            .blbMngNo("BLBM-2026-0003")
            .nacNm("테스트 게시물")
            .nacCone("<p>본문</p>")
            .ancYn("N")
            .sreYn("Y")
            .bbrC("10002")
            .nacInqNbr(0)
            .nacUnqId(id)
            .nacGrpSqn(0)
            .nacGrpLev(0)
            .flApgYn("N")
            .flNbr(0)
            .delYn("N")
            .build();
        ReflectionTestUtils.setField(post, "fstEnrUsid", owner);
        return post;
    }
}
