package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * [테스트 전용 / 동결] ePAMS {@code EaiService.getReqData()} UMS 조립 로직의 독립 전사본.
 *
 * <p>신규 {@link EaiMessageBuilder}와 바이트 단위로 동일함을 증명하기 위한 오라클이다.
 * 신규 빌더의 헬퍼를 재사용하지 않고 자체 구현을 가진다(독립성). 이 파일은 가급적 수정하지 않는다.</p>
 */
class EpamsReferenceMessageBuilder {

    private final EaiProperties props;
    private final Clock clock;
    private final Supplier<String> guidRandom;
    private final HostAddressProvider host;
    private final Charset cs;

    EpamsReferenceMessageBuilder(EaiProperties props, Clock clock, Supplier<String> guidRandom, HostAddressProvider host) {
        this.props = props;
        this.clock = clock;
        this.guidRandom = guidRandom;
        this.host = host;
        this.cs = Charset.forName(props.charset());
    }

    byte[] buildUms(EaiRequest req) {
        String p01 = p01();
        String p02 = p02(req);
        String p03 = p03();
        String p04 = p04();
        String p05 = p05();
        String p06 = "000";
        String p07 = p07(req);
        String p08 = "@@";

        String whl = pad("N", 8, String.valueOf(len(p01 + p02 + p03 + p04 + p05 + p06 + p07 + p08)));
        String her = pad("N", 8, String.valueOf(len(p01 + p02 + p03 + p04 + p05 + p06)));
        String pro = pad("N", 8, String.valueOf(len(p06)));

        String s = whl + her + pro + p01.substring(24) + p02 + p03 + p04 + p05 + p06 + p07 + p08;
        return s.getBytes(cs);
    }

    private String p01() {
        String dt = fmt("yyyyMMdd");
        String dt2 = fmt("HHmmssSSS");
        String guid = props.fwdiSysC() + dt + dt2 + guidRandom.get() + guidRandom.get();
        String ip = String.format("%40s", host.ipAddress());
        String mac = String.format("%12s", host.macAddress());
        return pad("N", 8, "") + pad("N", 8, "") + pad("N", 8, "")
                + "1.0" + "ko" + props.sysEnvTc() + ip + mac + guid + "0001" + guid
                + props.fwdiSysC() + props.fwdiSysC() + pad("C", 12, "");
    }

    private String p02(EaiRequest req) {
        String reqDtm = fmt("yyyyMMddHHmmssSSS");
        String trSlsDt = fmt("yyyyMMdd");
        return pad("C", 10, "") + pad("C", 3, req.getSystem()) + pad("C", 10, "") + pad("C", 10, "")
                + pad("C", 1, "") + "Q" + "2" + "TR" + pad("C", 2, "") + "S" + pad("C", 1, "")
                + "00000" + pad("C", 1, "") + "00000" + reqDtm + pad("C", 17, "") + trSlsDt + "0" + "0"
                + pad("C", 8, "") + "N" + "N" + "N" + pad("C", 1, "") + "01" + "10" + pad("C", 3, "")
                + pad("C", 14, "") + pad("C", 4, "") + pad("C", 4, "") + pad("C", 3, "") + pad("C", 10, "")
                + pad("C", 3, "") + pad("C", 10, "") + pad("C", 10, "") + pad("C", 10, "") + pad("C", 4, "")
                + pad("C", 4, "") + pad("C", 3, "") + pad("C", 8, "") + pad("C", 20, "") + pad("C", 1, "")
                + pad("C", 4, "") + pad("C", 12, req.getIfId()) + "00" + "11" + pad("C", 10, "") + pad("C", 10, "")
                + pad("C", 8, "") + "00000" + "000000000" + "00" + pad("C", 10, "") + pad("C", 1, "")
                + pad("C", 10, "") + pad("C", 2, "") + "0000000000000000.000" + "0000000000000000.000"
                + pad("C", 1, "") + pad("C", 8, "") + pad("C", 40, "");
    }

