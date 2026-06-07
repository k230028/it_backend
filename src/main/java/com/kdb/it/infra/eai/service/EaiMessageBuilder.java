package com.kdb.it.infra.eai.service;

import java.nio.charset.Charset;

/**
 * KDB 표준전문(고정길이 전문) 조립기.
 *
 * <p>ePAMS {@code EaiService.getReqData()}의 필드·오프셋·기본값을 그대로 옮기되,
 * (1) 인코딩을 명시적 charset(MS949)으로 중앙화하고,
 * (2) 시각/난수/IP·MAC를 주입 시임으로 외부화하여 테스트 가능하게 한다.</p>
 *
 * <p>전체 조립 메서드 {@code build()}는 다음 단계에서 추가된다. 본 단계는 토대 헬퍼 {@code lpad}만 제공한다.</p>
 */
public class EaiMessageBuilder {

    /**
     * 좌측 패딩. ePAMS {@code lpad}와 동일 규칙.
     *
     * @param cs     길이 계산에 사용할 charset (MS949)
     * @param type   "N"이면 '0', 그 외("C")면 공백으로 패딩
     * @param offset 목표 바이트 폭
     * @param str    원본(null이면 빈 문자열로 취급)
     * @return 좌측 패딩된 문자열
     * @throws IndexOutOfBoundsException 원본 바이트 길이가 offset을 초과할 때
     */
    public static String lpad(Charset cs, String type, int offset, String str) {
        String tmp = (str == null) ? "" : str;
        String pad = "C".equals(type) ? " " : "0";
        int len = offset - tmp.getBytes(cs).length;
        if (len < 0) {
            throw new IndexOutOfBoundsException(
                    "전문 필드 초과: offset=" + offset + ", actual=" + tmp.getBytes(cs).length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(pad);
        }
        sb.append(tmp);
        return sb.toString();
    }
}
