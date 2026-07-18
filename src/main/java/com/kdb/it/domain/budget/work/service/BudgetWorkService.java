package com.kdb.it.domain.budget.work.service;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.IoeCategories;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.budget.work.repository.BudgetWorkQueryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 예산 작업 서비스
 *
 * <p>
 * 편성비목 조회, 편성률 일괄 적용, 편성 결과 조회 등
 * 예산 편성 작업(TPRMPP_BBUGTM)의 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * [핵심 알고리즘]
 * 1. CCODEM에서 CTT_TP='DUP_IOE'인 편성비목 코드 조회
 * 2. 각 비목의 접두어로 결재완료 BCOSTM/BITEMM 매칭
 * 3. 요청금액 × (편성률/100) = 편성금액 계산
 * 4. BBUGTM Upsert (기존 존재하면 UPDATE, 없으면 INSERT)
 * </p>
 *
 * // Design Ref: §4.4 — BudgetWorkService 핵심 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetWorkService {

    /** 예산 데이터 접근 리포지토리 (TPRMPP_BBUGTM) */
    private final BbugtmRepository bbugtmRepository;

    /** 공통코드 리포지토리 (TPRMPP_CCODEM): 편성비목(DUP_IOE) 조회용 */
    private final CodeRepository codeRepository;

    /** 결재완료 원본 집계 쿼리 리포지토리: getSummary N+1 제거용 (DB-01) */
    private final BudgetWorkQueryRepository budgetWorkQueryRepository;

    /** 정보화사업 리포지토리 (TPRMPP_BPROJM): 사업명 조회용 */
    private final ProjectRepository projectRepository;

    /** 품목 리포지토리 (TPRMPP_BITEMM): 품목→프로젝트 매핑용 */
    private final ProjectItemRepository projectItemRepository;

    /** 전산업무비 리포지토리 (TPRMPP_BCOSTM): 계약명 조회용 */
    private final CostRepository costRepository;

    /** 현재 인증 사용자 사번 제공 (벌크 UPDATE 감사컬럼 LST_CHG_USID 세팅용) */
    private final AuditorAware<String> auditorAware;

    /**
     * 편성비목 목록 조회 (API-01)
     *
     * <p>
     * CCODEM에서 CTT_TP='DUP_IOE'인 편성비목을 조회하고,
     * 각 비목별 결재완료 요청금액 합계와 기존 편성률을 함께 반환합니다.
     * </p>
     *
     * [처리 순서]
     * 1. CCODEM에서 CTT_TP = 'DUP_IOE' 조회
     * 2. 각 비목별 접두어 추출 (DUP-IOE-237 → "237")
     * 3. 결재완료 원본 데이터에서 접두어 매칭 금액 합계 조회
     * 4. 기존 BBUGTM에서 편성률 조회
     *
     * @param bgYy 예산년도
     * @return 편성비목 목록 (코드ID, 코드명, 접두어, 기존 편성률, 요청금액 합계)
     */
    public List<BudgetWorkDto.IoeCategoryResponse> getIoeCategories(String bgYy) {
        // 1. 편성비목 코드 조회 (CTT_TP = 'DUP_IOE')
        List<Ccodem> ioeCodes = findCodes("DUP_IOE");

        // 기존 BBUGTM 데이터 조회 (편성률 확인용)
        List<Bbugtm> existingBudgets = bbugtmRepository.findByBseYyAndDelYn(bgYy, "N");

        // V003 마이그레이션 후 IOE_C는 단축 cdva("001" 등)를 저장하므로
        // DUP_IOE 접두어("237") → 해당하는 IOE cdva 집합 매핑을 빌드
        List<Ccodem> allIoeCodes = findCodes(CommonCodeGroups.IOE);
        Map<String, Set<String>> prefixToIoeCValues = buildPrefixToIoeCValuesMap(allIoeCodes);

        return ioeCodes.stream().map(code -> {
            String prefix = extractPrefix(code.getCdva());
            Set<String> ioeCValues = prefixToIoeCValues.getOrDefault(prefix, Set.of());

            // 2. 결재완료 요청금액 합계 (IN 조건 기반)
            BigDecimal requestAmount = bbugtmRepository.sumApprovedAmountByIoeCValues(ioeCValues, bgYy);
            if (requestAmount == null) {
                requestAmount = BigDecimal.ZERO;
            }

            // 3. 기존 편성률 조회 (ioeC IN ioeCValues 기반)
            Integer dupRt = existingBudgets.stream()
                    .filter(b -> b.getIoeC() != null && ioeCValues.contains(b.getIoeC()))
                    .map(value -> value.getAsgRt())
                    .findFirst()
                    .orElse(null);

            return new BudgetWorkDto.IoeCategoryResponse(
                    code.getCdva(), code.getCdvaDes() != null ? code.getCdvaDes() : code.getCNm(),
                    code.getCNm(), prefix, dupRt, requestAmount);
        }).toList();
    }

    /**
     * 편성률 일괄 적용 (API-02)
     *
     * <p>
     * 각 비목별 편성률을 결재완료 원본 데이터에 적용하여 BBUGTM에 저장합니다.
     * Upsert 패턴: (BG_YY, ORC_TB, ORC_PK_VL, ORC_SNO_VL, IOE_C)로 기존 레코드 확인 후
     * 존재하면 UPDATE, 없으면 INSERT.
     * </p>
     *
     * [처리 순서]
     * 1. rates 배열 순회
     * 2. cdId에서 접두어 추출 (DUP-IOE-237 → "237")
     * 3. 결재완료 BCOSTM 조회 (IOE_C LIKE '접두어%')
     * 4. 결재완료 BITEMM 조회 (GCL_DTT LIKE '접두어%')
     * 5. 각 레코드: 편성금액 = 요청금액 × (dupRt / 100), ROUND HALF UP
     * 6. Upsert BBUGTM
     *
     * // Plan SC: SC-03 — 편성금액 = Math.round(요청금액 × 편성률 / 100)
     * // Plan SC: SC-05 — Upsert 동작 (중복 INSERT 방지)
     *
     * @param request 편성률 적용 요청 (예산년도 + 비목별 편성률 목록)
     * @return 적용 결과 (처리 메시지, 레코드 수, 요약)
     */
    @Transactional
    public BudgetWorkDto.ApplyResponse applyRates(BudgetWorkDto.ApplyRequest request) {
        String bgYy = request.bgYy();
        String bgMngNo = bbugtmRepository.generateBgMngNo(bgYy);
        int snoCounter = 0;
        int totalRecords = 0;

        // V003 마이그레이션 후 IOE_C는 단축 cdva를 저장하므로 prefix→cdva 집합 매핑 빌드
        List<Ccodem> allIoeCodes = findCodes(CommonCodeGroups.IOE);
        Map<String, Set<String>> prefixToIoeCValues = buildPrefixToIoeCValuesMap(allIoeCodes);

        // 존재확인 N+1 제거: 연도·테이블 단위로 기존 BBUGTM을 일괄 조회해 키맵 구성.
        // 키 = pkColNm + "|" + fntTbCrySno + "|" + ioeC (레코드별 SELECT와 동일 조합).
        // (pkColNm,fntTbCrySno,ioeC)는 자연키이며 DB 제약으로 중복이 방지되므로,
        // 충돌 시 첫 행 채택 (a,b)->a 은 기존 Optional 단건 반환과 동등하다.
        Map<String, Bbugtm> existingCostMap = bbugtmRepository
                .findByBseYyAndFntTbNmAndDelYn(bgYy, "BCOSTM", "N").stream()
                .collect(Collectors.toMap(
                    b -> b.getPkColNm() + "|" + b.getFntTbCrySno() + "|" + b.getIoeC(),
                    b -> b, (a, b) -> a));
        Map<String, Bbugtm> existingItemMap = bbugtmRepository
                .findByBseYyAndFntTbNmAndDelYn(bgYy, "BITEMM", "N").stream()
                .collect(Collectors.toMap(
                    b -> b.getPkColNm() + "|" + b.getFntTbCrySno() + "|" + b.getIoeC(),
                    b -> b, (a, b) -> a));

        for (BudgetWorkDto.RateItem rate : request.rates()) {
            String prefix = extractPrefix(rate.cdId());
            Set<String> ioeCValues = prefixToIoeCValues.getOrDefault(prefix, Set.of());
            Integer dupRt = rate.dupRt();

            // 결재완료 BCOSTM 처리
            List<Bcostm> costs = bbugtmRepository.findApprovedCostsByIoeCValues(ioeCValues, bgYy);
            for (Bcostm cost : costs) {
                BigDecimal dupBgAmt = calculateDupBg(cost.getCostTotXpAmt(), dupRt);

                // 존재확인: 레코드별 SELECT 대신 일괄 조회 키맵 조회 (N+1 제거)
                String key = cost.getCostBgNo() + "|" + cost.getBgSno() + "|" + cost.getIoeC();
                Bbugtm existing = existingCostMap.get(key);

                if (existing != null) {
                    // Upsert: UPDATE (JPA Dirty Checking)
                    existing.update(dupBgAmt, dupRt);
                } else {
                    // Upsert: INSERT
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgNo(bgMngNo)
                            .sno(snoCounter)
                            .bseYy(bgYy)
                            .fntTbNm("BCOSTM")
                            .pkColNm(cost.getCostBgNo())
                            .fntTbCrySno(cost.getBgSno())
                            .ioeC(cost.getIoeC())
                            .bgDupAmt(dupBgAmt)
                            .asgRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                    // 동일 실행 내 중복 키 재삽입 방지 (기존 레코드별 SELECT가 같은 트랜잭션 내
                    // 직전 INSERT 행을 보던 동작과 동일하게 UPDATE로 처리되도록 키맵에 반영)
                    existingCostMap.put(key, bbugtm);
                }
                totalRecords++;
            }

            // 결재완료 BITEMM 처리
            // ORC_TB = "BITEMM": BITEMM은 자체 PK(GCL_MNG_NO + SNO)를 보유하므로
            // 개별 품목 단위로 추적 가능. Plan 설계 문서의 "BPROJM"은 결재 조회 대상을
            // 지칭한 것이며, BBUGTM에 저장 시 실제 원본은 BITEMM임.
            List<Bitemm> items = bbugtmRepository.findApprovedItemsByIoeCValues(ioeCValues, bgYy);
            for (Bitemm item : items) {
                // BITEMM.amt는 저장 시점에 원화로 환산된 금액이므로 환율을 다시 곱하지 않습니다.
                BigDecimal amountKrw = item.getAmt() != null ? item.getAmt() : BigDecimal.ZERO;
                BigDecimal dupBgAmt = calculateDupBg(amountKrw, dupRt);

                // 존재확인: 레코드별 SELECT 대신 일괄 조회 키맵 조회 (N+1 제거)
                String key = item.getGclMngNo() + "|" + item.getSno() + "|" + item.getIoeC();
                Bbugtm existing = existingItemMap.get(key);

                if (existing != null) {
                    existing.update(dupBgAmt, dupRt);
                } else {
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgNo(bgMngNo)
                            .sno(snoCounter)
                            .bseYy(bgYy)
                            .fntTbNm("BITEMM")
                            .pkColNm(item.getGclMngNo())
                            .fntTbCrySno(item.getSno())
                            .ioeC(item.getIoeC())
                            .bgDupAmt(dupBgAmt)
                            .asgRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                    // 동일 실행 내 중복 키 재삽입 방지 (직전 INSERT 행을 UPDATE로 처리)
                    existingItemMap.put(key, bbugtm);
                }
                totalRecords++;
            }
        }

        BudgetWorkDto.SummaryResponse summary = getSummary(bgYy);
        return new BudgetWorkDto.ApplyResponse("편성률 적용 완료", totalRecords, summary);
    }

    /**
     * 사업별 편성률 적용 (API-05, REQ-2)
     *
     * <p>
     * 각 사업(정보화사업/전산업무비)별로 자본예산 편성률과 일반관리비 편성률을 분리 적용합니다.
     * IOE_CPIT 계열 비목코드 → assetDupRt, 나머지 → costDupRt 적용.
     * </p>
     *
     * @param request 사업별 편성률 적용 요청 (예산년도 + 사업별 편성률 목록)
     * @return 적용 결과 (처리 메시지, 레코드 수, 요약)
     */
    @Transactional
    public BudgetWorkDto.ApplyResponse applyItemRates(BudgetWorkDto.ItemApplyRequest request) {
        String bgYy = request.bgYy();
        String bgMngNo = bbugtmRepository.generateBgMngNo(bgYy);
        int snoCounter = 0;
        int totalRecords = 0;

        /*
         * 해당 예산년도 BBUGTM 전체 Soft Delete (선 정리 → 후 재삽입 패턴):
         * targetItems에는 현재 결재완료 상태인 사업·전산업무비만 포함되므로,
         * 결재철회/삭제 등으로 대상에서 제외된 과거 BBUGTM 레코드가 남아서
         * 비목별 편성 결과의 편성금액을 부풀리는 문제를 원천 차단합니다.
         * 또한 BITEMM 구버전(LST_YN='N')이 과거 버그로 저장된 고아 레코드도 함께 제거됩니다.
         */
        // 선정리: 전체 로드+루프 delete 대신 단일 벌크 UPDATE로 soft-delete (P1 #1).
        // 변경자 사번은 현재 인증 사용자(없으면 SYSTEM)를 UPDATE문에 직접 세팅한다.
        // 벌크는 @PreUpdate→ChangeLogEntityListener를 우회하므로 이 과도적 선정리 구간의
        // 행별 BbugtL 로그는 생성되지 않는다(설계 §4.2 DECISION, 손실 수용).
        String changerUsid = auditorAware.getCurrentAuditor()
                .orElseGet(() -> {
                    log.warn("applyItemRates: AuditorAware에서 사번을 가져오지 못했습니다. LST_CHG_USID를 'SYSTEM'으로 설정합니다.");
                    return "SYSTEM";
                });
        bbugtmRepository.softDeleteByBseYy(bgYy, changerUsid, LocalDateTime.now());

        /* 자본예산 비목코드 목록 조회 — C_TP(IOE_DVC/HW/SW) 기준 */
        List<Ccodem> capitalCodes = findCodes(CommonCodeGroups.IOE)
                .stream()
                .filter(code -> isCapitalCTp(code.getCTp()))
                .toList();
        if (capitalCodes.isEmpty()) {
            capitalCodes = findCodes("IOE_CPIT");
        }
        java.util.Set<String> capitalPrefixes = new java.util.HashSet<>();
        for (Ccodem code : capitalCodes) {
            /* IOE-351-0100 → IOE-351 추출 (3세그먼트에서 2세그먼트로 축약) */
            String cdva = code.getCdva();
            int lastDash = cdva.lastIndexOf('-');
            if (lastDash > 0) {
                capitalPrefixes.add(cdva.substring(0, lastDash));
            }
            capitalPrefixes.add(cdva); // 전체 코드도 추가
        }

        for (BudgetWorkDto.ItemRate item : request.items()) {
            Integer assetDupRt = item.assetDupRt() != null ? item.assetDupRt() : 100;
            Integer costDupRt = item.costDupRt() != null ? item.costDupRt() : 100;

            if ("BPROJM".equals(item.orcTb())) {
                /* 정보화사업: BITEMM에서 해당 프로젝트의 최신 버전 품목(LST_YN='Y')만 조회 */
                List<Bitemm> items = projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(
                        item.orcPkVl(), "N", "Y");

                for (Bitemm bitemm : items) {
                    boolean isCapital = isCapitalIoeCode(bitemm.getIoeC(), capitalPrefixes);
                    int dupRt = isCapital ? assetDupRt : costDupRt;

                    // BITEMM.amt는 저장 시점에 원화로 환산된 금액이고, fcAmt가 원천 통화 금액입니다.
                    // BCOSTM도 기존처럼 원화 비용 합계(costTotXpAmt)를 그대로 사용합니다.
                    BigDecimal amountKrw = bitemm.getAmt() != null ? bitemm.getAmt() : BigDecimal.ZERO;
                    BigDecimal dupBgAmt = calculateDupBg(amountKrw, dupRt);

                    /* 선 Soft Delete 후 전체 재삽입 방식이므로 Upsert 불필요 (항상 INSERT) */
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgNo(bgMngNo)
                            .sno(snoCounter)
                            .bseYy(bgYy)
                            .fntTbNm("BITEMM")
                            .pkColNm(bitemm.getGclMngNo())
                            .fntTbCrySno(bitemm.getSno())
                            .ioeC(bitemm.getIoeC())
                            .bgDupAmt(dupBgAmt)
                            .asgRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                    totalRecords++;
                }
            } else if ("BCOSTM".equals(item.orcTb())) {
                /* 전산업무비: 해당 전산업무비의 최신 버전(LST_YN='Y')만 처리 */
                List<Bcostm> costList = costRepository.findByCostBgNoAndDelYnAndLstYn(
                        item.orcPkVl(), "N", "Y");

                for (Bcostm cost : costList) {
                    boolean isCapital = isCapitalIoeCode(cost.getIoeC(), capitalPrefixes);
                    int dupRt = isCapital ? assetDupRt : costDupRt;
                    BigDecimal dupBgAmt = calculateDupBg(cost.getCostTotXpAmt(), dupRt);

                    /* 선 Soft Delete 후 전체 재삽입 방식이므로 Upsert 불필요 (항상 INSERT) */
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgNo(bgMngNo)
                            .sno(snoCounter)
                            .bseYy(bgYy)
                            .fntTbNm("BCOSTM")
                            .pkColNm(cost.getCostBgNo())
                            .fntTbCrySno(cost.getBgSno())
                            .ioeC(cost.getIoeC())
                            .bgDupAmt(dupBgAmt)
                            .asgRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                    totalRecords++;
                }
            }
        }

        BudgetWorkDto.SummaryResponse summary = getSummary(bgYy);
        return new BudgetWorkDto.ApplyResponse("사업별 편성률 적용 완료", totalRecords, summary);
    }

    /**
     * IOE 코드가 자본예산 비목인지 판별
     *
     * @param ioeC            비목코드 (예: IOE-351-0100)
     * @param capitalPrefixes 자본예산 비목 접두어 Set (IOE-351, IOE-351-0100 등)
     * @return true이면 자본예산
     */
    private boolean isCapitalIoeCode(String ioeC, java.util.Set<String> capitalPrefixes) {
        if (ioeC == null) return false;
        if (capitalPrefixes.contains(ioeC)) return true;
        /* 접두어 매칭: IOE-351-0100이 IOE-351로 시작하는지 확인 */
        for (String prefix : capitalPrefixes) {
            if (ioeC.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * 편성 결과 조회 (API-03)
     *
     * <p>
     * BBUGTM에서 예산년도별 편성 데이터를 조회하고, 결재완료 원본 집계 쿼리에서
     * 요청금액을 계산한 뒤 비목 접두어 기준으로 합계를 반환합니다.
     * </p>
     *
     * [처리 순서]
     * 1. BBUGTM에서 BG_YY = :bgYy AND DEL_YN = 'N' 조회
     * 2. CCODEM에서 DUP_IOE 코드 조회 (비목명 매핑용)
     * 3. 결재완료 원본 데이터를 IOE 코드별로 일괄 집계하여 요청금액 계산
     * 4. 접두어 기준 GROUP BY → SUM(요청금액), SUM(편성금액) 집계
     *
     * @param bgYy 예산년도
     * @return 비목별 요약 목록 + 합계
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy) {
        return getSummary(bgYy, null);
    }

    /**
     * 편성 결과 조회 (API-03) — 선택 원본 한정 집계 지원.
     *
     * @param bgYy   예산연도
     * @param srcPks 선택 원본 PK 목록(BBUGTM.pkColNm). null/빈 목록이면 연도 전체 집계(예산작업 화면용),
     *               값이 있으면 해당 원본만 집계(정보기술부문 계획 화면의 선택 사업 카드용).
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy, java.util.List<String> srcPks) {
        List<Bbugtm> budgets = bbugtmRepository.findByBseYyAndDelYn(bgYy, "N");
        budgets = filterByApprovedSource(budgets, bgYy);

        // 선택 원본 한정(정보기술부문 계획 카드): 지정된 원본 PK의 편성행만 집계
        if (srcPks != null && !srcPks.isEmpty()) {
            java.util.Set<String> selectedPks = new java.util.HashSet<>(srcPks);
            budgets = budgets.stream()
                    .filter(b -> b.getPkColNm() != null && selectedPks.contains(b.getPkColNm()))
                    .toList();
        }

        // 편성비목 그룹 코드 조회 (DUP_IOE: 접두어 → 그룹명 매핑)
        List<Ccodem> dupIoeCodes = findCodes("DUP_IOE");

        // 세부 비목 코드 조회: 마이그레이션 후 cId=CommonCodeGroups.IOE 단일 그룹으로 통합됨
        // cdva("101") → 계층코드 cdvaDtlC("304-1100") 매핑으로 DUP_IOE 접두어("304")와 매칭
        List<Ccodem> allIoeCodes = findCodes(CommonCodeGroups.IOE);
        Map<String, String> cdvaToHierarchyCode = new LinkedHashMap<>();
        Map<String, String> cdvaToDisplayName = new LinkedHashMap<>();
        Map<String, String> cdvaToGroupName = new LinkedHashMap<>();
        Map<String, Boolean> cdvaToCapital = new LinkedHashMap<>();
        for (Ccodem code : allIoeCodes) {
            String hierarchyCode = code.getCdvaDtlC();  // 마이그레이션 후 계정과목코드: CDVA_DTL_C("304-1100")
            cdvaToHierarchyCode.put(code.getCdva(), hierarchyCode);
            String displayName = code.getCdvaNm() != null ? code.getCdvaNm()
                    : (code.getCdvaDtl() != null ? code.getCdvaDtl() : hierarchyCode);
            cdvaToDisplayName.put(code.getCdva(), displayName);
            cdvaToGroupName.put(code.getCdva(), resolveIoeGroupName(code));
            cdvaToCapital.put(code.getCdva(), isCapitalCTp(code.getCTp()));
        }

        // 접두어 → 그룹명 매핑 (DUP_IOE 기반, cdvaDes 우선 사용)
        Map<String, String> prefixToGroupName = new LinkedHashMap<>();
        List<String> prefixOrder = new ArrayList<>();
        for (Ccodem code : dupIoeCodes) {
            String prefix = extractPrefix(code.getCdva());
            prefixToGroupName.put(prefix, code.getCdvaDes() != null ? code.getCdvaDes() : code.getCNm());
            prefixOrder.add(prefix);
        }

        // BBUGTM 데이터를 실제 ioeC 단위로 그룹핑
        Map<String, List<Bbugtm>> budgetsByIoeC = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if (b.getIoeC() != null) {
                budgetsByIoeC.computeIfAbsent(b.getIoeC(), k -> new ArrayList<>()).add(b);
            }
        }

        // 예정금액(익년 이후분) 비목별 차감액 산출 — 예산년도분 기준으로 정렬(budget/list와 일치)
        Map<String, BigDecimal> mplReqAdjustByIoeC = new LinkedHashMap<>();
        Map<String, BigDecimal> mplDupAdjustByIoeC = new LinkedHashMap<>();
        computeMplAdjustment(budgets, cdvaToCapital, mplReqAdjustByIoeC, mplDupAdjustByIoeC);

        // 결재완료 원본 데이터에서 요청금액을 직접 계산 (DB-01: 단일 집계 쿼리로 N+1 제거)
        // 기존: 각 prefix별 findApprovedCostsByPrefix / findApprovedItemsByPrefix → N×2 쿼리
        // 개선: 전체를 한 번에 GROUP BY 집계 → 2 쿼리
        Map<String, BigDecimal> approvedCostAmountByIoeC =
                budgetWorkQueryRepository.findApprovedCostAmountByIoeC(bgYy, srcPks);
        Map<String, BigDecimal> approvedItemAmountByIoeC =
                budgetWorkQueryRepository.findApprovedItemAmountByGclDtt(bgYy, srcPks);

        List<BudgetWorkDto.SummaryItem> items = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalDup = BigDecimal.ZERO;

        // 편성비목 접두어 순서대로 처리 (DUP_IOE 코드 순서 유지)
        for (String prefix : prefixOrder) {
            String groupName = prefixToGroupName.get(prefix);

            // CCODEM[cId=CommonCodeGroups.IOE] 기반: 계층코드(cNm)의 접두어로 그룹 매칭
            // ioeC("101") → cNm("304-1100") → startsWith("304") 방식으로 매칭
            List<String> detailCodesForPrefix = new ArrayList<>();
            for (Map.Entry<String, String> e : cdvaToHierarchyCode.entrySet()) {
                if (e.getValue() != null && e.getValue().startsWith(prefix)) {
                    detailCodesForPrefix.add(e.getKey());
                }
            }

            // BBUGTM에만 있고 CCODEM에는 없는 ioeC도 포함
            for (String ioeC : budgetsByIoeC.keySet()) {
                String hc = cdvaToHierarchyCode.get(ioeC);
                if (hc != null && hc.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
                    detailCodesForPrefix.add(ioeC);
                }
            }

            // 원본 데이터에만 있고 CCODEM/BBUGTM에 없는 ioeC도 포함
            for (String ioeC : approvedCostAmountByIoeC.keySet()) {
                String hc = cdvaToHierarchyCode.get(ioeC);
                if (hc != null && hc.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
                    detailCodesForPrefix.add(ioeC);
                }
            }
            for (String ioeC : approvedItemAmountByIoeC.keySet()) {
                String hc = cdvaToHierarchyCode.get(ioeC);
                if (hc != null && hc.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
                    detailCodesForPrefix.add(ioeC);
                }
            }

            // 세부 코드가 하나도 없으면 그룹명으로 0건 행 표시
            if (detailCodesForPrefix.isEmpty()) {
                items.add(new BudgetWorkDto.SummaryItem(
                        groupName, prefix, prefix, groupName, false,
                        BigDecimal.ZERO, BigDecimal.ZERO, null));
                continue;
            }

            // 표시명 기준으로 동일 비목명 병합 (순서 유지)
            Map<String, List<String>> nameToIoeCodes = new LinkedHashMap<>();
            for (String ioeC : detailCodesForPrefix) {
                String rawName = cdvaToDisplayName.getOrDefault(ioeC, ioeC);
                String detailName = stripGroupPrefix(rawName);
                nameToIoeCodes.computeIfAbsent(detailName, k -> new ArrayList<>()).add(ioeC);
            }

            // 병합된 비목별 요약 행 생성
            for (Map.Entry<String, List<String>> nameEntry : nameToIoeCodes.entrySet()) {
                String detailName = nameEntry.getKey();
                List<String> ioeCodes = nameEntry.getValue();

                // 병합 대상 BBUGTM 레코드 수집
                List<Bbugtm> allRecords = new ArrayList<>();
                for (String ioeC : ioeCodes) {
                    allRecords.addAll(budgetsByIoeC.getOrDefault(ioeC, List.of()));
                }

                // 대표 ioeC (첫 번째 코드)
                String representativeIoeC = ioeCodes.get(0);

                // 편성금액 합계 (BBUGTM 기반)
                BigDecimal dupAmount = allRecords.stream()
                        .map(value -> value.getBgDupAmt())
                        .filter(v -> v != null)
                        .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));

                // 요청금액: 결재완료 원본 데이터(BCOSTM/BITEMM)에서 직접 계산
                BigDecimal requestAmount = BigDecimal.ZERO;
                for (String ioeC : ioeCodes) {
                    requestAmount = requestAmount.add(
                            approvedCostAmountByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                    requestAmount = requestAmount.add(
                            approvedItemAmountByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                }

                // 예정금액(익년분) 비례 차감 — 요청·편성 동일 비율 차감으로 편성률 보존
                for (String ioeC : ioeCodes) {
                    requestAmount = requestAmount.subtract(mplReqAdjustByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                    dupAmount = dupAmount.subtract(mplDupAdjustByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                }
                if (requestAmount.signum() < 0) requestAmount = BigDecimal.ZERO;
                if (dupAmount.signum() < 0) dupAmount = BigDecimal.ZERO;

                // 편성률 (BBUGTM 레코드가 있으면 해당 값, 없으면 null)
                Integer dupRt = allRecords.stream()
                        .map(value -> value.getAsgRt())
                        .findFirst()
                        .orElse(null);

                // 자본예산 여부: 대표 코드의 C_TP가 IOE_DVC/HW/SW이면 자본예산
                boolean capital = Boolean.TRUE.equals(cdvaToCapital.get(representativeIoeC));
                String itemGroupName = cdvaToGroupName.get(representativeIoeC);
                if (itemGroupName == null || itemGroupName.isBlank()) itemGroupName = groupName;

                items.add(new BudgetWorkDto.SummaryItem(
                        detailName, representativeIoeC, prefix, itemGroupName, capital,
                        requestAmount, dupAmount, dupRt));

                totalRequest = totalRequest.add(requestAmount);
                totalDup = totalDup.add(dupAmount);
            }
        }

        return new BudgetWorkDto.SummaryResponse(
                items,
                new BudgetWorkDto.SummaryTotals(totalRequest, totalDup));
    }

    /**
     * 세부 비목명에서 그룹 접두어를 제거
     *
     * <p>{@code "전산임차료 - 국내전산임차료"} → {@code "국내전산임차료"}</p>
     * <p>{@code "전산용역비 - 외주용역 - 외주운영/관제 등"} → {@code "외주용역 - 외주운영/관제 등"}</p>
     *
     * @param fullName CCODEM의 cdNm 원본
     * @return 그룹 접두어가 제거된 세부 비목명
     */
    private String stripGroupPrefix(String fullName) {
        int dashIdx = fullName.indexOf(" - ");
        if (dashIdx >= 0) {
            return fullName.substring(dashIdx + 3);
        }
        return fullName;
    }

    /**
     * BBUGTM 편성 행 중 "결재완료 원본"에 해당하는 것만 남깁니다.
     *
     * <p>편성요청금액 집계는 결재완료 원본만 대상으로 하지만, BBUGTM에는 이후 결재가
     * 취소·반려되었거나 잘못 입력된 미결재 원본의 편성 행이 stale 상태로 남아 합계를
     * 부풀릴 수 있습니다. 요청금액과 동일한 결재완료 기준(원본 PK 화이트리스트)으로
     * 필터링하여 편성액 집계를 일치시킵니다.</p>
     *
     * @param budgets 연도별 BBUGTM 편성 행 (DEL_YN='N')
     * @param bgYy    예산연도
     * @return 결재완료 원본(BBUGTM.pkColNm ∈ 결재완료 원본 PK)만 남긴 목록
     */
    private List<Bbugtm> filterByApprovedSource(List<Bbugtm> budgets, String bgYy) {
        java.util.Set<String> approvedSrcPks = budgetWorkQueryRepository.findApprovedSourcePks(bgYy);
        // 안전장치(fail-open): 결재완료 원본 집합을 구하지 못하면(null/빈 집합) 필터링하지 않는다.
        // 화이트리스트가 비었을 때 전체 편성행이 사라져 합계가 0이 되는 더 큰 사고를 방지.
        if (approvedSrcPks == null || approvedSrcPks.isEmpty()) return budgets;
        return budgets.stream()
                .filter(b -> b.getPkColNm() != null && approvedSrcPks.contains(b.getPkColNm()))
                .toList();
    }

    /**
     * 예정금액(익년 이후분) 비목별 차감액 산출.
     *
     * <p>품목별 예정금액(BITEMM.MPL_AMT)의 사업+그룹(자본/일반관리비) 합산액은 익년 이후 예정분이므로
     * 예산년도 편성요청/편성에서 제외해야 한다(ProjectBudgetSummaryService.applyBudgetSummary의
     * totRqmAmt = ∑AMT − ∑MPL_AMT 기준과 일치).
     * 예정금액은 사업 단위라 해당 사업의 그룹(자본/일반관리비) 품목에 비례 배분하여
     * 요청(req)·편성(dup)을 동일 비율로 차감한다(품목별 편성률 ≤ 100% 보존).
     * 전산업무비(BCOSTM)는 예정금액이 없어 대상에서 제외한다.</p>
     */
    private void computeMplAdjustment(List<Bbugtm> budgets, Map<String, Boolean> cdvaToCapital,
                                      Map<String, BigDecimal> reqAdjustOut, Map<String, BigDecimal> dupAdjustOut) {
        // 품목(gclMngNo)별 BBUGTM 편성행 (BITEMM 원본만)
        Map<String, List<Bbugtm>> byGcl = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if ("BITEMM".equals(b.getFntTbNm()) && b.getPkColNm() != null && b.getIoeC() != null) {
                byGcl.computeIfAbsent(b.getPkColNm(), k -> new ArrayList<>()).add(b);
            }
        }
        if (byGcl.isEmpty()) return;

        // gclMngNo → Bitemm(품목금액/환율/사업관리번호) — 품목 PK 집합 1회 배치 조회 (N+1 제거)
        // 원본 단건 로직과 동일하게 gclMngNo별 첫 행만 채택(putIfAbsent).
        Map<String, Bitemm> bitemmByGcl = new LinkedHashMap<>();
        for (Bitemm it : projectItemRepository.findByGclMngNoInAndDelYn(byGcl.keySet(), "N")) {
            bitemmByGcl.putIfAbsent(it.getGclMngNo(), it);
        }
        // 사업관리번호 → Bprojm(예정금액) — 사업관리번호 집합 1회 배치 조회 (N+1 제거)
        // 원본 단건 로직과 동일하게 abusMngNo별 첫 행만 채택(putIfAbsent).
        java.util.Set<String> prjNos = bitemmByGcl.values().stream()
                .map(value -> value.getAbusMngNo())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Bprojm> prjByNo = new LinkedHashMap<>();
        if (!prjNos.isEmpty()) {
            for (Bprojm p : projectRepository.findByAbusMngNoInAndDelYn(prjNos, "N")) {
                prjByNo.putIfAbsent(p.getAbusMngNo(), p);
            }
        }

        // 사업+그룹(자본/일반)별 품목 기여(요청/편성) 집계
        record ItemContrib(String ioeC, BigDecimal req, BigDecimal dup) {}
        Map<String, List<ItemContrib>> groupItems = new LinkedHashMap<>();
        Map<String, BigDecimal> groupReqSum = new LinkedHashMap<>();
        // 그룹별 품목 예정금액(mplAmt) 합산 — Bprojm.mplCpitAmt/mplMngcAmt 제거 후 품목 단위로 집계
        Map<String, BigDecimal> groupMplSum = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bbugtm>> e : byGcl.entrySet()) {
            Bitemm it = bitemmByGcl.get(e.getKey());
            if (it == null || it.getAbusMngNo() == null || !prjByNo.containsKey(it.getAbusMngNo())) continue;
            String ioeC = e.getValue().get(0).getIoeC();
            boolean capital = Boolean.TRUE.equals(cdvaToCapital.get(ioeC));
            BigDecimal req = it.getAmt() != null ? it.getAmt() : BigDecimal.ZERO;
            BigDecimal dup = e.getValue().stream().map(value -> value.getBgDupAmt())
                    .filter(v -> v != null).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
            String key = it.getAbusMngNo() + "|" + capital;
            groupItems.computeIfAbsent(key, k -> new ArrayList<>()).add(new ItemContrib(ioeC, req, dup));
            groupReqSum.merge(key, req, (left, right) -> left.add(right));
            // 품목 예정금액 그룹 합산
            BigDecimal mplAmt = it.getMplAmt() != null ? it.getMplAmt() : BigDecimal.ZERO;
            groupMplSum.merge(key, mplAmt, (left, right) -> left.add(right));
        }

        // 그룹별 예정금액(품목 단위 합산)을 비례 배분하여 비목별 차감액 누적
        for (Map.Entry<String, List<ItemContrib>> e : groupItems.entrySet()) {
            String key = e.getKey();
            BigDecimal mpl = groupMplSum.getOrDefault(key, BigDecimal.ZERO);
            if (mpl.signum() <= 0) continue;
            BigDecimal sum = groupReqSum.getOrDefault(key, BigDecimal.ZERO);
            if (sum.signum() <= 0) continue;
            BigDecimal factor = mpl.compareTo(sum) >= 0 ? BigDecimal.ONE
                    : mpl.divide(sum, 10, RoundingMode.HALF_UP);
            for (ItemContrib ic : e.getValue()) {
                reqAdjustOut.merge(ic.ioeC(), ic.req().multiply(factor), (left, right) -> left.add(right));
                dupAdjustOut.merge(ic.ioeC(), ic.dup().multiply(factor), (left, right) -> left.add(right));
            }
        }
    }

    /**
     * 공통코드 목록 조회 결과가 null이어도 빈 목록으로 처리합니다.
     */
    private List<Ccodem> findCodes(String cId) {
        return Optional.ofNullable(codeRepository.findByCIdWithValidDate(cId, null)).orElse(List.of());
    }

    /**
     * IOE 코드타입이 자본예산 세부 유형인지 판별합니다.
     */
    private boolean isCapitalCTp(String cTp) {
        return IoeCategories.isCapitalCTp(cTp);
    }

    /**
     * IOE 코드의 그룹명은 C_TP_DES를 우선 사용하고, 없으면 CDVA_DTL 계층의 중분류를 사용합니다.
     */
    private String resolveIoeGroupName(Ccodem code) {
        return IoeCategories.resolveGroupName(code);
    }

    /**
     * 사업별 편성 결과 컬럼명은 IOE 상세코드의 코드타입설명(C_TP_DES)을 우선 사용합니다.
     */
    private String resolveProjectSummaryCategoryName(String prefix, Ccodem dupCode, List<Ccodem> ioeDetailCodes) {
        for (Ccodem ioeCode : ioeDetailCodes) {
            if (ioeCode.getCdvaDtlC() != null && ioeCode.getCdvaDtlC().startsWith(prefix)) {
                String groupName = resolveIoeGroupName(ioeCode);
                if (groupName != null && !groupName.isBlank()) {
                    return groupName;
                }
            }
        }

        if (dupCode.getCdvaNm() != null && !dupCode.getCdvaNm().isBlank()) {
            return dupCode.getCdvaNm();
        }
        if (dupCode.getCNm() != null && !dupCode.getCNm().isBlank()) {
            return dupCode.getCNm();
        }
        if (dupCode.getCdvaDes() != null && !dupCode.getCdvaDes().isBlank()) {
            return dupCode.getCdvaDes();
        }
        return prefix;
    }

    /**
     * 사업별 편성 결과 조회 (API-04)
     *
     * <p>
     * BBUGTM 데이터를 원본PK(사업/전산업무비)별로 그룹핑하여
     * 각 사업의 요청금액/편성금액 합계 및 비목별 상세를 반환합니다.
     * </p>
     *
     * [처리 순서]
     * 1. CCODEM에서 편성비목 코드 + 편성률 조회 (컬럼 헤더용)
     * 2. BBUGTM에서 해당 연도 데이터 조회
     * 3. orcPkVl + ioeC 기준으로 이중 그룹핑
     * 4. orcTb에 따라 BPROJM 또는 BCOSTM에서 사업명/계약명 조회
     *
     * @param bgYy 예산년도
     * @return 사업별 편성 결과 요약 (비목 컬럼 정보, 사업별 비목별 금액, 합계)
     */
    public BudgetWorkDto.ProjectSummaryResponse getProjectSummary(String bgYy) {
        // 1. 편성비목 코드 조회 (컬럼 헤더용)
        List<Ccodem> ioeCodes = findCodes("DUP_IOE");
        List<Bbugtm> budgets = bbugtmRepository.findByBseYyAndDelYn(bgYy, "N");
        budgets = filterByApprovedSource(budgets, bgYy);

        // ioeC(cdva, "101") → 계층코드 cdvaDtlC("304-1100") 매핑: DUP_IOE 접두어("304") 매칭용
        List<Ccodem> ioeDetailCodes = findCodes(CommonCodeGroups.IOE);
        Map<String, String> ioeCdvaToHierarchyCode = new LinkedHashMap<>();
        Map<String, Boolean> ioeCdvaToCapital = new LinkedHashMap<>();
        for (Ccodem code : ioeDetailCodes) {
            ioeCdvaToHierarchyCode.put(code.getCdva(), code.getCdvaDtlC());
            ioeCdvaToCapital.put(code.getCdva(), isCapitalCTp(code.getCTp()));
        }

        // 비목별 편성률 맵 (prefix → dupRt)
        // ioeC("101") → cNm("304-1100") → startsWith("304") 방식으로 DUP_IOE 접두어 매칭
        Map<String, Integer> rateByPrefix = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if (b.getIoeC() != null && b.getAsgRt() != null) {
                String ioeHierarchyCode = ioeCdvaToHierarchyCode.get(b.getIoeC());
                if (ioeHierarchyCode == null) continue;
                for (Ccodem code : ioeCodes) {
                    String prefix = extractPrefix(code.getCdva());
                    if (ioeHierarchyCode.startsWith(prefix)) {
                        rateByPrefix.putIfAbsent(prefix, b.getAsgRt());
                        break;
                    }
                }
            }
        }

        // 컬럼 헤더 정보 구성
        List<BudgetWorkDto.ProjectSummaryCategory> categoryHeaders = new ArrayList<>();
        for (Ccodem code : ioeCodes) {
            String prefix = extractPrefix(code.getCdva());
            Integer dupRt = rateByPrefix.getOrDefault(prefix, 0);
            String categoryName = resolveProjectSummaryCategoryName(prefix, code, ioeDetailCodes);
            categoryHeaders.add(new BudgetWorkDto.ProjectSummaryCategory(prefix, categoryName, code.getCdvaDes(), dupRt));
        }

        // 2. 사업별 + 비목별 이중 그룹핑
        // BITEMM → prjMngNo로 변환하여 프로젝트 단위로 그룹핑
        // key: 프로젝트관리번호 또는 전산업무비관리번호, value: { prefix → [요청금액, 편성금액] }
        Map<String, Map<String, BigDecimal[]>> projectCategoryMap = new LinkedHashMap<>();
        Map<String, String> orcTbMap = new LinkedHashMap<>();

        // BITEMM gclMngNo → prjMngNo 선조회 Map (N+1 제거): BITEMM 원본의 품목 PK 집합을
        // 1회 배치 조회한 뒤 gclMngNo→abusMngNo 매핑을 미리 구성한다. 원본 단건 로직과 동일하게
        // gclMngNo별 첫 행만 채택(putIfAbsent)하고, 매핑이 없으면 gclMngNo 자체를 키로 사용한다.
        java.util.Set<String> gclPks = budgets.stream()
                .filter(b -> "BITEMM".equals(b.getFntTbNm()) && b.getPkColNm() != null)
                .map(value -> value.getPkColNm())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Bitemm> bitemmByGcl = new LinkedHashMap<>();
        Map<String, String> gclToPrj = new LinkedHashMap<>();
        if (!gclPks.isEmpty()) {
            for (Bitemm it : projectItemRepository.findByGclMngNoInAndDelYn(gclPks, "N")) {
                bitemmByGcl.putIfAbsent(it.getGclMngNo(), it);
                gclToPrj.putIfAbsent(it.getGclMngNo(), it.getAbusMngNo());
            }
        }

        // 사업별 결과에서도 예정금액은 예산년도분 요청/편성에서 제외한다.
        // 품목 예정금액은 사업+자본구분 그룹 내 품목금액 합계 대비 비율로 배분한다.
        Map<String, Bbugtm> firstBudgetByGcl = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if ("BITEMM".equals(b.getFntTbNm()) && b.getPkColNm() != null && b.getIoeC() != null) {
                firstBudgetByGcl.putIfAbsent(b.getPkColNm(), b);
            }
        }
        Map<String, BigDecimal> groupReqSum = new LinkedHashMap<>();
        Map<String, BigDecimal> groupMplSum = new LinkedHashMap<>();
        for (Map.Entry<String, Bbugtm> e : firstBudgetByGcl.entrySet()) {
            Bitemm item = bitemmByGcl.get(e.getKey());
            if (item == null || item.getAbusMngNo() == null) continue;
            boolean capital = Boolean.TRUE.equals(ioeCdvaToCapital.get(e.getValue().getIoeC()));
            String key = item.getAbusMngNo() + "|" + capital;
            BigDecimal requestAmount = item.getAmt() != null ? item.getAmt() : BigDecimal.ZERO;
            BigDecimal mplAmount = item.getMplAmt() != null ? item.getMplAmt() : BigDecimal.ZERO;
            groupReqSum.merge(key, requestAmount, (left, right) -> left.add(right));
            groupMplSum.merge(key, mplAmount, (left, right) -> left.add(right));
        }
        Map<String, BigDecimal> groupMplFactor = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : groupReqSum.entrySet()) {
            BigDecimal requestSum = e.getValue();
            BigDecimal mplSum = groupMplSum.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (requestSum.signum() <= 0 || mplSum.signum() <= 0) continue;
            BigDecimal factor = mplSum.compareTo(requestSum) >= 0 ? BigDecimal.ONE
                    : mplSum.divide(requestSum, 10, RoundingMode.HALF_UP);
            groupMplFactor.put(e.getKey(), factor);
        }

        for (Bbugtm b : budgets) {
            if (b.getPkColNm() == null) continue;

            // 그룹핑 키 결정: BITEMM은 프로젝트 단위로 통합
            String groupKey;
            String groupOrcTb;
            Bitemm sourceItem = null;
            if ("BITEMM".equals(b.getFntTbNm())) {
                // gclMngNo → prjMngNo 변환 (선조회 Map, 매핑 없으면 gclMngNo 자체)
                sourceItem = bitemmByGcl.get(b.getPkColNm());
                groupKey = gclToPrj.getOrDefault(b.getPkColNm(), b.getPkColNm());
                groupOrcTb = "BPROJM";
            } else {
                groupKey = b.getPkColNm();
                groupOrcTb = b.getFntTbNm();
            }

            orcTbMap.putIfAbsent(groupKey, groupOrcTb);
            projectCategoryMap.computeIfAbsent(groupKey, k -> new LinkedHashMap<>());

            // ioeC("101") → cNm("304-1100") → DUP_IOE 접두어("304") 매칭
            String matchedPrefix = null;
            String ioeHierarchyCode = b.getIoeC() != null ? ioeCdvaToHierarchyCode.get(b.getIoeC()) : null;
            if (ioeHierarchyCode != null) {
                for (Ccodem code : ioeCodes) {
                    String prefix = extractPrefix(code.getCdva());
                    if (ioeHierarchyCode.startsWith(prefix)) {
                        matchedPrefix = prefix;
                        break;
                    }
                }
            }
            if (matchedPrefix == null) continue;

            Map<String, BigDecimal[]> catMap = projectCategoryMap.get(groupKey);
            catMap.computeIfAbsent(matchedPrefix, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal[] amounts = catMap.get(matchedPrefix);
            BigDecimal requestAmt = BigDecimal.ZERO;
            BigDecimal dupAmt = b.getBgDupAmt() != null ? b.getBgDupAmt() : BigDecimal.ZERO;
            // 요청금액 역산: dupBgAmt / (dupRt / 100)
            if (b.getBgDupAmt() != null && b.getAsgRt() != null && b.getAsgRt() > 0) {
                requestAmt = b.getBgDupAmt()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(b.getAsgRt()), 2, RoundingMode.HALF_UP);
            }
            if (sourceItem != null && sourceItem.getAbusMngNo() != null) {
                boolean capital = Boolean.TRUE.equals(ioeCdvaToCapital.get(b.getIoeC()));
                BigDecimal factor = groupMplFactor.get(sourceItem.getAbusMngNo() + "|" + capital);
                if (factor != null) {
                    requestAmt = requestAmt.subtract(requestAmt.multiply(factor));
                    dupAmt = dupAmt.subtract(dupAmt.multiply(factor));
                    if (requestAmt.signum() < 0) requestAmt = BigDecimal.ZERO;
                    if (dupAmt.signum() < 0) dupAmt = BigDecimal.ZERO;
                }
            }
            amounts[0] = amounts[0].add(requestAmt);
            amounts[1] = amounts[1].add(dupAmt);
        }

        // 3. 응답 구성
        // 사업명(BPROJM)/계약명(BCOSTM) 배치 선조회 (N+1 제거): 그룹키를 원본테이블별로 분류하여
        // 각 1회 IN 조회한 뒤 Map으로 보관한다. 원본 resolveProjectName과 동일하게 첫 행을 채택하며,
        // BPROJM은 사업명이 null이면 orcPkVl로 폴백(null 이름은 Map에 넣지 않음), BCOSTM은 첫 행의
        // 계약명(null 포함)을 그대로 채택한다.
        java.util.Set<String> prjGroupNos = new java.util.LinkedHashSet<>();
        java.util.Set<String> costGroupNos = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, String> e : orcTbMap.entrySet()) {
            if ("BPROJM".equals(e.getValue())) prjGroupNos.add(e.getKey());
            else if ("BCOSTM".equals(e.getValue())) costGroupNos.add(e.getKey());
        }
        Map<String, String> prjNameByNo = new LinkedHashMap<>();
        if (!prjGroupNos.isEmpty()) {
            for (Bprojm p : projectRepository.findByAbusMngNoInAndDelYn(prjGroupNos, "N")) {
                // 첫 행 채택 + 사업명이 null/blank가 아닐 때만 등록 (없으면 orcPkVl 폴백)
                if (p.getAbusNm() != null) {
                    prjNameByNo.putIfAbsent(p.getAbusMngNo(), p.getAbusNm());
                }
            }
        }
        // 계약명: costBgNo별 첫 행의 cttNm(null 포함)을 채택하기 위해 키 존재 여부로 폴백 판단
        Map<String, String> costNameByNo = new LinkedHashMap<>();
        if (!costGroupNos.isEmpty()) {
            for (Bcostm c : costRepository.findByCostBgNoInAndDelYn(costGroupNos, "N")) {
                if (!costNameByNo.containsKey(c.getCostBgNo())) {
                    costNameByNo.put(c.getCostBgNo(), c.getCttNm());
                }
            }
        }

        List<BudgetWorkDto.ProjectSummaryItem> items = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalDup = BigDecimal.ZERO;

        for (Map.Entry<String, Map<String, BigDecimal[]>> entry : projectCategoryMap.entrySet()) {
            String orcPkVl = entry.getKey();
            Map<String, BigDecimal[]> catMap = entry.getValue();
            String orcTb = orcTbMap.get(orcPkVl);
            // 원본 resolveProjectName(orcTb, orcPkVl)와 동치: 선조회 Map 참조
            String name;
            if ("BPROJM".equals(orcTb)) {
                name = prjNameByNo.getOrDefault(orcPkVl, orcPkVl);
            } else if ("BCOSTM".equals(orcTb)) {
                name = costNameByNo.containsKey(orcPkVl) ? costNameByNo.get(orcPkVl) : orcPkVl;
            } else {
                name = orcPkVl;
            }

            // 비목별 금액 맵 구성
            Map<String, BudgetWorkDto.CategoryAmount> categoryAmounts = new LinkedHashMap<>();
            BigDecimal projectRequest = BigDecimal.ZERO;
            BigDecimal projectDup = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal[]> catEntry : catMap.entrySet()) {
                BigDecimal[] amounts = catEntry.getValue();
                categoryAmounts.put(catEntry.getKey(),
                        new BudgetWorkDto.CategoryAmount(amounts[0], amounts[1]));
                projectRequest = projectRequest.add(amounts[0]);
                projectDup = projectDup.add(amounts[1]);
            }

            items.add(new BudgetWorkDto.ProjectSummaryItem(
                    orcPkVl, orcTb, name, projectRequest, projectDup, categoryAmounts));

            totalRequest = totalRequest.add(projectRequest);
            totalDup = totalDup.add(projectDup);
        }

        return new BudgetWorkDto.ProjectSummaryResponse(
                categoryHeaders, items,
                new BudgetWorkDto.SummaryTotals(totalRequest, totalDup));
    }

    /**
     * 편성금액 계산
     *
     * <p>편성금액 = 요청금액 × (편성률 / 100), HALF_UP 반올림</p>
     *
     * @param requestAmount 요청금액
     * @param dupRt         편성률 (0~100)
     * @return 편성금액 (소수점 2자리)
     */
    private BigDecimal calculateDupBg(BigDecimal requestAmount, Integer dupRt) {
        if (requestAmount == null || dupRt == null) {
            return BigDecimal.ZERO;
        }
        return requestAmount
                .multiply(BigDecimal.valueOf(dupRt))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * DUP_IOE 접두어 → IOE cdva 값 집합 매핑 빌드
     *
     * <p>
     * V003 마이그레이션 후: CCODEM[cId=CommonCodeGroups.IOE]의 cNm이 계층코드("237-0700")를 담고,
     * cdva가 단축 값("001")을 담습니다. cNm에서 첫 '-' 이전 부분을 DUP_IOE 접두어로 사용합니다.
     * </p>
     *
     * @param allIoeCodes CCODEM[cId=CommonCodeGroups.IOE] 전체 코드 목록
     * @return 접두어("237") → cdva 집합({"001","002",...}) 맵
     */
    Map<String, Set<String>> buildPrefixToIoeCValuesMap(List<Ccodem> allIoeCodes) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (Ccodem code : allIoeCodes) {
            String hierarchyCode = code.getCdvaDtlC(); // 마이그레이션 후 계정과목코드: CDVA_DTL_C("237-0700")
            if (hierarchyCode == null || code.getCdva() == null) continue;
            int dashIdx = hierarchyCode.indexOf('-');
            String prefix = dashIdx > 0 ? hierarchyCode.substring(0, dashIdx) : hierarchyCode;
            map.computeIfAbsent(prefix, k -> new HashSet<>()).add(code.getCdva());
        }
        return map;
    }

    /**
     * DUP_IOE cdva에서 편성비목 접두어 추출
     *
     * <p>V003 마이그레이션 후 DUP_IOE cdva는 "237" 형태이므로 그대로 반환합니다.</p>
     *
     * @param cdva DUP_IOE 코드의 cdva (예: "237")
     * @return 비목 접두어 (예: "237")
     */
    private String extractPrefix(String cdva) {
        return cdva.replace("DUP-", "");
    }
}
