package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bpqnam;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.QnaRepository;

/**
 * QnaService 단위 테스트
 *
 * <p>
 * 사전질의응답 서비스의 조회·등록·수정·답변 메서드를 검증합니다.
 * Bpqnam 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CustomUserDetails도 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QnaServiceTest {

    @Mock
    private QnaRepository qnaRepository;

    @Mock
    private CouncilRepository councilRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private QnaService qnaService;

    @BeforeEach
    void injectEntityManager() {
        // 신규 질의 저장 경로의 persist() 호출을 단위 테스트용 mock으로 연결한다.
        ReflectionTestUtils.setField(qnaService, "entityManager", entityManager);
    }

    private static final String ASCT_ID = "ASCT-2026-0001";
    private static final String QTN_ID  = "QTN-ASCT-2026-0001-01";

    private Bpqnam mockQna(String qtnId, String asctId, String qtnEno) {
        Bpqnam qna = mock(Bpqnam.class);
        given(qna.getQtnId()).willReturn(qtnId);
        given(qna.getItPtlAsctId()).willReturn(asctId);
        given(qna.getQtnDwuUsid()).willReturn(qtnEno);
        given(qna.getQtnCone()).willReturn("테스트 질의내용");
        given(qna.getRepDwuUsid()).willReturn(null);
        given(qna.getRepCone()).willReturn(null);
        given(qna.getQtnRpdRltYn()).willReturn("N");
        return qna;
    }

    // ───────────────────────────────────────────────────────
    // getQnaList
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getQnaList: 존재하지 않는 협의회ID이면 IllegalArgumentException을 던진다")
    void getQnaList_존재하지않는협의회_IllegalArgumentException발생() {
        // given
        given(councilRepository.existsById(ASCT_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> qnaService.getQnaList(ASCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ASCT_ID);
    }

    @Test
    @DisplayName("getQnaList: 존재하는 협의회의 질의응답 목록을 DTO로 반환한다")
    void getQnaList_존재하는협의회_DTO목록반환() {
        // given
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        given(councilRepository.existsById(ASCT_ID)).willReturn(true);
        given(qnaRepository.findByItPtlAsctIdAndDelYnOrderByFstEnrDtmAsc(ASCT_ID, "N"))
                .willReturn(List.of(qna));

        // when
        List<CouncilDto.QnaResponse> result = qnaService.getQnaList(ASCT_ID);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).qtnId()).isEqualTo(QTN_ID);
    }

    // ───────────────────────────────────────────────────────
    // createQna
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createQna: 존재하지 않는 협의회ID이면 IllegalArgumentException을 던진다")
    void createQna_존재하지않는협의회_IllegalArgumentException발생() {
        // given
        given(councilRepository.existsById(ASCT_ID)).willReturn(false);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        // when & then
        assertThatThrownBy(() -> qnaService.createQna(ASCT_ID,
                new CouncilDto.QnaCreateRequest("질의내용"), userDetails))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ASCT_ID);
    }

    @Test
    @DisplayName("createQna: 정상 요청이면 QTN_ID를 채번하여 저장하고 반환한다")
    void createQna_정상요청_질의ID반환() {
        // given
        // createQna는 existsById 대신 findByIdForUpdate(비관적 잠금)로 협의회 존재를 검증한다
        given(councilRepository.findByIdForUpdate(ASCT_ID))
                .willReturn(java.util.Optional.of(mock(com.kdb.it.domain.council.entity.Basctm.class)));
        given(qnaRepository.getNextQtnSeq(ASCT_ID)).willReturn(1);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E10001");

        // when
        String result = qnaService.createQna(ASCT_ID,
                new CouncilDto.QnaCreateRequest("질의내용"), userDetails);

        // then
        assertThat(result).startsWith("QTN-").contains(ASCT_ID);
        verify(entityManager).persist(any(Bpqnam.class));
    }

    // ───────────────────────────────────────────────────────
    // updateQna
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateQna: 존재하지 않는 질의ID이면 IllegalArgumentException을 던진다")
    void updateQna_존재하지않는질의_IllegalArgumentException발생() {
        // given
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.empty());
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        // when & then
        assertThatThrownBy(() -> qnaService.updateQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaUpdateRequest("수정내용"), userDetails))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(QTN_ID);
    }

    @Test
    @DisplayName("updateQna: 협의회ID가 일치하지 않으면 IllegalArgumentException을 던진다")
    void updateQna_협의회ID불일치_IllegalArgumentException발생() {
        // given — qna는 다른 협의회 소속
        Bpqnam qna = mockQna(QTN_ID, "ASCT-2026-9999", "E10001");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        // when & then
        assertThatThrownBy(() -> qnaService.updateQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaUpdateRequest("수정내용"), userDetails))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("협의회ID");
    }

    @Test
    @DisplayName("updateQna: 본인이 아니고 관리자도 아니면 AccessDeniedException을 던진다")
    void updateQna_본인아님_관리자아님_AccessDenied발생() {
        // given
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E_OWNER");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E10001");
        given(userDetails.isAdmin()).willReturn(false);

        // when & then
        assertThatThrownBy(() -> qnaService.updateQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaUpdateRequest("수정 시도"), userDetails))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("updateQna: 본인 질의이면 내용을 수정한다")
    void updateQna_본인질의_수정성공() {
        // given
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E10001");
        given(userDetails.isAdmin()).willReturn(false);

        // when
        qnaService.updateQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaUpdateRequest("수정내용"), userDetails);

        // then
        verify(qna).updateQuestion("수정내용");
    }

    // ───────────────────────────────────────────────────────
    // replyQna
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("replyQna: 존재하지 않는 질의ID이면 IllegalArgumentException을 던진다")
    void replyQna_존재하지않는질의_IllegalArgumentException발생() {
        // given
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.empty());
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        // when & then
        assertThatThrownBy(() -> qnaService.replyQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaReplyRequest("답변내용"), userDetails))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(QTN_ID);
    }

    @Test
    @DisplayName("replyQna: 협의회ID가 일치하지 않으면 IllegalArgumentException을 던진다")
    void replyQna_협의회ID불일치_IllegalArgumentException발생() {
        // Arrange: qna는 다른 협의회 소속
        Bpqnam qna = mockQna(QTN_ID, "ASCT-2026-9999", "E10001");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        // Act & Assert: 협의회ID 불일치 분기 진입
        assertThatThrownBy(() -> qnaService.replyQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaReplyRequest("답변내용"), userDetails))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("협의회ID");
    }

    @Test
    @DisplayName("replyQna: 정상 요청이면 답변을 등록한다")
    void replyQna_정상요청_답변등록() {
        // given
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getEno()).willReturn("E20001");

        // when
        qnaService.replyQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaReplyRequest("답변내용"), userDetails);

        // then
        verify(qna).reply("E20001", "답변내용");
    }

    @Test
    @DisplayName("replyQna: 사업 주관부서와 요청자 부서가 다르면 답변을 거부한다")
    void replyQna_주관부서불일치_접근거부() {
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        Basctm council = mock(Basctm.class);
        Bprojm project = mock(Bprojm.class);
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        given(council.getAbusMngNo()).willReturn("PRJ-001");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-001", "Y", "N"))
                .willReturn(Optional.of(project));
        given(project.getSvnDpmC()).willReturn("D001");
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getBbrC()).willReturn("D002");

        assertThatThrownBy(() -> qnaService.replyQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaReplyRequest("답변내용"), user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("주관부서");
    }

    @Test
    @DisplayName("replyQna: 사업 주관부서와 요청자 부서가 같으면 답변한다")
    void replyQna_주관부서일치_답변등록() {
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        Basctm council = mock(Basctm.class);
        Bprojm project = mock(Bprojm.class);
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        given(council.getAbusMngNo()).willReturn("PRJ-001");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-001", "Y", "N"))
                .willReturn(Optional.of(project));
        given(project.getSvnDpmC()).willReturn("D001");
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getBbrC()).willReturn("D001");
        given(user.getEno()).willReturn("E20001");

        qnaService.replyQna(ASCT_ID, QTN_ID, new CouncilDto.QnaReplyRequest("답변내용"), user);

        verify(qna).reply("E20001", "답변내용");
    }

    @Test
    @DisplayName("replyQna: 요청자 부서를 확인할 수 없으면 기존 답변 동작을 유지한다")
    void replyQna_요청자부서미확인_답변등록() {
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "E10001");
        Basctm council = mock(Basctm.class);
        Bprojm project = mock(Bprojm.class);
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));
        given(council.getAbusMngNo()).willReturn("PRJ-001");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-001", "Y", "N"))
                .willReturn(Optional.of(project));
        given(project.getSvnDpmC()).willReturn("D001");
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getEno()).willReturn("E20001");

        qnaService.replyQna(ASCT_ID, QTN_ID, new CouncilDto.QnaReplyRequest("답변내용"), user);

        verify(qna).reply("E20001", "답변내용");
    }

    @Test
    @DisplayName("updateQna: 관리자이면 본인 질의가 아니어도 수정할 수 있다")
    void updateQna_관리자_타인질의수정가능() {
        // Arrange: qna 등록자는 OTHER_ENO, 로그인 사용자는 관리자
        Bpqnam qna = mockQna(QTN_ID, ASCT_ID, "OTHER_ENO");
        given(qnaRepository.findById(QTN_ID)).willReturn(Optional.of(qna));

        CustomUserDetails admin = mock(CustomUserDetails.class);
        given(admin.getEno()).willReturn("E_ADMIN");
        given(admin.isAdmin()).willReturn(true);

        // Act: 예외 없이 수정 완료
        qnaService.updateQna(ASCT_ID, QTN_ID,
                new CouncilDto.QnaUpdateRequest("관리자수정내용"), admin);

        // Assert
        verify(qna).updateQuestion("관리자수정내용");
    }

    @Test
    @DisplayName("getQnaList: 빈 목록인 협의회도 정상적으로 빈 리스트를 반환한다")
    void getQnaList_빈목록_빈리스트반환() {
        // Arrange
        given(councilRepository.existsById(ASCT_ID)).willReturn(true);
        given(qnaRepository.findByItPtlAsctIdAndDelYnOrderByFstEnrDtmAsc(ASCT_ID, "N"))
                .willReturn(List.of());

        // Act
        List<CouncilDto.QnaResponse> result = qnaService.getQnaList(ASCT_ID);

        // Assert
        assertThat(result).isEmpty();
    }
}
