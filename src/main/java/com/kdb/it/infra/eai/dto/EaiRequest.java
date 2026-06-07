package com.kdb.it.infra.eai.dto;

/**
 * EAI 발송 요청 — 헤더 식별자({@code ifId}) + 개별부 페이로드.
 *
 * <p>채널별 데이터는 {@link EaiPayload}(sealed)로 분리한다. 헤더 RMS_SYS_C는
 * 페이로드에 대응하는 섹션의 systemCode가 결정한다.</p>
 *
 * @param ifId    인터페이스ID IF_ID (KDB 발급, 최대 12자리)
 * @param payload 개별부 입력 (UmsPayload/GwePayload)
 */
public record EaiRequest(String ifId, EaiPayload payload) {

    /** UMS 발송 요청. */
    public static EaiRequest ums(String ifId, UmsPayload payload) {
        return new EaiRequest(ifId, payload);
    }

    /** GWE 발송 요청. */
    public static EaiRequest gwe(String ifId, GwePayload payload) {
        return new EaiRequest(ifId, payload);
    }
}