    private String p03() {
        return "2" + pad("C", 20, "") + pad("C", 129, "") + pad("C", 2, "") + "00" + pad("C", 64, "")
                + pad("C", 2, "") + pad("C", 5, "") + pad("C", 4, "") + pad("C", 10, "") + pad("C", 3, "")
                + pad("C", 1, "") + pad("C", 1, "") + pad("C", 8, "") + pad("C", 1, "") + pad("C", 2, "")
                + pad("C", 1, "") + pad("C", 2, "") + pad("C", 1, "") + "N" + pad("C", 38, "") + pad("C", 102, "");
    }

    private String p04() {
        return "00" + "00" + pad("C", 2, "") + "00" + "00" + "00" + "00" + pad("C", 18, "") + pad("C", 29, "") + "00";
    }

    private String p05() {
        return pad("C", 1, "") + pad("C", 50, "") + "000" + "00";
    }

    private String p07(EaiRequest req) {
        String umsBzDttId = req.getUmsBzDttId();
        String reqUsid = req.getEmplNum();
        String umsSdChnNo = req.getReqCh();
        String sendDt = req.getSendDt();
        String sendTime = req.getSendTime();
        String trDt = fmt("yyyyMMdd");
        String trTm = fmt("HHmmss");
        String umsTrSno = req.getUmsTrSno();
        String umsRetNo = umsBzDttId + trDt + String.format("%08d", Integer.parseInt(umsTrSno));
        String umsTmeChnNo = "1588-1500";
        if (!umsBzDttId.isEmpty() && "E".equals(umsBzDttId.substring(0, 1))) {
            umsTmeChnNo = "hrd@kdb.co.kr";
        }
        String reqUsrNm = req.getCstNm();
        String reqBbrC = req.getDeptKey();
        String reqBbrNm = req.getDeptNm();
        String umsSdChnTpC = umsBzDttId.isEmpty() ? "" : umsBzDttId.substring(0, 1);
        if ("E".equals(umsSdChnTpC)) {
            umsSdChnTpC = "M";
        }
        String vari = json(req);
        String variLen = String.valueOf(len(vari));

        return pad("C", 7, umsBzDttId) + trDt + pad("N", 10, umsTrSno) + pad("C", 23, umsRetNo)
                + pad("C", 8, reqUsid) + pad("C", 100, reqUsrNm) + pad("C", 30, "") + pad("C", 200, umsSdChnNo)
                + pad("C", 4000, "") + pad("C", 8, (sendDt == null) ? trDt : sendDt)
                + pad("C", 6, (sendTime == null) ? "" : sendTime) + pad("C", 100, umsTmeChnNo)
                + props.appC() + props.appBzLv1C() + pad("C", 14, reqUsid) + pad("C", 100, reqUsrNm)
                + pad("C", 3, reqBbrC) + pad("C", 100, reqBbrNm) + "N" + pad("C", 4000, "") + pad("C", 6, trTm)
                + umsSdChnTpC + "10" + pad("C", 100, "") + pad("C", 1000, "") + pad("C", 1, "N")
                + pad("C", 14, "SYSTEM") + props.bzCS3() + pad("N", 9, variLen) + vari;
    }

    private String json(EaiRequest req) {
        String[] keys = {"UM_DATA_1", "UM_DATA_2", "UM_DATA_3", "UM_DATA_4", "UM_DATA_5", "UM_DATA_6", "UM_DATA_7"};
        String[] vals = {req.getUmData1(), req.getUmData2(), req.getUmData3(), req.getUmData4(),
                req.getUmData5(), req.getUmData6(), req.getUmData7()};
        StringBuilder e = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < keys.length; i++) {
            if (vals[i] == null || vals[i].isEmpty()) {
                continue;
            }
            if (!first) {
                e.append(",");
            }
            e.append("\"").append(keys[i]).append("\":\"")
                    .append(vals[i].replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            first = false;
        }
        return "{\"type\":\"dataSet\",\"entries\":{" + e + "}}";
    }

    private int len(String s) {
        return s.getBytes(cs).length;
    }

    private String fmt(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(LocalDateTime.now(clock));
    }

    private String pad(String type, int offset, String str) {
        String tmp = (str == null) ? "" : str;
        String p = "C".equals(type) ? " " : "0";
        int n = offset - tmp.getBytes(cs).length;
        if (n < 0) {
            throw new IndexOutOfBoundsException();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(p);
        }
        return sb.append(tmp).toString();
    }
}
