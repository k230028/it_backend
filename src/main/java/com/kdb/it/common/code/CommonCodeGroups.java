package com.kdb.it.common.code;

/**
 * 공통코드 그룹ID 상수. CCODEM(TPRMPP_CCODEM)의 CO_C_ID와 1:1 대응한다.
 *
 * <p>2026-06-06 공통코드 마이그레이션으로 그룹ID가 신규 체계로 변경되었다.
 * 인라인 문자열 리터럴 대신 본 상수를 참조한다.</p>
 */
public final class CommonCodeGroups {

    private CommonCodeGroups() {
    }

    /** 단말기서비스 (구 TMN_USG/TMN_KD 병합) */
    public static final String TERM_SERVICE = "IT_PTL_TMN_SVC_TC";
    /** 단말기이용방식 (구 TMN_MAGR) */
    public static final String TERM_KIND = "IT_PTL_TMN_KD_TC";
    /** 기술분야 (구 TCHN_TP → IT_PTL_TCHN_TP_TC, 그룹ID 통일) */
    public static final String TECH_TYPE = "SKL_FLD";
    /** 발송구분코드 (구 SD) */
    public static final String SEND_DTT = "SD_TC";
    /** 보고상태 (구 RPR_STS) */
    public static final String REPORT_STS = "IT_PTL_RPR_STS_TC";
    /** 추진가능성 (구 PRJ_PUL_PTT) */
    public static final String EXE_POSSIBLE = "EXE_PTT_YN";
    /** 고객유형/주요사용자 (구 MN_USR) */
    public static final String MAIN_USER = "CST_TP_TC";
    /** 단말여부 (구 IT_MNGC_TP, 값 001→0/002→1) */
    public static final String TMN_YN = "TMN_YN";
    /** 비목코드 (구 IOE) */
    public static final String IOE = "IOE_C";
    /** 알림서비스 (구 INFM_SVC) */
    public static final String INFM_SVC = "INFM_SVC_TC";
    /** 전결권 (구 EDRT_MNGC/EDRT_CPIT 병합) */
    public static final String EDRT = "IT_PTL_EDRT_TC";
    /** 지급주기코드 (구 DFR_CLE) */
    public static final String DFR_CLE = "DFR_CLE_C";
    /** 통화코드 (구 CUR) */
    public static final String CURRENCY = "CUR_C";
    /** 신청서진행상태코드 (구 APF_STS, 값 01~04→1~4) */
    public static final String APF_STS = "APF_PRG_STS_C";
    /** 예산단위사업코드 (구 ABUS_C, 값 01→501) */
    public static final String ABUS_UNIT = "BG_UNT_ABUS_C";
    /** 사업구분코드 (구 PUL_DTT, 값 001/002→01/02) */
    public static final String ABUS = "ABUS_TC";

    // 사업/업무 관련 그룹 — 상수 집약용
    /** 사업유형 (구 PRJ_TP → ABUS_PPO, 그룹ID 통일) */
    public static final String PRJ_TYPE = "ABUS_PPO";
    /** 업무구분 (불변) */
    public static final String BZ_DTT = "BZ_DTT";
    /** 예산 신청기간 (불변) */
    public static final String BUDGET_RQS = "BG_RQS";
}
