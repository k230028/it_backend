package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산연도 하나의 기존 상태를 한 번에 읽어 둡니다.
 *
 * <p>중복 판정(§6.2)과 편성률 재적용 대상 구성(§7 5단계)이 같은 데이터를 필요로 하므로 각 요청에서 한 번만 읽습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MigrationYearSnapshot {

    private final CostRepository costRepository;
    private final ProjectRepository projectRepository;
    private final BbugtmRepository bbugtmRepository;
    private final BplanmRepository planRepository;

    /**
     * 연도 스냅샷 데이터입니다.
     *
     * @param bseYy 예산연도
     * @param costNaturalKeys 기존 전산업무비 자연키 집합 (§6.2 형식)
     * @param projectNoByNormalizedName 정규화 사업명 → 사업관리번호
     * @param existingPlanTypes 이미 존재하는 계획구분 집합
     * @param existingRateByOrigin `{orcTb}|{orcPkVl}` → 기존 편성률
     * @param allCostNos 그 연도의 전산업무비 관리번호 전체
     */
    public record Data(
            String bseYy,
            Set<String> costNaturalKeys,
            Map<String, String> projectNoByNormalizedName,
            Set<String> existingPlanTypes,
            Map<String, Integer> existingRateByOrigin,
            List<String> allCostNos) {

        /** 정규화 사업명에 대응하는 기존 사업관리번호를 반환합니다. 없으면 null. */
        public String projectNoByName(String normalizedName) {
            return projectNoByNormalizedName.get(normalizedName);
        }

        /** 그 연도에 해당 계획구분의 계획이 이미 있는지 판정합니다. */
        public boolean planExists(String plnTp) {
            return existingPlanTypes.contains(plnTp);
        }

        /** 원천(테이블, PK)의 기존 편성률을 반환합니다. 없으면 null. */
        public Integer existingRateOf(String orcTb, String orcPkVl) {
            return existingRateByOrigin.get(orcTb + "|" + orcPkVl);
        }

        /** 그 연도 사업관리번호 전체를 반환합니다. */
        public List<String> allProjectNos() {
            return List.copyOf(projectNoByNormalizedName.values());
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
        for (Bprojm project : projectRepository.findByBseYyAndLstYnAndDelYn(bseYy, "Y", "N")) {
            projectByName.putIfAbsent(normalizeName(project.getAbusNm()), project.getAbusMngNo());
        }
        Set<String> planTypes = new LinkedHashSet<>();
        for (String plnTp : List.of("신규", "조정")) {
            if (planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn(bseYy, plnTp, "N")) {
                planTypes.add(plnTp);
            }
        }
        Map<String, Integer> rateByOrigin = new LinkedHashMap<>();
        for (Bbugtm budget : bbugtmRepository.findByBseYyAndDelYn(bseYy, "N")) {
            rateByOrigin.putIfAbsent(
                    budget.getFntTbNm() + "|" + budget.getPkColNm(), budget.getAsgRt());
        }
        return new Data(bseYy, costKeys, projectByName, planTypes, rateByOrigin, costNos);
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
