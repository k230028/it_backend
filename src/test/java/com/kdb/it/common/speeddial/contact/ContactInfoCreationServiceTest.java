package com.kdb.it.common.speeddial.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.domain.budget.document.service.BgdocNumberAllocator;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactInfoCreationServiceTest {

    @Mock private GuideDocRepository guideDocRepository;
    @Mock private BgdocNumberAllocator bgdocNumberAllocator;
    @InjectMocks private ContactInfoCreationService contactInfoCreationService;

    @Test
    void createContactInfo_createsAnIdentifiedContactDocument() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "CDOC-", "N"))
                .willReturn(Optional.empty());
        given(bgdocNumberAllocator.next("CDOC-")).willReturn("CDOC-2026-0017");
        given(guideDocRepository.saveAndFlush(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ContactInfoDto.Response response =
                contactInfoCreationService.createContactInfo("<p>홍길동</p>");

        ArgumentCaptor<Bgdocm> documentCaptor = ArgumentCaptor.forClass(Bgdocm.class);
        verify(guideDocRepository).saveAndFlush(documentCaptor.capture());
        Bgdocm saved = documentCaptor.getValue();
        assertThat(saved.getDocMngNo()).isEqualTo("CDOC-2026-0017");
        assertThat(saved.getDocTtlCone()).isEqualTo(ContactInfoService.DOCUMENT_IDENTIFIER);
        assertThat(response.docMngNo()).isEqualTo(saved.getDocMngNo());
    }

    @Test
    void 기존_담당자_문서가_있으면_내용을_갱신한다() {
        Bgdocm existing =
                Bgdocm.builder()
                        .docMngNo("CDOC-2026-0001")
                        .docTtlCone(ContactInfoService.DOCUMENT_IDENTIFIER)
                        .nacTxtInf("<p>기존</p>")
                        .build();
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "CDOC-", "N"))
                .willReturn(Optional.of(existing));

        ContactInfoDto.Response response =
                contactInfoCreationService.createContactInfo("<p>변경</p>");

        assertThat(existing.getNacTxtInf()).isEqualTo("<p>변경</p>");
        assertThat(response.docMngNo()).isEqualTo("CDOC-2026-0001");
        assertThat(response.contentHtml()).isEqualTo("<p>변경</p>");
        verify(guideDocRepository, never()).saveAndFlush(any(Bgdocm.class));
    }
}
