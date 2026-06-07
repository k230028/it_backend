package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.EaiPayload;
import com.kdb.it.infra.eai.dto.EaiRequest;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * KDB 표준전문(고정길이 전문) 조립기.
 *
 * <p>ePAMS {@code EaiService.getReqData()}의 필드·오프셋·기본값을 그대로 옮기되,
 * 인코딩을 명시적 charset(MS949)으로 중앙화하고, 시각/난수/IP·MAC를 주입 시임으로
 * 외부화하여 테스트 가능하게 한다. 본 빌더는 전송을 수행하지 않는다.</p>
 */
public class EaiMessageBuilder {

    private final EaiProperties props;
    private final Clock clock;
    private final Supplier<String> guidRandom;        // 헤더 GUID 9자리 (변경 없음)
    private final HostAddressProvider host;
    private final IntFunction<String> randomDigits; // 섹션용 길이 인자 난수
    private final List<EaiPayloadSection> sections;          // 개별부 전략 레지스트리
    private final Charset cs;

    public EaiMessageBuilder(EaiProperties props, Clock clock, Supplier<String> guidRandom,
                             HostAddressProvider host,
                             IntFunction<String> randomDigits,
                             List<EaiPayloadSection> sections) {
        this.props = props;
        this.clock = clock;
        this.guidRandom = guidRandom;
        this.host = host;
        this.randomDigits = randomDigits;
        this.sections = sections;
        this.cs = Charset.forName(props.charset());
    }

    /** 표준전문(param01~08) 전체를 조립해 charset 바이트로 반환. */
    public byte[] build(EaiRequest req) {
        EaiPayload payload = req.payload();
        EaiPayloadSection section = sections.stream()
                .filter(s -> s.supports(payload))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 EAI 페이로드: " + payload.getClass().getSimpleName()));
        EaiSectionContext ctx = new EaiSectionContext(cs, props, this::date, randomDigits);

        String p01 = param01();
        String p02 = param02(section.systemCode(), req.ifId());
        String p03 = param03();
        String p04 = param04();
        String p05 = param05();
        String p06 = param06();
        String p07 = section.build(payload, ctx);
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
        p.append(lpad(cs, "N", 8, ""));  // WHL_TGR_LEN    전체전문길이 (조립 후 교체)
        p.append(lpad(cs, "N", 8, ""));  // HER_LEN        헤더길이 (조립 후 교체)
        p.append(lpad(cs, "N", 8, ""));  // PRO_MDA_LEN    출력매체부길이 (조립 후 교체)
        p.append("1.0");                 // TGR_VRS_INF    전문버전정보
        p.append("ko");                  // MLAN_TC        다국어구분코드
        p.append(props.sysEnvTc());      // SYS_ENV_TC     시스템환경구분코드 (운영 P / 그 외 L)
        p.append(ipAddr);                // IP_ADDR        IP주소 (40자리)
        p.append(macAddr);               // MAC_ADDR       MAC주소 (12자리)
        p.append(guid);                  // GUID           GUID
        p.append("0001");                // GUID_PRG_SNO   GUID진행일련번호
        p.append(guid);                  // FST_GUID       최초GUID
        p.append(props.fwdiSysC());      // FWDI_SYS_C     전송시스템코드
        p.append(props.fwdiSysC());      // FST_FWDI_SYS_C 최초전송시스템코드
        p.append(lpad(cs, "C", 12, "")); // SYS_CO_RSRV    시스템공통부 예비
        return p.toString();
    }

