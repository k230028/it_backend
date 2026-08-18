package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import java.nio.charset.Charset;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

/**
 * 개별부 섹션이 필요로 하는 공유 도구 묶음.
 *
 * <p>{@link EaiMessageBuilder}가 호출 시점에 charset·설정·시각/난수 시임을 묶어 전달한다. 섹션이 무상태로 유지되면서도 결정성 시임(테스트
 * 고정값)을 공유받는다.
 *
 * @param cs 고정길이 전문 charset (eai.charset)
 * @param props EAI 설정 (시스템 식별자 등)
 * @param dateFn 패턴 → 시각문자열 (Clock 시임 바인딩)
 * @param randomDigits 길이 → 0~9 난수 문자열 (SecureRandom 시임 바인딩)
 */
public record EaiSectionContext(
        Charset cs,
        EaiProperties props,
        UnaryOperator<String> dateFn,
        IntFunction<String> randomDigits) {
    /** 좌측 패딩 (ePAMS lpad 규칙). */
    public String lpad(String type, int offset, String str) {
        return EaiMessageBuilder.lpad(cs, type, offset, str);
    }

    /**
     * 표시용 텍스트를 필드 바이트 예산에 맞춰 자른 뒤 좌측 패딩합니다.
     *
     * <p>제목·본문처럼 길이가 업무 데이터에 좌우되는 필드에만 씁니다. 전문 문자셋에 따라 한 글자의 바이트 수가 달라지므로(MS949 한글 2바이트, UTF-8
     * 3바이트) 원본 컬럼 길이가 그대로 필드에 들어간다고 가정할 수 없습니다. 수신자·식별자처럼 잘리면 오배송이 되는 필드에는 쓰지 않고 {@link #lpad}로 초과를
     * 드러냅니다.
     *
     * @param offset 목표 바이트 길이
     * @param str 원본 문자열. null 허용
     * @param fieldName 잘렸을 때 로그에 남길 필드명
     * @return 예산 안으로 자르고 좌측 패딩한 문자열
     */
    public String lpadFit(int offset, String str, String fieldName) {
        return lpad("C", offset, EaiTextFitter.fit(str, offset, cs, fieldName));
    }

    /** 시각 문자열 (예: "yyyyMMdd"). */
    public String date(String pattern) {
        return dateFn.apply(pattern);
    }

    /** 길이 인자 난수 문자열 (예: GWE MSG_KEY 8자리). */
    public String randomDigits(int len) {
        return randomDigits.apply(len);
    }

    /** charset 기준 바이트 길이. */
    public int bytes(String s) {
        return s.getBytes(cs).length;
    }
}
