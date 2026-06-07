package com.kdb.it.infra.eai.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * EAI UMS 발송 요청.
 *
 * <p>ePAMS {@code EaiDTO}에서 UMS(SMS/알림톡/이메일) 발송과 거래공통부 조립에
 * 필요한 필드만 발췌했다. 템플릿ID({@code umsBzDttId})와 인터페이스ID({@code ifId})는
 * KDB 발급값으로, 호출자가 채널별로 지정한다.</p>
 */
@Getter
@Builder
public class EaiRequest {

    /** 수신시스템코드 RMS_SYS_C (기본 "EAI"). UMS 발송 시 호출자가 "UMS" 지정. */
    @Builder.Default
    private final String system = "EAI";

    /** 인터페이스ID IF_ID (KDB 발급, 최대 12자리). */
    @Builder.Default
    private final String ifId = "";

    /** UMS업무구분ID/템플릿 UMS_BZ_DTT_ID (7자리, 1번째 글자 S/A/E = SMS/알림톡/이메일). */
    @Builder.Default
    private final String umsBzDttId = "";

    /** UMS거래일련번호 UMS_TR_SNO (채번값, 숫자 문자열). */
    @Builder.Default
    private final String umsTrSno = "";

    /** 수신자 행번 REQ_USID/CNO (퇴직자 금지). */
    private final String emplNum;

    /** 수신자명/고객명 CST_NM, REQ_USR_NM. */
    private final String cstNm;

    /** 수신 채널값 UMS_SD_CHN_NO (휴대폰번호/이메일). */
    private final String reqCh;

    /** 요청부점코드 REQ_BBR_C (3자리). */
    private final String deptKey;

    /** 요청부점명 REQ_BBR_NM. */
    private final String deptNm;

    /** 발송예정일자 UMS_SD_MPL_DT (yyyyMMdd, null이면 당일). */
    private final String sendDt;

    /** 발송예정시각 UMS_SD_MPL_TM (HHmmss, null이면 즉시). */
    private final String sendTime;

    /** 가변데이터 (템플릿 치환용). */
    private final String umData1;
    private final String umData2;
    private final String umData3;
    private final String umData4;
    private final String umData5;
    private final String umData6;
    private final String umData7;
}
