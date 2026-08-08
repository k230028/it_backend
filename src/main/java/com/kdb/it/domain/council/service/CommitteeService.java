package com.kdb.it.domain.council.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.UserRepresentativeSelector;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.BcmmtmId;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협의회 평가위원 서비스 (Step 2 — 위원 선정)
 *
 * <p>IT관리자(ITPAD001)가 심의유형에 따라 당연위원/소집위원/간사를 선정합니다.
 *
 * <p>당연위원 자동 매핑 (TEM_C 기준):
 *
 * <ul>
 *   <li>INFO_SYS: 예산(12004), PMO(18010), 디지털기획(18501), 정보보호기획(18301)
 *   <li>INFO_SEC: 예산(12004), IT기획(18001), PMO(18010), 디지털기획(18501)
 *   <li>ETC: 예산(12004), PMO(18010), 디지털기획(18501)
 *   <li>정보기술부문계획(dbrTc=02): 미래전략(14011), IT기획(18001) — IT기획팀장은 평가위원 겸 간사('04')
 * </ul>
 *
 * <p>위원 저장 전략: 전체 교체 (기존 Soft Delete + 신규 INSERT)
 *
 * <p>Design Ref: §2.1 CommitteeService — Step 2 담당
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommitteeService {

    /** 평가위원 리포지토리 (TPRMPP_BCMMTM) */
    private final CommitteeRepository committeeRepository;

    /** 사용자 리포지토리 — 위원 정보 조회 및 당연위원 매핑용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 존재 확인 및 상태 전이용 */
    private final CouncilService councilService;

    /**
     * JPA EntityManager — 신규 위원 INSERT 전용 persist() 호출용.
     *
     * <p>JpaRepository.save()는 ID 채워진 detached entity에 대해 merge()를 호출해 BaseEntity 필드(특히 delYn)를
     * null로 덮어쓰는 회귀가 있어 직접 persist를 사용합니다(PRD §14/§15).
     */
    @PersistenceContext private EntityManager entityManager;

    // 심의유형별 당연위원 팀코드 매핑 (TEM_C 기준, Design §2.4)
    private static final Map<String, List<String>> MANDATORY_TEM_CODES =
            Map.of(
                    // 정보기술부문계획: 예산(12004)·PMO품질관리(18010)·IT계약(18003)·AI디지털전략(18501)
                    // ·정보보호기획(18301)·미래전략(14011)·IT기획(18001) 팀장. IT기획팀장은 평가위원 겸 간사('04').
                    "02",
                    List.of("12004", "18010", "18003", "18501", "18301", "14011", "18001"),
                    "03",
                    List.of("12004", "18010", "18501", "18301"), // 정보시스템
                    "04",
                    List.of("12004", "18001", "18010", "18501"), // 정보보안
                    "05",
                    List.of("12004", "18010", "18501") // 기타
                    );

    // 심의유형별 간사 팀코드 매핑 (TEM_C 기준)
    // 03(정보시스템) / 05(기타): IT기획(18001) → 간사
    // 04(정보보안): 정보보호기획(18301) → 간사
    private static final Map<String, List<String>> SECRETARY_TEM_CODES =
            Map.of(
                    "02", List.of("18001"), // 정보기술부문계획: IT기획팀장(당연위원과 동일인 → 겸직 '04')
                    "03", List.of("18001"), // 정보시스템
                    "04", List.of("18301"), // 정보보안
                    "05", List.of("18001") // 기타
                    );

    /** INFO_SYS 일정 확정 필수 응답 팀코드 (예산:12004, IT기획:18001) */
    static final List<String> INFO_SYS_REQUIRED_TEM_CODES = List.of("12004", "18001");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 심의유형별 당연위원 후보 조회 (IT관리자 선정 화면용)
     *
     * <p>협의회의 dbrTc(심의유형)를 기준으로 당연위원 대상 팀코드를 조회하고, 각 팀에서 팀장(ptCNm='팀장') 또는 첫 번째 사용자를 후보로 반환합니다.
     *
     * @param asctId 협의회ID
     * @return 당연위원 후보 목록
     */
    public List<CouncilDto.CommitteeMemberResponse> getDefaultCommittee(String asctId) {
        // 협의회 존재 확인 및 심의유형 조회
        String dbrTc = councilService.findActiveCouncil(asctId).getItPtlAsctDbrTc();

        // 당연위원(01)·간사(03) 후보 팀코드
        List<String> mandTemCodes = MANDATORY_TEM_CODES.getOrDefault(dbrTc, List.of());
        List<String> secrTemCodes = SECRETARY_TEM_CODES.getOrDefault(dbrTc, List.of());

        // 팀코드별 대표 후보(팀장 우선) 해석 — 입력 팀코드 순서 보존
        Map<String, UserRepository.CommitteeUserRow> mandCandidates =
                resolveTeamLeads(mandTemCodes);
        Map<String, UserRepository.CommitteeUserRow> secrCandidates =
                resolveTeamLeads(secrTemCodes);

        // 간사 후보 사번 집합 — 당연위원과 겹치면 겸직('04')으로 병합
        // (dbrTc='02' 정보기술부문계획: IT기획팀장이 평가위원 겸 간사. BCMMTM PK=(협의회ID,사번)이라
        //  1인 2행이 불가하므로 '04'(당연위원 겸 간사) 단일 유형으로 표현한다.)
        Set<String> secrEnos =
                secrCandidates.values().stream()
                        .map(user -> user.getEno())
                        .collect(Collectors.toSet());

        List<CouncilDto.CommitteeMemberResponse> result = new ArrayList<>();
        Set<String> emittedEnos = new HashSet<>();

        // 당연위원 배정: 간사 겸직이면 '04'(당연위원 겸 간사), 아니면 '01'
        for (UserRepository.CommitteeUserRow candidate : mandCandidates.values()) {
            String mebTc = secrEnos.contains(candidate.getEno()) ? "04" : "01";
            result.add(toMemberResponse(candidate, mebTc));
            emittedEnos.add(candidate.getEno());
        }

        // 당연위원과 겹치지 않는 순수 간사만 '03'으로 추가(겸직은 위에서 '04'로 이미 배정)
        for (UserRepository.CommitteeUserRow candidate : secrCandidates.values()) {
            if (emittedEnos.contains(candidate.getEno())) continue;
            result.add(toMemberResponse(candidate, "03"));
            emittedEnos.add(candidate.getEno());
        }

        return result;
    }

    /**
     * 협의회 평가위원 목록 조회
     *
     * <p>위원유형별(당연/소집/간사)로 분류하여 반환합니다.
     *
     * @param asctId 협의회ID
     * @return 위원유형별 목록
     */
    public CouncilDto.CommitteeListResponse getCommittee(String asctId) {
        councilService.findActiveCouncil(asctId);

        List<Bcmmtm> members = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N");

        // 위원 사번 목록으로 응답용 사용자 정보를 일괄 조회
        Map<String, UserRepository.CouncilMemberUserRow> userMap = buildUserMap(members);

        List<CouncilDto.CommitteeMemberResponse> mandatory = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> call = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> secretary = new ArrayList<>();

        for (Bcmmtm m : members) {
            UserRepository.CouncilMemberUserRow user = userMap.get(m.getEno());
            CouncilDto.CommitteeMemberResponse resp = toMemberResponseFromView(m, user);

            switch (m.getItPtlAsctMebTc()) {
                case "01" -> mandatory.add(resp); // 당연위원(MAND)
                case "02" -> call.add(resp); // 소집위원(CALL)
                case "03" -> secretary.add(resp); // 간사(SECR)
                // '04' 당연위원 겸 간사(dbrTc='02'): 평가위원 목록(당연위원)에 노출하고,
                // 간사 여부는 위원유형(mebTc='04')으로 프론트가 판별한다(중복 노출 방지).
                case "04" -> mandatory.add(resp);
            }
        }

        return new CouncilDto.CommitteeListResponse(mandatory, call, secretary);
    }

    // =========================================================================
    // 저장
    // =========================================================================

    /**
     * 평가위원 선정/수정 (upsert 패턴)
     *
     * <p>요청 위원 목록과 기존 위원을 사번 기준으로 비교하여:
     *
     * <ul>
     *   <li>이미 등록된 사번의 유형 변경 → 기존 행 Soft Delete 후 새 복합키 행 활성화
     *   <li>신규 사번 → INSERT
     *   <li>요청에 없는 기존 사번 → Soft Delete
     * </ul>
     *
     * <p>위원유형은 물리 복합 PK의 일부이므로 영속 엔티티에서 직접 변경하지 않습니다. 과거에 동일 복합키로 Soft Delete된 행이 있으면 복원하고, 없으면 새
     * 행을 persist합니다.
     *
     * <p>위원 확정 시 협의회 상태를 PREPARING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 위원 선정 요청 (심의유형 + 위원 목록)
     */
    @Transactional
    public void saveCommittee(String asctId, CouncilDto.CommitteeRequest request) {
        // 협의회 존재 확인 (없으면 예외)
        councilService.findActiveCouncil(asctId);

        // 기존 활성 위원을 사번 기준으로 인덱싱
        Map<String, Bcmmtm> existingByEno =
                committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                        .collect(Collectors.toMap(value -> value.getEno(), m -> m, (a, b) -> a));

        Set<String> requestedEnos = new HashSet<>();

        // 요청 위원: 유형이 같으면 유지하고, 유형이 바뀌면 물리 복합키 행을 교체한다.
        for (CouncilDto.CommitteeMemberRequest req : request.members()) {
            requestedEnos.add(req.eno());
            Bcmmtm existing = existingByEno.get(req.eno());
            if (existing != null) {
                if (!existing.getItPtlAsctMebTc().equals(req.vlrTc())) {
                    existing.delete();
                    activateMember(asctId, req);
                }
            } else {
                activateMember(asctId, req);
            }
        }

        // 요청에 없는 기존 위원만 Soft Delete
        existingByEno.entrySet().stream()
                .filter(e -> !requestedEnos.contains(e.getKey()))
                .forEach(e -> e.getValue().delete());

        // 04→05 전이는 더 이상 위원 저장의 부수효과로 처리하지 않는다.
        // Step1 상세의 '개최준비 진행' 버튼(startPreparation, PATCH /start-preparation)이
        // 명시적으로 수행한다. (PRD_c_20260620 #2)
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private void activateMember(String asctId, CouncilDto.CommitteeMemberRequest request) {
        BcmmtmId id = new BcmmtmId(asctId, request.vlrTc(), request.eno());
        committeeRepository
                .findById(id)
                .ifPresentOrElse(
                        Bcmmtm::restore,
                        () ->
                                entityManager.persist(
                                        Bcmmtm.builder()
                                                .itPtlAsctId(asctId)
                                                .eno(request.eno())
                                                .itPtlAsctMebTc(request.vlrTc())
                                                .build()));
    }

    /**
     * 팀코드 목록별 대표 후보(팀장 우선→사번 오름차순)를 해석합니다. (BE-10)
     *
     * <p>팀코드 전체의 활성 사용자를 팀 대표 프로젝션으로 한 번에 조회하고, 팀별 대표자는 {@link UserRepresentativeSelector}가 결정적으로
     * 선택합니다. 팀원이 없는 팀은 결과에서 제외하며, 반환 Map은 입력 팀코드 순서를 보존합니다(LinkedHashMap).
     *
     * @param temCodes 후보를 뽑을 팀코드 목록
     * @return 팀코드 → 대표 후보 프로젝션 매핑 (순서 보존)
     */
    private Map<String, UserRepository.CommitteeUserRow> resolveTeamLeads(List<String> temCodes) {
        if (temCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<UserRepository.CommitteeUserRow>> usersByTeam =
                userRepository.findCommitteeUserRowsByTemCInAndDelYn(temCodes, "N").stream()
                        .collect(Collectors.groupingBy(user -> user.getTemC()));
        Map<String, UserRepository.CommitteeUserRow> leads = new LinkedHashMap<>();
        for (String temC : temCodes) {
            UserRepresentativeSelector.pickView(usersByTeam.getOrDefault(temC, List.of()))
                    .ifPresent(user -> leads.put(temC, user));
        }
        return leads;
    }

    /**
     * 위원 목록의 사번으로 응답용 사용자 정보 Map 생성.
     *
     * <p>사번 집합을 모아 위원 응답 프로젝션으로 일괄 조회합니다.
     */
    private Map<String, UserRepository.CouncilMemberUserRow> buildUserMap(List<Bcmmtm> members) {
        List<String> enos = members.stream().map(member -> member.getEno()).distinct().toList();
        if (enos.isEmpty()) {
            return Map.of();
        }
        return userRepository.findCouncilMemberUserRowsByEnoIn(enos).stream()
                .collect(Collectors.toMap(user -> user.getEno(), user -> user, (a, b) -> a));
    }

    /** 팀 대표 사용자 프로젝션을 당연위원 후보 응답으로 변환합니다. */
    private CouncilDto.CommitteeMemberResponse toMemberResponse(
            UserRepository.CommitteeUserRow user, String vlrTc) {
        return new CouncilDto.CommitteeMemberResponse(
                user.getEno(), user.getUsrNm(), user.getBbrNm(), user.getPtCNm(), vlrTc, "N");
    }

    /**
     * 위원 엔티티와 사용자 프로젝션을 위원 응답으로 변환합니다.
     *
     * <p>사용자 정보가 없는 경우(탈퇴 등) 사번만 포함합니다. cnfmYn은 BCMMTM 엔티티의 실제 값을 반영합니다.
     */
    private CouncilDto.CommitteeMemberResponse toMemberResponseFromView(
            Bcmmtm member, UserRepository.CouncilMemberUserRow user) {
        return new CouncilDto.CommitteeMemberResponse(
                member.getEno(),
                user != null ? user.getUsrNm() : null,
                user != null ? user.getBbrNm() : null,
                user != null ? user.getPtCNm() : null,
                member.getItPtlAsctMebTc(),
                member.getCnfmYn() // 결과서 검토 확인 여부
                );
    }
}
