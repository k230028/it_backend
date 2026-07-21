package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.dto.EaiPayload;

/**
 * 표준전문 개별부(param07) 채널 1개를 책임지는 전략(SPI).
 *
 * <p>무상태 {@code @Component}로 구현한다. 신규 채널 = 새 페이로드 record + 새 섹션 1개.
 */
public interface EaiPayloadSection {

    /**
     * 이 섹션이 처리할 수 있는 페이로드인지 판정합니다.
     *
     * @param payload 판정할 EAI 페이로드
     * @return 이 섹션이 지원하면 true
     */
    boolean supports(EaiPayload payload);

    /**
     * 헤더 거래공통부 RMS_SYS_C 값을 반환합니다.
     *
     * @return 채널 시스템 코드(예: "UMS", "GWE")
     */
    String systemCode();

    /**
     * 전송 전 개별부 문자열을 조립합니다.
     *
     * @param payload 채널별 페이로드
     * @param ctx 시각·난수 등 섹션 조립 문맥
     * @return 문자셋 인코딩 전 개별부 문자열
     * @throws IllegalArgumentException 지원하지 않는 페이로드나 유효하지 않은 필드가 전달된 경우
     */
    String build(EaiPayload payload, EaiSectionContext ctx);
}
