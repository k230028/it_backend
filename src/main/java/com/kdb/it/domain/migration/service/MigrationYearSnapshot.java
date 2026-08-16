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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산연도 하나의 기존 상태를 한 번에 읽어 둡니다.
 *
 * <p>중복 판정(§6.2)과 편성률 재적용 대상 구성(§7 5단계)이 같은 데이터를 필요로 하므로 각 요청에서 한 번만 읽습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MigrationYearSnapshot {

    /** 기존 편성행이 없을 때 적용하는 기본 편성률. */
    public static final int DEFAULT_RATE = 100;

    private final CostRepository costRepository;
    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final BbugtmRepository bbugtmRepository;
    private final BplanmRepository planRepository;

    /**
     * 사업 하나의 기존 편성률입니다. {@code applyItemRates}가 자본예산 계열 비목과 그 밖의 비목에 서로 다른 편성률을 적용할 수 있으므로 두 값을 나눠
     * 보관합니다.
     *
     * @param assetRate 자본예산 계열 비목({@link MigrationIoeCodes#CAPITAL_CODES}) 품목의 기존 편성률
     * @param costRate 그 밖의 비목 품목의 기존 편성률
     */
    public record ProjectRate(int assetRate, int costRate) {}

    /**
     * 연도 스냅샷 데이터입니다.
     *
     * @param bseYy 예산연도
     * @param costNaturalKeys 기존 전산업무비 자연키 집합 (§6.2 형식)
     * @param projectNoByNormalizedName 정규화 사업명 → 사업관리번호
     * @param existingPlanTypes 이미 존재하는 계획구분 집합
     * @param existingCostRateByCostNo 전산업무비관리번호 → 기존 편성률
     * @param existingRateByProjectNo 사업관리번호 → 기존 편성률 (자본·일반 분리)
     * @param allProjectNos 그 연도의 사업관리번호 전체
     * @param allCostNos 그 연도의 전산업무비 관리번호 전체
     */
    public record Data(
            String bseYy,
            Set<String> costNaturalKeys,
            Map<String, String> projectNoByNormalizedName,
            Set<String> existingPlanTypes,
            Map<String, Integer> existingCostRateByCostNo,
            Map<String, ProjectRate> existingRateByProjectNo,
            List<String> allProjectNos,
            List<String> allCostNos) {

        /** 정규화 사업명에 대응하는 기존 사업관리번호를 반환합니다. 없으면 null. */
        public String projectNoByName(String normalizedName) {
            return projectNoByNormalizedName.get(normalizedName);
        }

        /** 그 연도에 해당 계획구분의 계획이 이미 있는지 판정합니다. */
        public boolean planExists(String plnTp) {
            return existingPlanTypes.contains(plnTp);
        }

        /**
         * 전산업무비의 기존 편성률을 반환합니다.
         *
         * @param costBgNo 전산업무비관리번호 ({@code BBUGTM.PK_COL_NM}에 그대로 들어가는 값)
         * @return 기존 편성률. 편성행이 없으면 null
         */
        public Integer existingCostRateOf(String costBgNo) {
            return existingCostRateByCostNo.get(costBgNo);
        }

        /**
         * 사업의 기존 편성률을 반환합니다.
         *
         * <p>{@code BBUGTM}에는 사업관리번호로 키가 걸린 행이 없습니다({@code FNT_TB_NM='BITEMM'} + {@code
         * PK_COL_NM=GCL_MNG_NO}). 그래서 이 값은 그 사업의 품목 편성행에서 역산한 것입니다.
         *
         * @param abusMngNo 사업관리번호
         * @return 기존 편성률. 그 사업의 품목 편성행이 없으면 null
         */
        public ProjectRate existingProjectRateOf(String abusMngNo) {
            return existingRateByProjectNo.get(abusMngNo);
        }
    }

    /**
     * 예산연도의 기존 상태를 읽습니다.
     *
     * @param bseYy 예산연도 (4자리)
     * @return 스냅샷 데이터
     */
    public Data load(String bseYy) {
        Set<String> costKeys = new LinkedHashSet<>();
        List<String> costNos = new ArrayList<>();
        for (Bcostm cost : costRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            costKeys.add(costNaturalKey(cost));
            costNos.add(cost.getCostBgNo());
        }
        Map<String, String> projectByName = new LinkedHashMap<>();
        List<String> projectNos = new ArrayList<>();
        for (Bprojm project : projectRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            projectByName.putIfAbsent(normalizeName(project.getAbusNm()), project.getAbusMngNo());
            projectNos.add(project.getAbusMngNo());
        }
        Set<String> planTypes = new LinkedHashSet<>();
        for (String plnTp : List.of("신규", "조정")) {
            if (planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn(bseYy, plnTp, "N")) {
                planTypes.add(plnTp);
            }
        }

        List<Bbugtm> budgets = bbugtmRepository.findByBseYyAndDelYn(bseYy, "N");
        Map<String, Integer> costRates = new LinkedHashMap<>();
        for (Bbugtm budget : budgets) {
            if ("BCOSTM".equals(budget.getFntTbNm())) {
                costRates.putIfAbsent(
                        budget.getPkColNm(),
                        budget.getAsgRt() == null ? null : budget.getAsgRt().intValue());
            }
        }
        return new Data(
                bseYy,
                costKeys,
                projectByName,
                planTypes,
                costRates,
                projectRates(budgets, projectNos),
                List.copyOf(projectNos),
                costNos);
    }

    /**
     * 품목 편성행({@code FNT_TB_NM='BITEMM'})에서 사업별 기존 편성률을 역산합니다.
     *
     * <p>{@code BBUGTM}의 키는 품목관리번호({@code GCL_MNG_NO})이고 사업관리번호가 아닙니다(§3.5). 그래서 그 연도 사업들의 품목을 한 번에
     * 읽어 {@code GCL_MNG_NO → ABUS_MNG_NO} 대응을 만들고, 그 대응으로 편성행을 사업에 귀속시킵니다.
     *
     * <p>한 사업의 품목들이 서로 다른 편성률을 갖는 일은 정상 경로에서는 생기지 않습니다 — {@code applyItemRates}가 사업 단위로 같은 값을 씁니다.
     * 그래도 어긋난 값이 발견되면 **더 작은 쪽**을 채택하고 경고를 남깁니다. 이관이 기존 사업의 편성률을 조용히 **올리는** 것(원래 결함이 100%로 리셋한
     * 방향)을 구조적으로 막는 선택이며, 반대로 낮춰 잡는 경우는 로그로 드러납니다.
     *
     * @param budgets 그 연도의 미삭제 편성행 전체
     * @param projectNos 그 연도의 사업관리번호 전체
     * @return 사업관리번호 → 기존 편성률. 품목 편성행이 없는 사업은 키가 없습니다
     */
    private Map<String, ProjectRate> projectRates(List<Bbugtm> budgets, List<String> projectNos) {
        if (projectNos.isEmpty()) {
            return Map.of();
        }
        Map<String, String> projectNoByItemNo = new LinkedHashMap<>();
        for (Bitemm item : projectItemRepository.findByAbusMngNoInAndDelYn(projectNos, "N")) {
            projectNoByItemNo.putIfAbsent(item.getGclMngNo(), item.getAbusMngNo());
        }

        Map<String, Integer> assetRates = new LinkedHashMap<>();
        Map<String, Integer> costRates = new LinkedHashMap<>();
        for (Bbugtm budget : budgets) {
            if (!"BITEMM".equals(budget.getFntTbNm()) || budget.getAsgRt() == null) {
                continue;
            }
            String projectNo = projectNoByItemNo.get(budget.getPkColNm());
            if (projectNo == null) {
                continue;
            }
            Map<String, Integer> target =
                    MigrationIoeCodes.isCapital(budget.getIoeC()) ? assetRates : costRates;
            Integer previous = target.get(projectNo);
            int currentRate = budget.getAsgRt().intValue();
            if (previous == null) {
                target.put(projectNo, currentRate);
            } else if (!previous.equals(currentRate)) {
                log.warn(
                        "사업 {}의 품목 편성률이 서로 다릅니다({} vs {}). 더 작은 값을 유지합니다.",
                        projectNo,
                        previous,
                        currentRate);
                target.put(projectNo, Math.min(previous, currentRate));
            }
        }

        Map<String, ProjectRate> out = new LinkedHashMap<>();
        for (String projectNo : projectNos) {
            Integer asset = assetRates.get(projectNo);
            Integer cost = costRates.get(projectNo);
            if (asset == null && cost == null) {
                continue;
            }
            out.put(
                    projectNo,
                    new ProjectRate(
                            asset == null ? DEFAULT_RATE : asset,
                            cost == null ? DEFAULT_RATE : cost));
        }
        return out;
    }

    /**
     * 전산업무비 자연키를 만듭니다. §6.2의 `(BSE_YY, BG_UNT_ABUS_C, IOE_C, CTT_OPP_NM, CTT_NM)` 조합입니다.
     *
     * @param cost 전산업무비 엔티티
     * @return 파이프로 이은 자연키
     */
    public static String costNaturalKey(Bcostm cost) {
        return costNaturalKey(
                cost.getBseYy(),
                cost.getBgUntAbusC(),
                cost.getIoeC(),
                cost.getCttOppNm(),
                cost.getCttNm());
    }

    /**
     * 값으로 전산업무비 자연키를 만듭니다. 어댑터가 엑셀 행에서 같은 키를 만들어 중복을 판정합니다.
     *
     * @param bseYy 예산연도
     * @param abusCode 사업코드
     * @param ioeC 비목코드
     * @param vendorName 계약상대처명
     * @param contractName 계약명
     * @return 파이프로 이은 자연키. null 값은 빈 문자열로 접습니다
     */
    public static String costNaturalKey(
            String bseYy, String abusCode, String ioeC, String vendorName, String contractName) {
        return String.join(
                "|",
                nz(bseYy),
                nz(abusCode),
                nz(ioeC),
                nz(vendorName).trim(),
                nz(contractName).trim());
    }

    /** 사업명을 공백 압축해 동일성 판정 키로 만듭니다. */
    public static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