    // ── 02. 거래공통부 ───────────────────────────────────────────────────────
    private String param02(String rmsSysC, String ifId) {
        String reqDtm = date("yyyyMMddHHmmssSSS");
        String trSlsDt = date("yyyyMMdd");
        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "C", 10, ""));             // TR_ID                 거래 ID
        p.append(lpad(cs, "C", 3, rmsSysC));         // RMS_SYS_C             수신시스템코드 (UMS)
        p.append(lpad(cs, "C", 10, ""));             // SRE_ID                화면ID
        p.append(lpad(cs, "C", 10, ""));             // LKG_SRE_ID            연계화면ID
        p.append(lpad(cs, "C", 1, ""));              // SRE_CNTR_TC           화면제어구분코드
        p.append("Q");                               // REQ_RPD_TC            요청/응답구분코드 (Q)
        p.append("2");                               // DTLS_TP_TC            세부유형구분코드
        p.append("TR");                              // CHN_TP_C              채널유형코드 (TR)
        p.append(lpad(cs, "C", 2, ""));              // MSG_CHN_C             메시지채널코드
        p.append("S");                               // SYNC_PRC_TC           동기처리구분코드 (S)
        p.append(lpad(cs, "C", 1, ""));              // RLT_TC                결과구분코드
        p.append("00000");                           // PAGE_ROW_COUNT        원페이지당조회건수
        p.append(lpad(cs, "C", 1, ""));              // NEXT_PAGE_YN          다음페이지여부
        p.append("00000");                           // REQ_PAGE_NO           요청페이지번호
        p.append(reqDtm);                            // REQ_DTM               요청일시 (yyyyMMddHHmmssSSS)
        p.append(lpad(cs, "C", 17, ""));             // RPD_DTM               응답일시
        p.append(trSlsDt);                           // TR_SLS_DT             거래영업일자 (yyyyMMdd)
        p.append("0");                               // TR_SLS_DT_TC          거래영업일자구분코드
        p.append("0");                               // SML_TC                시뮬레이션구분코드
        p.append(lpad(cs, "C", 8, ""));              // SML_SLS_YMD           시뮬레이션영업년월일
        p.append("N");                               // MSK_CCC_YN            마스킹해지여부
        p.append("N");                               // ATH_TES_ALY_YN        권한테스트적용여부
        p.append("N");                               // LQN_TR_YN             대량거래여부
        p.append(lpad(cs, "C", 1, ""));              // RE_TR_FLAG            재거래플래그
        p.append("01");                              // CSG_TC                마감전후구분코드
        p.append("10");                              // FSC_DT_BSE_TC         회계일자기준구분코드
        p.append(lpad(cs, "C", 3, ""));              // BLGT_BBR_C            소속부점코드
        p.append(lpad(cs, "C", 14, ""));             // USID                  사용자ID
        p.append(lpad(cs, "C", 4, ""));              // PSC_C                 직급코드
        p.append(lpad(cs, "C", 4, ""));              // DTS_C                 직무코드
        p.append(lpad(cs, "C", 3, ""));              // TMN_ITL_BBR_C         단말설치부점코드
        p.append(lpad(cs, "C", 10, ""));             // TMN_NO                단말번호
        p.append(lpad(cs, "C", 3, ""));              // AAP_BBR_C             대행부점코드
        p.append(lpad(cs, "C", 10, ""));             // NML_PRC_RMS_TR_ID     정상처리수신거래ID
        p.append(lpad(cs, "C", 10, ""));             // FRM_ERR_RMS_TR_ID     포멧오류수신거래ID
        p.append(lpad(cs, "C", 10, ""));             // NRPD_RMS_TR_ID        무응답수신거래ID
        p.append(lpad(cs, "C", 4, ""));              // FOOE_IST_C            대외기관코드
        p.append(lpad(cs, "C", 4, ""));              // FOOE_NEK_TC           대외망구분코드
        p.append(lpad(cs, "C", 3, ""));              // FOOE_HED_TP_TC        대외헤더유형구분코드
        p.append(lpad(cs, "C", 8, ""));              // FOOE_REQ_BYCL_C       대외요청종별코드
        p.append(lpad(cs, "C", 20, ""));             // FOOE_REQ_TR_TC        대외요청거래구분코드
        p.append(lpad(cs, "C", 1, ""));              // ONLD_CNTR_TP_TC       온렌딩제어구분코드
        p.append(lpad(cs, "C", 4, ""));              // ADN_FOOE_IST_CD       부가대외기관코드
        p.append(lpad(cs, "C", 12, ifId));           // IF_ID                 인터페이스ID
        p.append("00");                              // EAI_FWDI_SVR_NO       EAI전송서버번호
        p.append("11");                              // MCI_FWDI_SVR_NO       MCI전송서버번호
        p.append(lpad(cs, "C", 10, ""));             // MCI_SES_ID            MCI세션ID
        p.append(lpad(cs, "C", 10, ""));             // CC_ID                 센터컷ID
        p.append(lpad(cs, "C", 8, ""));              // CC_PRC_DT             센터컷처리일자
        p.append("00000");                           // CC_PRC_TO             센터컷처리회차
        p.append("000000000");                       // CC_TD_SNO             센터컷회차별일련번호
        p.append("00");                              // CC_PRC_TC             센터컷처리구분코드
        p.append(lpad(cs, "C", 10, ""));             // CC_CALL_TR_ID         센터컷호출거래ID
        p.append(lpad(cs, "C", 1, ""));              // RSD_CC_YN             상주센터컷여부
        p.append(lpad(cs, "C", 10, ""));             // DTL_CC_ID             상세CC_ID
        p.append(lpad(cs, "C", 2, ""));              // CC_PRC_RLT_C          센터컷처리결과코드
        p.append("0000000000000000.000");            // CC_ACL_TFR_AMT        센터컷실제이체금액
        p.append("0000000000000000.000");            // CC_TFR_TGT_AMT        센터컷이체대상금액
        p.append(lpad(cs, "C", 1, ""));              // CC_XTR_DETS_RE_ROM_YN 센터컷별도예금재입금여부
        p.append(lpad(cs, "C", 8, ""));              // CST_NO                고객번호
        p.append(lpad(cs, "C", 40, ""));             // TR_CO_RSRV            거래공통부 예비
        return p.toString();
    }

    // ── 03. 채널공통부 ───────────────────────────────────────────────────────
    private String param03() {
        StringBuilder p = new StringBuilder();
        p.append("2");                    // BKB_TC            통장구분코드
        p.append(lpad(cs, "C", 20, ""));  // BKB_CWT_NO        통장증서번호
        p.append(lpad(cs, "C", 129, "")); // CSF_CHN_RSRV      대면채널예비
        p.append(lpad(cs, "C", 2, ""));   // NFF_CNC_MDA_KD_C  비대면접속매체종류코드
        p.append("00");                   // CHN_TP_ADN_C      채널유형부가코드
        p.append(lpad(cs, "C", 64, ""));  // NFF_CNC_MCN_ID    비대면접속기기ID
        p.append(lpad(cs, "C", 2, ""));   // NFF_ISO_NTN_SYM_C 비대면ISO국가기호코드
        p.append(lpad(cs, "C", 5, ""));   // NFF_CIR_NO        비대면회선번호
        p.append(lpad(cs, "C", 4, ""));   // NFF_CHN_BZ_TC     비대면채널업무구분코드
        p.append(lpad(cs, "C", 10, ""));  // IB_SRE_MNU_ID     인터넷뱅킹화면메뉴ID
        p.append(lpad(cs, "C", 3, ""));   // TELB_SVC_C        텔레뱅킹서비스코드
        p.append(lpad(cs, "C", 1, ""));   // XP_LGN_TR_YN      비로그인거래여부
        p.append(lpad(cs, "C", 1, ""));   // NFF_CST_TC        비대면고객구분코드
        p.append(lpad(cs, "C", 8, ""));   // USR_NO            사용자번호
        p.append(lpad(cs, "C", 1, ""));   // ADMR_ATH_YN       관리인권한여부
        p.append(lpad(cs, "C", 2, ""));   // SECT_MDA_TC       보안매체구분코드
        p.append(lpad(cs, "C", 1, ""));   // CFA_KPN_MDA_TC    인증서저장매체구분코드
        p.append(lpad(cs, "C", 2, ""));   // MCT_TC            주계약구분코드
        p.append(lpad(cs, "C", 1, ""));   // USR_CER_MANR_C    사용자인증방법코드
        p.append("N");                    // CSR_TPR_CNFM_FLAG 상담원본인확인플래그
        p.append(lpad(cs, "C", 38, ""));  // CSR_TPR_CNFM_NO   상담원본인확인번호
        p.append(lpad(cs, "C", 102, "")); // NFF_CHN_RSRV      비대면채널예비
        return p.toString();
    }

    // ── 04. 책임자승인공통부 ─────────────────────────────────────────────────
    private String param04() {
        StringBuilder p = new StringBuilder();
        p.append("00");                  // RSPR_APV_STS_TC   책임자승인상태구분코드
        p.append("00");                  // RSPR_APV_LEV_NBR  책임자승인레벨수
        p.append(lpad(cs, "C", 2, ""));  // RSPR_TR_TC        책임자거래구분코드
        p.append("00");                  // RSPR_APV_MSG_CNT  책임자승인메시지건수
        p.append("00");                  // RSPR_CNT          책임자건수
        p.append("00");                  // RSPR_APV_DKEY_CNT 책임자승인문서키건수
        p.append("00");                  // APV_RSPR_CNT      승인책임자건수
        p.append(lpad(cs, "C", 18, "")); // APV_TR_SRE_DKEY   승인거래화면문서키
        p.append(lpad(cs, "C", 29, "")); // RSPR_APV_RSRV     책임자승인예비
        p.append("00");                  // IDFC_MNG_CNT      신분증관리건수
        return p.toString();
    }

    // ── 05. 메시지공통부 ─────────────────────────────────────────────────────
    private String param05() {
        StringBuilder p = new StringBuilder();
        p.append(lpad(cs, "C", 1, ""));  // MSG_IDCT_TC     메시지표시방법구분코드
        p.append(lpad(cs, "C", 50, "")); // ERR_OCC_TGR_ITM 오류발생전문항목
        p.append("000");                 // MSG_CNT         메시지건수
        p.append("00");                  // ETC_PRO_DAT_CNT 기타출력데이터건수
        return p.toString();
    }

    // ── 06. 출력매체부 ───────────────────────────────────────────────────────
    private String param06() {
        return "000"; // PRO_MDA_CNT 출력매체건수
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
