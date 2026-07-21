package com.kdb.it.common.code;

import com.kdb.it.common.code.entity.Ccodem;
import java.util.Set;

/**
 * 비목(IOE) 분류 공통 헬퍼
 *
 * <p>비목코드의 자본예산/일반관리비 판별과 중분류 그룹명 해석을 제공합니다. 예산작업(BudgetWorkService), 사업(ProjectService), 정보기술부문 예산
 * 조회(ItBudget)가 동일한 분류 기준을 공유하기 위한 단일 진실 공급원입니다.
 */
public final class IoeCategories {

    private IoeCategories() {}

    /** 자본예산 세부 코드타입: 개발비/기계장치/기타무형자산/구 자본예산코드 */
    public static final Set<String> CAPITAL_CTPS =
            Set.of("IOE_DVC", "IOE_HW", "IOE_SW", "IOE_CPIT");

    /**
     * 코드타입이 자본예산 계열인지 판별합니다.
     *
     * @param cTp 비목코드의 코드타입 (null이면 false)
     * @return true이면 자본예산, false이면 일반관리비
     */
    public static boolean isCapitalCTp(String cTp) {
        if (cTp == null) return false;
        return CAPITAL_CTPS.contains(cTp);
    }

    /**
     * 비목코드의 중분류 그룹명을 해석합니다.
     *
     * <p>C_TP_DES를 우선 사용하고, 없으면 CDVA_DTL 계층("대분류 - 중분류 - 세부")의 중분류를 사용하며, 그마저 없으면 CDVA_DES로 대체합니다.
     *
     * @param code 비목 공통코드 (null 아님)
     * @return 그룹명. 해석할 근거가 없으면 null일 수 있습니다.
     */
    public static String resolveGroupName(Ccodem code) {
        if (code.getCTpDes() != null && !code.getCTpDes().isBlank()) {
            return code.getCTpDes();
        }
        String detail = code.getCdvaDtl();
        if (detail != null) {
            String[] parts = detail.split(" - ");
            if (parts.length >= 2) return parts[1].trim();
        }
        return code.getCdvaDes();
    }
}
