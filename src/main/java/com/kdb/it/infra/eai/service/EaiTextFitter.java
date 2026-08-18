package com.kdb.it.infra.eai.service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import lombok.extern.slf4j.Slf4j;

/**
 * 표시용 텍스트를 고정길이 필드의 바이트 예산에 맞춥니다.
 *
 * <p>전문 필드는 <b>바이트</b> 단위인데 원본 컬럼은 <b>글자</b> 단위라, 문자셋이 바뀌면 같은 값이 들어가기도 하고 넘치기도 합니다. 예를 들어 제목
 * 컬럼(100자)은 MS949에서 정확히 200바이트에 맞지만 UTF-8에서는 최대 300바이트가 되어 SUBJECT 필드를 넘습니다.
 *
 * <p>자를 때 멀티바이트 문자를 중간에서 끊지 않습니다. {@link CharsetEncoder}가 출력 버퍼가 찰 때 문자 경계에서 멈추는 성질을 이용하므로, 서로게이트
 * 쌍(이모지 등)도 안전합니다.
 */
@Slf4j
final class EaiTextFitter {

    private EaiTextFitter() {}

    /**
     * 문자열을 주어진 바이트 예산 안으로 자릅니다.
     *
     * <p>예산을 넘지 않으면 원본을 그대로 돌려줍니다. 잘린 경우에만 WARN을 남기며, 로그에는 필드명과 길이만 남기고 내용은 남기지 않습니다.
     *
     * @param text 원본 문자열. null이면 빈 문자열로 취급
     * @param limitBytes 필드 바이트 예산
     * @param charset 전문 문자셋
     * @param fieldName 로그에 남길 필드명
     * @return 예산 이하로 인코딩되는 문자열
     */
    static String fit(String text, int limitBytes, Charset charset, String fieldName) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int actual = text.getBytes(charset).length;
        if (actual <= limitBytes) {
            return text;
        }

        CharsetEncoder encoder =
                charset.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer in = CharBuffer.wrap(text);
        encoder.encode(in, ByteBuffer.allocate(limitBytes), true);
        String fitted = text.substring(0, in.position());

        log.warn(
                "EAI 전문 필드 예산 초과로 잘랐습니다: field={}, charset={}, 원본={}바이트, 예산={}바이트, 남긴 글자수={}/{}",
                fieldName,
                charset.name(),
                actual,
                limitBytes,
                fitted.length(),
                text.length());
        return fitted;
    }
}
