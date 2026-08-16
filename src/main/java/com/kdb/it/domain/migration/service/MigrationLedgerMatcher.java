package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.dto.MigrationDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 종합본·조정본의 행을 이미 반입된 요청 원장에 매칭합니다.
 *
 * <p>이 화면은 원장을 만들지 않고 편성금액만 계산하므로(설계 §2.1), 각 행이 어느 원장을 가리키는지 정하는 것이 반영의 출발점입니다. 매칭에 실패한 행은
 * 조용히 새 원장을 만들지 않고 {@link Outcome#NOT_FOUND}·{@link Outcome#AMBIGUOUS}로 돌려보내 관리자가 결정하게 합니다.
 *
 * <p>전산업무비 자연키에서 사업코드를 뺀 이유는 {@link MigrationYearSnapshot#costDeptKey} Javadoc에 있습니다.
 */
@Component
public class MigrationLedgerMatcher {

    /** 매칭 결과 종류입니다. */
    public enum Outcome {
        /** 대상 원장을 하나로 특정했습니다. */
        MATCHED,
        /** 대상이 없습니다. 후보는 참고용입니다. */
        NOT_FOUND,
        /** 후보가 둘 이상이라 특정하지 못했습니다. */
        AMBIGUOUS
    }

    /**
     * 매칭 결과입니다.
     *
     * @param outcome 결과 종류
     * @param pk 매칭된 원장 PK. {@code MATCHED}가 아니면 null
     * @param candidates 관리자에게 보여줄 후보. 없으면 빈 목록
     */
    public record Match(Outcome outcome, String pk, List<MigrationDto.Candidate> candidates) {

        /** 대상을 특정한 결과입니다. */
        public static Match matched(String pk) {
            return new Match(Outcome.MATCHED, pk, List.of());
        }

        /** 대상이 없는 결과입니다. */
        public static Match notFound(List<MigrationDto.Candidate> candidates) {
            return new Match(Outcome.NOT_FOUND, null, candidates);
        }

        /** 후보가 둘 이상인 결과입니다. */
        public static Match ambiguous(List<MigrationDto.Candidate> candidates) {
            return new Match(Outcome.AMBIGUOUS, null, candidates);
        }
    }

    /**
     * 정규화 사업명으로 정보화사업을 찾습니다.
     *
     * @param normalizedName {@link MigrationYearSnapshot#normalizeName}으로 정규화한 사업명
     * @param snapshot 연도 스냅샷
     * @return 매칭 결과. 못 찾으면 그 연도 사업 전체가 후보다
     */
    public Match matchProject(String normalizedName, MigrationYearSnapshot.Data snapshot) {
        String projectNo = snapshot.projectNoByName(normalizedName);
        if (projectNo != null) {
            return Match.matched(projectNo);
        }
        return Match.notFound(allProjectCandidates(snapshot));
    }

    /**
     * 부서코드로 경상사업({@code ODN_YN='Y'})을 찾습니다.
     *
     * <p>사업명으로 찾지 않습니다 — 종합본 위임예산 시트는 부점명만 갖고 있고, 1단계가 만든 경상사업의 사업명은 부점이 시트 ②에 적은 임의 문자열이라
     * 두 값이 일치할 근거가 없습니다.
     *
     * @param deptCode 부점명을 해석한 부서코드. null·공백이면 대상 없음
     * @param snapshot 연도 스냅샷
     * @return 매칭 결과. 그 부서에 경상사업이 둘 이상이면 {@code AMBIGUOUS}
     */
    public Match matchOrdinaryProject(String deptCode, MigrationYearSnapshot.Data snapshot) {
        if (deptCode == null || deptCode.isBlank()) {
            return Match.notFound(allProjectCandidates(snapshot));
        }
        List<String> candidates = snapshot.ordinaryProjectNosOfDept(deptCode);
        if (candidates.size() == 1) {
            return Match.matched(candidates.get(0));
        }
        if (candidates.isEmpty()) {
            return Match.notFound(allProjectCandidates(snapshot));
        }
        return Match.ambiguous(projectCandidates(candidates, snapshot));
    }

    /**
     * 부서 기준 자연키로 전산업무비를 찾습니다. 완화 매칭 3단계입니다.
     *
     * <ol>
     *   <li>부서·비목·상대처·계약명 5요소 정확 일치
     *   <li>상대처를 뺀 4요소 일치 — 계약업체명 공란 표기가 두 문서에서 흔들립니다
     *   <li>부서+비목만으로 좁힌 후보 제시
     * </ol>
     *
     * @param bseYy 예산연도
     * @param deptCode 요구부서를 해석한 부서코드
     * @param ioeC 비목코드
     * @param vendorName 계약업체명 (공란 허용)
     * @param contractName 요구내역 = 계약명
     * @param snapshot 연도 스냅샷
     * @return 매칭 결과
     */
    public Match matchCost(
            String bseYy,
            String deptCode,
            String ioeC,
            String vendorName,
            String contractName,
            MigrationYearSnapshot.Data snapshot) {
        String exact =
                snapshot.costNoByDeptKey(
                        MigrationYearSnapshot.costDeptKey(
                                bseYy, deptCode, ioeC, vendorName, contractName));
        if (exact != null) {
            return Match.matched(exact);
        }

        String normalizedContract = MigrationYearSnapshot.normalizeText(contractName);
        List<String> sameDeptIoe = snapshot.costNosByDeptAndIoe(deptCode, ioeC);
        List<String> byContract = new ArrayList<>();
        for (String costNo : sameDeptIoe) {
            MigrationYearSnapshot.CostRef ref = snapshot.costOf(costNo);
            if (ref == null) {
                continue;
            }
            // label은 "계약명 / 상대처" 형식이라 계약명만 떼어 비교한다
            String label = ref.label();
            int separator = label.lastIndexOf(" / ");
            String ledgerContract = separator < 0 ? label : label.substring(0, separator);
            if (MigrationYearSnapshot.normalizeText(ledgerContract).equals(normalizedContract)) {
                byContract.add(costNo);
            }
        }
        if (byContract.size() == 1) {
            return Match.matched(byContract.get(0));
        }
        if (byContract.size() > 1) {
            return Match.ambiguous(costCandidates(byContract, snapshot));
        }
        return Match.notFound(costCandidates(sameDeptIoe, snapshot));
    }

    /** 그 연도 사업 전체를 후보로 냅니다. 후보를 비워 두면 화면에 드롭다운이 그려지지 않습니다. */
    private List<MigrationDto.Candidate> allProjectCandidates(
            MigrationYearSnapshot.Data snapshot) {
        return projectCandidates(snapshot.allProjectNos(), snapshot);
    }

    private List<MigrationDto.Candidate> projectCandidates(
            List<String> projectNos, MigrationYearSnapshot.Data snapshot) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        for (String projectNo : projectNos) {
            String name = snapshot.projectNameOf(projectNo);
            out.add(new MigrationDto.Candidate(projectNo, name == null ? projectNo : name));
        }
        return out;
    }

    private List<MigrationDto.Candidate> costCandidates(
            List<String> costNos, MigrationYearSnapshot.Data snapshot) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        for (String costNo : costNos) {
            MigrationYearSnapshot.CostRef ref = snapshot.costOf(costNo);
            out.add(new MigrationDto.Candidate(costNo, ref == null ? costNo : ref.label()));
        }
        return out;
    }
}
