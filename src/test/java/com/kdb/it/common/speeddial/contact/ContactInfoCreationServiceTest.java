package com.kdb.it.common.speeddial.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.LocalDate;
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
    @InjectMocks private ContactInfoCreationService contactInfoCreationService;

    @Test
    void createContactInfo_createsAnIdentifiedGdocDocument() {
        given(
                        guideDocRepository
                                .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                        ContactInfoService.DOCUMENT_IDENTIFIER, "GDOC-", "N"))
                .willReturn(Optional.empty());
        given(guideDocRepository.getNextSequenceValue()).willReturn(17L);
        given(guideDocRepository.saveAndFlush(any(Bgdocm.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ContactInfoDto.Response response = contactInfoCreationService.createContactInfo("<p>홍길동</p>");

        ArgumentCaptor<Bgdocm> documentCaptor = ArgumentCaptor.forClass(Bgdocm.class);
        verify(guideDocRepository).saveAndFlush(documentCaptor.capture());
        Bgdocm saved = documentCaptor.getValue();
        assertThat(saved.getDocMngNo())
                .isEqualTo("GDOC-" + LocalDate.now().getYear() + "-0017");
        assertThat(saved.getDocTtlCone()).isEqualTo(ContactInfoService.DOCUMENT_IDENTIFIER);
        assertThat(response.docMngNo()).isEqualTo(saved.getDocMngNo());
    }
}
