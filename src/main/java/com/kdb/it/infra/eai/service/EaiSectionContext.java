package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;

import java.nio.charset.Charset;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/**
 * 개별부 섹션이 필요로 하는 공유 도구 묶음.
 *
 * <p>{@link EaiMessageBuilder}가 호출 시점에 charset·설정·시각/난수 시임을 묶어 전달한다.
 * 섹션이 무상태로 유지되면서도 결정성 시임(테스트 고정값)을 공유받는다.</p>
 *
 * @param cs           고정길이 전문 charset (MS949)
 * @param props        EAI 설정 (시스템 식별자 등)
 * @param dateFn       패턴 → 시각문자열 (Clock 시임 바인딩)
 * @param randomDigits 길이 → 0~9 난수 문자열 (SecureRandom 시임 바인딩)
 */
public record EaiSectionContext(
        Charset cs,
        EaiProperties props,
        UnaryOperator<String> dateFn,
        IntFunction<String> randomDigits
) {
    /** 좌측 패딩 (ePAMS lpad 규칙). */
    public String lpad(String type, int offset, String str) {
        return EaiMessageBuilder.lpad(cs, type, offset, str);
    }

    /** 시각 문자열 (예: "yyyyMMdd"). */
    public String date(String pattern) {
        return dateFn.apply(pattern);
    }

    /** charset 기준 바이트 길이. */
    public int bytes(String s) {
        return s.getBytes(cs).length;
    }
}
