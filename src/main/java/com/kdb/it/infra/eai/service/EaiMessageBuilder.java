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
 * KDB 표준전문(고정길이 전문) 조립기.
 *
 * <p>ePAMS {@code EaiService.getReqData()}의 필드·오프셋·기본값을 그대로 옮기되,
 * 인코딩을 명시적 charset(MS949)으로 중앙화하고, 시각/난수/IP·MAC를 주입 시임으로
 * 외부화하여 테스트 가능하게 한다. 본 빌더는 전송을 수행하지 않는다.</p>
 */
public class EaiMessageBuilder {

    /** 가변데이터 JSON 안정 직렬화를 위한 항목 키. */
    private static final String[] UM_KEYS =
            {"UM_DATA_1", "UM_DATA_2", "UM_DATA_3", "UM_DATA_4", "UM_DATA_5", "UM_DATA_6", "UM_DATA_7"};

    private final EaiProperties props;
    private final Clock clock;
    private final Supplier<String> guidRandom; // 9자리 숫자 문자열 공급
    private final HostAddressProvider host;
    private final Charset cs;

    public EaiMessageBuilder(EaiProperties props, Clock clock, Supplier<String> guidRandom, HostAddressProvider host) {
        this.props = props;
        this.clock = clock;
        this.guidRandom = guidRandom;
        this.host = host;
        this.cs = Charset.forName(props.charset());
    }

    /** 표준전문(param01~08) 전체를 조립해 charset 바이트로 반환. */
    public byte[] build(EaiRequest req) {
        String p01 = param01();
        String p02 = param02(req);
        String p03 = param03();
        String p04 = param04();
        String p05 = param05();
        String p06 = param06();
        String p07 = param07Ums(req);
        String p08 = "@@";

        String whlTgrLe = lpad(cs, "N", 8, String.valueOf(bytes(p01 + p02 + p03 + p04 + p05 + p06 + p07 + p08)));
        String herLen   = lpad(cs, "N", 8, String.valueOf(bytes(p01 + p02 + p03 + p04 + p05 + p06)));
        String proMdaLen = lpad(cs, "N", 8, String.valueOf(bytes(p06)));

        String param = whlTgrLe + herLen + proMdaLen + p01.substring(24)
                + p02 + p03 + p04 + p05 + p06 + p07 + p08;
        return param.getBytes(cs);
    }

