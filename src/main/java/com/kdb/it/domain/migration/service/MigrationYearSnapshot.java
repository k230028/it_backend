package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산연도 하나의 기존 상태를 한 번에 읽어 둡니다.
 *
 * <p>매칭({@code MigrationLedgerMatcher})과 배분({@code MigrationAllocationPlanner})이 같은 데이터를 필요로 하므로
 * 각 요청에서 한 번만 읽습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MigrationYearSnapshot {

    /** 기존 편성행이 없을 때 적용하는 기본 편성률. */
    public static final BigDecimal DEFAULT_RATE = BigDecimal.valueOf(100);

    private final CostRepository costRepository;
    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final BbugtmRepository bbugtmRepository;
    private final BplanmRepository planRepository;

    /**
     * 매칭된 사업의 요청 품목 하나입니다. 실효 편성률의 분모와 편성행 키가 여기서 나옵니다.
     *
     * @param gclMngNo 품목관리번호 ({@code BBUGTM.PK_COL_NM})
     * @param sno 품목 일련번호 ({@code BBUGTM.FNT_TB_CRY_SNO})
     * @param ioeC 비목코드
     * @param amount 요청금액. null이면 0원으로 접습니다
     */
    public record RequestItem(String gclMngNo, Integer sno, String ioeC, BigDecimal amount) {}

    /**
     * 매칭 대상 전산업무비 한 건입니다.
     *
     * @param costBgNo 전산업무비관리번호 ({@code BBUGTM.PK_COL_NM})
     * @param bgSno 예산일련번호 ({@code BBUGTM.FNT_TB_CRY_SNO})
     * @param ioeC 비목코드
     * @param amount 요청금액 ({@code COST_TOT_XP_AMT})
     * @param label 후보 표시용 문구 (계약명 + 상대처)
     */
    public record CostRef(
            String costBgNo, Integer bgSno, String ioeC, BigDecimal amount, String label) {}

    /**
     * 연도 스냅샷 데이터입니다.
     *
     * @param bseYy 예산연도
     * @param projectNoByNormalizedName 정규화 사업명 → 사업관리번호
     * @param projectNameByNo 사업관리번호 → 사업명
     * @param ordinaryProjectNosByDept 부서코드 → 경상사업({@code ODN_YN='Y'}) 사업관리번호 목록
     * @param itemsByProjectNo 사업관리번호 → 활성·최신 요청 품목 목록
     * @param costByNo 전산업무비관리번호 → 전산업무비 대상
     * @param costNoByDeptKey 부서 기준 자연키({@link #costDeptKey}) → 전산업무비관리번호
     * @param costNosByDeptIoe 부서+비목 키 → 전산업무비관리번호 목록 (완화 매칭 3단계용)
     * @param bgUntAbusCByCostNo 전산업무비관리번호 → 원장의 사업코드
     * @param existingPlanTypes 이미 존재하는 계획구분 집합
     * @param existingCostRateByCostNo 전산업무비관리번호 → 기존 편성률 (원본 보존)
     * @param existingItemRateByItemNo 품목관리번호 → 기존 편성률 (원본 보존)
     * @param allProjectNos 그 연도의 사업관리번호 전체
     * @param allCostNos 그 연도의 전산업무비 관리번호 전체
     */
    public record Data(
            String bseYy,
            Map<String, String> projectNoByNormalizedName,
            Map<String, String> projectNameByNo,
            Map<String, List<String>> ordinaryProjectNosByDept,
            Map<String, List<RequestItem>> itemsByProjectNo,
            Map<String, CostRef> costByNo,
            Map<String, String> costNoByDeptKey,
            Map<String, List<String>> costNosByDeptIoe,
            Map<String, String> bgUntAbusCByCostNo,
            Set<String> existingPlanTypes,
            Map<String, BigDecimal> existingCostRateByCostNo,
            Map<String, BigDecimal> existingItemRateByItemNo,
            List<String> allProjectNos,
            List<String> allCostNos) {

        /** 정규화 사업명에 대응하는 기존 사업관리번호를 반환합니다. 없으면 null. */
        public String projectNoByName(String normalizedName) {
            return projectNoByNormalizedName.get(normalizedName);
        }

        /** 사업관리번호의 사업명입니다. 후보 라벨에 씁니다. 없으면 null. */
        public String projectNameOf(String abusMngNo) {
            return projectNameByNo.get(abusMngNo);
        }

        /** 그 연도에 해당 계획구분의 계획이 이미 있는지 판정합니다. */
        public boolean planExists(String plnTp) {
            return existingPlanTypes.contains(plnTp);
        }

        /** 부서의 경상사업({@code ODN_YN='Y'}) 관리번호 목록입니다. 없으면 빈 목록. */
        public List<String> ordinaryProjectNosOfDept(String deptCode) {
            return ordinaryProjectNosByDept.getOrDefault(deptCode, List.of());
        }

        /** 사업의 활성·최신 요청 품목 목록입니다. 없으면 빈 목록. */
        public List<RequestItem> itemsOfProject(String abusMngNo) {
            return itemsByProjectNo.getOrDefault(abusMngNo, List.of());
        }

        /** 전산업무비관리번호로 대상을 조회합니다. 없으면 null. */
        public CostRef costOf(String costBgNo) {
            return costByNo.get(costBgNo);
        }

        /** 부서 기준 자연키({@link #costDeptKey})로 전산업무비관리번호를 조회합니다. 없으면 null. */
        public String costNoByDeptKey(String key) {
            return costNoByDeptKey.get(key);
        }

        /** 부서+비목만으로 좁힌 전산업무비 후보입니다. 완화 매칭 3단계에서 씁니다. */
        public List<String> costNosByDeptAndIoe(String deptCode, String ioeC) {
            return costNosByDeptIoe.getOrDefault(nz(deptCode) + "|" + nz(ioeC), List.of());
        }

        /** 원장에 이미 있는 사업코드입니다. 공백이면 null — 종합본 값으로 채울 대상이라는 뜻입니다. */
        public String bgUntAbusCOf(String costBgNo) {
            String value = bgUntAbusCByCostNo.get(costBgNo);
            return value == null || value.isBlank() ? null : value;
        }

        /** 전산업무비의 기존 편성률입니다. 편성행이 없으면 null. */
        public BigDecimal existingCostRateOf(String costBgNo) {
            return existingCostRateByCostNo.get(costBgNo);
        }
    }

    /**
     * 예산연도의 기존 상태를 읽습니다.
     *
     * <p>매칭(§4)과 배분(§3)이 같은 데이터를 필요로 하므로 각 요청에서 한 번만 읽습니다. 품목 편성률은 자본·일반으로 접지 않고 품목관리번호별 원본을
     * 그대로 보존합니다 — 종합본이 한 사업 안에서 비목그룹마다 다른 편성률을 주기 때문입니다.
     *
     * @param bseYy 예산연도 (4자리)
     * @return 스냅샷 데이터
     */
    public Data load(String bseYy) {
        Map<String, CostRef> costByNo = new LinkedHashMap<>();
        Map<String, String> costNoByDeptKey = new LinkedHashMap<>();
        Map<String, List<String>> costNosByDeptIoe = new LinkedHashMap<>();
        Map<String, String> bgUntAbusCByCostNo = new LinkedHashMap<>();
        List<String> costNos = new ArrayList<>();
        for (Bcostm cost : costRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            String costNo = cost.getCostBgNo();
            costByNo.putIfAbsent(
                    costNo,
                    new CostRef(
                            costNo,
                            cost.getBgSno(),
                            cost.getIoeC(),
                            cost.getCostTotXpAmt() == null
                                    ? BigDecimal.ZERO
                                    : cost.getCostTotXpAmt(),
                            nz(cost.getCttNm()) + " / " + nz(cost.getCttOppNm())));
            costNoByDeptKey.putIfAbsent(
                    costDeptKey(
                            bseYy,
                            cost.getCostSvnDpmC(),
                            cost.getIoeC(),
                            cost.getCttOppNm(),
                            cost.getCttNm()),
                    costNo);
            costNosByDeptIoe
                    .computeIfAbsent(
                            nz(cost.getCostSvnDpmC()) + "|" + nz(cost.getIoeC()),
                            ignored -> new ArrayList<>())
                    .add(costNo);
            bgUntAbusCByCostNo.put(costNo, cost.getBgUntAbusC());
            costNos.add(costNo);
        }

        Map<String, String> projectByName = new LinkedHashMap<>();
        Map<String, String> projectNameByNo = new LinkedHashMap<>();
        Map<String, List<String>> ordinaryByDept = new LinkedHashMap<>();
        List<String> projectNos = new ArrayList<>();
        for (Bprojm project : projectRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            String projectNo = project.getAbusMngNo();
            projectByName.putIfAbsent(normalizeName(project.getAbusNm()), projectNo);
            projectNameByNo.putIfAbsent(projectNo, project.getAbusNm());
            if ("Y".equals(project.getOdnYn())) {
                ordinaryByDept
                        .computeIfAbsent(nz(project.getSvnDpmC()), ignored -> new ArrayList<>())
                        .add(projectNo);
            }
            projectNos.add(projectNo);
        }

        Map<String, List<RequestItem>> itemsByProject = new LinkedHashMap<>();
        if (!projectNos.isEmpty()) {
            for (Bitemm item : projectItemRepository.findByAbusMngNoInAndDelYn(projectNos, "N")) {
                if (!"Y".equals(item.getLstYn())) {
                    continue;
                }
                itemsByProject
                        .computeIfAbsent(item.getAbusMngNo(), ignored -> new ArrayList<>())
                        .add(
                                new RequestItem(
                                        item.getGclMngNo(),
                                        item.getSno(),
                                        item.getIoeC(),
                                        item.getAmt() == null ? BigDecimal.ZERO : item.getAmt()));
            }
        }

        Set<String> planTypes = new LinkedHashSet<>();
        for (String plnTp : List.of("신규", "조정")) {
            if (planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn(bseYy, plnTp, "N")) {
                planTypes.add(plnTp);
            }
        }

        Map<String, BigDecimal> costRates = new LinkedHashMap<>();
        Map<String, BigDecimal> itemRates = new LinkedHashMap<>();
        for (Bbugtm budget : bbugtmRepository.findByBseYyAndDelYn(bseYy, "N")) {
            if ("BCOSTM".equals(budget.getFntTbNm())) {
                costRates.putIfAbsent(budget.getPkColNm(), budget.getAsgRt());
            } else if ("BITEMM".equals(budget.getFntTbNm())) {
                itemRates.putIfAbsent(budget.getPkColNm(), budget.getAsgRt());
            }
        }

        return new Data(
                bseYy,
                projectByName,
                projectNameByNo,
                ordinaryByDept,
                itemsByProject,
                costByNo,
                costNoByDeptKey,
                costNosByDeptIoe,
                bgUntAbusCByCostNo,
                planTypes,
                costRates,
                itemRates,
                List.copyOf(projectNos),
                costNos);
    }

    /**
     * 부서 기준 전산업무비 자연키를 만듭니다.
     *
     * <p>사업코드({@code BG_UNT_ABUS_C})를 키에서 뺐습니다. 편성요청서 양식에 그 열이 없어 1단계가 만든 행은 대부분 null이므로,
     * 사업코드를 키에 두면 같은 계약이 매칭되지 않고 새 행으로 다시 생깁니다.
     *
     * @param bseYy 예산연도
     * @param deptCode 주관부서코드 ({@code COST_SVN_DPM_C})
     * @param ioeC 비목코드
     * @param vendorName 계약상대처명. 공백 압축·소문자로 정규화됩니다
     * @param contractName 계약명. 같은 규칙으로 정규화됩니다
     * @return 파이프로 이은 자연키
     */
    public static String costDeptKey(
            String bseYy, String deptCode, String ioeC, String vendorName, String contractName) {
        return String.join(
                "|",
                nz(bseYy),
                nz(deptCode),
                nz(ioeC),
                normalizeText(vendorName),
                normalizeText(contractName));
    }

    /** 사업명을 공백 압축해 동일성 판정 키로 만듭니다. */
    public static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    /** 상대처·계약명 비교용 정규화입니다. 공백을 모두 없애고 소문자로 접습니다. */
    public static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
