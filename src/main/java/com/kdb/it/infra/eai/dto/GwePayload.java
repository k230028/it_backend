package com.kdb.it.infra.eai.dto;

import lombok.Builder;

/**
 * GWE(그룹웨어 메신저/메일) 개별부 입력.
 *
 * <p>{@code msgGubun}: "1"=메신저, "3"=메일. 메신저 전용 {@code url},
 * 메일 전용 {@code ccRecvIds/bccRecvIds/attFlag/att}.</p>
 */
@Builder
public record GwePayload(
        String msgGubun,    // "1" 메신저 / "3" 메일
        String recvIds,     // 수신자 사번/부서코드 콤마목록
        String subject,     // 제목
        String contents,    // 본문(HTML, 치환 완료본)
        String destGubun,   // "1" 사용자 / "2" 부서 (기본 "1")
        String url,         // 메신저 클릭 URL
        String ccRecvIds,   // 메일 참조
        String bccRecvIds,  // 메일 숨은참조
        String attFlag,     // 메일 첨부여부
        String att,         // 메일 첨부정보
        String sendId,      // 전송자 사번 (기본 "systemalert")
        String sendName     // 전송자 이름 (기본 "관리자")
) implements EaiPayload {

    /** null 기본값 보정. */
    public GwePayload {
        msgGubun = msgGubun == null ? "" : msgGubun;
        recvIds = recvIds == null ? "" : recvIds;
        subject = subject == null ? "" : subject;
        contents = contents == null ? "" : contents;
        destGubun = (destGubun == null || destGubun.isBlank()) ? "1" : destGubun;
        url = url == null ? "" : url;
        ccRecvIds = ccRecvIds == null ? "" : ccRecvIds;
        bccRecvIds = bccRecvIds == null ? "" : bccRecvIds;
        attFlag = attFlag == null ? "" : attFlag;
        att = att == null ? "" : att;
        sendId = (sendId == null || sendId.isBlank()) ? "systemalert" : sendId;
        sendName = (sendName == null || sendName.isBlank()) ? "관리자" : sendName;
    }
}
