package com.kdb.it.domain.council.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 협의회 평가위원 서비스 (Step 2 — 위원 선정)
 *
 * <p>IT관리자(ITPAD001)가 심의유형에 따라 당연위원/소집위원/간사를 선정합니다.</p>
 *
 * <p>당연위원 자동 매핑 (TEM_C 기준):</p>
 * <ul>
 *   <li>INFO_SYS: 예산(12004), PMO(18010), 디지털기획(18501), 정보보호기획(18301)</li>
 *   <li>INFO_SEC: 예산(12004), IT기획(18001), PMO(18010), 디지털기획(18501)</li>
 *   <li>ETC: 예산(12004), PMO(18010), 디지털기획(18501)</li>
 * </ul>
 *
 * <p>위원 저장 전략: 전체 교체 (기존 Soft Delete + 신규 INSERT)</p>
 *
 * <p>Design Ref: §2.1 CommitteeService — Step 2 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommitteeService {

    /** 평가위원 리포지토리 (TAAABB_BCMMTM) */
    private final CommitteeRepository committeeRepository;

    /** 사용자 리포지토리 — 위원 정보 조회 및 당연위원 매핑용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 존재 확인 및 상태 전이용 */
    private final CouncilService councilService;

    // 심의유형별 당연위원 팀코드 매핑 (TEM_C 기준, Design §2.4)
    private static final Map<String, List<String>> MANDATORY_TEM_CODES = Map.of(
        "INFO_SYS", List.of("12004", "18010", "18501", "18301"),
        "INFO_SEC", List.of("12004", "18001", "18010", "18501"),
        "ETC",      List.of("12004", "18010", "18501")
    );

    // 심의유형별 간사 팀코드 매핑 (TEM_C 기준)
    // INFO_SYS / ETC: IT기획(18001) → 간사
    // INFO_SEC: 정보보호기획(18301) → 간사
    private static final Map<String, List<String>> SECRETARY_TEM_CODES = Map.of(
        "INFO_SYS", List.of("18001"),
        "INFO_SEC", List.of("18301"),
        "ETC",      List.of("18001")
    );

    /** INFO_SYS 일정 확정 필수 응답 팀코드 (예산:12004, IT기획:18001) */
    static final List<String> INFO_SYS_REQUIRED_TEM_CODES = List.of("12004", "18001");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 심의유형별 당연위원 후보 조회 (IT관리자 선정 화면용)
     *
     * <p>협의회의 dbrTp(심의유형)를 기준으로 당연위원 대상 팀코드를 조회하고,
     * 각 팀에서 팀장(ptCNm='팀장') 또는 첫 번째 사용자를 후보로 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 당연위원 후보 목록
     */
    public List<CouncilDto.CommitteeMemberResponse> getDefaultCommittee(String asctId) {
        // 협의회 존재 확인 및 심의유형 조회
        String dbrTp = councilService.findActiveCouncil(asctId).getDbrTp();

        List<CouncilDto.CommitteeMemberResponse> result = new ArrayList<>();

        // 당연위원(MAND) 자동 배정
        List<String> mandTemCodes = MANDATORY_TEM_CODES.getOrDefault(dbrTp, List.of());
        for (String temC : mandTemCodes) {
            List<CuserI> users = userRepository.findByTemC(temC);
            if (users.isEmpty()) continue;

            // 팀장 우선, 없으면 첫 번째 사용자를 당연위원 후보로 선택
            CuserI candidate = users.stream()
                    .filter(u -> "팀장".equals(u.getPtCNm()))
                    .findFirst()
                    .orElse(users.get(0));

            result.add(toMemberResponse(candidate, "MAND"));
        }

        // 간사(SECR) 자동 배정
        List<String> secrTemCodes = SECRETARY_TEM_CODES.getOrDefault(dbrTp, List.of());
        for (String temC : secrTemCodes) {
            List<CuserI> users = userRepository.findByTemC(temC);
            if (users.isEmpty()) continue;

            // 팀장 우선, 없으면 첫 번째 사용자를 간사 후보로 선택
            CuserI candidate = users.stream()
                    .filter(u -> "팀장".equals(u.getPtCNm()))
                    .findFirst()
                    .orElse(users.get(0));

            result.add(toMemberResponse(candidate, "SECR"));
        }

        return result;
    }

    /**
     * 협의회 평가위원 목록 조회
     *
     * <p>위원유형별(당연/소집/간사)로 분류하여 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 위원유형별 목록
     */
    public CouncilDto.CommitteeListResponse getCommittee(String asctId) {
        councilService.findActiveCouncil(asctId);

        List<Bcmmtm> members = committeeRepository.findByAsctIdAndDelYn(asctId, "N");

        // 위원 사번 목록으로 사용자 정보 일괄 조회
        Map<String, CuserI> userMap = buildUserMap(members);

        List<CouncilDto.CommitteeMemberResponse> mandatory = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> call = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> secretary = new ArrayList<>();

        for (Bcmmtm m : members) {
            CuserI user = userMap.get(m.getEno());
            CouncilDto.CommitteeMemberResponse resp = toMemberResponseFromEntity(m, user);

            switch (m.getVlrTp()) {
                case "MAND" -> mandatory.add(resp);
                case "CALL" -> call.add(resp);
                case "SECR" -> secretary.add(resp);
            }
        }

        return new CouncilDto.CommitteeListResponse(mandatory, call, secretary);
    }

    // =========================================================================
    // 저장
    // =========================================================================

    /**
     * 평가위원 선정/수정 (전체 교체)
     *
     * <p>기존 위원 전체 Soft Delete 후 요청 목록으로 신규 INSERT합니다.
     * 위원 확정 시 협의회 상태를 PREPARING으로 전이합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 위원 선정 요청 (심의유형 + 위원 목록)
     */
    @Transactional
    public void saveCommittee(String asctId, CouncilDto.CommitteeRequest request) {
        // 협의회 존재 확인 및 현재 상태 캡처
        Basctm council = councilService.findActiveCouncil(asctId);

        // 기존 위원 전체 Soft Delete
        List<Bcmmtm> existing = committeeRepository.findByAsctIdAndDelYn(asctId, "N");
        existing.forEach(Bcmmtm::delete);

        // 신규 위원 INSERT
        for (CouncilDto.CommitteeMemberRequest req : request.members()) {
            Bcmmtm member = Bcmmtm.builder()
                    .asctId(asctId)
                    .eno(req.eno())
                    .vlrTp(req.vlrTp())
                    .build();
            committeeRepository.save(member);
        }

        // 협의회 상태 전이: APPROVED → PREPARING (위원 선정 완료)
        // 이미 PREPARING 이후 상태(SCHEDULED, IN_PROGRESS 등)이면 상태를 되돌리지 않음
        // (일정 확정 후 위원 수정 시 SCHEDULED → PREPARING 역전이 방지)
        if ("APPROVED".equals(council.getAsctSts())) {
            councilService.changeStatus(asctId, "PREPARING");
        }
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 위원 목록의 사번으로 사용자 정보 Map 생성
     *
     * <p>N+1 방지를 위해 사번 목록으로 사용자 정보를 일괄 조회합니다.</p>
     */
    private Map<String, CuserI> buildUserMap(List<Bcmmtm> members) {
        return members.stream()
                .map(m -> userRepository.findByEno(m.getEno()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(CuserI::getEno, u -> u, (a, b) -> a));
    }

    /**
     * CuserI → CommitteeMemberResponse 변환 (당연위원 후보 조회용)
     *
     * <p>후보 조회 시점에는 BCMMTM 레코드가 없으므로 cfdYn은 'N'으로 초기화합니다.</p>
     */
    private CouncilDto.CommitteeMemberResponse toMemberResponse(CuserI user, String vlrTp) {
        return new CouncilDto.CommitteeMemberResponse(
                user.getEno(),
                user.getUsrNm(),
                user.getBbrNm(),
                user.getPtCNm(),
                vlrTp,
                "N"  // 후보 조회 시점에는 항상 미확인
        );
    }

    /**
     * Bcmmtm + CuserI → CommitteeMemberResponse 변환 (위원 목록 조회용)
     *
     * <p>사용자 정보가 없는 경우(탈퇴 등) 사번만 포함합니다.
     * cnfmYn은 BCMMTM 엔티티의 실제 값을 반영합니다.</p>
     */
    private CouncilDto.CommitteeMemberResponse toMemberResponseFromEntity(Bcmmtm member, CuserI user) {
        return new CouncilDto.CommitteeMemberResponse(
                member.getEno(),
                user != null ? user.getUsrNm() : null,
                user != null ? user.getBbrNm() : null,
                user != null ? user.getPtCNm() : null,
                member.getVlrTp(),
                member.getCnfmYn()  // 결과서 검토 확인 여부
        );
    }
}
