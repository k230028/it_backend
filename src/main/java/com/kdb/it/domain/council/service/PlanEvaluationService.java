package com.kdb.it.domain.council.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.entity.BplevmId;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.PlanEvaluationRepository;
import com.kdb.it.domain.entity.EntityRestoreSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보기술부문계획 협의회(dbrTc='02') 사업별 적정/유보 평가 서비스.
 *
 * <p>평가위원이 계획(BPLANM)에 포함된 각 정보화사업에 대해 적정/유보(PPRT_YN)와 사유를 남기고, 사업별 최종 판정은 "위원 중 1명이라도 유보(N)면 유보,
 * 전원 적정(Y)이면 적정"으로 집계합니다.
 *
 * <p>기존 타당성검토 평가(EvaluationService, 6항목 점수)와 분리된 dbrTc='02' 전용 흐름입니다. 심의 대상 사업 목록·예산은 계획 스냅샷을
 * 재사용합니다(PlanService).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanEvaluationService {

    /** 사업별 평가의견 리포지토리 (TPRMPP_BPLEVM) */
    private final PlanEvaluationRepository planEvaluationRepository;

    /** 사용자 리포지토리 — 위원 이름 조회용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 존재 확인·상태 전이용 */
    private final CouncilService councilService;

    /** 평가위원 리포지토리 — 위원 본인 검증용 */
    private final CommitteeRepository committeeRepository;

    /** 계획 서비스 — 심의 대상 계획(스냅샷) 조회용 */
    private final PlanService planService;

    /** 정보화사업 서비스 — 사업개요/기간 보강용 (스냅샷에 없는 필드) */
    private final ProjectService projectService;

    /** 협의회 리포지토리 — 조정 협의회의 '직전 승인 계획' 조회용 */
    private final CouncilRepository councilRepository;

    /** JPA EntityManager — 평가 신규 INSERT persist용 (§5.12.1.1) */
    @PersistenceContext private EntityManager entityManager;

    /**
     * 계획 스냅샷 JSON(redtConeInf) 파서 — {@link #parseSnapshot}의 단일 파싱 지점 전용(Jackson 기본 설정). 정적 상수가 아닌
     * 인스턴스 필드로 두어 단위 테스트에서 스파이(spy)로 교체해 파싱 호출 횟수를 검증할 수 있게 한다.
     */
    private final ObjectMapper snapshotMapper = new ObjectMapper();

    // =========================================================================
    // 심의 대상(계획) 조회
    // =========================================================================

    /**
     * 협의회의 심의 대상 조회 (사업 카드·예산 표출용).
     *
     * <p>계획협의회의 ABUS_MNG_NO에 저장된 계획관리번호로 계획 스냅샷의 예산·부서 정보를 얻고, 스냅샷에 없는 사업개요·시작/종료일자는 BPROJM(정보화사업)
     * 상세에서 보강해 병합합니다. 경상사업(ornYn='Y')은 제외하고 정보화사업만 반환합니다.
     *
     * <p>계획 스냅샷(redtConeInf)이 구문 오류(JSON 파싱 실패)이거나 구조가 손상된 경우 더 이상 예외로 전체 응답을 실패시키지 않습니다({@link
     * #parseSnapshot} 참고). 대신 손상된 원소/부분만 조용히 제외하고 유효한 나머지 데이터로 부분 응답을 구성하며, {@link
     * CouncilDto.PlanTargetsResponse#snapshotIncomplete()}를 true로 설정해 호출부(프론트)가 데이터 누락을 인지하도록 합니다.
     * 이 플래그는 다음 중 하나라도 해당하면 true입니다: (1) 현재 계획 자체의 스냅샷이 손상된 경우, (2) 조정(itPtlPlnTpC='조정') 협의회에서 예산
     * 최초/조정 비교에 쓰이는 기준(직전 승인) 계획의 스냅샷이 손상된 경우. 단, DB/권한 오류나 {@code planService.getPlan()} 조회 실패(대상
     * 계획·기준 계획 모두)는 파싱 손상과 무관하므로 그대로 전파합니다.
     *
     * @param asctId 협의회ID
     * @return 심의 대상 (계획 요약 + 사업별 기본정보), 스냅샷 손상 시 부분 데이터 + snapshotIncomplete=true
     * @throws IllegalStateException 계획이 연결되지 않은 협의회(dbrTc≠'02' 등)
     */
    public CouncilDto.PlanTargetsResponse getPlanTargets(String asctId) {
        Basctm council = councilService.findActiveCouncil(asctId);
        String reqDocNo = council.getAbusMngNo();
        if (reqDocNo == null || reqDocNo.isBlank()) {
            throw new IllegalStateException("계획이 연결되지 않은 협의회입니다: " + asctId);
        }
        PlanDto.DetailResponse plan = planService.getPlan(reqDocNo);

        // 계획 스냅샷 1회 파싱 — 정보화사업(경상 제외) 노드·전산업무비 건수를 동시에 얻는다
        ParsedSnapshot parsed = parseSnapshot(plan.getRedtConeInf(), reqDocNo);
        boolean incomplete = parsed.incomplete();

        // 사업개요·시작/종료일자는 스냅샷에 없어 BPROJM 상세에서 보강
        List<String> prjMngNos =
                parsed.businesses().stream().map(n -> textOf(n, "prjMngNo")).toList();
        Map<String, ProjectDto.Response> detailMap =
                fetchProjectDetails(prjMngNos, plan.getBseYy());

        // 조정 협의회는 '직전 승인(수립) 계획'의 사업별 예산을 최초값으로 병합(예산 최초/조정 비교)
        Map<String, JsonNode> baselineByBiz = Map.of();
        if ("조정".equals(plan.getItPtlPlnTpC())) {
            PlanDto.DetailResponse baselinePlan = findBaselinePlan(plan.getBseYy(), reqDocNo);
            if (baselinePlan != null) {
                ParsedSnapshot baselineParsed =
                        parseSnapshot(baselinePlan.getRedtConeInf(), baselinePlan.getReqDocNo());
                baselineByBiz = businessesByMngNo(baselineParsed.businesses());
                incomplete = incomplete || baselineParsed.incomplete();
            }
        }

        List<CouncilDto.PlanTargetBusiness> businesses = new ArrayList<>();
        for (JsonNode n : parsed.businesses()) {
            String id = textOf(n, "prjMngNo");
            ProjectDto.Response d = detailMap.get(id);
            JsonNode base = baselineByBiz.get(id);
            businesses.add(
                    new CouncilDto.PlanTargetBusiness(
                            id,
                            textOf(n, "abusNm"),
                            textOf(n, "pulDtt"),
                            textOf(n, "svnHdq"),
                            textOf(n, "svnDpmNm"),
                            d != null ? d.getAbusCone() : null,
                            d != null ? d.getSttDtm() : null,
                            d != null ? d.getEndDtm() : null,
                            decimalOf(n, "prjBg"),
                            decimalOf(n, "assetBg"),
                            decimalOf(n, "costBg"),
                            base != null ? decimalOf(base, "prjBg") : null,
                            base != null ? decimalOf(base, "assetBg") : null,
                            base != null ? decimalOf(base, "costBg") : null));
        }

        return new CouncilDto.PlanTargetsResponse(
                reqDocNo,
                plan.getBseYy(),
                plan.getItPtlPlnTpC(),
                businesses,
                parsed.costCount(),
                incomplete);
    }

    /**
     * 계획 스냅샷(redtConeInf JSON) 파싱 결과 — 단일 파싱 지점({@link #parseSnapshot})의 반환값.
     *
     * @param businesses 유효한(구조 손상이 없는) 정보화사업 노드 목록(경상사업 제외)
     * @param costCount 전산업무비 참고 건수
     * @param nameByBusiness 사업관리번호 → 사업명 매핑(abusNm이 있는 사업만 포함)
     * @param incomplete 구문/구조 손상으로 원본 데이터 일부가 제외됐는지 여부
     */
    private record ParsedSnapshot(
            List<JsonNode> businesses,
            int costCount,
            Map<String, String> nameByBusiness,
            boolean incomplete) {

        private static ParsedSnapshot empty(boolean incomplete) {
            return new ParsedSnapshot(List.of(), 0, Map.of(), incomplete);
        }
    }

    /** 계획 스냅샷(redtConeInf JSON)의 알려진 최상위 배열 키(사업 목록 별칭 2종 + 전산업무비). */
    private static final String SNAPSHOT_KEY_PROJECTS = "projects";

    private static final String SNAPSHOT_KEY_PRJ_SNAPSHOTS = "prjSnapshots";
    private static final String SNAPSHOT_KEY_COST_DETAILS = "costDetails";

    /**
     * 계획 스냅샷(redtConeInf JSON)의 단일 파서 — 스냅샷 문자열 1건당 정확히 1회만 {@code readTree()}를 호출한다(조정 협의회는 현재
     * 계획·기준 계획 스냅샷이 서로 다른 문자열이라 각각 1회씩, 요청당 최대 2회).
     *
     * <p>구문/구조 손상이 있어도 예외를 던지지 않고 유효한 부분만 살려 반환하며, {@code incomplete=true}로 원본 데이터 일부가 제외됐음을 알린다.
     * DB/권한/{@code planService.getPlan()} 오류는 이 메서드 밖(호출자)에서 발생하므로 그대로 전파되며 이 메서드가 삼키지 않는다.
     *
     * <p>구조 판정 흐름(ASCII, 원소별 검증 순서는 실제 코드 순서와 동일):
     *
     * <pre>
     * json == null || blank?
     *   YES -&gt; 정상 빈 결과 (incomplete=false)
     *   NO  -&gt; readTree() 시도
     *          구문 오류(JsonProcessingException)?
     *            YES -&gt; 빈 결과 (incomplete=true)
     *            NO  -&gt; root.isObject()?
     *                     NO  -&gt; 빈 결과 (incomplete=true)              // 배열/스칼라 루트
     *                     YES -&gt; projects/prjSnapshots/costDetails 중 하나라도 존재?
     *                              NO  -&gt; 빈 결과 (incomplete=true)     // 알려진 루트 키 없음
     *                              YES -&gt; 부분별(사업 배열 · 전산업무비 배열) 개별 검증:
     *                                       존재하지만 배열 아님 -&gt; 그 부분만 제외 + incomplete=true
     *                                       배열 -&gt; 원소별로 아래 순서대로 검증
     *                                         1) 객체 아님?
     *                                              YES -&gt; 그 원소 제외 + incomplete=true
     *                                         2) ornYn='Y'(경상사업)?
     *                                              YES -&gt; 그 원소 제외, incomplete 플래그는 세우지
     *                                                      않음(정상 필터링이지 손상이 아님)
     *                                         3) prjMngNo 없음/공백? (경상사업이 아닌 원소만 해당)
     *                                              YES -&gt; 그 원소 제외 + incomplete=true
     *                                         4) abusNm 없음?
     *                                              YES -&gt; 사업 노드는 유지하되 이름매핑만 생략 +
     *                                                      incomplete=true(호출부는 관리번호로 대체)
     * </pre>
     *
     * <p>입력 계약을 변경하면 이 주석과 {@code PlanEvaluationServiceTest}의 대응 테스트를 함께 갱신한다.
     *
     * @param json 계획 스냅샷 JSON (redtConeInf)
     * @param reqDocNo 진단 로그용 계획관리번호
     * @return 유효 사업/전산업무비 건수/사업명 매핑과 구조 손상 여부를 담은 파싱 결과
     */
    private ParsedSnapshot parseSnapshot(String json, String reqDocNo) {
        if (json == null || json.isBlank()) {
            return ParsedSnapshot.empty(false);
        }

        JsonNode root;
        try {
            root = snapshotMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("계획 스냅샷 구문 손상(JSON 파싱 실패): reqDocNo={}", reqDocNo);
            return ParsedSnapshot.empty(true);
        }

        if (root == null || !root.isObject()) {
            log.warn("계획 스냅샷 구조 손상(루트가 객체가 아님): reqDocNo={}", reqDocNo);
            return ParsedSnapshot.empty(true);
        }

        boolean hasKnownRootKey =
                root.has(SNAPSHOT_KEY_PROJECTS)
                        || root.has(SNAPSHOT_KEY_PRJ_SNAPSHOTS)
                        || root.has(SNAPSHOT_KEY_COST_DETAILS);
        if (!hasKnownRootKey) {
            log.warn("계획 스냅샷 구조 손상(알려진 루트 키 없음): reqDocNo={}", reqDocNo);
            return ParsedSnapshot.empty(true);
        }

        boolean incomplete = false;

        // 사업 목록: prjSnapshots 우선, 없으면 projects
        JsonNode businessArr =
                root.has(SNAPSHOT_KEY_PRJ_SNAPSHOTS)
                        ? root.get(SNAPSHOT_KEY_PRJ_SNAPSHOTS)
                        : root.get(SNAPSHOT_KEY_PROJECTS);
        List<JsonNode> businesses = new ArrayList<>();
        Map<String, String> nameByBusiness = new LinkedHashMap<>();
        if (businessArr != null) {
            if (!businessArr.isArray()) {
                log.warn("계획 스냅샷 구조 손상(사업 목록이 배열이 아님): reqDocNo={}", reqDocNo);
                incomplete = true;
            } else {
                for (JsonNode n : businessArr) {
                    if (!n.isObject()) {
                        incomplete = true;
                        continue;
                    }
                    JsonNode orn = n.get("ornYn");
                    if (orn != null && "Y".equals(orn.asText())) {
                        continue; // 경상사업 제외 → 정보화사업만 (구조 손상 아님)
                    }
                    String id = textOf(n, "prjMngNo");
                    if (id == null || id.isBlank()) {
                        incomplete = true;
                        continue;
                    }
                    businesses.add(n);
                    String nm = textOf(n, "abusNm");
                    if (nm != null) {
                        nameByBusiness.put(id, nm);
                    } else {
                        incomplete = true; // 사업은 유지하되 이름 매핑은 생략(호출부 관리번호 대체)
                    }
                }
            }
        }

        // 전산업무비 참고 건수
        JsonNode costArr = root.get(SNAPSHOT_KEY_COST_DETAILS);
        int costCount = 0;
        if (costArr != null) {
            if (!costArr.isArray()) {
                log.warn("계획 스냅샷 구조 손상(전산업무비 목록이 배열이 아님): reqDocNo={}", reqDocNo);
                incomplete = true;
            } else {
                costCount = costArr.size();
            }
        }

        return new ParsedSnapshot(businesses, costCount, nameByBusiness, incomplete);
    }

    /** BPROJM 상세를 사업관리번호별로 조회(사업개요/기간 보강). 대상년도로 편성예산 컨텍스트 전달. */
    private Map<String, ProjectDto.Response> fetchProjectDetails(
            List<String> prjMngNos, String bseYy) {
        if (prjMngNos.isEmpty()) {
            return Map.of();
        }
        ProjectDto.BulkGetRequest req = new ProjectDto.BulkGetRequest();
        req.setPrjMngNos(prjMngNos);
        req.setBseYy(bseYy);
        return projectService.getProjectsByIds(req).items().stream()
                .collect(Collectors.toMap(r -> r.getAbusMngNo(), r -> r, (a, b) -> a));
    }

    /**
     * 조정 협의회 기준: 같은 대상년도의 '직전 승인(완료 13) 수립(신규) 계획'을 찾는다.
     *
     * <p>완료 협의회와 활성 계획을 조인해 최근 등록순 후보 한 건을 결정한다. 후보가 없으면 null을 반환하고 후보 계획 조회 실패는 호출자에게 전파한다.
     *
     * @param bseYy 대상년도
     * @param currentReqDocNo 제외할 현재 계획관리번호
     * @return 기준 계획 상세, 후보가 없으면 null
     */
    private PlanDto.DetailResponse findBaselinePlan(String bseYy, String currentReqDocNo) {
        if (bseYy == null) {
            return null;
        }
        List<String> reqDocNos =
                councilRepository.findBaselineReqDocNos(
                        "02",
                        "13",
                        bseYy,
                        "신규",
                        currentReqDocNo,
                        org.springframework.data.domain.PageRequest.of(0, 1));
        if (reqDocNos.isEmpty()) {
            return null;
        }
        return planService.getPlan(reqDocNos.getFirst());
    }

    /** 파싱된 사업 노드 목록을 사업관리번호 기준 맵으로 변환(prjBg/assetBg/costBg 조회용). */
    private static Map<String, JsonNode> businessesByMngNo(List<JsonNode> businesses) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode n : businesses) {
            map.put(textOf(n, "prjMngNo"), n);
        }
        return map;
    }

    /** JsonNode 필드를 문자열로(없으면 null). */
    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** JsonNode 필드를 BigDecimal로(없으면 null). */
    private static java.math.BigDecimal decimalOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.decimalValue();
    }

    // =========================================================================
    // 평가 조회
    // =========================================================================

    /**
     * 사업별 평가 전체 현황 조회 (IT관리자용): 위원별 평가 + 사업별 최종 판정.
     *
     * @param asctId 협의회ID
     * @return 위원별 평가 목록 + 사업별 판정
     */
    public CouncilDto.PlanEvaluationSummaryResponse getAllEvaluations(String asctId) {
        councilService.findActiveCouncil(asctId);

        List<Bplevm> all = planEvaluationRepository.findByItPtlAsctIdAndDelYn(asctId, "N");
        Map<String, UserRepository.UserNameView> userMap = buildUserMap(all);

        List<CouncilDto.PlanEvaluationItemResponse> evaluations =
                all.stream()
                        .map(
                                e -> {
                                    UserRepository.UserNameView user = userMap.get(e.getEno());
                                    return new CouncilDto.PlanEvaluationItemResponse(
                                            e.getEno(),
                                            user != null ? user.getUsrNm() : null,
                                            e.getAbusMngNo(),
                                            e.getPprtYn(),
                                            e.getEvalOpnn());
                                })
                        .toList();

        return new CouncilDto.PlanEvaluationSummaryResponse(evaluations, aggregateVerdicts(all));
    }

    /**
     * 내 사업별 평가 조회 (평가위원 본인).
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return 내가 남긴 사업별 적정/유보 목록 (없으면 빈 배열)
     */
    public List<CouncilDto.PlanEvaluationItemResponse> getMyEvaluation(
            String asctId, CustomUserDetails userDetails) {
        councilService.findActiveCouncil(asctId);
        String eno = userDetails.getEno();
        return planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N").stream()
                .map(
                        e ->
                                new CouncilDto.PlanEvaluationItemResponse(
                                        e.getEno(),
                                        null,
                                        e.getAbusMngNo(),
                                        e.getPprtYn(),
                                        e.getEvalOpnn()))
                .toList();
    }

    // =========================================================================
    // 판정 집계
    // =========================================================================

    /**
     * 사업별 최종 판정 집계: 위원 중 1명이라도 유보(N)면 그 사업은 '유보', 전원 적정(Y)이면 '적정'.
     *
     * @param all 협의회 전체 평가 행
     * @return 사업관리번호별 최종 판정(입력 순서 보존)
     */
    private List<CouncilDto.PlanBusinessVerdict> aggregateVerdicts(List<Bplevm> all) {
        Map<String, List<Bplevm>> byBusiness =
                all.stream()
                        .collect(
                                Collectors.groupingBy(
                                        e -> e.getAbusMngNo(),
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        List<CouncilDto.PlanBusinessVerdict> verdicts = new ArrayList<>();
        for (Map.Entry<String, List<Bplevm>> entry : byBusiness.entrySet()) {
            List<Bplevm> rows = entry.getValue();
            long reserveCount = rows.stream().filter(r -> "N".equals(r.getPprtYn())).count();
            long evaluatorCount = rows.stream().map(r -> r.getEno()).distinct().count();
            String finalPprtYn = reserveCount > 0 ? "N" : "Y"; // 1명이라도 유보면 유보
            verdicts.add(
                    new CouncilDto.PlanBusinessVerdict(
                            entry.getKey(), finalPprtYn, reserveCount, evaluatorCount));
        }
        return verdicts;
    }

    // =========================================================================
    // 결과서 프리필 요약 (M4)
    // =========================================================================

    /**
     * 결과서 프리필용 사업별 판정 요약 생성.
     *
     * <p>사업별 최종 판정과 유보 사유를 HTML 표로 렌더링해 결과서(BRSLTM) 본문 프리필에 사용합니다. 사업명은 대상 계획 스냅샷(prjSnapshots의
     * prjMngNo→abusNm)에서 해석하며, 없으면 사업관리번호로 대체합니다.
     *
     * <p>사업명 해석도 {@link #parseSnapshot}을 통하므로 스냅샷이 구문/구조 손상이어도 예외로 실패하지 않습니다. 손상 시 사업명 매핑이 비거나 일부
     * 누락된 채 사업관리번호 대체 표시로 부분 응답을 구성하고, {@link
     * CouncilDto.PlanResultSummaryResponse#snapshotIncomplete()}를 true로 설정합니다. 다만 DB/권한 오류나 {@code
     * planService.getPlan()} 조회 실패는 파싱 손상과 무관하므로 그대로 전파합니다.
     *
     * @param asctId 협의회ID
     * @return 요약 HTML + 구조화 판정, 스냅샷 손상 시 부분 데이터 + snapshotIncomplete=true
     */
    public CouncilDto.PlanResultSummaryResponse buildResultSummary(String asctId) {
        Basctm council = councilService.findActiveCouncil(asctId);

        List<Bplevm> all = planEvaluationRepository.findByItPtlAsctIdAndDelYn(asctId, "N");
        List<CouncilDto.PlanBusinessVerdict> verdicts = aggregateVerdicts(all);

        // 사업명 매핑 (계획 스냅샷) — getPlan()은 파싱 밖에서 호출되므로 DB/권한 오류는 그대로 전파된다
        Map<String, String> nameById = Map.of();
        boolean incomplete = false;
        String reqDocNo = council.getAbusMngNo();
        if (reqDocNo != null && !reqDocNo.isBlank()) {
            ParsedSnapshot parsed =
                    parseSnapshot(planService.getPlan(reqDocNo).getRedtConeInf(), reqDocNo);
            nameById = parsed.nameByBusiness();
            incomplete = parsed.incomplete();
        }

        // 사업별 유보 사유 수집 (유보 위원의 사유)
        Map<String, List<String>> reserveOpinions =
                all.stream()
                        .filter(e -> "N".equals(e.getPprtYn()))
                        .filter(e -> e.getEvalOpnn() != null && !e.getEvalOpnn().isBlank())
                        .collect(
                                Collectors.groupingBy(
                                        e -> e.getAbusMngNo(),
                                        Collectors.mapping(
                                                e -> e.getEvalOpnn(), Collectors.toList())));

        return new CouncilDto.PlanResultSummaryResponse(
                renderSummaryHtml(verdicts, nameById, reserveOpinions), verdicts, incomplete);
    }

    /** 사업별 판정 요약 HTML 표 렌더링(텍스트 셀은 이스케이프). */
    private String renderSummaryHtml(
            List<CouncilDto.PlanBusinessVerdict> verdicts,
            Map<String, String> nameById,
            Map<String, List<String>> reserveOpinions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>")
                .append("<th>사업명</th><th>최종판정</th><th>적정/유보(위원)</th><th>주요 의견(유보 사유)</th>")
                .append("</tr></thead><tbody>");
        for (CouncilDto.PlanBusinessVerdict v : verdicts) {
            String nm = nameById.getOrDefault(v.abusMngNo(), v.abusMngNo());
            String verdict = "N".equals(v.finalPprtYn()) ? "유보" : "적정";
            long adequate = v.evaluatorCount() - v.reserveCount();
            List<String> ops = reserveOpinions.getOrDefault(v.abusMngNo(), List.of());
            String opinions = ops.isEmpty() ? "-" : String.join(" / ", ops);
            sb.append("<tr>")
                    .append("<td>")
                    .append(escapeHtml(nm))
                    .append("</td>")
                    .append("<td>")
                    .append(verdict)
                    .append("</td>")
                    .append("<td>적정 ")
                    .append(adequate)
                    .append(", 유보 ")
                    .append(v.reserveCount())
                    .append("</td>")
                    .append("<td>")
                    .append(escapeHtml(opinions))
                    .append("</td>")
                    .append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    /** HTML 텍스트 셀 이스케이프(&, <, > — 프리필 주입 방지). */
    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // =========================================================================
    // 평가 저장
    // =========================================================================

    /**
     * 사업별 적정/유보 저장 (평가위원). 사업관리번호 기준 upsert.
     *
     * <p>적정여부는 Y/N만 허용하고 사유(evalOpnn)는 필수입니다(PRD #1). 첫 제출 시 협의회 상태를 07(진행 중) → 08(평가의견 작성 중)로
     * 전이합니다. 08→09(결과서) 완료 전이는 계획별 사업 수가 가변적이라 결과 단계/후속에서 처리합니다.
     *
     * @param asctId 협의회ID
     * @param request 사업별 적정/유보 요청
     * @param userDetails 로그인한 평가위원
     * @throws AccessDeniedException 해당 협의회 평가위원이 아닌 경우
     * @throws IllegalArgumentException 적정여부가 Y/N이 아니거나 사유가 비어 있는 경우
     */
    @Transactional
    public void saveEvaluation(
            String asctId,
            CouncilDto.PlanEvaluationRequest request,
            CustomUserDetails userDetails) {
        Basctm council = councilService.findActiveCouncil(asctId);
        String eno = userDetails.getEno();

        // 해당 협의회 평가위원 본인만 제출 가능 (비위원 평가 주입 차단)
        if (committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N").isEmpty()) {
            throw new AccessDeniedException("해당 협의회의 평가위원만 평가의견을 제출할 수 있습니다.");
        }

        // 기존 내 평가를 사업관리번호 기준으로 1회 배치 조회 (사업별 개별 SELECT N+1 제거)
        Map<String, Bplevm> existingByBusiness =
                planEvaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N").stream()
                        .collect(Collectors.toMap(e -> e.getAbusMngNo(), e -> e, (a, b) -> a));

        for (CouncilDto.PlanEvaluationItem item : request.items()) {
            // 적정/유보 값 검증
            if (!"Y".equals(item.pprtYn()) && !"N".equals(item.pprtYn())) {
                throw new IllegalArgumentException(
                        "적정여부는 Y(적정)/N(유보)만 허용됩니다. 사업: " + item.abusMngNo());
            }
            // 사유 필수 (적정/유보 모두)
            if (item.evalOpnn() == null || item.evalOpnn().isBlank()) {
                throw new IllegalArgumentException("적정/유보 사유(의견)는 필수입니다. 사업: " + item.abusMngNo());
            }

            // upsert: 기존 평가 있으면 update, 없으면 신규 INSERT
            Bplevm existing = existingByBusiness.get(item.abusMngNo());
            if (existing == null) {
                existing =
                        EntityRestoreSupport.findAndRestore(
                                entityManager,
                                Bplevm.class,
                                new BplevmId(asctId, eno, item.abusMngNo()));
            }
            if (existing != null) {
                existing.update(item.pprtYn(), item.evalOpnn());
            } else {
                Bplevm evaluation =
                        Bplevm.builder()
                                .itPtlAsctId(asctId)
                                .eno(eno)
                                .abusMngNo(item.abusMngNo())
                                .pprtYn(item.pprtYn())
                                .evalOpnn(item.evalOpnn())
                                .build();
                // 신규 INSERT는 persist()로 @PrePersist 발화 보장 (§5.12.1.1)
                entityManager.persist(evaluation);
            }
        }

        // 첫 제출 시 협의회 상태 전이: 07 → 08 (루프가 상태를 바꾸지 않으므로 최초 조회분 재사용)
        if ("07".equals(council.getItPtlAsctPrgStsTc())) {
            councilService.changeStatus(asctId, "08");
        }
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /** 평가 목록의 사번으로 사용자 이름 프로젝션 Map을 생성합니다. */
    private Map<String, UserRepository.UserNameView> buildUserMap(List<Bplevm> rows) {
        List<String> enos = rows.stream().map(r -> r.getEno()).distinct().toList();
        if (enos.isEmpty()) {
            return Map.of();
        }
        return userRepository.findNameViewsByEnoIn(enos).stream()
                .collect(Collectors.toMap(u -> u.getEno(), u -> u, (a, b) -> a));
    }
}
