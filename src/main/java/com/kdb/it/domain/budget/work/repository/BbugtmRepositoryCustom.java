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
     * 결재완료 전산업무비(BCOSTM) 중 비목코드가 ioeCValues 집합에 속하는 목록 조회
     *
     * <p>
     * CCODEM 마이그레이션(V003) 이후 BCOSTM.IOE_C는 단축 cdva 값("001" 등)을 저장하므로
     * LIKE 접두어 매칭 대신 IN 조건을 사용합니다.
     * </p>
     *
     * @param ioeCValues 해당 편성비목에 속하는 IOE cdva 값 집합 (예: {"001", "002"})
     * @param bgYy       예산연도
     * @return 결재완료된 전산업무비 목록
     */
    List<Bcostm> findApprovedCostsByIoeCValues(Set<String> ioeCValues, String bgYy);

    /**
     * 결재완료 품목(BITEMM) 중 비목코드가 ioeCValues 집합에 속하는 목록 조회
     *
     * <p>
     * CCODEM 마이그레이션(V003) 이후 BITEMM.IOE_C는 단축 cdva 값을 저장하므로
     * LIKE 접두어 매칭 대신 IN 조건을 사용합니다.
     * </p>
     *
     * @param ioeCValues 해당 편성비목에 속하는 IOE cdva 값 집합
     * @param bgYy       예산연도
     * @return 결재완료된 품목 목록
     */
    List<Bitemm> findApprovedItemsByIoeCValues(Set<String> ioeCValues, String bgYy);

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
     * 비목 ioeCValues 집합별 결재완료 요청금액 합계 조회
     *
     * <p>
     * 편성비목 조회(API-01) 시 각 비목별 결재완료 요청금액을 집계합니다.
     * BCOSTM.IT_MNGC_BG + BITEMM.GCL_AMT * XCR 를 ioeCValues 기준으로 SUM합니다.
     * </p>
     *
     * @param ioeCValues 해당 편성비목에 속하는 IOE cdva 값 집합
     * @param bgYy       예산연도
     * @return 결재완료 요청금액 합계 (집합이 비어있으면 ZERO)
     */
    BigDecimal sumApprovedAmountByIoeCValues(Set<String> ioeCValues, String bgYy);
}
