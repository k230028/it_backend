package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.time.Clock;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * EAI 게이트웨이 발송 오케스트레이션.
 *
 * <p>전문 조립({@link EaiMessageBuilder})과 전송을 연결한다. {@code eai.enabled=false}면
 * 전문을 빌드·로깅만 하고 HTTP를 호출하지 않는다(개발/CI 안전). 모든 실패는 예외를 전파하지 않고
 * {@link EaiResult}로 표현한다(부수효과 원칙). 민감정보(휴대폰/OTP) 노출 방지를 위해 전문 전체를
 * 평문 로깅하지 않고 마스킹한다.</p>
 */
@Slf4j
@Service
public class EaiService {

    private final EaiProperties props;
    private final RestClient restClient;
    private final Charset charset;
    private final EaiMessageBuilder builder;

    public EaiService(EaiProperties props,
                      @Qualifier("eaiRestClient") RestClient restClient,
                      @Qualifier("eaiClock") Clock eaiClock,
                      @Qualifier("eaiGuidRandom") Supplier<String> guidRandom,
                      HostAddressProvider host,
                      @Qualifier("eaiRandomDigits") IntFunction<String> randomDigits,
                      List<EaiPayloadSection> sections) {
        this.props = props;
        this.restClient = restClient;
        this.charset = Charset.forName(props.charset());
        this.builder = new EaiMessageBuilder(props, eaiClock, guidRandom, host, randomDigits, sections);
    }

    /**
     * UMS 표준전문을 조립해 EAI로 전송한다.
     *
     * @param request 발송 요청
     * @return 전송 결과(성공/스킵/실패). 절대 예외를 던지지 않는다.
     */
    public EaiResult sendEai(EaiRequest request) {
        byte[] message;
        try {
            message = builder.build(request);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            log.warn("EAI 전문 조립 실패: ifId={}, payload={}, 사유={}",
                    request.ifId(), request.payload().getClass().getSimpleName(), safeMessage(e));
            return EaiResult.failure("전문 조립 실패: " + safeMessage(e));
        }

        if (!props.enabled()) {
            log.info("EAI 비활성화(eai.enabled=false) — 전송 스킵. ifId={}, payload={}, len={}바이트, 미리보기=[{}]",
                    request.ifId(), request.payload().getClass().getSimpleName(), message.length, maskedPreview(message));
            return EaiResult.skip();
        }

        try {
            byte[] response = restClient.post()
                    .uri(props.url())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(message)
                    .retrieve()
                    .body(byte[].class);
            String responseRaw = (response == null) ? "" : new String(response, charset);
            log.info("EAI 전송 성공: ifId={}, payload={}, reqLen={}바이트", request.ifId(), request.payload().getClass().getSimpleName(), message.length);
            return EaiResult.success(responseRaw);
        } catch (RuntimeException e) {
            log.warn("EAI 전송 실패: ifId={}, payload={}, 사유={}", request.ifId(), request.payload().getClass().getSimpleName(), safeMessage(e));
            return EaiResult.failure("전송 실패: " + safeMessage(e));
        }
    }

    /** 예외 메시지를 안전하게 추출 — null/과도한 길이를 방어해 결과/로그 오염을 막는다. */
    private static String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "...(생략)" : msg;
    }

    /**
     * 로그용 마스킹 미리보기 — 헤더 앞 60바이트만 노출하고 개별부(민감정보 구간)는 가린다.
     * 휴대폰번호/OTP가 포함된 개별부 전체 평문 로깅을 금지한다.
     */
    private String maskedPreview(byte[] message) {
        int head = Math.min(60, message.length);
        String prefix = new String(message, 0, head, charset);
        return prefix + (message.length > head ? "...(개별부 " + (message.length - head) + "바이트 마스킹)" : "");
    }
}
