package com.kdb.it.common.popup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CommonPopupServiceTest {

    private static final String DOC_NO = "PDOC-2026-0100";
    private static final LocalDateTime CHANGED_AT =
            LocalDateTime.parse("2026-09-02T10:20:30.123456");
    private static final String VERSION = DOC_NO + ":2026-09-02T10:20:30.123456";

    @Mock private GuideDocRepository guideDocRepository;
    @Mock private CommonPopupCreationService creationService;

    @InjectMocks private CommonPopupService service;

    @Test
    @DisplayName("게시 중인 안내가 없으면 사용자 조회 결과가 비어 있다")
    void getActivePopup_returnsEmptyWhenNoDocumentExists() {
        givenActiveDocument(Optional.empty());

        assertThat(service.getActivePopup()).isEmpty();
    }

    @Test
    @DisplayName("게시 중인 안내는 문서번호와 최종 변경시각을 결합한 버전으로 조회된다")
    void getActivePopup_returnsStableContentVersion() {
        Bgdocm document = popupDocument();
        given(document.getNacTxtInf()).willReturn("<p>안내</p>");
        givenActiveDocument(Optional.of(document));

        CommonPopupDto.Response response = service.getActivePopup().orElseThrow();

        assertThat(response.docMngNo()).isEqualTo(DOC_NO);
        assertThat(response.contentHtml()).isEqualTo("<p>안내</p>");
        assertThat(response.contentVersion()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("전결권 안내는 별도 문서 식별자와 PDOC 관리번호 범위에서 조회된다")
    void getApprovalAuthorityNotice_usesDedicatedDocumentIdentifier() {
        Bgdocm document = popupDocument();
        given(document.getNacTxtInf()).willReturn("<p>전결권 안내</p>");
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                CommonPopupService.APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER,
                                "05",
                                "N"))
                .willReturn(Optional.of(document));

        CommonPopupDto.Response response = service.getApprovalAuthorityNotice().orElseThrow();

        assertThat(response.docMngNo()).isEqualTo(DOC_NO);
        assertThat(response.contentHtml()).isEqualTo("<p>전결권 안내</p>");
        assertThat(response.contentVersion()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("화면별 안내는 유형에 고정된 문서 식별자로 조회한다")
    void getActivePopup_byType_usesTypeIdentifier() {
        Bgdocm document = popupDocument();
        given(document.getNacTxtInf()).willReturn("<p>경상사업 안내</p>");
        given(guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn("common.ordn", "05", "N"))
                .willReturn(Optional.of(document));

        CommonPopupDto.Response response =
                service.getActivePopup(CommonPopupType.ORDN).orElseThrow();

        assertThat(response.contentHtml()).isEqualTo("<p>경상사업 안내</p>");
    }

    @Test
    @DisplayName("화면별 안내 최초 저장은 유형과 전용 접두사를 생성 서비스에 전달한다")
    void save_byType_usesDedicatedIdentifierAndPrefix() {
        given(guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn("common.info", "05", "N"))
                .willReturn(Optional.empty());
        Bgdocm created = popupDocument();
        given(creationService.createPopup("common.info", "IPOP-", "<p>정보화사업 안내</p>"))
                .willReturn(created);

        service.save(CommonPopupType.INFO, "<p>정보화사업 안내</p>");

        verify(creationService).createPopup("common.info", "IPOP-", "<p>정보화사업 안내</p>");
    }

    @Test
    @DisplayName("관리자 조회는 미등록 상태를 null 필드로 반환한다")
    void getAdminPopup_returnsUnregisteredResponse() {
        givenActiveDocument(Optional.empty());

        CommonPopupDto.AdminResponse response = service.getAdminPopup();

        assertThat(response.docMngNo()).isNull();
        assertThat(response.contentHtml()).isNull();
        assertThat(response.contentVersion()).isNull();
    }

    @Test
    @DisplayName("기존 안내 저장은 위험 HTML을 제거하고 같은 문서를 갱신한다")
    void save_sanitizesAndUpdatesExistingDocument() {
        Bgdocm document = popupDocument();
        givenActiveDocument(Optional.of(document));
        given(guideDocRepository.saveAndFlush(document)).willReturn(document);

        CommonPopupDto.AdminResponse response =
                service.save("<p onclick=\"alert(1)\">새 안내</p><script>alert(2)</script>");

        verify(document).update(CommonPopupService.DOCUMENT_IDENTIFIER, "<p>새 안내</p>");
        assertThat(response.contentHtml()).isEqualTo("<p>새 안내</p>");
        assertThat(response.contentVersion()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("정화 뒤 표시 내용이 없으면 저장을 거부한다")
    void save_rejectsContentThatBecomesEmpty() {
        assertThatThrownBy(() -> service.save("<script>alert('x')</script>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본문");

        verify(creationService, never())
                .createPopup(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("최초 저장은 생성 서비스가 만든 문서를 반환한다")
    void save_createsPopupWhenNoDocumentExists() {
        Bgdocm created = popupDocument();
        givenActiveDocument(Optional.empty());
        given(
                        creationService.createPopup(
                                CommonPopupService.DOCUMENT_IDENTIFIER, "PDOC-", "<p>첫 안내</p>"))
                .willReturn(created);

        CommonPopupDto.AdminResponse response = service.save("<p>첫 안내</p>");

        assertThat(response.docMngNo()).isEqualTo(DOC_NO);
        assertThat(response.contentVersion()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("전결권 안내 최초 저장은 전용 식별자로 PDOC 문서를 생성한다")
    void saveApprovalAuthorityNotice_createsDedicatedDocument() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                CommonPopupService.APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER,
                                "05",
                                "N"))
                .willReturn(Optional.empty());
        Bgdocm created = popupDocument();
        given(
                        creationService.createPopup(
                                CommonPopupService.APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER,
                                "PDOC-",
                                "<p>전결권 안내</p>"))
                .willReturn(created);

        CommonPopupDto.AdminResponse response =
                service.saveApprovalAuthorityNotice("<p>전결권 안내</p>");

        assertThat(response.docMngNo()).isEqualTo(DOC_NO);
        assertThat(response.contentHtml()).isEqualTo("<p>전결권 안내</p>");
    }

    @Test
    @DisplayName("동시 최초 저장 충돌 뒤에는 생성된 활성 문서를 갱신한다")
    void save_updatesConcurrentDocumentAfterUniqueConstraintConflict() {
        Bgdocm concurrent = popupDocument();
        givenActiveDocument(Optional.empty(), Optional.of(concurrent));
        given(
                        creationService.createPopup(
                                CommonPopupService.DOCUMENT_IDENTIFIER, "PDOC-", "<p>내 안내</p>"))
                .willThrow(new DataIntegrityViolationException("UX_BGDOCM_COMMON_POPUP"));
        given(guideDocRepository.saveAndFlush(concurrent)).willReturn(concurrent);

        CommonPopupDto.AdminResponse response = service.save("<p>내 안내</p>");

        verify(concurrent).update(CommonPopupService.DOCUMENT_IDENTIFIER, "<p>내 안내</p>");
        assertThat(response.contentHtml()).isEqualTo("<p>내 안내</p>");
    }

    @Test
    @DisplayName("게시 중지는 활성 문서를 논리 삭제한다")
    void stopPublishing_softDeletesActiveDocument() {
        Bgdocm document = org.mockito.Mockito.mock(Bgdocm.class);
        givenActiveDocument(Optional.of(document));
        given(guideDocRepository.saveAndFlush(document)).willReturn(document);

        service.stopPublishing();

        verify(document).delete();
        verify(guideDocRepository).saveAndFlush(document);
    }

    @Test
    @DisplayName("활성 문서가 없는 게시 중지는 반복 호출해도 성공한다")
    void stopPublishing_isIdempotentWhenNoDocumentExists() {
        givenActiveDocument(Optional.empty());

        service.stopPublishing();

        verify(guideDocRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @SafeVarargs
    private void givenActiveDocument(Optional<Bgdocm>... results) {
        var stubbing =
                given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                CommonPopupService.DOCUMENT_IDENTIFIER, "05", "N"));
        if (results.length == 1) {
            stubbing.willReturn(results[0]);
            return;
        }
        stubbing.willReturn(results[0], java.util.Arrays.copyOfRange(results, 1, results.length));
    }

    private Bgdocm popupDocument() {
        Bgdocm document = org.mockito.Mockito.mock(Bgdocm.class);
        given(document.getDocMngNo()).willReturn(DOC_NO);
        given(document.getLstChgDtm()).willReturn(CHANGED_AT);
        return document;
    }
}
