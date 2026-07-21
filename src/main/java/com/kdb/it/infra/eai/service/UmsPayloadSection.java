package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.dto.EaiPayload;
import com.kdb.it.infra.eai.dto.UmsPayload;
import org.springframework.stereotype.Component;

/**
 * UMS 개별부 섹션 — SMS/알림톡/이메일.
 *
 * <p>기존 {@code EaiMessageBuilder.param07Ums}를 그대로 이전했다. 출력 바이트는 불변.
 */
@Component
public class UmsPayloadSection implements EaiPayloadSection {

    private static final String[] UM_KEYS = {
        "UM_DATA_1", "UM_DATA_2", "UM_DATA_3", "UM_DATA_4", "UM_DATA_5", "UM_DATA_6", "UM_DATA_7"
    };

    @Override
    public boolean supports(EaiPayload payload) {
        return payload instanceof UmsPayload;
    }

    @Override
    public String systemCode() {
        return "UMS";
    }

    @Override
    public String build(EaiPayload payload, EaiSectionContext ctx) {
        UmsPayload u = (UmsPayload) payload;

        String umsBzDttId = u.umsBzDttId();
        String reqUsid = u.emplNum();
        String umsSdChnNo = u.reqCh();
        String sendDt = u.sendDt();
        String sendTime = u.sendTime();

        String trDt = ctx.date("yyyyMMdd");
        String trTm = ctx.date("HHmmss");

        String umsTrSno = u.umsTrSno();
        String umsRetNo = umsBzDttId + trDt + String.format("%08d", Integer.parseInt(umsTrSno));

        String umsTmeChnNo = "1588-1500";
        if (!umsBzDttId.isEmpty() && "E".equals(umsBzDttId.substring(0, 1))) {
            umsTmeChnNo = "hrd@kdb.co.kr";
        }

        String reqUsrNm = u.cstNm();
        String reqBbrC = u.deptKey();
        String reqBbrNm = u.deptNm();

        String umsSdChnTpC = umsBzDttId.isEmpty() ? "" : umsBzDttId.substring(0, 1);
        if ("E".equals(umsSdChnTpC)) {
            umsSdChnTpC = "M";
        }

        String variDatS0 = variableDataJson(u);
        String variDatLenN9 = String.valueOf(ctx.bytes(variDatS0));

        String sdMplDt = (sendDt == null) ? trDt : sendDt;
        String sdMplTm = (sendTime == null) ? "" : sendTime;

        StringBuilder p = new StringBuilder();
        p.append(ctx.lpad("C", 7, umsBzDttId)); // UMS_BZ_DTT_ID
        p.append(trDt); // TR_DT
        p.append(ctx.lpad("N", 10, umsTrSno)); // UMS_TR_SNO
        p.append(ctx.lpad("C", 23, umsRetNo)); // UMS_RET_NO
        p.append(ctx.lpad("C", 8, reqUsid)); // CNO
        p.append(ctx.lpad("C", 100, reqUsrNm)); // CST_NM
        p.append(ctx.lpad("C", 30, "")); // SECT_EML_CNFM_NO
        p.append(ctx.lpad("C", 200, umsSdChnNo)); // UMS_SD_CHN_NO
        p.append(ctx.lpad("C", 4000, "")); // UMS_SD_CHN_ADDR_CONE
        p.append(ctx.lpad("C", 8, sdMplDt)); // UMS_SD_MPL_DT
        p.append(ctx.lpad("C", 6, sdMplTm)); // UMS_SD_MPL_TM
        p.append(ctx.lpad("C", 100, umsTmeChnNo)); // UMS_TME_CHN_NO
        p.append(ctx.props().appC()); // APP_C
        p.append(ctx.props().appBzLv1C()); // APP_BZ_LV1_C
        p.append(ctx.lpad("C", 14, reqUsid)); // REQ_USID
        p.append(ctx.lpad("C", 100, reqUsrNm)); // REQ_USR_NM
        p.append(ctx.lpad("C", 3, reqBbrC)); // REQ_BBR_C
        p.append(ctx.lpad("C", 100, reqBbrNm)); // REQ_BBR_NM
        p.append("N"); // UMS_CKG_C
        p.append(ctx.lpad("C", 4000, "")); // APG_FL_CONE
        p.append(ctx.lpad("C", 6, trTm)); // TR_TM
        p.append(umsSdChnTpC); // UMS_SD_CHN_TP_C
        p.append("10"); // CHN_REQ_TP_C
        p.append(ctx.lpad("C", 100, "")); // BZ_DCM_INF_CONE
        p.append(ctx.lpad("C", 1000, "")); // UMS_BZ_RFR_CONE
        p.append(ctx.lpad("C", 1, "N")); // DEL_YN
        p.append(ctx.lpad("C", 14, "SYSTEM")); // LST_CHG_USID
        p.append(ctx.props().bzCS3()); // BZ_C_S3
        p.append(ctx.lpad("N", 9, variDatLenN9)); // VARI_DAT_LEN_N9
        p.append(variDatS0); // VARI_DAT_S0
        return p.toString();
    }

    /** 가변데이터 JSON 안정 직렬화 (키 순서 고정, 비어있지 않은 항목만). */
    private String variableDataJson(UmsPayload u) {
        String[] vals = {
            u.umData1(),
            u.umData2(),
            u.umData3(),
            u.umData4(),
            u.umData5(),
            u.umData6(),
            u.umData7()
        };
        StringBuilder entries = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < UM_KEYS.length; i++) {
            String v = vals[i];
            if (v == null || v.isEmpty()) {
                continue;
            }
            if (!first) {
                entries.append(",");
            }
            entries.append("\"").append(UM_KEYS[i]).append("\":\"").append(escape(v)).append("\"");
            first = false;
        }
        return "{\"type\":\"dataSet\",\"entries\":{" + entries + "}}";
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
