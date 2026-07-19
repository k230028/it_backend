package com.kdb.it.domain.council.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

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
 *   <li>정보기술부문계획(dbrTc=02): 미래전략(14011), IT기획(18001) — IT기획팀장은 평가위원 겸 간사('04')</li>
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

    /** 평가위원 리포지토리 (TPRMPP_BCMMTM) */
    private final CommitteeRepository committeeRepository;

    /** 사용자 리포지토리 — 위원 정보 조회 및 당연위원 매핑용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 존재 확인 및 상태 전이용 */
    private final CouncilService councilService;

    /**
     * JPA EntityManager — 신규 위원 INSERT 전용 persist() 호출용.
     *
     * <p>JpaRepository.save()는 ID 채워진 detached entity에 대해 merge()를 호출해
     * BaseEntity 필드(특히 delYn)를 null로 덮어쓰는 회귀가 있어 직접 persist를 사용합니다(PRD §14/§15).</p>
     */
    @PersistenceContext
    private EntityManager entityManager;

    // 심의유형별 당연위원 팀코드 매핑 (TEM_C 기준, Design §2.4)
    private static final Map<String, List<String>> MANDATORY_TEM_CODES = Map.of(
        "02", List.of("14011", "18001"),                    // 정보기술부문계획: 미래전략팀장, IT기획팀장
        "03", List.of("12004", "18010", "18501", "18301"),  // INFO_SYS
        "04", List.of("12004", "18001", "18010", "18501"),  // INFO_SEC
        "05", List.of("12004", "18010", "18501")            // ETC
    );

    // 심의유형별 간사 팀코드 매핑 (TEM_C 기준)
    // 003(INFO_SYS) / 005(ETC): IT기획(18001) → 간사
    // 004(INFO_SEC): 정보보호기획(18301) → 간사
    private static final Map<String, List<String>> SECRETARY_TEM_CODES = Map.of(
        "02", List.of("18001"),  // 정보기술부문계획: IT기획팀장(당연위원과 동일인 → 겸직 '04')
        "03", List.of("18001"),  // INFO_SYS
        "04", List.of("18301"),  // INFO_SEC
        "05", List.of("18001")   // ETC
    );

    /** INFO_SYS 일정 확정 필수 응답 팀코드 (예산:12004, IT기획:18001) */
    static final List<String> INFO_SYS_REQUIRED_TEM_CODES = List.of("12004", "18001");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 심의유형별 당연위원 후보 조회 (IT관리자 선정 화면용)
     *
     * <p>협의회의 dbrTc(심의유형)를 기준으로 당연위원 대상 팀코드를 조회하고,
     * 각 팀에서 팀장(ptCNm='팀장') 또는 첫 번째 사용자를 후보로 반환합니다.</p>
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
        Map<String, CuserI> mandCandidates = resolveTeamLeads(mandTemCodes);
        Map<String, CuserI> secrCandidates = resolveTeamLeads(secrTemCodes);

        // 간사 후보 사번 집합 — 당연위원과 겹치면 겸직('04')으로 병합
        // (dbrTc='02' 정보기술부문계획: IT기획팀장이 평가위원 겸 간사. BCMMTM PK=(협의회ID,사번)이라
        //  1인 2행이 불가하므로 '04'(당연위원 겸 간사) 단일 유형으로 표현한다.)
        Set<String> secrEnos = secrCandidates.values().stream()
                .map(CuserI::getEno)
                .collect(Collectors.toSet());

        List<CouncilDto.CommitteeMemberResponse> result = new ArrayList<>();
        Set<String> emittedEnos = new HashSet<>();

        // 당연위원 배정: 간사 겸직이면 '04'(당연위원 겸 간사), 아니면 '01'
        for (CuserI candidate : mandCandidates.values()) {
            String mebTc = secrEnos.contains(candidate.getEno()) ? "04" : "01";
            result.add(toMemberResponse(candidate, mebTc));
            emittedEnos.add(candidate.getEno());
        }

        // 당연위원과 겹치지 않는 순수 간사만 '03'으로 추가(겸직은 위에서 '04'로 이미 배정)
        for (CuserI candidate : secrCandidates.values()) {
            if (emittedEnos.contains(candidate.getEno())) continue;
            result.add(toMemberResponse(candidate, "03"));
            emittedEnos.add(candidate.getEno());
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

        List<Bcmmtm> members = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N");

        // 위원 사번 목록으로 사용자 정보 일괄 조회
        Map<String, CuserI> userMap = buildUserMap(members);

        List<CouncilDto.CommitteeMemberResponse> mandatory = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> call = new ArrayList<>();
        List<CouncilDto.CommitteeMemberResponse> secretary = new ArrayList<>();

        for (Bcmmtm m : members) {
            CuserI user = userMap.get(m.getEno());
            CouncilDto.CommitteeMemberResponse resp = toMemberResponseFromEntity(m, user);

            switch (m.getItPtlAsctMebTc()) {
                case "01" -> mandatory.add(resp);   // 당연위원(MAND)
                case "02" -> call.add(resp);         // 소집위원(CALL)
                case "03" -> secretary.add(resp);    // 간사(SECR)
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
     * <p>요청 위원 목록과 기존 위원을 사번 기준으로 비교하여:</p>
     * <ul>
     *   <li>이미 등록된 사번 → 영속 객체의 vlrTc만 변경 (JPA Dirty Checking)</li>
     *   <li>신규 사번 → INSERT</li>
     *   <li>요청에 없는 기존 사번 → Soft Delete</li>
     * </ul>
     *
     * <p>이전 구현(전체 Soft Delete 후 신규 INSERT)은 같은 PK(ASCT_ID, ENO)에 대해
     * 영속성 컨텍스트의 delete 처리 객체와 신규 build 객체가 merge되며 BaseEntity 컬럼이
     * 비정상 덮어써져, 후속 조회에서 빈 목록이 반환되는 회귀가 있었습니다(PRD §14).</p>
     *
     * <p>위원 확정 시 협의회 상태를 PREPARING으로 전이합니다.</p>
     *
     * @param asctId  협의회ID
     * @param request 위원 선정 요청 (심의유형 + 위원 목록)
     */
    @Transactional
    public void saveCommittee(String asctId, CouncilDto.CommitteeRequest request) {
        // 협의회 존재 확인 (없으면 예외)
        councilService.findActiveCouncil(asctId);

        // 기존 활성 위원을 사번 기준으로 인덱싱
        Map<String, Bcmmtm> existingByEno = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .stream()
                .collect(Collectors.toMap(value -> value.getEno(), m -> m, (a, b) -> a));

        Set<String> requestedEnos = new HashSet<>();

        // 요청 위원: 기존이면 vlrTc 갱신, 없으면 신규 INSERT
        for (CouncilDto.CommitteeMemberRequest req : request.members()) {
            requestedEnos.add(req.eno());
            Bcmmtm existing = existingByEno.get(req.eno());
            if (existing != null) {
                // 기존 영속 객체에 위원유형만 갱신 (Dirty Checking)
                existing.changeType(req.vlrTc());
            } else {
                /*
                 * 신규 위원: ID가 채워져 있어도 새 엔티티이므로 persist()로 직접 INSERT.
                 *   - JpaRepository.save()는 ID 보유 시 merge() 분기로 빠져 detached의 delYn=null이
                 *     영속 객체에 복사돼 DEL_YN=null로 저장되는 회귀가 있었음 (PRD §15)
                 *   - persist()는 새 entity로 처리되며 @PrePersist가 발화해 delYn='N'으로 자동 채움
                 */
                Bcmmtm member = Bcmmtm.builder()
                        .itPtlAsctId(asctId)
                        .eno(req.eno())
                        .itPtlAsctMebTc(req.vlrTc())
                        .build();
                entityManager.persist(member);
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

    /**
     * 팀코드 목록별 대표 후보(팀장 우선, 없으면 첫 사용자)를 해석합니다.
     *
     * <p>팀원이 없는 팀은 결과에서 제외합니다. 반환 Map은 입력 팀코드 순서를 보존합니다(LinkedHashMap).</p>
     *
     * @param temCodes 후보를 뽑을 팀코드 목록
     * @return 팀코드 → 대표 후보(CuserI) 매핑 (순서 보존)
     */
    private Map<String, CuserI> resolveTeamLeads(List<String> temCodes) {
        Map<String, CuserI> leads = new LinkedHashMap<>();
        for (String temC : temCodes) {
            List<CuserI> users = userRepository.findByTemC(temC);
            if (users.isEmpty()) continue;
            CuserI candidate = users.stream()
                    .filter(u -> "팀장".equals(u.getPtCNm()))
                    .findFirst()
                    .orElse(users.get(0));
            leads.put(temC, candidate);
        }
        return leads;
    }

    /**
     * 위원 목록의 사번으로 사용자 정보 Map 생성.
     *
     * <p>사번 집합을 모아 {@code findByEnoIn}으로 일괄 조회(N+1 제거).</p>
     */
    private Map<String, CuserI> buildUserMap(List<Bcmmtm> members) {
        List<String> enos = members.stream().map(member -> member.getEno()).distinct().toList();
        if (enos.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByEnoIn(enos).stream()
                .collect(Collectors.toMap(user -> user.getEno(), user -> user, (a, b) -> a));
    }

    /**
     * CuserI → CommitteeMemberResponse 변환 (당연위원 후보 조회용)
     *
     * <p>후보 조회 시점에는 BCMMTM 레코드가 없으므로 cfdYn은 'N'으로 초기화합니다.</p>
     */
    private CouncilDto.CommitteeMemberResponse toMemberResponse(CuserI user, String vlrTc) {
        return new CouncilDto.CommitteeMemberResponse(
                user.getEno(),
                user.getUsrNm(),
                user.getBbrNm(),
                user.getPtCNm(),
                vlrTc,
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
                member.getItPtlAsctMebTc(),
                member.getCnfmYn()  // 결과서 검토 확인 여부
        );
    }
}
