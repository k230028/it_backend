package com.kdb.it.common.notification.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 외부 발송 페이로드 계약을 검증한다. */
class MailPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("제목과 본문을 JSON으로 왕복한다")
    void roundTrip() throws Exception {
        MailPayload original = new MailPayload("[IT정보화포탈] 결재 요청", "<p>본문</p>");

        String json = objectMapper.writeValueAsString(original);
        MailPayload parsed = objectMapper.readValue(json, MailPayload.class);

        assertThat(parsed).isEqualTo(original);
        assertThat(json).contains("subject").contains("html");
    }

    @Test
    @DisplayName("모르는 필드가 있어도 역직렬화된다")
    void deserialize_ignoresUnknownFields() throws Exception {
        MailPayload parsed =
                objectMapper.readValue(
                        "{\"subject\":\"제목\",\"html\":\"<p>a</p>\",\"extra\":1}",
                        MailPayload.class);

        assertThat(parsed.subject()).isEqualTo("제목");
        assertThat(parsed.html()).isEqualTo("<p>a</p>");
    }
}
