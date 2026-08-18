package com.kdb.it.infra.eai.service;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * KDB 표준전문 공통부 레이아웃 — 필드명·순서·길이 표.
 *
 * <p>{@link EaiMessageBuilder}가 {@code param01}~{@code param05}에서 조립하는 순서와 동일한 표를 한곳에 모아, 오프셋을 상수로
 * 흩어 적지 않고 누적 합으로 유도한다. 진단 로그({@link EaiWireLogger})가 전문을 필드 단위로 해석할 때 쓴다.
 *
 * <p>표가 실제 전문과 어긋나면 동결 골든 전문과 대조하는 {@code EaiStandardLayoutTest}가 먼저 깨진다.
 */
final class EaiStandardLayout {

    /**
     * 표준전문 필드 하나.
     *
     * @param name 전문 규격상의 필드명
     * @param offset 전문 선두 기준 절대 바이트 위치
     * @param length 바이트 길이
     */
    record Field(String name, int offset, int length) {

        /** 전문이 이 필드를 담을 만큼 긴지 여부. */
        boolean fitsIn(byte[] message) {
            return message != null && offset + length <= message.length;
        }

        /**
         * 필드 값을 읽어 좌우 패딩을 제거합니다.
         *
         * @param message 전문 바이트
         * @param charset 전문 문자셋
         * @return 패딩 제거한 값. 전문이 짧아 필드가 없으면 {@link Optional#empty()}
         */
        Optional<String> read(byte[] message, Charset charset) {
            if (!fitsIn(message)) {
                return Optional.empty();
            }
            return Optional.of(new String(message, offset, length, charset).trim());
        }
    }

    /** 길이 신고 필드(전체전문길이/헤더길이/출력매체부길이) 하나의 바이트 길이. */
    static final int LEN_FIELD = 8;

    /** 시스템공통부 필드 표 (합 180바이트). */
    static final List<Field> SYSTEM_COMMON;

    /** 거래공통부 필드 표 (합 400바이트). */
    static final List<Field> TRANSACTION_COMMON;

    /** 메시지공통부 필드 표 (합 56바이트). */
    static final List<Field> MESSAGE_COMMON;

    /** 채널공통부 길이 — 진단 가치가 없어 필드로 펼치지 않고 블록 길이만 둔다. */
    static final int CHANNEL_COMMON_LEN = 400;

    /** 책임자승인공통부 길이 — 채널공통부와 같은 이유로 블록 길이만 둔다. */
    static final int APPROVAL_COMMON_LEN = 63;

    /** 출력매체부 길이 — 출력매체건수 3. */
    static final int PRO_MDA_PART_LEN = 3;

    /** 공통부 전체(=헤더) 길이. 개별부는 이 뒤에 붙는다. */
    static final int HEADER_LEN;

