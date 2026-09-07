package com.kdb.it.common.speeddial.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ContactInfoServiceTest {

    @Mock private GuideDocRepository guideDocRepository;
    @Mock private ContactInfoCreationService contactInfoCreationService;

    @InjectMocks private ContactInfoService contactInfoService;

    @Test
    @DisplayName("담당자 정보가 없으면 비어 있는 응답을 반환한다")
    void getContactInfo_returnsEmptyResponseWhenDocumentDoesNotExist() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "04", "N"))
                .willReturn(Optional.empty());

        ContactInfoDto.Response response = contactInfoService.getContactInfo();

        assertThat(response.docMngNo()).isNull();
        assertThat(response.contentHtml()).isNull();
    }

    @Test
    @DisplayName("첫 저장은 고정 식별자와 CDOC 자동 채번으로 문서를 생성한다")
    void saveContactInfo_createsIdentifiedDocumentWithGeneratedContactNumber() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "04", "N"))
                .willReturn(Optional.empty());
        given(contactInfoCreationService.createContactInfo("<p>홍길동</p>"))
                .willReturn(
                        new ContactInfoDto.Response(
                                "CDOC-" + java.time.LocalDate.now().getYear() + "-0017",
                                "<p>홍길동</p>"));

        ContactInfoDto.Response response = contactInfoService.saveContactInfo("<p>홍길동</p>");

        verify(contactInfoCreationService).createContactInfo("<p>홍길동</p>");
        assertThat(response.docMngNo())
                .isEqualTo("CDOC-" + java.time.LocalDate.now().getYear() + "-0017");
        assertThat(response.contentHtml()).isEqualTo("<p>홍길동</p>");
    }

    @Test
    @DisplayName("이미 작성한 담당자 정보는 같은 문서를 갱신한다")
    void saveContactInfo_updatesExistingDocument() {
        Bgdocm existing = org.mockito.Mockito.mock(Bgdocm.class);
        given(existing.getDocMngNo()).willReturn("CDOC-2026-0042");
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "04", "N"))
                .willReturn(Optional.of(existing));

        ContactInfoDto.Response response = contactInfoService.saveContactInfo("<p>새 담당자</p>");

        verify(existing).update(ContactInfoService.DOCUMENT_IDENTIFIER, "<p>새 담당자</p>");
        assertThat(response.docMngNo()).isEqualTo("CDOC-2026-0042");
        assertThat(response.contentHtml()).isEqualTo("<p>새 담당자</p>");
    }

    @Test
    @DisplayName("정화 후 본문이 비면 담당자 정보 문서를 만들지 않는다")
    void saveContactInfo_rejectsContentThatBecomesEmptyAfterSanitization() {
        assertThatThrownBy(() -> contactInfoService.saveContactInfo("<script>alert('x')</script>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본문");

        verify(guideDocRepository, org.mockito.Mockito.never()).save(any(Bgdocm.class));
    }

    @Test
    @DisplayName("동시 최초 등록 충돌 뒤에는 생성된 문서를 다시 찾아 갱신한다")
    void saveContactInfo_updatesDocumentCreatedByConcurrentRequest() {
        Bgdocm existing = org.mockito.Mockito.mock(Bgdocm.class);
        given(existing.getDocMngNo()).willReturn("CDOC-2026-0042");
        given(
                        guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                                ContactInfoService.DOCUMENT_IDENTIFIER, "04", "N"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existing));
        given(contactInfoCreationService.createContactInfo("<p>새 담당자</p>"))
                .willThrow(new DataIntegrityViolationException("UX_BGDOCM_CONTACT_INFO"));

        ContactInfoDto.Response response = contactInfoService.saveContactInfo("<p>새 담당자</p>");

        verify(existing).update(ContactInfoService.DOCUMENT_IDENTIFIER, "<p>새 담당자</p>");
        assertThat(response.docMngNo()).isEqualTo("CDOC-2026-0042");
    }
}
