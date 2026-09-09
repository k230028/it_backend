package com.kdb.it.common.popup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.domain.budget.document.service.BgdocNumberAllocator;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonPopupCreationServiceTest {

    @Mock private GuideDocRepository guideDocRepository;
    @Mock private BgdocNumberAllocator bgdocNumberAllocator;
    @InjectMocks private CommonPopupCreationService creationService;

    @Test
    @DisplayName("최초 저장은 common.popup 구분자와 PDOC 관리번호로 문서를 생성한다")
    void createPopup_createsIdentifiedPopupDocument() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                CommonPopupService.DOCUMENT_IDENTIFIER, "05", "N"))
                .willReturn(Optional.empty());
        given(bgdocNumberAllocator.next("PDOC-")).willReturn("PDOC-2026-0017");
        given(guideDocRepository.saveAndFlush(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Bgdocm created = creationService.createPopup("<p>첫 안내</p>");

        ArgumentCaptor<Bgdocm> documentCaptor = ArgumentCaptor.forClass(Bgdocm.class);
        verify(guideDocRepository).saveAndFlush(documentCaptor.capture());
        Bgdocm saved = documentCaptor.getValue();
        assertThat(saved.getDocMngNo()).isEqualTo("PDOC-2026-0017");
        assertThat(saved.getDocTtlCone()).isEqualTo(CommonPopupService.DOCUMENT_IDENTIFIER);
        assertThat(saved.getNacTxtInf()).isEqualTo("<p>첫 안내</p>");
        assertThat(created).isSameAs(saved);
    }

    @Test
    @DisplayName("생성 직전 활성 문서가 생겼으면 새 행 대신 그 문서를 갱신한다")
    void createPopup_updatesDocumentCreatedByAnotherRequest() {
        Bgdocm concurrent = org.mockito.Mockito.mock(Bgdocm.class);
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                CommonPopupService.DOCUMENT_IDENTIFIER, "05", "N"))
                .willReturn(Optional.of(concurrent));
        given(guideDocRepository.saveAndFlush(concurrent)).willReturn(concurrent);

        Bgdocm created = creationService.createPopup("<p>첫 안내</p>");

        assertThat(created).isSameAs(concurrent);
        verify(concurrent).update(CommonPopupService.DOCUMENT_IDENTIFIER, "<p>첫 안내</p>");
        verify(guideDocRepository).saveAndFlush(concurrent);
        verify(guideDocRepository, org.mockito.Mockito.never()).getNextSequenceValue();
    }

    @Test
    @DisplayName("전결권 안내도 전용 식별자와 PDOC 관리번호로 생성한다")
    void createPopup_createsApprovalAuthorityDocument() {
        String identifier = CommonPopupService.APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER;
        given(guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(identifier, "05", "N"))
                .willReturn(Optional.empty());
        given(bgdocNumberAllocator.next("PDOC-")).willReturn("PDOC-2026-0018");
        given(guideDocRepository.saveAndFlush(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Bgdocm created = creationService.createPopup(identifier, "<p>전결권 안내</p>");

        assertThat(created.getDocMngNo()).isEqualTo("PDOC-2026-0018");
        assertThat(created.getDocTtlCone()).isEqualTo(identifier);
        assertThat(created.getNacTxtInf()).isEqualTo("<p>전결권 안내</p>");
    }

    @Test
    @DisplayName("화면별 안내는 지정한 전용 관리번호 접두사로 생성한다")
    void createPopup_usesDedicatedPrefix() {
        given(guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn("common.info", "05", "N"))
                .willReturn(Optional.empty());
        given(bgdocNumberAllocator.next("IPOP-")).willReturn("IPOP-2026-0019");
        given(guideDocRepository.saveAndFlush(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Bgdocm created = creationService.createPopup("common.info", "IPOP-", "<p>정보화사업 안내</p>");

        assertThat(created.getDocMngNo()).isEqualTo("IPOP-2026-0019");
        assertThat(created.getDocTtlCone()).isEqualTo("common.info");
    }
}
