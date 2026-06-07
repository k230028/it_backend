package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.dto.GwePayload;

/**
 * [테스트 전용 / 동결] ePAMS {@code getParamGWE}(주석 원본)의 개별부 필드 레이아웃 독립 전사.
 *
 * <p>{@link GwePayloadSection}과 바이트 동일함을 증명하기 위한 오라클. 자체 헬퍼만 사용.</p>
 */
class EpamsGweReferenceBuilder {

    private final EaiSectionContext ctx;

    EpamsGweReferenceBuilder(EaiSectionContext ctx) {
        this.ctx = ctx;
    }

    String build(GwePayload g) {
        String msgKey = "mailt" + ctx.props().appC() + ctx.props().appBzLv1C()
                + ctx.date("yyyyMMddHHmmss") + ctx.randomDigits().apply(8);

        StringBuilder p = new StringBuilder();
        p.append(pad("C", 32, msgKey));               // MSG_KEY
        p.append(pad("C", 1, g.msgGubun()));          // MSG_GUBUN
        p.append(pad("C", 50, g.sendId()));           // SEND_ID
        p.append(pad("C", 100, g.sendName()));        // SEND_NAME
        p.append(pad("C", 1, g.destGubun()));         // DEST_GUBUN
        p.append(pad("C", 4000, g.recvIds()));        // RECV_IDS
        p.append(pad("C", 500, g.ccRecvIds()));       // CC_RECV_IDS
        p.append(pad("C", 500, g.bccRecvIds()));      // BCC_RECV_IDS
        p.append(pad("C", 200, g.subject()));         // SUBJECT
        p.append(pad("C", 4000, g.contents()));       // CONTENTS
        p.append(pad("C", 500, g.url()));             // URL
        p.append(pad("C", 1, g.attFlag()));           // ATT_FLAG
        p.append(pad("C", 4000, g.att()));            // ATT
        p.append(pad("C", 3, ctx.props().fwdiSysC())); // SYSTEM_CODE
        return p.toString();
    }

    private String pad(String type, int offset, String str) {
        return EaiMessageBuilder.lpad(java.nio.charset.Charset.forName("MS949"), type, offset, str);
    }
}
