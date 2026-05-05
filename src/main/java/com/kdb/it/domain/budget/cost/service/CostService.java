package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.common.system.security.CustomUserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전산관리비(IT 관리비) 서비스
 *
 * <p>
 * 전산관리비(TAAABB_BCOSTM) 엔티티의 생성, 조회, 수정, 삭제(Soft Delete)
 * 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * 복합키 구조:
 * </p>
 * <ul>
 * <li>{@code IT_MNGC_NO} (전산관리비관리번호): 논리적 식별자</li>
 * <li>{@code IT_MNGC_SNO} (전산관리비일련번호): 동일 관리번호 내의 이력 순번</li>
 * </ul>
 *
 * <p>
 * 조회/수정/삭제는 {@code IT_MNGC_NO}를 기준으로 하며,
 * {@code LST_YN='Y'}인 항목(최신 이력)을 대상으로 처리합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴: {@code DEL_YN='Y'}로 변경하여 논리 삭제합니다.
 * </p>
 *
 * <p>
 * {@code @Transactional(readOnly = true)}: 조회 메서드의 기본값.
 * 쓰기 메서드는 {@code @Transactional}로 오버라이드합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostService {

    private final CostRepository costRepository;
    private final BtermmRepository btermmRepository;
    private final ApplicationMapRepository capplaRepository;
    private final ApplicationRepository capplmRepository;
    private final OrganizationRepository corgnIRepository;
    private final UserRepository cuserIRepository;
    private final ApproverRepository cdecimRepository;
    private final CodeRepository ccodemRepository;

    /** 공통코드 서비스: 예산 신청 기간 검증용 */
    private final com.kdb.it.common.code.service.CodeService codeService;

    /** 편성예산(BBUGTM) 리포지토리: 일괄 조회 시 itMngcNo별 DUP_BG 합계 조회용 */
    private final BbugtmRepository bbugtmRepository;

    /** 일반관리비 대상 코드값구분 */
    private static final Set<String> COST_CTT_TPS = Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

    /**
     * 특정 전산관리비 단건 조회
     *
     * <p>
     * {@code IT_MNGC_NO}로 삭제되지 않은({@code DEL_YN='N'}) 항목을 조회합니다.
     * 동일 관리번호에 여러 이력(SNO)이 있을 수 있으므로, 첫 번째 항목을 반환합니다.
     * </p>
     *
     * <p>
     * 비즈니스 규칙상 {@code IT_MNGC_NO}가 유니크하게 관리된다면 목록 크기는 1입니다.
     * </p>
     *
     * @param itMngcNo 조회할 전산관리비관리번호
     * @return 전산관리비 응답 DTO
     * @throws IllegalArgumentException 해당 관리번호의 항목이 없는 경우
     */
    public CostDto.Response getCost(String itMngcNo) {
        List<Bcostm> costs = costRepository.findByItMngcNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }
        CostDto.Response response = CostDto.Response.fromEntity(costs.get(0));
        enrichResponse(response, costs.get(0));
        attachTerminals(response);
        return response;
    }

    /**
     * 전체 전산관리비 목록 조회
     *
     * <p>
     * 삭제되지 않은({@code DEL_YN='N'}) 모든 전산관리비를 조회하여
     * DTO 목록으로 변환하여 반환합니다.
     * </p>
     *
     * @return 전체 전산관리비 응답 DTO 목록
     */
    public List<CostDto.Response> getCostList() {
        List<Bcostm> costs = costRepository.findAllByDelYn("N");
        List<CostDto.Response> responses = costs.stream()
                .map(CostDto.Response::fromEntity)
                .collect(Collectors.toList());
        enrichCostListBatch(costs, responses);
        return responses;
    }

    /**
     * 검색 조건으로 전산관리비 목록 조회
     *
     * <p>
     * {@link CostDto.SearchCondition}의 조건이 모두 비어있으면 전체 조회({@link #getCostList()})와 동일합니다.
     * </p>
     *
     * <p>
     * {@code apfSts} 필터 처리:
     * </p>
     * <ul>
     * <li>{@code "none"}: 신청서가 없는 전산관리비만 조회 (CAPPLA 연결 없음)</li>
     * <li>그 외 값: 최신 신청서의 결재상태가 해당 값인 전산관리비만 조회</li>
     * <li>null/미입력: 결재상태 필터 없음</li>
     * </ul>
     *
     * @param condition 검색 조건 DTO (apfSts, biceDpm, biceTem, infPrtYn)
     * @return 조건에 맞는 전산관리비 응답 DTO 목록
     */
    public List<CostDto.Response> searchCostList(CostDto.SearchCondition condition) {
        List<Bcostm> costs = costRepository.searchByCondition(condition);
        List<CostDto.Response> responses = costs.stream()
                .map(CostDto.Response::fromEntity)
                .collect(Collectors.toList());
        enrichCostListBatch(costs, responses);
        return responses;
    }

    /**
     * 신규 전산관리비 생성
     *
     * <p>
     * 전산관리비관리번호({@code IT_MNGC_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     * 일련번호({@code IT_MNGC_SNO})는 기존 데이터 기준 MAX+1로 설정합니다.
     * </p>
     *
     * <p>
     * 관리번호 자동 생성 형식: {@code COST_{yyyy}_{seq:04d}}
     * </p>
     * <p>
     * 예: {@code COST_2026_0001}
     * </p>
     *
     * <p>
     * 일련번호(SNO) 채번:
     * </p>
     * <ul>
     * <li>기존 데이터가 없으면 1</li>
     * <li>기존 데이터가 있으면 MAX(IT_MNGC_SNO) + 1</li>
     * </ul>
     *
     * @param request 전산관리비 생성 요청 DTO (관리번호, 비목명, 계약 정보 등)
     * @return 생성된 전산관리비관리번호
     */
    @Transactional
    public String createCost(CostDto.CreateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        String itMngcNo = request.getItMngcNo();

        if (itMngcNo == null || itMngcNo.isEmpty()) {
            Long seq = costRepository.getNextSequenceValue();
            String year = String.valueOf(LocalDate.now().getYear());
            itMngcNo = String.format("COST_%s_%04d", year, seq);
            request.setItMngcNo(itMngcNo);
        }

        Integer nextSno = costRepository.getNextSnoValue(itMngcNo);
        if (nextSno == null) {
            nextSno = 1;
        }

        Bcostm bcostm = request.toEntity(nextSno);
        costRepository.save(bcostm);

        if (request.getTerminals() != null && !request.getTerminals().isEmpty()) {
            for (CostDto.TerminalDto tDto : request.getTerminals()) {
                if (tDto.getTmnMngNo() == null || tDto.getTmnMngNo().isEmpty()) {
                    tDto.setTmnMngNo(generateTmnMngNo());
                }
                if (tDto.getTmnSno() == null || tDto.getTmnSno().isEmpty()) {
                    tDto.setTmnSno("1");
                }

                Btermm btermm = tDto.toEntity();
                btermm.setBcostmInfo(bcostm.getItMngcNo(), bcostm.getItMngcSno());
                btermmRepository.save(btermm);
            }
        }

        return bcostm.getItMngcNo();
    }

    /**
     * 전산관리비 수정
     *
     * <p>
     * {@code IT_MNGC_NO}로 조회된 항목 중 {@code LST_YN='Y'}인 최신 이력을 수정합니다.
     * 최신 이력이 없으면 첫 번째 항목을 수정 대상으로 사용합니다.
     * </p>
     *
     * <p>
     * JPA Dirty Checking: 조회된 엔티티의 필드를 변경하면 트랜잭션 종료 시
     * 자동으로 UPDATE 쿼리가 실행됩니다.
     * </p>
     *
     * @param itMngcNo 수정할 전산관리비관리번호
     * @param request  수정 요청 DTO (비목명, 계약 정보, 예산 등)
     * @return 수정된 전산관리비관리번호
     * @throws IllegalArgumentException 해당 관리번호의 항목이 없는 경우
     */
    @Transactional
    public String updateCost(String itMngcNo, CostDto.UpdateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        List<Bcostm> costs = costRepository.findByItMngcNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }

        Bcostm target = costs.stream()
                .filter(c -> "Y".equals(c.getLstYn()))
                .findFirst()
                .orElse(costs.get(0));

        validateModifyPermission(target.getFstEnrUsid(), target.getBiceDpm());

        target.update(
                request.getIoeC(), request.getCttNm(), request.getCttOpp(),
                request.getItMngcBg(), request.getDfrCle(), request.getFstDfrDt(),
                request.getCur(), request.getXcr(), request.getXcrBseDt(),
                request.getInfPrtYn(), request.getIndRsn(), request.getCgpr(),
                request.getBiceDpm(), request.getBiceTem(), request.getAbusC(),
                request.getItMngcTp(), request.getPulDtt(), request.getBgYy());

        /* 연관된 단말기 목록 업데이트: 기존 Soft Delete 후 재등록 */
        List<Btermm> existingTerminals = btermmRepository.findByItMngcNoAndItMngcSno(target.getItMngcNo(), target.getItMngcSno());
        for (Btermm et : existingTerminals) {
            et.delete();
        }

        if (request.getTerminals() != null && !request.getTerminals().isEmpty()) {
            for (CostDto.TerminalDto tDto : request.getTerminals()) {
                /* 새 PK를 발급하여 Soft Delete된 기존 레코드와 충돌 방지 */
                tDto.setTmnMngNo(generateTmnMngNo());
                tDto.setTmnSno("1");

                Btermm btermm = tDto.toEntity();
                btermm.setBcostmInfo(target.getItMngcNo(), target.getItMngcSno());
                btermmRepository.save(btermm);
            }
        }

        return target.getItMngcNo();
    }

    /**
     * 전산관리비 삭제 (Soft Delete)
     *
     * <p>
     * {@code IT_MNGC_NO}에 해당하는 모든 이력(SNO)에 대해 {@code DEL_YN='Y'}로 설정합니다.
     * </p>
     *
     * <p>
     * 물리 삭제(DELETE)를 수행하지 않으며, 논리적으로 삭제 처리합니다.
     * </p>
     *
     * @param itMngcNo 삭제할 전산관리비관리번호
     * @throws IllegalArgumentException 해당 관리번호의 항목이 없는 경우
     */
    @Transactional
    public void deleteCost(String itMngcNo) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        List<Bcostm> costs = costRepository.findByItMngcNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }

        validateModifyPermission(costs.get(0).getFstEnrUsid(), costs.get(0).getBiceDpm());

        for (Bcostm cost : costs) {
            cost.delete();
            List<Btermm> terminals = btermmRepository.findByItMngcNoAndItMngcSno(cost.getItMngcNo(), cost.getItMngcSno());
            for (Btermm t : terminals) {
                t.delete();
            }
        }
    }

    /**
     * 전산관리비 일괄 조회
     *
     * <p>
     * 여러 관리번호를 한 번에 조회합니다. 존재하지 않는 항목은 결과에서 제외합니다.
     * </p>
     *
     * @param request 일괄 조회 요청 DTO (전산관리비관리번호 목록)
     * @return 존재하는 항목의 응답 DTO 목록 (없는 항목 제외)
     */
    public List<CostDto.Response> getCostsByIds(CostDto.BulkGetRequest request) {
        List<CostDto.Response> responses = request.getItMngcNos().stream()
                .map(itMngcNo -> {
                    try {
                        return getCost(itMngcNo);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());

        // TAAABB_BBUGTM 기준 편성예산(DUP_BG) 일괄 조회 후 각 응답에 설정
        String bgYy = request.getBgYy();
        if (bgYy != null && !bgYy.isBlank() && !responses.isEmpty()) {
            List<String> itMngcNos = responses.stream()
                    .map(CostDto.Response::getItMngcNo)
                    .toList();
            Map<String, BigDecimal> dupBgMap = bbugtmRepository.sumDupBgByItMngcNos(itMngcNos, bgYy);
            // 전산업무비는 ioeC가 IOE_CPIT이면 자본예산, 나머지면 일반관리비 단일 분류
            responses.forEach(r -> {
                BigDecimal dupBg = dupBgMap.getOrDefault(r.getItMngcNo(), BigDecimal.ZERO);
                r.setDupBg(dupBg);
                boolean isAsset = r.getAssetBg() != null && r.getAssetBg().compareTo(BigDecimal.ZERO) > 0;
                r.setAssetDupBg(isAsset ? dupBg : BigDecimal.ZERO);
                r.setCostDupBg(isAsset ? BigDecimal.ZERO : dupBg);
            });
        }
        return responses;
    }

    /**
     * 전산관리비 응답 DTO에 신청서 정보 설정 (내부 헬퍼 메서드)
     *
     * <p>
     * 전산관리비관리번호와 순번으로 연결된 신청서(CAPPLA) 중 가장 최신 신청서를 조회하여
     * 응답 DTO에 신청관리번호({@code apfMngNo})와 결재상태({@code apfSts})를 설정합니다.
     * </p>
     *
     * <p>
     * 조회 기준:
     * </p>
     * <ul>
     * <li>{@code ORC_TB_CD = 'BCOSTM'}: 전산관리비 원본 테이블 코드</li>
     * <li>{@code ORC_PK_VL = itMngcNo}: 전산관리비관리번호</li>
     * <li>{@code ORC_SNO_VL = itMngcSno}: 전산관리비일련번호</li>
     * <li>최신순 정렬 ({@code APF_REL_SNO DESC})</li>
     * </ul>
     *
     * @param response  신청서 정보를 설정할 응답 DTO
     * @param itMngcNo  전산관리비관리번호
     * @param itMngcSno 전산관리비일련번호
     */
    private void setApplicationInfo(CostDto.Response response, String itMngcNo, Integer itMngcSno) {
        List<Cappla> capplas = capplaRepository
                .findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc("BCOSTM", itMngcNo, itMngcSno);

        if (!capplas.isEmpty()) {
            Cappla cappla = capplas.get(0);
            response.setApfMngNo(cappla.getApfMngNo());

            capplmRepository.findById(cappla.getApfMngNo())
                    .ifPresent(capplm -> {
                        response.setApfSts(capplm.getApfSts());
                        List<Cdecim> decisions = cdecimRepository
                                .findByDcdMngNoOrderByDcdSqnAsc(cappla.getApfMngNo());
                        response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                    });
        }
    }

    /**
     * 전산관리비 응답 DTO에 자본예산/일반관리비 설정 (내부 헬퍼 메서드)
     *
     * <p>
     * 비목코드(ioeC)를 공통코드에서 조회하여 코드값구분(cttTp) 기준으로 분류합니다.
     * </p>
     * <ul>
     * <li>자본예산: cttTp가 IOE_CPIT인 경우 → assetBg = itMngcBg, costBg = 0</li>
     * <li>일반관리비: cttTp가 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE인 경우 → assetBg = 0, costBg = itMngcBg</li>
     * </ul>
     *
     * @param response 예산 구분을 설정할 응답 DTO
     */
    private void setBudgetCategory(CostDto.Response response) {
        BigDecimal totalBg = response.getItMngcBg() != null ? response.getItMngcBg() : BigDecimal.ZERO;
        BigDecimal zero = BigDecimal.ZERO;

        // 세부 자본예산 필드 초기화
        response.setAssetBg(zero);
        response.setDevBg(zero);
        response.setMachBg(zero);
        response.setIntanBg(zero);
        response.setCostBg(zero);

        if (response.getIoeC() == null || response.getIoeC().isEmpty()) {
            return;
        }

        Optional<Ccodem> codeOpt = ccodemRepository.findByCdIdWithValidDate(response.getIoeC(), null);

        if (codeOpt.isPresent()) {
            Ccodem code = codeOpt.get();
            String cttTp = code.getCttTp();
            if ("IOE_CPIT".equals(cttTp)) {
                response.setAssetBg(totalBg);
                // 코드설명(cdDes) 기준으로 세부 분류
                String cdDes = code.getCdDes() != null ? code.getCdDes() : "";
                switch (cdDes) {
                    case "개발비" -> response.setDevBg(totalBg);
                    case "기계장치" -> response.setMachBg(totalBg);
                    case "기타무형자산" -> response.setIntanBg(totalBg);
                }
                return;
            }
            if (COST_CTT_TPS.contains(cttTp)) {
                response.setCostBg(totalBg);
                return;
            }
        }
    }

    /**
     * 전산관리비 목록 응답에 신청서 정보·코드명·예산 구분을 배치로 주입 (N+1 방지)
     *
     * <p>CAPPLA 1회, CAPPLM 1회, CDECIM 1회, CORGNI 1회, CUSERI 1회 — 총 5 쿼리로 처리합니다.</p>
     */
    private void enrichCostListBatch(List<Bcostm> costs, List<CostDto.Response> responses) {
        if (costs.isEmpty()) return;

        // --- 1. CAPPLA 배치 조회 ---
        List<String> itMngcNos = costs.stream().map(Bcostm::getItMngcNo).distinct().collect(Collectors.toList());
        List<Cappla> allCapplas = capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc("BCOSTM", itMngcNos);

        // itMngcNo+sno 복합키 → 최신 Cappla
        Map<String, Cappla> latestCappla = new java.util.LinkedHashMap<>();
        for (Cappla c : allCapplas) {
            String key = c.getOrcPkVl() + "_" + c.getOrcSnoVl();
            latestCappla.putIfAbsent(key, c);
        }

        // --- 2. CAPPLM 배치 조회 ---
        List<String> apfMngNos = latestCappla.values().stream()
                .map(Cappla::getApfMngNo).collect(Collectors.toList());
        Map<String, Capplm> capplmMap = capplmRepository.findAllById(apfMngNos).stream()
                .collect(Collectors.toMap(Capplm::getApfMngNo, m -> m));

        // --- 3. CDECIM 배치 조회 ---
        List<Cdecim> allDecisions = cdecimRepository.findByDcdMngNoInOrderByDcdSqnAsc(apfMngNos);
        Map<String, List<Cdecim>> decisionMap = allDecisions.stream()
                .collect(Collectors.groupingBy(Cdecim::getDcdMngNo));

        // --- 4. 부서코드·사원번호 수집 ---
        Set<String> orgCodes = new java.util.HashSet<>();
        Set<String> userEnos = new java.util.HashSet<>();
        for (CostDto.Response r : responses) {
            if (r.getBiceDpm() != null && !r.getBiceDpm().isEmpty()) orgCodes.add(r.getBiceDpm());
            if (r.getBiceTem() != null && !r.getBiceTem().isEmpty()) orgCodes.add(r.getBiceTem());
            if (r.getCgpr() != null && !r.getCgpr().isEmpty()) userEnos.add(r.getCgpr());
        }

        // --- 5. 배치 조회 ---
        Map<String, String> orgNameMap = corgnIRepository.findAllById(orgCodes).stream()
                .collect(Collectors.toMap(CorgnI::getPrlmOgzCCone, CorgnI::getBbrNm));
        Map<String, String> userNameMap = cuserIRepository.findAllById(userEnos).stream()
                .collect(Collectors.toMap(CuserI::getEno, CuserI::getUsrNm));

        // --- 6. 응답 DTO에 일괄 주입 ---
        for (int i = 0; i < costs.size(); i++) {
            Bcostm cost = costs.get(i);
            CostDto.Response response = responses.get(i);

            String key = cost.getItMngcNo() + "_" + cost.getItMngcSno();
            Cappla cappla = latestCappla.get(key);
            if (cappla != null) {
                response.setApfMngNo(cappla.getApfMngNo());
                Capplm capplm = capplmMap.get(cappla.getApfMngNo());
                if (capplm != null) {
                    response.setApfSts(capplm.getApfSts());
                    List<Cdecim> decisions = decisionMap.getOrDefault(cappla.getApfMngNo(), List.of());
                    response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                }
            }

            if (response.getBiceDpm() != null) response.setBiceDpmNm(orgNameMap.get(response.getBiceDpm()));
            if (response.getBiceTem() != null) response.setBiceTemNm(orgNameMap.get(response.getBiceTem()));
            if (response.getCgpr() != null) response.setCgprNm(userNameMap.get(response.getCgpr()));

            setBudgetCategory(response);

            if ("IT_MNGC_TP_002".equals(cost.getItMngcTp())) {
                attachTerminals(response);
            }
        }
    }

    /** 응답 DTO에 신청서 정보, 코드명, 예산 구분을 일괄 설정 */
    private void enrichResponse(CostDto.Response response, Bcostm cost) {
        setApplicationInfo(response, cost.getItMngcNo(), cost.getItMngcSno());
        setCodeNames(response);
        setBudgetCategory(response);
    }

    /**
     * 응답 DTO에 연관된 단말기 목록을 조회·변환하여 설정
     */
    private void attachTerminals(CostDto.Response response) {
        List<Btermm> terminals = btermmRepository
                .findByItMngcNoAndItMngcSnoAndDelYn(response.getItMngcNo(), response.getItMngcSno(), "N");
        List<CostDto.TerminalDto> dtos = terminals.stream().map(CostDto.TerminalDto::fromEntity).toList();
        setTerminalCodeNames(dtos);
        response.setTerminals(dtos);
    }

    /** 부서코드→부서명, 사원번호→사용자명 조회 및 설정 */
    private void setCodeNames(CostDto.Response response) {
        if (response.getBiceDpm() != null && !response.getBiceDpm().isEmpty()) {
            corgnIRepository.findById(response.getBiceDpm())
                    .ifPresent(org -> response.setBiceDpmNm(org.getBbrNm()));
        }
        if (response.getBiceTem() != null && !response.getBiceTem().isEmpty()) {
            corgnIRepository.findById(response.getBiceTem())
                    .ifPresent(org -> response.setBiceTemNm(org.getBbrNm()));
        }
        if (response.getCgpr() != null && !response.getCgpr().isEmpty()) {
            cuserIRepository.findById(response.getCgpr())
                    .ifPresent(user -> response.setCgprNm(user.getUsrNm()));
        }
    }

    /** 단말기 DTO 목록에 담당자명(cgprNm) 일괄 설정 (배치 조회로 N+1 방지) */
    private void setTerminalCodeNames(List<CostDto.TerminalDto> terminalDtos) {
        Set<String> enos = terminalDtos.stream()
                .map(CostDto.TerminalDto::getCgpr)
                .filter(cgpr -> cgpr != null && !cgpr.isEmpty())
                .collect(Collectors.toSet());
        if (enos.isEmpty()) return;

        Map<String, String> nameMap = cuserIRepository.findByEnoIn(enos).stream()
                .collect(Collectors.toMap(
                        CuserI::getEno,
                        CuserI::getUsrNm));
        terminalDtos.forEach(tDto -> {
            if (tDto.getCgpr() != null) {
                tDto.setCgprNm(nameMap.get(tDto.getCgpr()));
            }
        });
    }

    /** 단말기관리번호 자동 생성 (형식: TER_{yyyy}_{seq:04d}) */
    private String generateTmnMngNo() {
        Long seq = btermmRepository.getNextSequenceValue();
        String year = String.valueOf(LocalDate.now().getYear());
        return String.format("TER_%s_%04d", year, seq);
    }

    /**
     * 수정/삭제 권한 검증 (ProjectService.validateModifyPermission과 동일 규칙)
     *
     * <ol>
     *   <li>시스템관리자(ITPAD001): 전체 허용</li>
     *   <li>기획통할담당자(ITPZZ002): 소속 부서 리소스 허용</li>
     *   <li>일반사용자(ITPZZ001): 본인 작성 리소스만 허용</li>
     * </ol>
     */
    private void validateModifyPermission(String creatorEno, String resourceBbrC) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails currentUser)) {
            throw new AccessDeniedException("인증 정보를 확인할 수 없습니다.");
        }
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isDeptManager()) {
            if (currentUser.getBbrC() != null && currentUser.getBbrC().equals(resourceBbrC)) {
                return;
            }
            throw new AccessDeniedException("소속 부서의 리소스만 수정할 수 있습니다.");
        }
        if (currentUser.getEno().equals(creatorEno)) {
            return;
        }
        throw new AccessDeniedException("본인이 작성한 리소스만 수정할 수 있습니다.");
    }
}
