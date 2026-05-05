package com.kdb.it.domain.budget.work.repository;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.project.entity.Bitemm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 예산(BBUGTM) QueryDSL 커스텀 리포지토리 인터페이스
 *
 * <p>
 * 결재완료 필터링 + 비목 접두어 매칭 등 복잡한 동적 쿼리를 정의합니다.
 * 기존 {@code ProjectRepositoryImpl}, {@code CostRepositoryImpl}의
 * CAPPLA+CAPPLM 서브쿼리 패턴을 재사용합니다.
 * </p>
 *
 * // Design Ref: §4.6 — BbugtmRepositoryCustom (QueryDSL)
 */
public interface BbugtmRepositoryCustom {

    /**
     * 결재완료 전산업무비(BCOSTM) 중 비목코드가 접두어와 매칭되는 목록 조회
     *
     * <p>
     * [조건]
     * 1. BCOSTM.DEL_YN = 'N' AND LST_YN = 'Y'
     * 2. BCOSTM.IOE_C LIKE '접두어%'
     * 3. CAPPLA-CAPPLM JOIN으로 최신 신청서의 APF_STS = '결재완료'
     * </p>
     *
     * @param prefix 편성비목 접두어 (예: "237")
     * @return 결재완료된 전산업무비 목록
     */
    List<Bcostm> findApprovedCostsByPrefix(String prefix, String bgYy);

    /**
     * 결재완료 품목(BITEMM) 중 품목구분이 접두어와 매칭되는 목록 조회
     *
     * <p>
     * [조건]
     * 1. BITEMM.DEL_YN = 'N' AND LST_YN = 'Y'
     * 2. BITEMM.GCL_DTT LIKE '접두어%'
     * 3. BITEMM의 상위 BPROJM이 결재완료 상태
     *    (CAPPLA.ORC_TB_CD = 'BPROJM' → 최신 CAPPLM.APF_STS = '결재완료')
     * 4. BPROJM.BG_YY = :bgYy
     * </p>
     *
     * @param prefix 편성비목 접두어 (예: "237")
     * @param bgYy   예산연도 (예: 2026)
     * @return 결재완료된 품목 목록
     */
    List<Bitemm> findApprovedItemsByPrefix(String prefix, String bgYy);

    /**
     * 정보화사업(BPROJM)별 편성예산(DUP_BG) 합계 일괄 조회
     *
     * <p>
     * 정보화사업 편성은 품목(BITEMM) 단위로 저장되므로(ORC_TB='BITEMM'),
     * BBUGTM을 BITEMM과 JOIN하여 BITEMM.PRJ_MNG_NO 기준으로 SUM(DUP_BG)를 집계합니다.
     * </p>
     *
     * @param prjMngNos 조회할 프로젝트관리번호 목록
     * @param bgYy      예산연도 (YYYY)
     * @return prjMngNo → SUM(dupBg) 맵
     */
    Map<String, BigDecimal> sumDupBgByPrjMngNos(List<String> prjMngNos, String bgYy);

    /**
     * 전산업무비(BCOSTM)별 편성예산(DUP_BG) 합계 일괄 조회
     *
     * <p>
     * TAAABB_BBUGTM에서 ORC_TB='BCOSTM' 조건으로 itMngcNo별 SUM(DUP_BG)를 집계합니다.
     * </p>
     *
     * @param itMngcNos 조회할 전산관리비관리번호 목록
     * @param bgYy      예산연도 (YYYY)
     * @return itMngcNo → SUM(dupBg) 맵
     */
    Map<String, BigDecimal> sumDupBgByItMngcNos(List<String> itMngcNos, String bgYy);

    /**
     * 정보화사업별 자본예산 편성예산(DUP_BG) 합계 조회
     *
     * <p>
     * BBUGTM JOIN BITEMM, gclDtt ∈ assetGclDttCodes(공통코드 IOE_CPIT)인 품목의 DUP_BG를
     * PRJ_MNG_NO 기준으로 집계합니다.
     * </p>
     *
     * @param prjMngNos        프로젝트관리번호 목록
     * @param bgYy             예산연도
     * @param assetGclDttCodes 자본예산 품목구분 코드 집합 (IOE_CPIT 계열)
     * @return prjMngNo → SUM(dupBg) 맵
     */
    Map<String, BigDecimal> sumAssetDupBgByPrjMngNos(List<String> prjMngNos, String bgYy, Set<String> assetGclDttCodes);

    /**
     * 정보화사업별 일반관리비 편성예산(DUP_BG) 합계 조회
     *
     * <p>
     * BBUGTM JOIN BITEMM, gclDtt ∈ costGclDttCodes(공통코드 IOE_IDR/SEVS/XPN/LEAFE)인 품목의 DUP_BG를
     * PRJ_MNG_NO 기준으로 집계합니다.
     * </p>
     *
     * @param prjMngNos       프로젝트관리번호 목록
     * @param bgYy            예산연도
     * @param costGclDttCodes 일반관리비 품목구분 코드 집합
     * @return prjMngNo → SUM(dupBg) 맵
     */
    Map<String, BigDecimal> sumCostDupBgByPrjMngNos(List<String> prjMngNos, String bgYy, Set<String> costGclDttCodes);

    /**
     * 비목 접두어별 결재완료 요청금액 합계 조회
     *
     * <p>
     * 편성비목 조회(API-01) 시 각 비목별 결재완료 요청금액을 집계합니다.
     * BCOSTM.IT_MNGC_BG + BITEMM.GCL_AMT를 접두어별로 SUM합니다.
     * </p>
     *
     * @param prefix 편성비목 접두어 (예: "237")
     * @param bgYy   예산연도 (예: 2026)
     * @return 해당 접두어의 결재완료 요청금액 합계
     */
    BigDecimal sumApprovedAmountByPrefix(String prefix, String bgYy);
}
