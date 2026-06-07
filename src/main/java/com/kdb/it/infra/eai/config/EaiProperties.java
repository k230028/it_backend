package com.kdb.it.infra.eai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EAI 게이트웨이 연동 설정 — 접두사 {@code eai}.
 *
 * <p>시스템 식별자(IPP/PRM/PP)는 소스 하드코딩 없이 프로퍼티/프로파일로 관리한다.
 * 비결정 값(시각/난수/IP·MAC)은 본 설정이 아니라 별도 시임 빈으로 주입한다.</p>
 *
 * @param enabled        실제 HTTP 전송 여부. false면 전문 빌드·로깅만 수행.
 * @param url            EAI 게이트웨이 URL. enabled=true일 때 필수.
 * @param charset        고정길이 전문 인코딩(기본 MS949).
 * @param connectTimeout 연결 타임아웃(ms).
 * @param readTimeout    읽기 타임아웃(ms).
 * @param sysEnvTc       시스템환경구분코드 1자리(운영 "P" / 그 외 "L").
 * @param fwdiSysC       전송시스템코드 3자리(FWDI_SYS_C/FST_FWDI_SYS_C, GUID 접두로도 사용).
 * @param bzCS3          업무코드_S3 3자리.
 * @param appC           어플리케이션코드 3자리(APP_C).
 * @param appBzLv1C      어플리케이션업무1레벨코드 2자리(APP_BZ_LV1_C).
 */
@ConfigurationProperties(prefix = "eai")
public record EaiProperties(
        boolean enabled,
        String url,
        String charset,
        int connectTimeout,
        int readTimeout,
        String sysEnvTc,
        String fwdiSysC,
        String bzCS3,
        String appC,
        String appBzLv1C
) {
    /** 누락 기본값 보정 — 프로퍼티 미지정 시 안전한 기본값 적용. */
    public EaiProperties {
        if (charset == null || charset.isBlank()) charset = "MS949";
        if (connectTimeout <= 0) connectTimeout = 3000;
        if (readTimeout <= 0) readTimeout = 3000;
        if (sysEnvTc == null || sysEnvTc.isBlank()) sysEnvTc = "L";
        if (fwdiSysC == null || fwdiSysC.isBlank()) fwdiSysC = "IPP";
        if (bzCS3 == null || bzCS3.isBlank()) bzCS3 = "IPP";
        if (appC == null || appC.isBlank()) appC = "PRM";
        if (appBzLv1C == null || appBzLv1C.isBlank()) appBzLv1C = "PP";
        if (fwdiSysC.length() != 3) throw new IllegalStateException("eai.fwdi-sys-c must be 3 chars: " + fwdiSysC);
        if (bzCS3.length() != 3) throw new IllegalStateException("eai.bz-c-s3 must be 3 chars: " + bzCS3);
        if (appC.length() != 3) throw new IllegalStateException("eai.app-c must be 3 chars: " + appC);
        if (appBzLv1C.length() != 2) throw new IllegalStateException("eai.app-bz-lv1-c must be 2 chars: " + appBzLv1C);
    }
}
