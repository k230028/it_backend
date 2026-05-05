package com.kdb.it.domain.budget.work.service;

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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 예산 작업 서비스
 *
 * <p>
 * 편성비목 조회, 편성률 일괄 적용, 편성 결과 조회 등
 * 예산 편성 작업(TAAABB_BBUGTM)의 비즈니스 로직을 처리합니다.
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
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetWorkService {

    /** 예산 데이터 접근 리포지토리 (TAAABB_BBUGTM) */
    private final BbugtmRepository bbugtmRepository;

    /** 공통코드 리포지토리 (TAAABB_CCODEM): 편성비목(DUP_IOE) 조회용 */
    private final CodeRepository codeRepository;

    /** 정보화사업 리포지토리 (TAAABB_BPROJM): 사업명 조회용 */
    private final ProjectRepository projectRepository;

    /** 품목 리포지토리 (TAAABB_BITEMM): 품목→프로젝트 매핑용 */
    private final ProjectItemRepository projectItemRepository;

    /** 전산업무비 리포지토리 (TAAABB_BCOSTM): 계약명 조회용 */
    private final CostRepository costRepository;

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
        List<Ccodem> ioeCodes = codeRepository.findByCttTpWithValidDate("DUP_IOE", null);

        // 기존 BBUGTM 데이터 조회 (편성률 확인용)
        List<Bbugtm> existingBudgets = bbugtmRepository.findByBgYyAndDelYn(bgYy, "N");

        return ioeCodes.stream().map(code -> {
            String prefix = extractPrefix(code.getCdId());

            // 2. 결재완료 요청금액 합계
            BigDecimal requestAmount = bbugtmRepository.sumApprovedAmountByPrefix(prefix, bgYy);
            if (requestAmount == null) {
                requestAmount = BigDecimal.ZERO;
            }

            // 3. 기존 편성률 조회
            Integer dupRt = existingBudgets.stream()
                    .filter(b -> b.getIoeC() != null && b.getIoeC().startsWith(prefix))
                    .map(Bbugtm::getDupRt)
                    .findFirst()
                    .orElse(null);

            return new BudgetWorkDto.IoeCategoryResponse(
                    code.getCdId(), code.getCdDes() != null ? code.getCdDes() : code.getCdNm(),
                    code.getCdva(), prefix, dupRt, requestAmount);
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

        for (BudgetWorkDto.RateItem rate : request.rates()) {
            String prefix = extractPrefix(rate.cdId());
            Integer dupRt = rate.dupRt();

            // 결재완료 BCOSTM 처리
            List<Bcostm> costs = bbugtmRepository.findApprovedCostsByPrefix(prefix, bgYy);
            for (Bcostm cost : costs) {
                BigDecimal dupBg = calculateDupBg(cost.getItMngcBg(), dupRt);

                Optional<Bbugtm> existing = bbugtmRepository
                        .findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                                bgYy, "BCOSTM", cost.getItMngcNo(),
                                cost.getItMngcSno(), cost.getIoeC(), "N");

                if (existing.isPresent()) {
                    // Upsert: UPDATE (JPA Dirty Checking)
                    existing.get().update(dupBg, dupRt);
                } else {
                    // Upsert: INSERT
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgMngNo(bgMngNo)
                            .bgSno(snoCounter)
                            .bgYy(bgYy)
                            .orcTb("BCOSTM")
                            .orcPkVl(cost.getItMngcNo())
                            .orcSnoVl(cost.getItMngcSno())
                            .ioeC(cost.getIoeC())
                            .dupBg(dupBg)
                            .dupRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                }
                totalRecords++;
            }

            // 결재완료 BITEMM 처리
            // ORC_TB = "BITEMM": BITEMM은 자체 PK(GCL_MNG_NO + GCL_SNO)를 보유하므로
            // 개별 품목 단위로 추적 가능. Plan 설계 문서의 "BPROJM"은 결재 조회 대상을
            // 지칭한 것이며, BBUGTM에 저장 시 실제 원본은 BITEMM임.
            List<Bitemm> items = bbugtmRepository.findApprovedItemsByPrefix(prefix, bgYy);
            for (Bitemm item : items) {
                // 환율 적용: gclAmt × coalesce(xcr, 1) → 원화 금액
                BigDecimal xcrVal = item.getXcr() != null ? item.getXcr() : BigDecimal.ONE;
                BigDecimal amountKrw = item.getGclAmt() != null ? item.getGclAmt().multiply(xcrVal) : BigDecimal.ZERO;
                BigDecimal dupBg = calculateDupBg(amountKrw, dupRt);

                Optional<Bbugtm> existing = bbugtmRepository
                        .findByBgYyAndOrcTbAndOrcPkVlAndOrcSnoVlAndIoeCAndDelYn(
                                bgYy, "BITEMM", item.getGclMngNo(),
                                item.getGclSno(), item.getGclDtt(), "N");

                if (existing.isPresent()) {
                    existing.get().update(dupBg, dupRt);
                } else {
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgMngNo(bgMngNo)
                            .bgSno(snoCounter)
                            .bgYy(bgYy)
                            .orcTb("BITEMM")
                            .orcPkVl(item.getGclMngNo())
                            .orcSnoVl(item.getGclSno())
                            .ioeC(item.getGclDtt())
                            .dupBg(dupBg)
                            .dupRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
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
        List<Bbugtm> priorBudgets = bbugtmRepository.findByBgYyAndDelYn(bgYy, "N");
        for (Bbugtm prior : priorBudgets) prior.delete();

        /* 자본예산 비목코드(IOE_CPIT) 목록 조회 — 자본/경상 구분용 */
        List<Ccodem> capitalCodes = codeRepository.findByCttTpWithValidDate("IOE_CPIT", null);
        java.util.Set<String> capitalPrefixes = new java.util.HashSet<>();
        for (Ccodem code : capitalCodes) {
            /* IOE-351-0100 → IOE-351 추출 (3세그먼트에서 2세그먼트로 축약) */
            String cdId = code.getCdId();
            int lastDash = cdId.lastIndexOf('-');
            if (lastDash > 0) {
                capitalPrefixes.add(cdId.substring(0, lastDash));
            }
            capitalPrefixes.add(cdId); // 전체 코드도 추가
        }

        for (BudgetWorkDto.ItemRate item : request.items()) {
            Integer assetDupRt = item.assetDupRt() != null ? item.assetDupRt() : 100;
            Integer costDupRt = item.costDupRt() != null ? item.costDupRt() : 100;

            if ("BPROJM".equals(item.orcTb())) {
                /* 정보화사업: BITEMM에서 해당 프로젝트의 최신 버전 품목(LST_YN='Y')만 조회 */
                List<Bitemm> items = projectItemRepository.findByPrjMngNoAndDelYnAndLstYn(
                        item.orcPkVl(), "N", "Y");

                for (Bitemm bitemm : items) {
                    boolean isCapital = isCapitalIoeCode(bitemm.getGclDtt(), capitalPrefixes);
                    int dupRt = isCapital ? assetDupRt : costDupRt;

                    BigDecimal xcrVal = bitemm.getXcr() != null ? bitemm.getXcr() : BigDecimal.ONE;
                    BigDecimal amountKrw = bitemm.getGclAmt() != null ? bitemm.getGclAmt().multiply(xcrVal) : BigDecimal.ZERO;
                    BigDecimal dupBg = calculateDupBg(amountKrw, dupRt);

                    /* 선 Soft Delete 후 전체 재삽입 방식이므로 Upsert 불필요 (항상 INSERT) */
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgMngNo(bgMngNo)
                            .bgSno(snoCounter)
                            .bgYy(bgYy)
                            .orcTb("BITEMM")
                            .orcPkVl(bitemm.getGclMngNo())
                            .orcSnoVl(bitemm.getGclSno())
                            .ioeC(bitemm.getGclDtt())
                            .dupBg(dupBg)
                            .dupRt(dupRt)
                            .build();
                    bbugtmRepository.save(bbugtm);
                    totalRecords++;
                }
            } else if ("BCOSTM".equals(item.orcTb())) {
                /* 전산업무비: 해당 전산업무비의 최신 버전(LST_YN='Y')만 처리 */
                List<Bcostm> costList = costRepository.findByItMngcNoAndDelYnAndLstYn(
                        item.orcPkVl(), "N", "Y");

                for (Bcostm cost : costList) {
                    boolean isCapital = isCapitalIoeCode(cost.getIoeC(), capitalPrefixes);
                    int dupRt = isCapital ? assetDupRt : costDupRt;
                    BigDecimal dupBg = calculateDupBg(cost.getItMngcBg(), dupRt);

                    /* 선 Soft Delete 후 전체 재삽입 방식이므로 Upsert 불필요 (항상 INSERT) */
                    snoCounter++;
                    Bbugtm bbugtm = Bbugtm.builder()
                            .bgMngNo(bgMngNo)
                            .bgSno(snoCounter)
                            .bgYy(bgYy)
                            .orcTb("BCOSTM")
                            .orcPkVl(cost.getItMngcNo())
                            .orcSnoVl(cost.getItMngcSno())
                            .ioeC(cost.getIoeC())
                            .dupBg(dupBg)
                            .dupRt(dupRt)
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
     * BBUGTM에서 예산년도별 데이터를 조회하고, 비목 접두어 기준으로 그룹핑하여
     * 요청금액/편성금액 합계를 반환합니다.
     * </p>
     *
     * [처리 순서]
     * 1. BBUGTM에서 BG_YY = :bgYy AND DEL_YN = 'N' 조회
     * 2. CCODEM에서 DUP_IOE 코드 조회 (비목명 매핑용)
     * 3. 접두어 기준 GROUP BY → SUM(요청금액), SUM(편성금액) 집계
     *
     * @param bgYy 예산년도
     * @return 비목별 요약 목록 + 합계
     */
    public BudgetWorkDto.SummaryResponse getSummary(String bgYy) {
        List<Bbugtm> budgets = bbugtmRepository.findByBgYyAndDelYn(bgYy, "N");

        // 편성비목 그룹 코드 조회 (DUP_IOE: 접두어 → 그룹명 매핑)
        List<Ccodem> dupIoeCodes = codeRepository.findByCttTpWithValidDate("DUP_IOE", null);

        // 세부 비목 코드 조회 (IOE_CPIT, IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE)
        // cdId → cdDes 매핑 (코드설명 기준으로 비목명 표시)
        List<String> detailCttTps = List.of("IOE_CPIT", "IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");
        Map<String, String> detailCodeNameMap = new LinkedHashMap<>();
        Map<String, Boolean> detailCodeCapitalMap = new LinkedHashMap<>();
        for (String cttTp : detailCttTps) {
            boolean isCapital = "IOE_CPIT".equals(cttTp);
            for (Ccodem code : codeRepository.findByCttTpWithValidDate(cttTp, null)) {
                detailCodeNameMap.put(code.getCdId(), code.getCdDes() != null ? code.getCdDes() : code.getCdNm());
                detailCodeCapitalMap.put(code.getCdId(), isCapital);
            }
        }

        // 접두어 → 그룹명 매핑 (DUP_IOE 기반, cdDes 우선 사용)
        Map<String, String> prefixToGroupName = new LinkedHashMap<>();
        List<String> prefixOrder = new ArrayList<>();
        for (Ccodem code : dupIoeCodes) {
            String prefix = extractPrefix(code.getCdId());
            prefixToGroupName.put(prefix, code.getCdDes() != null ? code.getCdDes() : code.getCdNm());
            prefixOrder.add(prefix);
        }

        // BBUGTM 데이터를 실제 ioeC 단위로 그룹핑
        Map<String, List<Bbugtm>> budgetsByIoeC = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if (b.getIoeC() != null) {
                budgetsByIoeC.computeIfAbsent(b.getIoeC(), k -> new ArrayList<>()).add(b);
            }
        }

        // 결재완료 원본 데이터에서 요청금액을 직접 계산 (BBUGTM 유무와 무관)
        // BCOSTM: ioeC별 itMngcBg 합계
        Map<String, BigDecimal> approvedCostAmountByIoeC = new LinkedHashMap<>();
        // BITEMM: gclDtt별 gclAmt * coalesce(xcr, 1) 합계
        Map<String, BigDecimal> approvedItemAmountByIoeC = new LinkedHashMap<>();

        for (String prefix : prefixOrder) {
            // 결재완료 전산업무비 요청금액 집계
            List<Bcostm> approvedCosts = bbugtmRepository.findApprovedCostsByPrefix(prefix, bgYy);
            for (Bcostm cost : approvedCosts) {
                if (cost.getIoeC() != null && cost.getItMngcBg() != null) {
                    approvedCostAmountByIoeC.merge(cost.getIoeC(), cost.getItMngcBg(), BigDecimal::add);
                }
            }
            // 결재완료 품목 요청금액 집계 (환율 적용)
            List<Bitemm> approvedItems = bbugtmRepository.findApprovedItemsByPrefix(prefix, bgYy);
            for (Bitemm item : approvedItems) {
                if (item.getGclDtt() != null && item.getGclAmt() != null) {
                    BigDecimal xcrVal = item.getXcr() != null ? item.getXcr() : BigDecimal.ONE;
                    BigDecimal amountKrw = item.getGclAmt().multiply(xcrVal);
                    approvedItemAmountByIoeC.merge(item.getGclDtt(), amountKrw, BigDecimal::add);
                }
            }
        }

        List<BudgetWorkDto.SummaryItem> items = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalDup = BigDecimal.ZERO;

        // 편성비목 접두어 순서대로 처리 (DUP_IOE 코드 순서 유지)
        for (String prefix : prefixOrder) {
            String groupName = prefixToGroupName.get(prefix);

            // CCODEM 세부 코드 중 이 접두어에 해당하는 코드 수집 (BBUGTM 데이터 유무와 무관)
            List<String> detailCodesForPrefix = new ArrayList<>();
            for (String cdId : detailCodeNameMap.keySet()) {
                if (cdId.startsWith(prefix)) {
                    detailCodesForPrefix.add(cdId);
                }
            }

            // BBUGTM에만 있고 CCODEM에는 없는 ioeC도 포함
            for (String ioeC : budgetsByIoeC.keySet()) {
                if (ioeC.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
                    detailCodesForPrefix.add(ioeC);
                }
            }

            // 원본 데이터에만 있고 CCODEM/BBUGTM에 없는 ioeC도 포함
            for (String ioeC : approvedCostAmountByIoeC.keySet()) {
                if (ioeC.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
                    detailCodesForPrefix.add(ioeC);
                }
            }
            for (String ioeC : approvedItemAmountByIoeC.keySet()) {
                if (ioeC.startsWith(prefix) && !detailCodesForPrefix.contains(ioeC)) {
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

            // cdDes(stripGroupPrefix 적용) 기준으로 동일 비목명 병합 (순서 유지)
            Map<String, List<String>> nameToIoeCodes = new LinkedHashMap<>();
            for (String ioeC : detailCodesForPrefix) {
                String rawName = detailCodeNameMap.getOrDefault(ioeC, ioeC);
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
                        .map(Bbugtm::getDupBg)
                        .filter(v -> v != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 요청금액: 결재완료 원본 데이터(BCOSTM/BITEMM)에서 직접 계산
                BigDecimal requestAmount = BigDecimal.ZERO;
                for (String ioeC : ioeCodes) {
                    requestAmount = requestAmount.add(
                            approvedCostAmountByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                    requestAmount = requestAmount.add(
                            approvedItemAmountByIoeC.getOrDefault(ioeC, BigDecimal.ZERO));
                }

                // 편성률 (BBUGTM 레코드가 있으면 해당 값, 없으면 null)
                Integer dupRt = allRecords.stream()
                        .map(Bbugtm::getDupRt)
                        .findFirst()
                        .orElse(null);

                // 자본예산 여부: 대표 코드의 cttTp가 IOE_CPIT이면 자본예산
                boolean capital = Boolean.TRUE.equals(detailCodeCapitalMap.get(representativeIoeC));

                items.add(new BudgetWorkDto.SummaryItem(
                        detailName, representativeIoeC, prefix, groupName, capital,
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
        List<Ccodem> ioeCodes = codeRepository.findByCttTpWithValidDate("DUP_IOE", null);
        List<Bbugtm> budgets = bbugtmRepository.findByBgYyAndDelYn(bgYy, "N");

        // 비목별 편성률 맵 (prefix → dupRt)
        Map<String, Integer> rateByPrefix = new LinkedHashMap<>();
        for (Bbugtm b : budgets) {
            if (b.getIoeC() != null && b.getDupRt() != null) {
                for (Ccodem code : ioeCodes) {
                    String prefix = extractPrefix(code.getCdId());
                    if (b.getIoeC().startsWith(prefix)) {
                        rateByPrefix.putIfAbsent(prefix, b.getDupRt());
                        break;
                    }
                }
            }
        }

        // 컬럼 헤더 정보 구성
        List<BudgetWorkDto.ProjectSummaryCategory> categoryHeaders = new ArrayList<>();
        for (Ccodem code : ioeCodes) {
            String prefix = extractPrefix(code.getCdId());
            Integer dupRt = rateByPrefix.getOrDefault(prefix, 0);
            categoryHeaders.add(new BudgetWorkDto.ProjectSummaryCategory(prefix, code.getCdNm(), code.getCdDes(), dupRt));
        }

        // 2. 사업별 + 비목별 이중 그룹핑
        // BITEMM → prjMngNo로 변환하여 프로젝트 단위로 그룹핑
        // key: 프로젝트관리번호 또는 전산업무비관리번호, value: { prefix → [요청금액, 편성금액] }
        Map<String, Map<String, BigDecimal[]>> projectCategoryMap = new LinkedHashMap<>();
        Map<String, String> orcTbMap = new LinkedHashMap<>();

        // BITEMM gclMngNo → prjMngNo 캐시 (중복 DB 조회 방지)
        Map<String, String> itemToPrjCache = new LinkedHashMap<>();

        for (Bbugtm b : budgets) {
            if (b.getOrcPkVl() == null) continue;

            // 그룹핑 키 결정: BITEMM은 프로젝트 단위로 통합
            String groupKey;
            String groupOrcTb;
            if ("BITEMM".equals(b.getOrcTb())) {
                // gclMngNo → prjMngNo 변환
                groupKey = itemToPrjCache.computeIfAbsent(b.getOrcPkVl(), gclMngNo -> {
                    List<Bitemm> items = projectItemRepository.findByGclMngNoAndDelYn(gclMngNo, "N");
                    return items.isEmpty() ? gclMngNo : items.get(0).getPrjMngNo();
                });
                groupOrcTb = "BPROJM";
            } else {
                groupKey = b.getOrcPkVl();
                groupOrcTb = b.getOrcTb();
            }

            orcTbMap.putIfAbsent(groupKey, groupOrcTb);
            projectCategoryMap.computeIfAbsent(groupKey, k -> new LinkedHashMap<>());

            // ioeC에서 매칭되는 prefix 찾기
            String matchedPrefix = null;
            for (Ccodem code : ioeCodes) {
                String prefix = extractPrefix(code.getCdId());
                if (b.getIoeC() != null && b.getIoeC().startsWith(prefix)) {
                    matchedPrefix = prefix;
                    break;
                }
            }
            if (matchedPrefix == null) continue;

            Map<String, BigDecimal[]> catMap = projectCategoryMap.get(groupKey);
            catMap.computeIfAbsent(matchedPrefix, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal[] amounts = catMap.get(matchedPrefix);
            // 요청금액 역산: dupBg / (dupRt / 100)
            if (b.getDupBg() != null && b.getDupRt() != null && b.getDupRt() > 0) {
                BigDecimal requestAmt = b.getDupBg()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(b.getDupRt()), 2, RoundingMode.HALF_UP);
                amounts[0] = amounts[0].add(requestAmt);
            }
            if (b.getDupBg() != null) {
                amounts[1] = amounts[1].add(b.getDupBg());
            }
        }

        // 3. 응답 구성
        List<BudgetWorkDto.ProjectSummaryItem> items = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;
        BigDecimal totalDup = BigDecimal.ZERO;

        for (Map.Entry<String, Map<String, BigDecimal[]>> entry : projectCategoryMap.entrySet()) {
            String orcPkVl = entry.getKey();
            Map<String, BigDecimal[]> catMap = entry.getValue();
            String orcTb = orcTbMap.get(orcPkVl);
            String name = resolveProjectName(orcTb, orcPkVl);

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
     * 원본테이블 유형에 따라 사업명/계약명 조회
     *
     * @param orcTb   원본테이블 (BPROJM/BCOSTM)
     * @param orcPkVl 원본PK값
     * @return 사업명 또는 계약명
     */
    private String resolveProjectName(String orcTb, String orcPkVl) {
        if ("BPROJM".equals(orcTb)) {
            return projectRepository.findByPrjMngNoAndDelYn(orcPkVl, "N")
                    .map(Bprojm::getPrjNm)
                    .orElse(orcPkVl);
        } else if ("BCOSTM".equals(orcTb)) {
            List<Bcostm> costs = costRepository.findByItMngcNoAndDelYn(orcPkVl, "N");
            if (!costs.isEmpty()) {
                return costs.get(0).getCttNm();
            }
            return orcPkVl;
        }
        return orcPkVl;
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
     * 편성비목 코드ID에서 접두어 추출
     *
     * <p>{@code DUP-IOE-237} → {@code "IOE-237"}</p>
     * <p>실제 IOE_C/GCL_DTT 값이 {@code IOE-237-0700} 형태이므로
     * {@code "DUP-"} 만 제거하여 {@code "IOE-237"} 접두어로 매칭합니다.</p>
     *
     * @param cdId 편성비목 코드ID (예: DUP-IOE-237)
     * @return 비목 접두어 (예: IOE-237)
     */
    private String extractPrefix(String cdId) {
        return cdId.replace("DUP-", "");
    }
}
