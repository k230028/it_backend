package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.service.EaiStandardLayout.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * EAI 게이트웨이 요청·응답 전문의 상세 진단 로그.
 *
 * <p>공통부는 {@link EaiStandardLayout} 표로 해석해 <b>필드명=값</b>으로 펼치고, 개별부(수신자·제목·본문·휴대폰·OTP가 들어가는 구간)는 길이만
 * 남긴다. 전문 전체 평문 로깅 금지 규약을 지키면서도 게이트웨이가 요청을 거부한 이유(IF_ID, 수신시스템코드, 시스템환경구분, 길이 신고값)를 볼 수 있게 하는 것이
 * 목적이다.
 *
 * <p>상세 로그는 전용 로거 {@value #LOGGER_NAME}의 DEBUG로 나간다. 운영에서는 {@code
 * logging.level.com.kdb.it.infra.eai.wire=DEBUG}(환경변수 {@code EAI_WIRE_LOG_LEVEL=DEBUG})로 켠다. 전송 실패
 * 건은 이 설정과 무관하게 {@link EaiService}가 같은 덤프를 WARN으로 남긴다.
 */
final class EaiWireLogger {

    /** 상세 로그 전용 로거명 — 업무 로그와 따로 켜고 끄기 위해 클래스명이 아닌 고정 이름을 쓴다. */
    static final String LOGGER_NAME = "com.kdb.it.infra.eai.wire";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /** 표준전문이 아닌 응답의 미리보기 상한 — 프록시·WAF 오류 페이지를 식별하기에 충분한 길이. */
    private static final int PREVIEW_LIMIT = 256;

    /** 응답 메시지부 기록 상한 — 게이트웨이 오류 문구를 담기에 충분하고 로그 한 줄이 비대해지지 않는 길이. */
    private static final int MESSAGE_PART_LIMIT = 512;

    /** 값이 비어도 항상 출력하는 필드 — 비어 있다는 사실 자체가 판정 근거인 것들. */
    private static final Set<String> ALWAYS_SHOWN =
            Set.of("RLT_TC", "MSG_IDCT_TC", "IF_ID", "RMS_SYS_C", "ERR_OCC_TGR_ITM");

    /** 예비 필드 접미사 — 항상 공백이라 출력에서 제외한다. */
    private static final String RESERVED_SUFFIX = "_RSRV";

    private final Charset charset;

    EaiWireLogger(Charset charset) {
        this.charset = charset;
    }

    /** 상세 로그가 켜져 있는지 여부. 덤프 문자열 조립 비용을 피하기 위해 호출부에서 먼저 확인한다. */
    boolean enabled() {
        return log.isDebugEnabled();
    }

    /**
     * 전송 직전 요청 전문을 상세 로깅합니다. 상세 로그가 꺼져 있으면 아무것도 하지 않습니다.
     *
     * @param ifId 인터페이스 ID
     * @param payloadType 개별부 payload 타입명
     * @param message 조립된 요청 전문
     */
    void logRequest(String ifId, String payloadType, byte[] message) {
        if (enabled()) {
            log.debug("EAI 요청 전문 상세: ifId={}\n{}", ifId, requestDetail(payloadType, message));
        }
    }

    /**
     * 게이트웨이 응답을 상세 로깅합니다. 상세 로그가 꺼져 있으면 아무것도 하지 않습니다.
     *
     * @param ifId 인터페이스 ID
     * @param status HTTP 상태코드
     * @param headers 응답 헤더
     * @param body 응답 본문. null 허용
     */
    void logResponse(String ifId, int status, HttpHeaders headers, byte[] body) {
        if (enabled()) {
            log.debug("EAI 응답 전문 상세: ifId={}\n{}", ifId, responseDetail(status, headers, body));
        }
    }

