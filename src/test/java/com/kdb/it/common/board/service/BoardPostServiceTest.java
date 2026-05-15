package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
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
    @InjectMocks BoardPostService service;

    private Cblbmm publicBoard;
    private Cblbmm adminOnlyBoard;
    private CustomUserDetails normalUser;
    private CustomUserDetails adminUser;

    @BeforeEach
    void setUp() {
        publicBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0001").blbNm("공지사항")
            .inqAthC("ALL").enrAthC("ROLE_ADMIN")
            .repUseYn("N").cmmtUseYn("N")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        adminOnlyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0099").blbNm("내부게시판")
            .inqAthC("ROLE_ADMIN").enrAthC("ROLE_ADMIN")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        normalUser = new CustomUserDetails("USER001",  List.of("ITPZZ001"), "10002");
        adminUser = new CustomUserDetails("ADMIN001",  List.of("ITPAD001"), "99999");
    }

    @Test
    @DisplayName("관리자가 아닌 사용자는 관리자 전용 게시판 목록을 조회할 수 없다")
    void searchPosts_nonAdminOnAdminBoard_throwsForbidden() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0099", "N"))
            .willReturn(Optional.of(adminOnlyBoard));

        assertThatThrownBy(() ->
            service.searchPosts("BLBM-2026-0099", new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(), normalUser)
        ).isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("공개 게시판 게시물 목록을 일반 사용자가 조회할 수 있다")
    void searchPosts_publicBoard_normalUser_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard));
        given(postRepository.searchPosts(any(), any(), anyBoolean(), any(), any()))
            .willReturn(List.of());

        var result = service.searchPosts(
            "BLBM-2026-0001",
            new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(),
            normalUser
        );
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("ROLE_ADMIN 등록 게시판에 일반 사용자가 게시물을 등록하면 예외가 발생한다")
    void createPost_noWritePermission_throwsForbidden() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard)); // enrAthC = ROLE_ADMIN

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("제목");

        assertThatThrownBy(() -> service.createPost("BLBM-2026-0001", req, normalUser))
            .isInstanceOf(CustomGeneralException.class);
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
                && "N".equals(post.getHrkFxnYn())
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
        ok.setPritC("PRIT_C_002");
        ok.setHrkFxnYn("N");
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
        verify(postRepository).shiftGroupSqn(parent.getNacGrpNo(), parent.getNacGrpSqn(), parent.getNacGrpLev());
        verify(postRepository).save(argThat(reply -> reply.getNacGrpLev() == parent.getNacGrpLev() + 1));

        Cblbmm noReplyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0004").blbNm("답변 미지원")
            .inqAthC("ALL").enrAthC("ALL").repUseYn("N")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0004", "N"))
            .willReturn(Optional.of(noReplyBoard));
        assertThatThrownBy(() -> service.createReply("BLBM-2026-0004", "NAC-2026-0001", req, normalUser))
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("답변 기능");
    }

    @Test
    @DisplayName("비공개 기간 또는 부서 제한 게시물은 일반 사용자 접근을 차단한다")
    void verifyCanReadPost_hiddenOrDepartmentLimited_throws() {
        Cblbmm limitedBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0005").blbNm("부서한정")
            .inqAthC("ALL").enrAthC("ALL").repUseYn("Y")
            .bbrLmtnUseYn("Y").useYn("Y").delYn("N")
            .build();
        Cblbcm hidden = post("NAC-2026-0002", "OTHER");
        hidden.update(new Cblbcm.UpdateCommand(
            hidden.getNacNm(), hidden.getNacCone(), hidden.getNacTp(), hidden.getKdC(),
            hidden.getPritC(), hidden.getHrkFxnYn(), "N", hidden.getBbrC(),
            LocalDate.now().plusDays(1), null
        ));

        assertThatThrownBy(() -> service.verifyCanReadPost(normalUser, hidden, limitedBoard))
            .isInstanceOf(CustomGeneralException.class);
    }

    private Cblbmm writableBoard() {
        return Cblbmm.builder()
            .blbMngNo("BLBM-2026-0003").blbNm("자유게시판")
            .inqAthC("ALL").enrAthC("ALL")
            .repUseYn("Y").cmmtUseYn("Y")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();
    }

    private Cblbcm post(String id, String owner) {
        Cblbcm post = Cblbcm.builder()
            .nacMngNo(id)
            .blbMngNo("BLBM-2026-0003")
            .nacNm("테스트 게시물")
            .nacCone("<p>본문</p>")
            .pritC("PRIT_C_001")
            .hrkFxnYn("N")
            .sreYn("Y")
            .bbrC("10002")
            .nacInqNbr(0)
            .nacGrpNo(id)
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
