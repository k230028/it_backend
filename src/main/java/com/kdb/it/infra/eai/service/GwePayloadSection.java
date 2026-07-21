package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.dto.EaiPayload;
import com.kdb.it.infra.eai.dto.GwePayload;
import org.springframework.stereotype.Component;

/**
 * GWE(그룹웨어) 개별부 섹션 — 메신저(1)/메일(3).
 *
 * <p>ePAMS {@code getParamGWE}(주석 원본)의 필드 레이아웃을 포팅했다. {@code MSG_KEY} = "mailt" + APP_C +
 * APP_BZ_LV1_C + 일시(14) + 난수(8) = 32자.
 */
@Component
public class GwePayloadSection implements EaiPayloadSection {

    @Override
    public boolean supports(EaiPayload payload) {
        return payload instanceof GwePayload;
    }

    @Override
    public String systemCode() {
        return "GWE";
    }

    @Override
    public String build(EaiPayload payload, EaiSectionContext ctx) {
        GwePayload g = (GwePayload) payload;

        String msgKey =
                "mailt"
                        + ctx.props().appC()
                        + ctx.props().appBzLv1C()
                        + ctx.date("yyyyMMddHHmmss")
                        + ctx.randomDigits(8);

        StringBuilder p = new StringBuilder();
        p.append(ctx.lpad("C", 32, msgKey)); // MSG_KEY      메시지키값
        p.append(ctx.lpad("C", 1, g.msgGubun())); // MSG_GUBUN    알림구분 (1메신저/3메일)
        p.append(ctx.lpad("C", 50, g.sendId())); // SEND_ID      전송자 사번
        p.append(ctx.lpad("C", 100, g.sendName())); // SEND_NAME    전송자 이름
        p.append(ctx.lpad("C", 1, g.destGubun())); // DEST_GUBUN   수신자 구분 (1사용자/2부서)
        p.append(ctx.lpad("C", 4000, g.recvIds())); // RECV_IDS     수신자 정보
        p.append(ctx.lpad("C", 500, g.ccRecvIds())); // CC_RECV_IDS  참조 (메일)
        p.append(ctx.lpad("C", 500, g.bccRecvIds())); // BCC_RECV_IDS 숨은참조 (메일)
        p.append(ctx.lpad("C", 200, g.subject())); // SUBJECT      제목
        p.append(ctx.lpad("C", 4000, g.contents())); // CONTENTS     내용 (HTML)
        p.append(ctx.lpad("C", 500, g.url())); // URL          메신저 클릭URL
        p.append(ctx.lpad("C", 1, g.attFlag())); // ATT_FLAG     첨부여부 (메일)
        p.append(ctx.lpad("C", 4000, g.att())); // ATT          첨부정보 (메일)
        p.append(ctx.lpad("C", 3, ctx.props().fwdiSysC())); // SYSTEM_CODE 발송요청 시스템코드
        return p.toString();
    }
}
