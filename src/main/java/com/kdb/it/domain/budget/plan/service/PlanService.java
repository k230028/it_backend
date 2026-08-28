package com.kdb.it.domain.budget.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 정보기술부문 계획 서비스
 *
 * <p>정보기술부문계획(TPRMPP_BPLANM)과 정보기술부문계획 관계(TPRMPP_BPLANA)의 등록, 조회, 삭제 비즈니스 로직을 담당합니다.
 *
 * <p>조회 위주 서비스이므로 클래스 레벨 {@code @Transactional(readOnly=true)}를 적용하고, 쓰기 메서드는 메서드 레벨
 * {@code @Transactional}로 오버라이드합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private static final String IT_AI_HEADQUARTERS_NAME = "IT·AI본부";
    private static final String IT_PLANNING_DEPARTMENT_CODE = "180";
    private static final String IT_PLANNING_DEPARTMENT_NAME = "IT기획부";
    private static final String ORDINARY_PROJECT_SUMMARY_ID = "__ORDINARY_PROJECT_SUMMARY__";
    private static final String NEW_PROJECT_TYPE_CODE = "01";

    /** 정보기술부문계획(TPRMPP_BPLANM) CRUD 리포지토리 */
    private final BplanmRepository bplanmRepository;

    /** 정보기술부문계획 관계(TPRMPP_BPLANA) 리포지토리 */
    private final BplanaRepository bplanaRepository;

    /** 정보화사업 서비스: 프로젝트 목록·상세 조회 위임 */
    private final ProjectService projectService;

    /** 전산관리비 서비스: 비용 목록·상세 조회 위임 */
    private final CostService costService;

    /** 공통코드 서비스: 예산 신청 기간 검증 및 코드명 조회용 */
    private final CodeService codeService;

    /** 사용자(TPRMPP_CUSERI) 리포지토리: 작성자명 조회용 */
    private final UserRepository cuserIRepository;

    /** JSON 직렬화/역직렬화: 계획 스냅샷 파싱용 */
    private final ObjectMapper objectMapper;

    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;

    /**
     * 전체 계획 목록을 조회합니다.
     *
     * <p>삭제되지 않은(DEL_YN='N') 계획을 등록일시 내림차순으로 반환합니다.
     *
     * @return 계획 목록 응답 DTO 리스트
     */
    public List<PlanDto.ListResponse> getPlans() {
        List<BplanmRepository.PlanListView> plans =
                bplanmRepository.findListViewsByDelYnOrderByFstEnrDtmDesc("N");
        if (plans.isEmpty()) {
            return List.of();
        }

        // PUL_DTT 공통코드 cdva → cNm 매핑 (신규/계속 구분에 사용)
        Map<String, String> pulDttNameByCdva =
                codeService.findCodeEntitiesByCId(CommonCodeGroups.ABUS).stream()
                        .collect(
                                Collectors.toMap(
                                        value -> value.getCdva(),
                                        value -> value.getCdvaNm(),
                                        (a, b) -> a));

        // 최초생성자 사번 → 이름 매핑 (CUSERI 조인)
        List<String> userEnos =
                plans.stream()
                        .map(value -> value.getFstEnrUsid())
                        .filter(eno -> eno != null && !eno.isBlank())
                        .distinct()
                        .toList();
        Map<String, String> userNameByEno =
                userEnos.isEmpty()
                        ? Map.of()
                        : cuserIRepository.findNameViewsByEnoIn(userEnos).stream()
                                .collect(
                                        Collectors.toMap(
                                                value -> value.getEno(),
                                                value -> value.getUsrNm(),
                                                (a, b) -> a));

        return plans.stream()
                .map(
                        plan -> {
                            PlanDto.ListResponse dto = PlanDto.ListResponse.fromView(plan);
                            dto.setFstEnrUsNm(userNameByEno.get(plan.getFstEnrUsid()));
                            // 계획 저장 시점의 스냅샷 JSON 의 prjSnapshots 를 그대로 사용한다.
                            // - prjSnapshots 는 폼 단계에서 경상사업을 신규 정보화사업 대표 1건으로 합산해 구성됨
                            // - 각 항목의 pulDtt 는 공통코드 cdva (예: "001"=신규, "002"=계속)
                            // BPROJM 재조회 시 동일 prjMngNo 의 여러 스냅샷 중 ornYn='Y' 가 선택되어
                            // 카운트가 줄어드는 문제를 피하기 위함이다.
                            int itCnt = 0;
                            int newCnt = 0;
                            int contCnt = 0;
                            String dtlCone = plan.getRedtConeInf();
                            if (dtlCone != null && !dtlCone.isBlank()) {
                                try {
                                    Map<String, Object> snapshot =
                                            objectMapper.readValue(
                                                    dtlCone,
                                                    new TypeReference<Map<String, Object>>() {});
                                    // 신 포맷(prjSnapshots) 우선, 구 포맷(projects) 폴백
                                    Object snaps = snapshot.get("prjSnapshots");
                                    if (!(snaps instanceof List<?>)) {
                                        snaps = snapshot.get("projects");
                                    }
                                    if (snaps instanceof List<?> list) {
                                        for (Object item : list) {
                                            if (!(item instanceof Map<?, ?> m)) continue;
                                            itCnt++;
                                            // 신 포맷: pulDtt(=abusTc), 구 포맷: prjTp 에 추진유형 저장
                                            Object raw = m.get("pulDtt");
                                            if (raw == null) raw = m.get("prjTp");
                                            String pulDttNm =
                                                    pulDttNameByCdva.get(normalizeAbusTc(raw));
                                            if ("신규".equals(pulDttNm)) newCnt++;
                                            else if ("계속".equals(pulDttNm)) contCnt++;
                                        }
                                    }
                                } catch (JsonProcessingException e) {
                                    // 스냅샷 파싱 실패 시 카운트는 0 으로 유지(목록 화면은 동작해야 함).
                                    // 단, 잘못된 예산 집계가 조용히 산출되지 않도록 원인 예외를 warn으로 남긴다.
                                    log.warn("[계획] 스냅샷 파싱 실패 — 사업 카운트 0으로 폴백", e);
                                }
                            }
                            dto.setItPrjCnt(itCnt);
                            dto.setNewPrjCnt(newCnt);
                            dto.setContPrjCnt(contCnt);
                            return dto;
                        })
                .toList();
    }

    /**
     * 계획관리번호로 단건 상세 조회합니다.
     *
     * <p>계획 정보와 연결된 프로젝트관리번호 목록, JSON 스냅샷을 함께 반환합니다.
     *
     * @param reqDocNo 계획관리번호 (예: PLN-2026-0001)
     * @return 계획 상세 응답 DTO
     * @throws ResponseStatusException 계획을 찾을 수 없는 경우 404
     */
    public PlanDto.DetailResponse getPlan(String reqDocNo) {
        // 계획 조회
        Bplanm plan =
                bplanmRepository
                        .findByReqDocNoAndDelYn(reqDocNo, "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "존재하지 않는 계획입니다: " + reqDocNo));

        // 연결된 프로젝트관리번호 목록 조회
        List<String> prjMngNos =
                bplanaRepository.findAllByReqDocNoAndDelYn(reqDocNo, "N").stream()
                        .map(value -> value.getPrjMngNo())
                        .toList();

        return PlanDto.DetailResponse.fromEntity(plan, prjMngNos);
    }

    /**
     * 정보기술부문 계획을 등록합니다.
     *
     * <p>[처리 순서] 1. 대상 정보화사업 목록을 ProjectService에서 조회 2. 대상 전산업무비 목록을 CostService에서 조회 3. 예산
     * 합계(ADU_TOT_AMT, TOT_CPIT_AMT, TOT_XP_AMT) 계산 (정보화사업 + 전산업무비 합산) 4. JSON 스냅샷 생성 5. 계획관리번호 채번:
     * PLN-{bseYy}-{seq:04d} 6. TPRMPP_BPLANM 저장 7. 각 프로젝트·전산업무비에 대해 TPRMPP_BPLANA 저장
     *
     * @param request 계획 생성 요청 DTO
     * @return 생성된 계획관리번호
     * @throws ResponseStatusException 정보화사업과 전산업무비가 모두 비어있는 경우 400
     */
    @Transactional
    public String createPlan(PlanDto.CreateRequest request) {
        // 대상사업 유효성 검사 (프로젝트 또는 전산업무비 중 1개 이상 선택 필수)
        List<String> prjMngNos =
                request.getPrjMngNos() != null ? request.getPrjMngNos() : List.of();
        List<String> itMngcNos =
                request.getItMngcNos() != null ? request.getItMngcNos() : List.of();

        if (prjMngNos.isEmpty() && itMngcNos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상사업을 1개 이상 선택해야 합니다.");
        }

        // 1. 대상 정보화사업 목록 조회
        List<ProjectDto.Response> projects = List.of();
        if (!prjMngNos.isEmpty()) {
            ProjectDto.BulkGetRequest bulkRequest = new ProjectDto.BulkGetRequest();
            bulkRequest.setPrjMngNos(prjMngNos);
            // 대상년도 전달 필수: 그래야 bulk-get이 BBUGTM 편성예산(assetDupBg/costDupBg)을 채워
            // 폼 미리보기와 동일한 편성예산 합계를 계산할 수 있다.
            bulkRequest.setBseYy(request.getBseYy());
            // BulkResponse(부분 성공)에서 조회 성공 항목만 사용 (미존재 failedIds는 합계 계산 대상 아님)
            projects = projectService.getProjectsByIds(bulkRequest).items();
        }

        // 2. 대상 전산업무비 목록 조회
        List<CostDto.Response> costs = List.of();
        if (!itMngcNos.isEmpty()) {
            CostDto.BulkGetRequest costBulkRequest = new CostDto.BulkGetRequest();
            costBulkRequest.setCostBgNos(itMngcNos);
            // 대상년도 전달 필수: BBUGTM 편성예산(assetDupBg/costDupBg) 산출용 (위 사업과 동일)
            costBulkRequest.setBseYy(request.getBseYy());
            // BulkResponse(부분 성공)에서 조회 성공 항목만 사용 (미존재 failedIds는 합계 계산 대상 아님)
            costs = costService.getCostsByIds(costBulkRequest).items();
        }

        // 3. 예산 합계 계산 (정보화사업 + 전산업무비)
        // 폼 미리보기와 동일하게 BBUGTM 편성예산(DUP_BG) 기준으로 집계한다.
        // 과거에는 요청/소요 금액(totRqmAmt·costTotXpAmt·assetBg·costBg)을 합산해
        // 미리보기(편성예산)보다 큰 값이 저장되는 불일치가 있었다.
        // - 자본예산 편성액 = Σ assetDupBg, 일반관리비 편성액 = Σ costDupBg
        // - 총예산 = 자본예산 편성액 + 일반관리비 편성액 (미리보기 ttlBg = cptBg + mngc 와 동일)
        BigDecimal cpitBgApvAmt =
                projects.stream()
                        .map(p -> p.getAssetDupBg() != null ? p.getAssetDupBg() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        cpitBgApvAmt =
                costs.stream()
                        .map(c -> c.getAssetDupBg() != null ? c.getAssetDupBg() : BigDecimal.ZERO)
                        .reduce(cpitBgApvAmt, (left, right) -> left.add(right));

        BigDecimal totXpAmt =
                projects.stream()
                        .map(p -> p.getCostDupBg() != null ? p.getCostDupBg() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        totXpAmt =
                costs.stream()
                        .map(c -> c.getCostDupBg() != null ? c.getCostDupBg() : BigDecimal.ZERO)
                        .reduce(totXpAmt, (left, right) -> left.add(right));

        BigDecimal aduTotAmt = cpitBgApvAmt.add(totXpAmt);

        // 4. JSON 스냅샷 생성
        String snapshotJson =
                buildSnapshot(request, projects, costs, aduTotAmt, cpitBgApvAmt, totXpAmt);

        // 5. 계획관리번호 채번
        Long seq = bplanmRepository.getNextSequenceValue();
        String reqDocNo = String.format("PLN-%s-%04d", request.getBseYy(), seq);

        // 6. TPRMPP_BPLANM 저장
        Bplanm plan =
                Bplanm.builder()
                        .reqDocNo(reqDocNo)
                        .itPtlPlnTpC(request.getItPtlPlnTpC())
                        .bseYy(request.getBseYy())
                        .aduTotAmt(aduTotAmt)
                        .cpitBgApvAmt(cpitBgApvAmt)
                        .totXpAmt(totXpAmt)
                        .redtConeInf(snapshotJson)
                        .prjDvmCone(request.getPrjDvmCone())
                        .itBgCone(request.getItBgCone())
                        .itPrjRmk(request.getItPrjRmk())
                        .cpitBgRmk(request.getCpitBgRmk())
                        .mngcBgRmk(request.getMngcBgRmk())
                        .build();
        bplanmRepository.save(plan);

        // 7. TPRMPP_BPLANA 저장 (prjMngNo 컬럼에 프로젝트/전산업무비 관리번호를 함께 저장)
        for (String prjMngNo : prjMngNos) {
            Bplana relation = Bplana.builder().prjMngNo(prjMngNo).reqDocNo(reqDocNo).build();
            bplanaRepository.save(relation);
            bprojaSyncService.upsert(prjMngNo, reqDocNo, "11"); // 계획 진행중
        }
        for (String itMngcNo : itMngcNos) {
            Bplana relation = Bplana.builder().prjMngNo(itMngcNo).reqDocNo(reqDocNo).build();
            bplanaRepository.save(relation);
        }

        return reqDocNo;
    }

    /**
     * 이관 전용 — 부문계획 조정을 등록합니다.
     *
     * <p>{@link #createPlan}을 재사용하지 않는 이유 두 가지. 첫째, {@code createPlan}은 예산 합계를 대상 사업의 {@code
     * BBUGTM} 편성행({@code DUP_BG})에서 집계하는데, 이관 반영은 편성행을 마지막 단계({@code applyItemRates} 단일 호출)에서 한 번에
     * 만들므로 부문계획을 쓰는 시점에는 그 사업의 편성행이 아직 없거나 이전 값 그대로입니다 — 그대로 재사용하면 합계가 0이거나 stale 값으로 저장됩니다. 그래서 이
     * 경로는 조정 금액(자본예산 세 비목 합)을 호출자가 직접 넘깁니다. 둘째, 조정 시트의 집행 실적·사업진행·비고(§5.4, 원장 컬럼에 대응하는 자리가 없음)를 담을
     * 자리가 {@link PlanDto.ProjectSnapshot}에는 없어 {@link PlanDto.SnapshotDto}의 {@code
     * getMigrationAdjustments()}에 사업관리번호별로 별도로 남깁니다. 이관 오케스트레이션 서비스가 이미 {@code
     * com.kdb.it.domain.migration.service.adapter.PlanIntent}를 알고 있으므로, 이 서비스가 그 타입을 몰라도 되도록(신규 도메인
     * 역의존 방지) 원시 타입으로만 받습니다.
     *
     * @param bseYy 예산연도
     * @param plnTp 계획구분 (이관은 "조정" 고정)
     * @param projectNos 대상 사업관리번호 목록. capitalAmounts·generalAmounts와 같은 순서로 대응합니다
     * @param capitalAmounts 사업별 자본예산 조정 합계(개발비+기계장치+기타무형자산). projectNos와 같은 순서
     * @param generalAmounts 사업별 일반관리비 조정액. projectNos와 같은 순서. null 요소는 0으로 봅니다
     * @param snapshotFieldsByProject 사업관리번호 → 원장 외 스냅샷 전용 필드(집행 실적·사업진행·비고)
     * @return 생성된 계획관리번호
     * @throws IllegalArgumentException projectNos와 금액 목록들의 크기가 다른 경우
     * @throws ResponseStatusException 스냅샷 직렬화에 실패한 경우 500
     */
    @Transactional
    public String createPlanForMigration(
            String bseYy,
            String plnTp,
            List<String> projectNos,
            List<BigDecimal> capitalAmounts,
            List<BigDecimal> generalAmounts,
            Map<String, Map<String, String>> snapshotFieldsByProject) {
        if (projectNos.size() != capitalAmounts.size()
                || projectNos.size() != generalAmounts.size()) {
            throw new IllegalArgumentException("projectNos와 금액 목록의 크기가 다릅니다.");
        }

        BigDecimal cpitBgApvAmt = BigDecimal.ZERO;
        BigDecimal totXpAmt = BigDecimal.ZERO;
        List<PlanDto.ProjectSnapshot> projectSnapshots = new ArrayList<>();
        for (int i = 0; i < projectNos.size(); i++) {
            String prjMngNo = projectNos.get(i);
            BigDecimal amount =
                    capitalAmounts.get(i) != null ? capitalAmounts.get(i) : BigDecimal.ZERO;
            BigDecimal generalAmount =
                    generalAmounts.get(i) != null ? generalAmounts.get(i) : BigDecimal.ZERO;
            cpitBgApvAmt = cpitBgApvAmt.add(amount);
            totXpAmt = totXpAmt.add(generalAmount);

            ProjectDto.Response project =
                    Objects.requireNonNull(
                            projectService.getProject(prjMngNo), "이관 대상 사업 조회 결과가 없습니다.");
            projectSnapshots.add(
                    PlanDto.ProjectSnapshot.builder()
                            .prjMngNo(prjMngNo)
                            .abusNm(project.getAbusNm())
                            .prjTp(project.getBzTpC())
                            .pulDtt(project.getAbusTc())
                            .svnHdq(project.getPrlmHrkOgzCCone())
                            .svnDpm(project.getSvnDpmC())
                            .svnDpmNm(project.getSvnDpmCNm())
                            .prjBg(amount.add(generalAmount))
                            .assetBg(amount)
                            .costBg(generalAmount)
                            .build());
        }
        BigDecimal aduTotAmt = cpitBgApvAmt.add(totXpAmt);

        PlanDto.SnapshotDto snapshot =
                PlanDto.SnapshotDto.builder()
                        .bseYy(bseYy)
                        .itPtlPlnTpC(plnTp)
                        .aduTotAmt(aduTotAmt)
                        .cpitBgApvAmt(cpitBgApvAmt)
                        .totXpAmt(totXpAmt)
                        .projects(projectSnapshots)
                        .migrationAdjustments(snapshotFieldsByProject)
                        .build();
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "계획 스냅샷 직렬화에 실패했습니다.", e);
        }

        Long seq = bplanmRepository.getNextSequenceValue();
        String reqDocNo = String.format("PLN-%s-%04d", bseYy, seq);

        Bplanm plan =
                Bplanm.builder()
                        .reqDocNo(reqDocNo)
                        .itPtlPlnTpC(plnTp)
                        .bseYy(bseYy)
                        .aduTotAmt(aduTotAmt)
                        .cpitBgApvAmt(cpitBgApvAmt)
                        .totXpAmt(totXpAmt)
                        .redtConeInf(snapshotJson)
                        .build();
        bplanmRepository.save(plan);

        for (String prjMngNo : projectNos) {
            Bplana relation = Bplana.builder().prjMngNo(prjMngNo).reqDocNo(reqDocNo).build();
            bplanaRepository.save(relation);
            bprojaSyncService.upsert(prjMngNo, reqDocNo, "11"); // 계획 진행중
        }

        return reqDocNo;
    }

    /**
     * 계획을 논리 삭제합니다.
     *
     * <p>계획 엔티티의 DEL_YN을 'Y'로 변경하며, 연결된 정보기술부문계획 관계(BPLANA) 레코드도 함께 논리 삭제합니다.
     *
     * @param reqDocNo 계획관리번호
     * @throws ResponseStatusException 계획을 찾을 수 없는 경우 404
     */
    @Transactional
    public void deletePlan(String reqDocNo) {
        // 계획 존재 여부 확인
        Bplanm plan =
                bplanmRepository
                        .findByReqDocNoAndDelYn(reqDocNo, "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "존재하지 않는 계획입니다: " + reqDocNo));

        // 계획 논리 삭제
        plan.delete();
        bplanmRepository.save(plan);

        // 연결된 정보기술부문계획 관계 논리 삭제
        List<Bplana> relations = bplanaRepository.findAllByReqDocNoAndDelYn(reqDocNo, "N");
        for (Bplana relation : relations) {
            relation.delete();
            bplanaRepository.save(relation);
            bprojaSyncService.softDelete(relation.getPrjMngNo(), reqDocNo);
        }
    }

    /**
     * 계획의 5개 텍스트 필드를 수정합니다.
     *
     * @param reqDocNo 계획관리번호
     * @param request 수정 요청 DTO (prjDvmCone, itBgCone, itPrjRmk, cpitBgRmk, mngcBgRmk)
     * @throws ResponseStatusException 계획을 찾을 수 없는 경우 404
     */
    @Transactional
    public void updatePlanText(String reqDocNo, PlanDto.UpdateRequest request) {
        Bplanm plan =
                bplanmRepository
                        .findByReqDocNoAndDelYn(reqDocNo, "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "존재하지 않는 계획입니다: " + reqDocNo));
        plan.updateText(
                request.getPrjDvmCone(),
                request.getItBgCone(),
                request.getItPrjRmk(),
                request.getCpitBgRmk(),
                request.getMngcBgRmk());
        bplanmRepository.save(plan);
    }

    /**
     * 계획 저장 시 REDT_CONE_INF에 보관할 JSON 스냅샷을 생성합니다.
     *
     * <p>스냅샷 구조: - 기본 정보(bseYy, itPtlPlnTpC, 예산 합계) - 전체 프로젝트 목록(projects) - 부문(SVN_HDQ)별 그룹
     * 목록(byDepartment) - 사업유형(PRJ_TP)별 그룹 목록(byProjectType)
     *
     * @param request 계획 생성 요청
     * @param projects 대상 정보화사업 목록
     * @param costs 대상 전산업무비 목록
     * @param aduTotAmt 총예산 합계
     * @param cpitBgApvAmt 자본예산 합계
     * @param totXpAmt 일반관리비 합계
     * @return JSON 직렬화 문자열
     */
    private String buildSnapshot(
            PlanDto.CreateRequest request,
            List<ProjectDto.Response> projects,
            List<CostDto.Response> costs,
            BigDecimal aduTotAmt,
            BigDecimal cpitBgApvAmt,
            BigDecimal totXpAmt) {
        // 정보화사업 스냅샷 변환
        List<PlanDto.ProjectSnapshot> projectSnapshots =
                projects.stream()
                        .map(
                                p ->
                                        PlanDto.ProjectSnapshot.builder()
                                                .prjMngNo(p.getAbusMngNo())
                                                .abusNm(p.getAbusNm())
                                                .prjTp(p.getBzTpC())
                                                .pulDtt(p.getAbusTc())
                                                .svnHdq(p.getPrlmHrkOgzCCone())
                                                .svnDpm(p.getSvnDpmC())
                                                .svnDpmNm(p.getSvnDpmCNm())
                                                .prjBg(p.getTyyBgAmt())
                                                .assetBg(p.getAssetBg())
                                                .costBg(p.getCostBg())
                                                .build())
                        .collect(Collectors.toList());

        // 전산업무비 스냅샷 변환 (정보화사업과 동일한 형식으로 매핑)
        List<PlanDto.ProjectSnapshot> costSnapshots =
                costs.stream()
                        .map(
                                c ->
                                        PlanDto.ProjectSnapshot.builder()
                                                .prjMngNo(c.getCostBgNo())
                                                .abusNm(c.getCttNm())
                                                .prjTp(c.getTmnYn())
                                                .pulDtt(null)
                                                .svnHdq("미분류")
                                                .svnDpm(c.getCostSvnDpmC())
                                                .svnDpmNm(
                                                        c.getCostSvnDpmNm() != null
                                                                ? c.getCostSvnDpmNm()
                                                                : "")
                                                .prjBg(c.getCostTotXpAmt())
                                                .assetBg(c.getAssetBg())
                                                .costBg(c.getCostBg())
                                                .build())
                        .toList();

        // 부문별 목록은 경상사업을 IT기획부 대표 1건으로 합산하고,
        // 사업유형별 목록은 기존처럼 일반 정보화사업만 표시합니다.
        List<ProjectDto.Response> ordinaryProjects =
                projects.stream().filter(p -> "Y".equals(p.getOdnYn())).toList();
        List<PlanDto.ProjectSnapshot> generalBusinessListSnapshots =
                projectSnapshots.stream()
                        .filter(
                                p ->
                                        ordinaryProjects.stream()
                                                .noneMatch(
                                                        ordinary ->
                                                                Objects.equals(
                                                                        ordinary.getAbusMngNo(),
                                                                        p.getPrjMngNo())))
                        .toList();
        List<PlanDto.ProjectSnapshot> departmentBusinessListSnapshots =
                new ArrayList<>(generalBusinessListSnapshots);
        PlanDto.ProjectSnapshot ordinaryProjectSummary =
                buildOrdinaryProjectSummary(request.getBseYy(), ordinaryProjects);
        if (ordinaryProjectSummary != null) {
            departmentBusinessListSnapshots.add(ordinaryProjectSummary);
        }

        // 통합 스냅샷 목록: 경상사업 원본 여러 건은 대표 신규 정보화사업 1건으로 치환합니다.
        List<PlanDto.ProjectSnapshot> combinedProjectSnapshots =
                new ArrayList<>(departmentBusinessListSnapshots);
        combinedProjectSnapshots.addAll(costSnapshots);

        // 부문(SVN_HDQ)별 그룹핑
        Map<String, List<PlanDto.ProjectSnapshot>> byDeptMap =
                departmentBusinessListSnapshots.stream()
                        .collect(
                                Collectors.groupingBy(
                                        p -> p.getSvnHdq() != null ? p.getSvnHdq() : "미분류",
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        List<Map<String, Object>> byDepartment =
                byDeptMap.entrySet().stream()
                        .map(
                                entry -> {
                                    Map<String, Object> group = new HashMap<>();
                                    group.put("svnHdq", entry.getKey());
                                    group.put("projects", entry.getValue());
                                    return group;
                                })
                        .toList();

        // 사업유형(PRJ_TP)별 그룹핑
        Map<String, List<PlanDto.ProjectSnapshot>> byTypeMap =
                generalBusinessListSnapshots.stream()
                        .collect(
                                Collectors.groupingBy(
                                        p -> p.getPrjTp() != null ? p.getPrjTp() : "미분류",
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        List<Map<String, Object>> byProjectType =
                byTypeMap.entrySet().stream()
                        .map(
                                entry -> {
                                    Map<String, Object> group = new HashMap<>();
                                    group.put("prjTp", entry.getKey());
                                    group.put("projects", entry.getValue());
                                    return group;
                                })
                        .toList();

        // 스냅샷 DTO 생성
        PlanDto.SnapshotDto snapshot =
                PlanDto.SnapshotDto.builder()
                        .bseYy(request.getBseYy())
                        .itPtlPlnTpC(request.getItPtlPlnTpC())
                        .aduTotAmt(aduTotAmt)
                        .cpitBgApvAmt(cpitBgApvAmt)
                        .totXpAmt(totXpAmt)
                        .projects(combinedProjectSnapshots)
                        .byDepartment(byDepartment)
                        .byProjectType(byProjectType)
                        .budgetAllocation(request.getBudgetAllocation())
                        .capitalBudget(request.getCapitalBudget())
                        .expenseCost(request.getExpenseCost())
                        // 카드 원천 데이터(B안): 상세 화면에서 폼과 동일하게 4개 카드를 재현하기 위한 패스스루
                        .prjSnapshots(request.getPrjSnapshots())
                        .capitalSummaryItems(request.getCapitalSummaryItems())
                        .expenseItems(request.getExpenseItems())
                        .costDetails(request.getCostDetails())
                        .costPrjNm(request.getCostPrjNm())
                        .build();

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "계획 스냅샷 직렬화에 실패했습니다.", e);
        }
    }

    /**
     * 부문별 사업목록에 표시할 경상사업 대표 행을 생성합니다.
     *
     * @param bseYy 기준년도
     * @param ordinaryProjects 선택된 경상사업 목록
     * @return 경상사업 대표 스냅샷. 경상사업이 없으면 null.
     */
    private static PlanDto.ProjectSnapshot buildOrdinaryProjectSummary(
            String bseYy, List<ProjectDto.Response> ordinaryProjects) {
        if (ordinaryProjects.isEmpty()) {
            return null;
        }

        ProjectDto.Response firstProject = ordinaryProjects.getFirst();
        BigDecimal assetBg = sumAmount(ordinaryProjects, project -> project.getAssetBg());
        BigDecimal costBg = sumAmount(ordinaryProjects, project -> project.getCostBg());
        BigDecimal prjBg = sumAmount(ordinaryProjects, project -> project.getTyyBgAmt());
        if (BigDecimal.ZERO.compareTo(prjBg) == 0) {
            prjBg = assetBg.add(costBg);
        }

        return PlanDto.ProjectSnapshot.builder()
                .prjMngNo(ORDINARY_PROJECT_SUMMARY_ID)
                .abusNm(
                        String.format(
                                "%s년 경상사업 (%s 등 %d건)",
                                bseYy,
                                firstProject.getAbusNm() != null
                                        ? firstProject.getAbusNm()
                                        : "경상사업",
                                ordinaryProjects.size()))
                .prjTp(firstProject.getBzTpC() != null ? firstProject.getBzTpC() : "미분류")
                .pulDtt(NEW_PROJECT_TYPE_CODE)
                .svnHdq(IT_AI_HEADQUARTERS_NAME)
                .svnDpm(IT_PLANNING_DEPARTMENT_CODE)
                .svnDpmNm(IT_PLANNING_DEPARTMENT_NAME)
                .prjBg(prjBg)
                .assetBg(assetBg)
                .costBg(costBg)
                .build();
    }

    /**
     * null 금액을 0으로 보정해 합산합니다.
     *
     * @param projects 선택 사업 목록
     * @param selector 합산 대상 금액 선택자
     * @return 합산 금액
     */
    private static BigDecimal sumAmount(
            List<ProjectDto.Response> projects,
            Function<ProjectDto.Response, BigDecimal> selector) {
        return projects.stream()
                .map(selector)
                .map(value -> value != null ? value : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    /**
     * 스냅샷에 저장된 추진유형 값을 현행 ABUS_TC 코드값ID(10/20, 운영 표준)로 정규화한다.
     *
     * <p>마이그레이션 이전 스냅샷은 구 PUL_DTT 값(001/002), 그룹ID 접두 형식 (PUL_DTT_001 등) 또는 구 개발 체계 값(01/02)을 저장했을
     * 수 있어 현행 코드값과 매칭되도록 변환한다.
     *
     * @param raw 스냅샷 항목의 추진유형 원본값(null 허용)
     * @return 정규화된 코드값ID(예: "10", "20"). 입력이 null 이면 null.
     */
    private static String normalizeAbusTc(Object raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.toString().trim();
        if (code.startsWith("PUL_DTT_")) {
            code = code.substring("PUL_DTT_".length());
        }
        // 구 3자리(001/002) → 구 2자리(01/02)
        if (code.length() == 3 && code.startsWith("0")) {
            code = code.substring(1);
        }
        // 구 개발 체계(01=신규/02=계속) → 운영 표준(10/20)
        if ("01".equals(code)) {
            return "10";
        }
        if ("02".equals(code)) {
            return "20";
        }
        return code;
    }
}
