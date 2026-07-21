package com.kdb.it.infra.eai.dto;

import lombok.Builder;

/**
 * UMS(SMS/알림톡/이메일) 개별부 입력.
 *
 * <p>기존 {@code EaiRequest}의 UMS 필드를 그대로 이전했다. 헤더용 {@code ifId}/{@code systemCode}는 페이로드가 아니라 {@link
 * EaiRequest}/섹션이 책임진다.
 *
 * @param umsBzDttId UMS 업무구분 ID 또는 템플릿 ID
 * @param umsTrSno UMS 거래일련번호
 * @param emplNum 수신자 행번
 * @param cstNm 수신자명
 * @param reqCh 휴대전화번호 또는 이메일 주소인 수신 채널값
 * @param deptKey 요청 부점 코드
 * @param deptNm 요청 부점명
 * @param sendDt 발송예정일자, 당일 발송이면 {@code null}
 * @param sendTime 발송예정시각, 즉시 발송이면 {@code null}
 * @param umData1 첫 번째 템플릿 치환 데이터
 * @param umData2 두 번째 템플릿 치환 데이터
 * @param umData3 세 번째 템플릿 치환 데이터
 * @param umData4 네 번째 템플릿 치환 데이터
 * @param umData5 다섯 번째 템플릿 치환 데이터
 * @param umData6 여섯 번째 템플릿 치환 데이터
 * @param umData7 일곱 번째 템플릿 치환 데이터
 */
@Builder
public record UmsPayload(
        String umsBzDttId, // UMS업무구분ID/템플릿 (7자리, 1번째 글자 S/A/E)
        String umsTrSno, // UMS거래일련번호 (숫자 문자열)
        String emplNum, // 수신자 행번 (CNO/REQ_USID)
        String cstNm, // 수신자명/고객명
        String reqCh, // 수신 채널값 (휴대폰/이메일)
        String deptKey, // 요청부점코드
        String deptNm, // 요청부점명
        String sendDt, // 발송예정일자 (yyyyMMdd, null=당일)
        String sendTime, // 발송예정시각 (HHmmss, null=즉시)
        String umData1,
        String umData2,
        String umData3,
        String umData4,
        String umData5,
        String umData6,
        String umData7)
        implements EaiPayload {

    /** null 기본값 보정 — 필수값은 빈 문자열로, 나머지는 그대로. */
    public UmsPayload {
        umsBzDttId = umsBzDttId == null ? "" : umsBzDttId;
        umsTrSno = umsTrSno == null ? "" : umsTrSno;
    }
}
