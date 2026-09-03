package com.kdb.it.domain.budget.document.service;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository.GuideDocListView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * GuideDocService 단위 테스트
 *
 * <p>가이드 문서 서비스의 CRUD 메서드를 검증합니다. Bgdocm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다. Oracle
 * DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuideDocServiceTest {

    @Mock private GuideDocRepository guideDocRepository;
    @Mock private BgdocNumberAllocator bgdocNumberAllocator;

    @InjectMocks private GuideDocService guideDocService;

    private Bgdocm mockDocument(String docMngNo, String docNm) {
        Bgdocm doc = mock(Bgdocm.class);
        given(doc.getDocMngNo()).willReturn(docMngNo);
        given(doc.getDocTtlCone()).willReturn(docNm);
        given(doc.getNacTxtInf()).willReturn(null);
        given(doc.getDelYn()).willReturn("N");
        return doc;
    }

    private GuideDocListView mockListView(String docMngNo, String docNm) {
        GuideDocListView view = mock(GuideDocListView.class);
        given(view.getDocMngNo()).willReturn(docMngNo);
        given(view.getDocTtlCone()).willReturn(docNm);
        given(view.getDelYn()).willReturn("N");
        return view;
    }

    // ───────────────────────────────────────────────────────
    // getDocumentList
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDocumentList: GDOC 문서만 본문 없는 DTO로 반환한다")
    void getDocumentList_GDOC문서만_본문없는DTO목록반환() {
        // given
        GuideDocListView view1 = mockListView("GDOC-2026-0001", "가이드문서1");
        given(guideDocRepository.findListViewsByDocMngNoStartingWithAndDelYn("GDOC-", "N"))
                .willReturn(List.of(view1));

        // when
        List<GuideDocDto.ListResponse> result = guideDocService.getDocumentList();

        // then: 목록 응답에는 본문(nacTxtInf) 필드 자체가 존재하지 않는다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).docMngNo()).isEqualTo("GDOC-2026-0001");
        assertThat(result.get(0).docTtlCone()).isEqualTo("가이드문서1");
        assertThat(declaredMethodNames(GuideDocDto.ListResponse.class)).doesNotContain("nacTxtInf");
        verify(guideDocRepository).findListViewsByDocMngNoStartingWithAndDelYn("GDOC-", "N");
    }

    @Test
    @DisplayName("getDocumentList: 문서가 없으면 빈 목록을 반환한다")
    void getDocumentList_문서없음_빈목록반환() {
        // given
        given(guideDocRepository.findListViewsByDocMngNoStartingWithAndDelYn("GDOC-", "N"))
                .willReturn(List.of());

        // when
        List<GuideDocDto.ListResponse> result = guideDocService.getDocumentList();

        // then
        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getDocument
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDocument: 존재하는 문서관리번호로 조회하면 DTO를 반환한다")
    void getDocument_존재하는문서_DTO반환() {
        // given
        Bgdocm doc = mockDocument("GDOC-2026-0001", "가이드문서");
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "GDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.of(doc));

        // when
        GuideDocDto.Response result = guideDocService.getDocument("GDOC-2026-0001");

        // then
        assertThat(result.getDocMngNo()).isEqualTo("GDOC-2026-0001");
    }

    @Test
    @DisplayName("getDocument: FDOC 문서는 존재해도 존재하지 않는 문서로 처리한다")
    void getDocument_FDOC문서_IllegalArgumentException발생() {
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "FDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> guideDocService.getDocument("FDOC-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FDOC-2026-0001");
    }

    @Test
    @DisplayName("getDocument: 존재하지 않는 문서관리번호이면 IllegalArgumentException을 던진다")
    void getDocument_존재하지않는문서_IllegalArgumentException발생() {
        // given
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "INVALID", "GDOC-", "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> guideDocService.getDocument("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    // ───────────────────────────────────────────────────────
    // createDocument
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createDocument: 문서관리번호 미입력 시 시퀀스로 자동 채번하여 생성한다")
    void createDocument_번호미입력_자동채번생성() {
        // given
        given(bgdocNumberAllocator.next("GDOC-")).willReturn("GDOC-2026-0001");
        GuideDocDto.CreateRequest request =
                GuideDocDto.CreateRequest.builder()
                        .docTtlCone("가이드문서")
                        .nacTxtInf("<p>내용</p>")
                        .build();

        // when
        String result = guideDocService.createDocument(request);

        // then
        assertThat(result).isEqualTo("GDOC-2026-0001");
        verify(guideDocRepository).save(any(Bgdocm.class));
    }

    @Test
    @DisplayName("createDocument: 이미 존재하는 문서관리번호이면 IllegalArgumentException을 던진다")
    void createDocument_중복번호_IllegalArgumentException발생() {
        // given
        given(guideDocRepository.existsByDocMngNoAndDelYn("GDOC-2026-0001", "N")).willReturn(true);
        GuideDocDto.CreateRequest request =
                GuideDocDto.CreateRequest.builder()
                        .docMngNo("GDOC-2026-0001")
                        .docTtlCone("가이드문서")
                        .build();

        // when & then
        assertThatThrownBy(() -> guideDocService.createDocument(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GDOC-2026-0001");
    }

    // ───────────────────────────────────────────────────────
    // updateDocument
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: 존재하지 않는 문서관리번호이면 IllegalArgumentException을 던진다")
    void updateDocument_존재하지않는문서_IllegalArgumentException발생() {
        // given
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "INVALID", "GDOC-", "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                        () ->
                                guideDocService.updateDocument(
                                        "INVALID", new GuideDocDto.UpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    // ───────────────────────────────────────────────────────
    // deleteDocument
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteDocument: 존재하지 않는 문서관리번호이면 IllegalArgumentException을 던진다")
    void deleteDocument_존재하지않는문서_IllegalArgumentException발생() {
        // given
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "INVALID", "GDOC-", "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> guideDocService.deleteDocument("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    @DisplayName("deleteDocument: 존재하는 문서를 논리 삭제한다")
    void deleteDocument_존재하는문서_논리삭제수행() {
        // given
        Bgdocm doc = mockDocument("GDOC-2026-0001", "가이드문서");
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "GDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.of(doc));

        // when
        guideDocService.deleteDocument("GDOC-2026-0001");

        // then
        verify(doc).delete();
    }

    // ───────────────────────────────────────────────────────
    // updateDocument — 정상 수정 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: 존재하는 문서를 수정하면 문서관리번호를 반환한다")
    void updateDocument_존재하는문서_수정성공() {
        // given: 실제 Bgdocm 엔티티 사용 (update() 호출 후 필드 변경 검증)
        Bgdocm doc = Bgdocm.builder().docMngNo("GDOC-2026-0001").docTtlCone("기존 가이드문서").build();
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "GDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.of(doc));

        GuideDocDto.UpdateRequest req = new GuideDocDto.UpdateRequest();
        req.setDocTtlCone("수정된 가이드문서");

        // when
        String result = guideDocService.updateDocument("GDOC-2026-0001", req);

        // then: 반환값은 문서관리번호, 문서명이 수정됨
        assertThat(result).isEqualTo("GDOC-2026-0001");
        assertThat(doc.getDocTtlCone()).isEqualTo("수정된 가이드문서");
    }

    @Test
    @DisplayName("updateDocument: JPA Dirty Checking 사용으로 save()가 호출되지 않는다")
    void updateDocument_save호출없음_DirtyChecking() {
        // given
        Bgdocm doc = mockDocument("GDOC-2026-0001", "가이드문서");
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "GDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.of(doc));

        // when
        guideDocService.updateDocument("GDOC-2026-0001", new GuideDocDto.UpdateRequest());

        // then: JPA Dirty Checking으로 처리되므로 save() 불필요
        verify(guideDocRepository, org.mockito.Mockito.never()).save(any(Bgdocm.class));
    }

    // ───────────────────────────────────────────────────────
    // createDocument — 번호 직접 지정 생성
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createDocument: 문서관리번호 직접 지정 시 중복 확인 후 저장한다")
    void createDocument_번호직접지정_저장성공() {
        // given: 지정한 번호가 존재하지 않음
        given(guideDocRepository.existsByDocMngNoAndDelYn("GDOC-2026-9999", "N")).willReturn(false);
        GuideDocDto.CreateRequest request =
                GuideDocDto.CreateRequest.builder()
                        .docMngNo("GDOC-2026-9999")
                        .docTtlCone("직접지정 가이드문서")
                        .build();

        // when
        String result = guideDocService.createDocument(request);

        // then: 저장 호출 및 지정한 번호 반환
        assertThat(result).isEqualTo("GDOC-2026-9999");
        verify(guideDocRepository).save(any(Bgdocm.class));
    }

    @Test
    @DisplayName("createDocument: 비GDOC 문서관리번호 직접 지정은 거부한다")
    void createDocument_비GDOC번호직접지정_IllegalArgumentException발생() {
        GuideDocDto.CreateRequest request =
                GuideDocDto.CreateRequest.builder()
                        .docMngNo("FDOC-2026-0001")
                        .docTtlCone("입력 길라잡이")
                        .build();

        assertThatThrownBy(() -> guideDocService.createDocument(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GDOC-");
    }

    @Test
    @DisplayName("updateDocument: FDOC 문서는 존재해도 존재하지 않는 문서로 처리한다")
    void updateDocument_FDOC문서_IllegalArgumentException발생() {
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "FDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                guideDocService.updateDocument(
                                        "FDOC-2026-0001", new GuideDocDto.UpdateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FDOC-2026-0001");
    }

    @Test
    @DisplayName("deleteDocument: FDOC 문서는 존재해도 존재하지 않는 문서로 처리한다")
    void deleteDocument_FDOC문서_IllegalArgumentException발생() {
        given(
                        guideDocRepository.findByDocMngNoAndDocMngNoStartingWithAndDelYn(
                                "FDOC-2026-0001", "GDOC-", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> guideDocService.deleteDocument("FDOC-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FDOC-2026-0001");
    }
}
