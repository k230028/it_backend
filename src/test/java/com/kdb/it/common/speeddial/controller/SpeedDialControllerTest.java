package com.kdb.it.common.speeddial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.speeddial.contact.ContactInfoDto;
import com.kdb.it.common.speeddial.contact.ContactInfoService;
import com.kdb.it.common.speeddial.dto.SpeedDialDto;
import com.kdb.it.common.speeddial.service.SpeedDialService;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpeedDialControllerTest {

    @Mock private SpeedDialService speedDialService;
    @Mock private ContactInfoService contactInfoService;
    @Mock private CustomUserDetails user;

    @InjectMocks private SpeedDialController controller;

    @Test
    void returnsFaqsFromService() {
        List<SpeedDialDto.FaqResponse> faqs =
                List.of(
                        new SpeedDialDto.FaqResponse(
                                "NAC-0001", "FAQ", "<p>본문</p>", LocalDateTime.now()));
        given(speedDialService.getFaqs()).willReturn(faqs);

        var response = controller.getFaqs();

        assertThat(response.getBody()).isSameAs(faqs);
    }

    @Test
    void createsQnaForAuthenticatedUser() {
        SpeedDialDto.QnaCreateRequest request =
                new SpeedDialDto.QnaCreateRequest("정보화사업", "/info", "IMPROVEMENT", "<p>문의</p>");
        given(speedDialService.createQna(request, user)).willReturn("NAC-2026-0001");

        var response = controller.createQna(request, user);

        assertThat(response.getBody().postId()).isEqualTo("NAC-2026-0001");
        verify(speedDialService).createQna(request, user);
    }

    @Test
    void returnsContactInformationFromService() {
        ContactInfoDto.Response contactInfo =
                new ContactInfoDto.Response("GDOC-2026-0042", "<p>담당자</p>");
        given(contactInfoService.getContactInfo()).willReturn(contactInfo);

        var response = controller.getContactInfo();

        assertThat(response.getBody()).isSameAs(contactInfo);
    }
}
