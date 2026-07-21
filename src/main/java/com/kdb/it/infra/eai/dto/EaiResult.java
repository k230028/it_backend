package com.kdb.it.infra.eai.dto;

/**
 * EAI 발송 결과.
 *
 * <p>ePAMS의 어색한 {@code null}/빈 {@code EaiDTO} 반환 패턴을 대체한다. 호출자 흐름을 차단하지 않는 부수효과 원칙을 따른다(실패해도 예외 전파
 * 없음).
 *
 * @param success 전송 성공 여부
 * @param skipped 전송 스킵 여부 ({@code eai.enabled=false})
 * @param responseRaw 게이트웨이 원시 응답(성공 시), 그 외 null
 * @param errorMessage 실패 사유(실패 시), 그 외 null
 */
public record EaiResult(boolean success, boolean skipped, String responseRaw, String errorMessage) {

    /** 전송 성공. */
    public static EaiResult success(String responseRaw) {
        return new EaiResult(true, false, responseRaw, null);
    }

    /** 전송 스킵(비활성화). 전문은 빌드·로깅되었으나 HTTP는 호출되지 않음. */
    public static EaiResult skip() {
        return new EaiResult(false, true, null, null);
    }

    /** 전송/빌드 실패. 예외 전파 없이 결과로만 표현. */
    public static EaiResult failure(String errorMessage) {
        return new EaiResult(false, false, null, errorMessage);
    }
}
