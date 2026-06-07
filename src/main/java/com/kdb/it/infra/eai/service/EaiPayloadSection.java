package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.dto.EaiPayload;

/**
 * 표준전문 개별부(param07) 채널 1개를 책임지는 전략(SPI).
 *
 * <p>무상태 {@code @Component}로 구현한다. 신규 채널 = 새 페이로드 record + 새 섹션 1개.</p>
 */
public interface EaiPayloadSection {

    /** 이 섹션이 처리할 수 있는 페이로드인지. */
    boolean supports(EaiPayload payload);

    /** 헤더 거래공통부 RMS_SYS_C 값 (예: "UMS", "GWE"). */
    String systemCode();

    /** 개별부 문자열 조립 (charset 미적용, 전송 전 단계). */
    String build(EaiPayload payload, EaiSectionContext ctx);
}
