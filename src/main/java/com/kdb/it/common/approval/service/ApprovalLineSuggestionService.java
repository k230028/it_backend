package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApplicationDto.ApprovalLineSuggestion.SuggestionReason;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.BranchCodes;
import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전산예산 요청서 결재라인 자동지정 제안.
 *
 * <p>국내점포 기안자에 대해 1차(같은 부점·같은 팀의 1차 직위)와 2차(같은 부점의 2차 직위)를 직위코드 공통코드 그룹 {@link
 * CommonCodeGroups#APF_DCR_PT}로 판정합니다. 후보가 정확히 1명일 때만 지정하고 그 외에는 사유를 돌려줍니다. 국외점포는 자동지정하지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalLineSuggestionService {

    /** 결재자 후보 사번 접두사 — 프론트 직원 검색과 같은 규칙 */
    static final String APPROVER_ENO_PREFIX = "K";

    private static final String TIER_TEAM_LEAD = "1";
    private static final String TIER_DEPT_HEAD = "2";

    private final UserRepository userRepository;
    private final CodeService codeService;

    /** 차수별 후보 판정 결과 */
    private record Pick(CuserI user, SuggestionReason reason) {}

    /**
     * 기안자 기준 결재라인을 제안합니다.
     *
     * @param drafterEno 기안자 사번
     * @return 제안 결과. 국외점포면 {@code foreignBranch=true}에 결재자 없음
     * @throws IllegalArgumentException 기안자 사용자 행이 없는 경우
     */
    public ApplicationDto.ApprovalLineSuggestion suggest(String drafterEno) {
        CuserI drafter =
                userRepository
                        .findByEno(drafterEno)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "사용자를 찾을 수 없습니다: " + drafterEno));
        if (BranchCodes.isForeign(drafter.getBbrC())) {
            return ApplicationDto.ApprovalLineSuggestion.foreign();
        }

        List<CodeDto.Response> positions =
                codeService.getCcodemsByCId(CommonCodeGroups.APF_DCR_PT, LocalDate.now());
        List<String> teamLeadCodes = codesOfTier(positions, TIER_TEAM_LEAD);
        List<String> deptHeadCodes = codesOfTier(positions, TIER_DEPT_HEAD);
        if (positions.isEmpty()) {
            log.warn("결재자직위코드 그룹({})이 비어 있어 자동지정하지 않습니다", CommonCodeGroups.APF_DCR_PT);
        }

        Pick teamLead =
                pick(
                        teamLeadCodes.isEmpty() || drafter.getTemC() == null
                                ? List.of()
                                : userRepository.findByBbrCAndTemCAndPtCInAndDelYn(
                                        drafter.getBbrC(), drafter.getTemC(), teamLeadCodes, "N"),
                        drafterEno);
        Pick deptHead =
                pick(
                        deptHeadCodes.isEmpty()
                                ? List.of()
                                : userRepository.findByBbrCAndPtCInAndDelYn(
                                        drafter.getBbrC(), deptHeadCodes, "N"),
                        drafterEno);
        if (teamLead.user() != null
                && deptHead.user() != null
                && Objects.equals(teamLead.user().getEno(), deptHead.user().getEno())) {
            deptHead = new Pick(null, SuggestionReason.DUPLICATE);
        }

        return ApplicationDto.ApprovalLineSuggestion.builder()
                .foreignBranch(false)
                .teamLead(toResponse(teamLead.user()))
                .teamLeadReason(teamLead.reason())
                .deptHead(toResponse(deptHead.user()))
                .deptHeadReason(deptHead.reason())
                .build();
    }

    private static List<String> codesOfTier(List<CodeDto.Response> positions, String tier) {
        return positions.stream()
                .filter(code -> tier.equals(code.getCdvaDtlC()))
                .map(CodeDto.Response::getCdva)
                .toList();
    }

    /** 사번 접두사·본인 제외 후 정확히 1명이면 지정, 0명이면 NONE, 2명 이상이면 MULTIPLE */
    private static Pick pick(List<CuserI> candidates, String drafterEno) {
        List<CuserI> eligible =
                candidates.stream()
                        .filter(
                                user ->
                                        user.getEno() != null
                                                && user.getEno().startsWith(APPROVER_ENO_PREFIX))
                        .filter(user -> !user.getEno().equals(drafterEno))
                        .toList();
        if (eligible.isEmpty()) {
            return new Pick(null, SuggestionReason.NONE);
        }
        if (eligible.size() > 1) {
            return new Pick(null, SuggestionReason.MULTIPLE);
        }
        return new Pick(eligible.get(0), null);
    }

    private static UserDto.ListResponse toResponse(CuserI user) {
        return user == null ? null : UserDto.ListResponse.fromEntity(user, user.getBbrNm());
    }
}
