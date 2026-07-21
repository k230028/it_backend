package com.kdb.it.domain.council.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.PlanEvaluationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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

    // =========================================================================
    // 심의 대상(계획) 조회
    // =========================================================================

    /**
     * 협의회의 심의 대상 조회 (사업 카드·예산 표출용).
     *
     * <p>계획협의회의 ABUS_MNG_NO에 저장된 계획관리번호로 계획 스냅샷의 예산·부서 정보를 얻고, 스냅샷에 없는 사업개요·시작/종료일자는 BPROJM(정보화사업)
     * 상세에서 보강해 병합합니다. 경상사업(ornYn='Y')은 제외하고 정보화사업만 반환합니다.
     *
     * @param asctId 협의회ID
     * @return 심의 대상 (계획 요약 + 사업별 기본정보)
     * @throws IllegalStateException 계획이 연결되지 않은 협의회(dbrTc≠'02' 등)
     */
    public CouncilDto.PlanTargetsResponse getPlanTargets(String asctId) {
        Basctm council = councilService.findActiveCouncil(asctId);
        String reqDocNo = council.getAbusMngNo();
        if (reqDocNo == null || reqDocNo.isBlank()) {
            throw new IllegalStateException("계획이 연결되지 않은 협의회입니다: " + asctId);
        }
        PlanDto.DetailResponse plan = planService.getPlan(reqDocNo);

        // 계획 스냅샷에서 정보화사업(경상 제외) 노드 추출 (예산·부서·진행구분)
        List<JsonNode> snapBusinesses = parseSnapshotBusinesses(plan.getRedtConeInf());

        // 사업개요·시작/종료일자는 스냅샷에 없어 BPROJM 상세에서 보강
        List<String> prjMngNos =
                snapBusinesses.stream()
                        .map(n -> textOf(n, "prjMngNo"))
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
        Map<String, ProjectDto.Response> detailMap =
                fetchProjectDetails(prjMngNos, plan.getBseYy());

        // 조정 협의회는 '직전 승인(수립) 계획'의 사업별 예산을 최초값으로 병합(예산 최초/조정 비교)
        Map<String, JsonNode> baselineByBiz =
                "조정".equals(plan.getItPtlPlnTpC())
                        ? baselineBudgetByBusiness(findBaselinePlan(plan.getBseYy(), reqDocNo))
                        : Map.of();

        List<CouncilDto.PlanTargetBusiness> businesses = new ArrayList<>();
        for (JsonNode n : snapBusinesses) {
            String id = textOf(n, "prjMngNo");
            if (id == null || id.isBlank()) {
                continue;
            }
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
                countCostDetails(plan.getRedtConeInf()));
    }

    /** 계획 스냅샷(redtConeInf)에서 정보화사업(경상 제외) 노드 목록 추출. 실패 시 빈 목록. */
    private List<JsonNode> parseSnapshotBusinesses(String json) {
        List<JsonNode> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode root = SNAPSHOT_MAPPER.readTree(json);
            JsonNode arr =
                    root.has("prjSnapshots") ? root.get("prjSnapshots") : root.get("projects");
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    JsonNode orn = n.get("ornYn");
                    if (orn != null && "Y".equals(orn.asText())) {
                        continue; // 경상사업 제외 → 정보화사업만
                    }
                    result.add(n);
                }
            }
        } catch (Exception e) {
            // 파싱 실패 시 빈 목록 폴백
            // TODO: 유효한 빈 스냅샷과 파싱 오류를 구분하고 문서번호·원인을 진단 로그 또는 오류 응답에 남긴다.
        }
        return result;
    }

    /** 스냅샷의 전산업무비(costDetails) 건수. 실패 시 0. */
    private int countCostDetails(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode arr = SNAPSHOT_MAPPER.readTree(json).get("costDetails");
            return (arr != null && arr.isArray()) ? arr.size() : 0;
        } catch (Exception e) {
            // TODO: 실제 전산업무비 0건과 JSON 파싱 실패를 구분하고 대상 문맥과 원인을 기록한다.
            return 0;
        }
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
     * <p>완료된 계획협의회(dbrTc='02', 상태13)를 최근 등록순으로 훑어, 대상 계획이 같은 대상년도이고 계획구분이 '신규'(수립)이며 현재 계획과 다른 첫
     * 계획을 반환한다. 없으면 null.
     */
    private PlanDto.DetailResponse findBaselinePlan(String bseYy, String currentReqDocNo) {
        if (bseYy == null) {
            return null;
        }
        List<Basctm> completed =
                councilRepository
                        .findByItPtlAsctDbrTcAndItPtlAsctPrgStsTcAndDelYnOrderByFstEnrDtmDesc(
                                "02", "13", "N");
        for (Basctm c : completed) {
            String rd = c.getAbusMngNo();
            if (rd == null || rd.isBlank() || rd.equals(currentReqDocNo)) {
                continue;
            }
            try {
                PlanDto.DetailResponse p = planService.getPlan(rd);
                if (bseYy.equals(p.getBseYy()) && "신규".equals(p.getItPtlPlnTpC())) {
                    return p; // 최근 등록순 첫 매칭 = 직전 승인 수립 계획
                }
            } catch (Exception e) {
                // 계획 조회 실패 시 다음 후보로
                // TODO: 미존재 예외만 다음 후보로 넘기고 DB·권한·시스템 예외는 기록한 뒤 호출자에게 전파한다.
            }
        }
        return null;
    }

    /** 기준 계획 스냅샷에서 사업관리번호 → 예산 노드 매핑(prjBg/assetBg/costBg 조회용). */
    private Map<String, JsonNode> baselineBudgetByBusiness(PlanDto.DetailResponse baseline) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        if (baseline == null) {
            return map;
        }
        for (JsonNode n : parseSnapshotBusinesses(baseline.getRedtConeInf())) {
            String id = textOf(n, "prjMngNo");
            if (id != null && !id.isBlank()) {
                map.put(id, n);
            }
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
     * @param asctId 협의회ID
     * @return 요약 HTML + 구조화 판정
     */
    public CouncilDto.PlanResultSummaryResponse buildResultSummary(String asctId) {
        Basctm council = councilService.findActiveCouncil(asctId);

        List<Bplevm> all = planEvaluationRepository.findByItPtlAsctIdAndDelYn(asctId, "N");
        List<CouncilDto.PlanBusinessVerdict> verdicts = aggregateVerdicts(all);

        // 사업명 매핑 (계획 스냅샷)
        Map<String, String> nameById = resolveBusinessNames(council.getAbusMngNo());

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
                renderSummaryHtml(verdicts, nameById, reserveOpinions), verdicts);
    }

    /** 계획 스냅샷 JSON(redtConeInf) 파서 — 사업명 해석 전용(Jackson 2, 로컬 인스턴스). */
    private static final com.fasterxml.jackson.databind.ObjectMapper SNAPSHOT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 대상 계획의 스냅샷(redtConeInf JSON)에서 사업관리번호(prjMngNo) → 사업명(abusNm) 매핑을 해석한다. 파싱 실패/부재 시 빈 맵(호출부에서
     * 사업관리번호로 폴백).
     */
    private Map<String, String> resolveBusinessNames(String reqDocNo) {
        Map<String, String> map = new LinkedHashMap<>();
        if (reqDocNo == null || reqDocNo.isBlank()) {
            return map;
        }
        String json = planService.getPlan(reqDocNo).getRedtConeInf();
        if (json == null || json.isBlank()) {
            return map;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode snaps =
                    SNAPSHOT_MAPPER.readTree(json).get("prjSnapshots");
            if (snaps != null && snaps.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode s : snaps) {
                    com.fasterxml.jackson.databind.JsonNode id = s.get("prjMngNo");
                    com.fasterxml.jackson.databind.JsonNode nm = s.get("abusNm");
                    if (id != null && nm != null) {
                        map.put(id.asText(), nm.asText());
                    }
                }
            }
        } catch (Exception e) {
            // 스냅샷 파싱 실패 시 사업명 미해석(사업관리번호로 폴백)
            // TODO: 결과서의 사업명 폴백이 발생했음을 문서번호·원인과 함께 경고 로그로 남긴다.
        }
        return map;
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