    static {
        Cursor sys = new Cursor(0);
        sys.add("WHL_TGR_LEN", LEN_FIELD) // 전체전문길이
                .add("HER_LEN", LEN_FIELD) // 헤더길이
                .add("PRO_MDA_LEN", LEN_FIELD) // 출력매체부길이
                .add("TGR_VRS_INF", 3) // 전문버전정보
                .add("MLAN_TC", 2) // 다국어구분코드
                .add("SYS_ENV_TC", 1) // 시스템환경구분코드
                .add("IP_ADDR", 40) // IP주소
                .add("MAC_ADDR", 12) // MAC주소
                .add("GUID", 38) // GUID
                .add("GUID_PRG_SNO", 4) // GUID진행일련번호
                .add("FST_GUID", 38) // 최초GUID
                .add("FWDI_SYS_C", 3) // 전송시스템코드
                .add("FST_FWDI_SYS_C", 3) // 최초전송시스템코드
                .add("SYS_CO_RSRV", 12); // 시스템공통부 예비
        SYSTEM_COMMON = sys.done();

        Cursor tr = new Cursor(sys.offset());
        tr.add("TR_ID", 10) // 거래ID
                .add("RMS_SYS_C", 3) // 수신시스템코드
                .add("SRE_ID", 10) // 화면ID
                .add("LKG_SRE_ID", 10) // 연계화면ID
                .add("SRE_CNTR_TC", 1) // 화면제어구분코드
                .add("REQ_RPD_TC", 1) // 요청응답구분코드
                .add("DTLS_TP_TC", 1) // 세부유형구분코드
                .add("CHN_TP_C", 2) // 채널유형코드
                .add("MSG_CHN_C", 2) // 메시지채널코드
                .add("SYNC_PRC_TC", 1) // 동기처리구분코드
                .add("RLT_TC", 1) // 결과구분코드
                .add("PAGE_ROW_COUNT", 5) // 원페이지당조회건수
                .add("NEXT_PAGE_YN", 1) // 다음페이지여부
                .add("REQ_PAGE_NO", 5) // 요청페이지번호
                .add("REQ_DTM", 17) // 요청일시
                .add("RPD_DTM", 17) // 응답일시
                .add("TR_SLS_DT", 8) // 거래영업일자
                .add("TR_SLS_DT_TC", 1) // 거래영업일자구분코드
                .add("SML_TC", 1) // 시뮬레이션구분코드
                .add("SML_SLS_YMD", 8) // 시뮬레이션영업년월일
                .add("MSK_CCC_YN", 1) // 마스킹해지여부
                .add("ATH_TES_ALY_YN", 1) // 권한테스트적용여부
                .add("LQN_TR_YN", 1) // 대량거래여부
                .add("RE_TR_FLAG", 1) // 재거래플래그
                .add("CSG_TC", 2) // 마감전후구분코드
                .add("FSC_DT_BSE_TC", 2) // 회계일자기준구분코드
                .add("BLGT_BBR_C", 3) // 소속부점코드
                .add("USID", 14) // 사용자ID
                .add("PSC_C", 4) // 직급코드
                .add("DTS_C", 4) // 직무코드
                .add("TMN_ITL_BBR_C", 3) // 단말설치부점코드
                .add("TMN_NO", 10) // 단말번호
                .add("AAP_BBR_C", 3) // 대행부점코드
                .add("NML_PRC_RMS_TR_ID", 10) // 정상처리수신거래ID
                .add("FRM_ERR_RMS_TR_ID", 10) // 포멧오류수신거래ID
                .add("NRPD_RMS_TR_ID", 10) // 무응답수신거래ID
                .add("FOOE_IST_C", 4) // 대외기관코드
                .add("FOOE_NEK_TC", 4) // 대외망구분코드
                .add("FOOE_HED_TP_TC", 3) // 대외헤더유형구분코드
                .add("FOOE_REQ_BYCL_C", 8) // 대외요청종별코드
                .add("FOOE_REQ_TR_TC", 20) // 대외요청거래구분코드
                .add("ONLD_CNTR_TP_TC", 1) // 온렌딩제어구분코드
                .add("ADN_FOOE_IST_CD", 4) // 부가대외기관코드
                .add("IF_ID", 12) // 인터페이스ID
                .add("EAI_FWDI_SVR_NO", 2) // EAI전송서버번호
                .add("MCI_FWDI_SVR_NO", 2) // MCI전송서버번호
                .add("MCI_SES_ID", 10) // MCI세션ID
                .add("CC_ID", 10) // 센터컷ID
                .add("CC_PRC_DT", 8) // 센터컷처리일자
                .add("CC_PRC_TO", 5) // 센터컷처리회차
                .add("CC_TD_SNO", 9) // 센터컷회차별일련번호
                .add("CC_PRC_TC", 2) // 센터컷처리구분코드
                .add("CC_CALL_TR_ID", 10) // 센터컷호출거래ID
                .add("RSD_CC_YN", 1) // 상주센터컷여부
                .add("DTL_CC_ID", 10) // 상세CC_ID
                .add("CC_PRC_RLT_C", 2) // 센터컷처리결과코드
                .add("CC_ACL_TFR_AMT", 20) // 센터컷실제이체금액
                .add("CC_TFR_TGT_AMT", 20) // 센터컷이체대상금액
                .add("CC_XTR_DETS_RE_ROM_YN", 1) // 센터컷별도예금재입금여부
                .add("CST_NO", 8) // 고객번호
                .add("TR_CO_RSRV", 40); // 거래공통부 예비
        TRANSACTION_COMMON = tr.done();

        Cursor msg = new Cursor(tr.offset() + CHANNEL_COMMON_LEN + APPROVAL_COMMON_LEN);
        msg.add("MSG_IDCT_TC", 1) // 메시지표시방법구분코드
                .add("ERR_OCC_TGR_ITM", 50) // 오류발생전문항목
                .add("MSG_CNT", 3) // 메시지건수
                .add("ETC_PRO_DAT_CNT", 2); // 기타출력데이터건수
        MESSAGE_COMMON = msg.done();

        HEADER_LEN = msg.offset() + PRO_MDA_PART_LEN;
    }

    private EaiStandardLayout() {}

    /**
     * 필드명으로 표에서 필드를 찾습니다.
     *
     * @param section 필드 표
     * @param name 필드명
     * @return 해당 필드
     * @throws IllegalArgumentException 표에 없는 필드명일 때
     */
    static Field field(List<Field> section, String name) {
        return section.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("표준전문 필드 없음: " + name));
    }

    /** 필드 표를 누적 오프셋으로 만드는 조립 커서. */
    private static final class Cursor {

        private final List<Field> fields = new ArrayList<>();
        private int offset;

        Cursor(int start) {
            this.offset = start;
        }

        Cursor add(String name, int length) {
            fields.add(new Field(name, offset, length));
            offset += length;
            return this;
        }

        int offset() {
            return offset;
        }

        List<Field> done() {
            return List.copyOf(fields);
        }
    }
}