    /**
     * 요청 전문의 상세 덤프를 만듭니다.
     *
     * <p>개별부는 길이만 남기고 값은 남기지 않습니다.
     *
     * @param payloadType 개별부 payload 타입명
     * @param message 요청 전문 바이트. null 허용
     * @return 여러 줄 덤프 문자열
     */
    String requestDetail(String payloadType, byte[] message) {
        if (message == null) {
            return "  요청 전문 없음";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  [요청] len=%d바이트%n".formatted(message.length));
        appendCommonParts(sb, message);
        int individual = message.length - EaiStandardLayout.HEADER_LEN;
        if (individual < 0) {
            sb.append("  [개별부] %s 헤더 길이 미만 — 전문 조립 이상".formatted(payloadType));
            return sb.toString();
        }
        sb.append(
                "  [개별부] %s %d바이트 (값 미기록), %s"
                        .formatted(
                                payloadType,
                                individual,
                                encodingHealth(message, EaiStandardLayout.HEADER_LEN)));
        return sb.toString();
    }

    /**
     * 응답의 상세 덤프를 만듭니다.
     *
     * <p>표준전문이면 공통부를 필드 단위로 펼치고, 표준전문이 아니면 앞부분을 16진수와 텍스트로 미리 보여 프록시 오류 페이지·빈 응답을 구분할 수 있게 합니다. 본문
     * 없는 204는 게이트웨이 정상 수신이므로 비표준 응답과 구분해 표시합니다.
     *
     * @param status HTTP 상태코드
     * @param headers 응답 헤더. null 허용
     * @param body 응답 본문. null 허용
     * @return 여러 줄 덤프 문자열
     */
    String responseDetail(int status, HttpHeaders headers, byte[] body) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "  [응답] status=%d, len=%d바이트, contentType=%s%n"
                        .formatted(status, body == null ? 0 : body.length, contentType(headers)));
        if (status == 204 && (body == null || body.length == 0)) {
            // GWE는 정상 수신을 본문 없는 204로 응답한다. 표준전문 파싱 대상이 아니므로
            // "비표준" 문구로 남기면 성공 건이 매번 이상 징후처럼 보인다.
            sb.append("  [정상] 204 No Content — 게이트웨이 수신 완료(응답 전문 없음)");
            return sb.toString();
        }
        if (body == null || body.length < EaiStandardLayout.HEADER_LEN) {
            sb.append("  [비표준] 표준전문 헤더(%d바이트)에 못 미침%n".formatted(EaiStandardLayout.HEADER_LEN));
            sb.append(preview(body));
            return sb.toString();
        }
        appendCommonParts(sb, body);
        appendMessagePart(sb, body);
        return sb.toString();
    }

    /**
     * 응답 헤더 뒤에 붙은 메시지부와 개별부를 구분해 남긴다.
     *
     * <p>전문이 신고한 {@code HER_LEN}이 기준 공통부 길이보다 길면 그 차이는 게이트웨이가 만든 <b>메시지부</b>다. 실패 원인 설명(SEEAI 코드에
     * 딸린 문구)이 여기 들어오므로 값을 남긴다. 발신자가 채운 개별부(수신자·제목·본문)는 그 뒤 구간이며 길이만 남긴다.
     */
    private void appendMessagePart(StringBuilder sb, byte[] body) {
        int declaredHeaderLen = declaredHeaderLen(body);
        int messagePartEnd = Math.min(declaredHeaderLen, body.length);
        if (messagePartEnd <= EaiStandardLayout.HEADER_LEN) {
            sb.append(
                    "  [개별부] %d바이트 (값 미기록)".formatted(body.length - EaiStandardLayout.HEADER_LEN));
            return;
        }
        int from = EaiStandardLayout.HEADER_LEN;
        int len = Math.min(MESSAGE_PART_LIMIT, messagePartEnd - from);
        String text = sanitize(new String(body, from, len, charset));
        sb.append(
                "  [메시지부] HER_LEN=%d(기준 %d), %d바이트: %s%s%n"
                        .formatted(
                                declaredHeaderLen,
                                EaiStandardLayout.HEADER_LEN,
                                messagePartEnd - from,
                                text,
                                messagePartEnd - from > len ? "...(생략)" : ""));
        sb.append("  [개별부] %d바이트 (값 미기록)".formatted(body.length - messagePartEnd));
    }

    /**
     * 전문이 스스로 신고한 헤더 길이(HER_LEN)를 읽는다.
     *
     * @return 신고값. 필드가 없거나 숫자가 아니면 0
     */
    private int declaredHeaderLen(byte[] body) {
        String raw =
                EaiStandardLayout.field(EaiStandardLayout.SYSTEM_COMMON, "HER_LEN")
                        .read(body, charset)
                        .orElse("");
        try {
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 제어문자를 점으로 바꿔 로그 한 줄이 깨지지 않게 한다. */
    private static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(c < 0x20 || c == 0x7F ? '.' : c);
        }
        return sb.toString().trim();
    }

    /** 공통부 세 구간을 필드명=값으로 펼친다. 값이 빈 필드와 예비 필드는 생략해 잡음을 줄인다. */
    private void appendCommonParts(StringBuilder sb, byte[] message) {
        sb.append("  [시스템공통부] %s%n".formatted(fields(EaiStandardLayout.SYSTEM_COMMON, message)));
        sb.append(
                "  [거래공통부] %s%n".formatted(fields(EaiStandardLayout.TRANSACTION_COMMON, message)));
        sb.append("  [메시지공통부] %s%n".formatted(fields(EaiStandardLayout.MESSAGE_COMMON, message)));
    }

    /** 한 구간의 필드를 {@code 이름=값} 목록으로 만든다. */
    private String fields(List<Field> section, byte[] message) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Field f : section) {
            if (f.name().endsWith(RESERVED_SUFFIX)) {
                continue;
            }
            String value = f.read(message, charset).orElse(null);
            if (value == null) {
                joiner.add(f.name() + "=범위밖");
                continue;
            }
            if (value.isEmpty() && !ALWAYS_SHOWN.contains(f.name())) {
                continue;
            }
            joiner.add("%s='%s'".formatted(f.name(), value));
        }
        return joiner.length() == 0 ? "(값 있는 필드 없음)" : joiner.toString();
    }

    /**
     * 개별부의 인코딩 건전성 요약 — 값은 남기지 않고 바이트 성질만 센다.
     *
     * <p>{@code 물음표}는 문자셋이 표현하지 못한 문자를 인코더가 {@code '?'}(0x3F)로 치환한 흔적이다. 한글 제목·본문을 보내는데 비ASCII가 0이고
     * 물음표가 많다면 <b>전문을 만들기 전에</b> 이미 문자열이 깨진 것이고, 비ASCII가 정상적으로 잡히는데 수신측에서만 깨져 보인다면 수신측 해석 문자셋 문제다.
     *
     * @param message 전문 바이트
     * @param from 개별부 시작 위치
     * @return 로그 전용 요약
     */
    private static String encodingHealth(byte[] message, int from) {
        int nonAscii = 0;
        int question = 0;
        for (int i = from; i < message.length; i++) {
            byte b = message[i];
            if (b < 0) {
                nonAscii++;
            } else if (b == '?') {
                question++;
            }
        }
        return "인코딩: 비ASCII=%d바이트, 물음표(0x3F)=%d개".formatted(nonAscii, question);
    }

    /** 표준전문이 아닌 응답의 앞부분을 16진수와 출력 가능 문자로 보여준다. */
    private String preview(byte[] body) {
        if (body == null || body.length == 0) {
            return "  [미리보기] 본문 없음";
        }
        int len = Math.min(PREVIEW_LIMIT, body.length);
        StringBuilder hex = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < len; i++) {
            byte b = body[i];
            hex.append("%02X ".formatted(b));
            text.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
        }
        String suffix = body.length > len ? "...(이후 %d바이트 생략)".formatted(body.length - len) : "";
        return "  [미리보기 %d바이트] hex=%s%n  [미리보기 텍스트] %s%s"
                .formatted(len, hex.toString().trim(), text, suffix);
    }

    /** Content-Type 헤더 표기 — 헤더가 없으면 미상으로 남긴다. */
    private static String contentType(HttpHeaders headers) {
        if (headers == null || headers.getContentType() == null) {
            return "미상";
        }
        return headers.getContentType().toString();
    }
}