    // ── 01. 시스템공통부 ─────────────────────────────────────────────────────
    private String param01() {
        String dt = date("yyyyMMdd");
        String dt2 = date("HHmmssSSS");
        String guid = props.fwdiSysC() + dt + dt2 + guidRandom.get() + guidRandom.get();
        String ipAddr = String.format("%40s", host.ipAddress());
        String macAddr = String.format("%12s", host.macAddress());

        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "N", 8, ""));
        p.append(lpad(cs, "N", 8, ""));
        p.append(lpad(cs, "N", 8, ""));
        p.append("1.0");
        p.append("ko");
        p.append(props.sysEnvTc());
        p.append(ipAddr);
        p.append(macAddr);
        p.append(guid);
        p.append("0001");
        p.append(guid);
        p.append(props.fwdiSysC());
        p.append(props.fwdiSysC());
        p.append(lpad(cs, "C", 12, ""));
        return p.toString();
    }

    // ── 02. 거래공통부 ───────────────────────────────────────────────────────
    private String param02(EaiRequest req) {
        String reqDtm = date("yyyyMMddHHmmssSSS");
        String trSlsDt = date("yyyyMMdd");
        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 3, req.getSystem()));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append("Q");
        p.append("2");
        p.append("TR");
        p.append(lpad(cs, "C", 2, ""));
        p.append("S");
        p.append(lpad(cs, "C", 1, ""));
        p.append("00000");
        p.append(lpad(cs, "C", 1, ""));
        p.append("00000");
        p.append(reqDtm);
        p.append(lpad(cs, "C", 17, ""));
        p.append(trSlsDt);
        p.append("0");
        p.append("0");
        p.append(lpad(cs, "C", 8, ""));
        p.append("N");
        p.append("N");
        p.append("N");
        p.append(lpad(cs, "C", 1, ""));
        p.append("01");
        p.append("10");
        p.append(lpad(cs, "C", 3, ""));
        p.append(lpad(cs, "C", 14, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 3, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 3, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 3, ""));
        p.append(lpad(cs, "C", 8, ""));
        p.append(lpad(cs, "C", 20, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 12, req.getIfId()));
        p.append("00");
        p.append("11");
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 8, ""));
        p.append("00000");
        p.append("000000000");
        p.append("00");
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 2, ""));
        p.append("0000000000000000.000");
        p.append("0000000000000000.000");
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 8, ""));
        p.append(lpad(cs, "C", 40, ""));
        return p.toString();
    }

    // ── 03. 채널공통부 ───────────────────────────────────────────────────────
    private String param03() {
        StringBuilder p = new StringBuilder();
        p.append("2");
        p.append(lpad(cs, "C", 20, ""));
        p.append(lpad(cs, "C", 129, ""));
        p.append(lpad(cs, "C", 2, ""));
        p.append("00");
        p.append(lpad(cs, "C", 64, ""));
        p.append(lpad(cs, "C", 2, ""));
        p.append(lpad(cs, "C", 5, ""));
        p.append(lpad(cs, "C", 4, ""));
        p.append(lpad(cs, "C", 10, ""));
        p.append(lpad(cs, "C", 3, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 8, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 2, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 2, ""));
        p.append(lpad(cs, "C", 1, ""));
        p.append("N");
        p.append(lpad(cs, "C", 38, ""));
        p.append(lpad(cs, "C", 102, ""));
        return p.toString();
    }

    // ── 04. 책임자승인공통부 ─────────────────────────────────────────────────
    private String param04() {
        StringBuilder p = new StringBuilder();
        p.append("00");
        p.append("00");
        p.append(lpad(cs, "C", 2, ""));
        p.append("00");
        p.append("00");
        p.append("00");
        p.append("00");
        p.append(lpad(cs, "C", 18, ""));
        p.append(lpad(cs, "C", 29, ""));
        p.append("00");
        return p.toString();
    }

    // ── 05. 메시지공통부 ─────────────────────────────────────────────────────
    private String param05() {
        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "C", 1, ""));
        p.append(lpad(cs, "C", 50, ""));
        p.append("000");
        p.append("00");
        return p.toString();
    }

    // ── 06. 출력매체부 ───────────────────────────────────────────────────────
    private String param06() {
        return "000";
    }

    // ── 07. 개별부(UMS 입력데이터) ───────────────────────────────────────────
    private String param07Ums(EaiRequest req) {
        String umsBzDttId = req.getUmsBzDttId();
        String reqUsid = req.getEmplNum();
        String umsSdChnNo = req.getReqCh();
        String sendDt = req.getSendDt();
        String sendTime = req.getSendTime();

        String trDt = date("yyyyMMdd");
        String trTm = date("HHmmss");

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

        String variDatS0 = variableDataJson(req);
        String variDatLenN9 = String.valueOf(bytes(variDatS0));

        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "C", 7, umsBzDttId));
        p.append(trDt);
        p.append(lpad(cs, "N", 10, umsTrSno));
        p.append(lpad(cs, "C", 23, umsRetNo));
        p.append(lpad(cs, "C", 8, reqUsid));
        p.append(lpad(cs, "C", 100, reqUsrNm));
        p.append(lpad(cs, "C", 30, ""));
        p.append(lpad(cs, "C", 200, umsSdChnNo));
        p.append(lpad(cs, "C", 4000, ""));
        p.append(lpad(cs, "C", 8, (sendDt == null) ? trDt : sendDt));
        p.append(lpad(cs, "C", 6, (sendTime == null) ? "" : sendTime));
        p.append(lpad(cs, "C", 100, umsTmeChnNo));
        p.append(props.appC());
        p.append(props.appBzLv1C());
        p.append(lpad(cs, "C", 14, reqUsid));
        p.append(lpad(cs, "C", 100, reqUsrNm));
        p.append(lpad(cs, "C", 3, reqBbrC));
        p.append(lpad(cs, "C", 100, reqBbrNm));
        p.append("N");
        p.append(lpad(cs, "C", 4000, ""));
        p.append(lpad(cs, "C", 6, trTm));
        p.append(umsSdChnTpC);
        p.append("10");
        p.append(lpad(cs, "C", 100, ""));
        p.append(lpad(cs, "C", 1000, ""));
        p.append(lpad(cs, "C", 1, "N"));
        p.append(lpad(cs, "C", 14, "SYSTEM"));
        p.append(props.bzCS3());
        p.append(lpad(cs, "N", 9, variDatLenN9));
        p.append(variDatS0);
        return p.toString();
    }

    /**
     * 가변데이터 JSON 안정 직렬화.
     * {@code {"type":"dataSet","entries":{"UM_DATA_1":"v1",...}}} (비어있지 않은 항목만, 키 순서 고정).
     */
    private String variableDataJson(EaiRequest req) {
        String[] vals = {req.getUmData1(), req.getUmData2(), req.getUmData3(), req.getUmData4(),
                req.getUmData5(), req.getUmData6(), req.getUmData7()};
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

    /** JSON 문자열 값 최소 이스케이프(역슬래시·따옴표). */
    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int bytes(String s) {
        return s.getBytes(cs).length;
    }

    private String date(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(LocalDateTime.now(clock));
    }

    /**
     * 좌측 패딩. ePAMS {@code lpad}와 동일 규칙.
     *
     * @throws IndexOutOfBoundsException 원본 바이트 길이가 offset을 초과할 때
     */
    public static String lpad(Charset cs, String type, int offset, String str) {
        String tmp = (str == null) ? "" : str;
        String pad = "C".equals(type) ? " " : "0";
        int len = offset - tmp.getBytes(cs).length;
        if (len < 0) {
            throw new IndexOutOfBoundsException(
                    "전문 필드 초과: offset=" + offset + ", actual=" + tmp.getBytes(cs).length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(pad);
        }
        sb.append(tmp);
        return sb.toString();
    }
}
