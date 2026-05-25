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
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
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
 * 전산관리비(TPRMPP_BCOSTM) 엔티티의 생성, 조회, 수정, 삭제(Soft Delete)
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

    /** 전산관리비 엔티티(TPRMPP_BITEMC) CRUD 리포지토리 */
    private final CostRepository costRepository;
    /** 단말기 관리(TPRMPP_BTERMM) 리포지토리: 단말기 연결 비용 조회용 */
    private final BtermmRepository btermmRepository;
    /** 신청서 연결 맵(TPRMPP_CAPPLA) 리포지토리: 결재 연결 조회용 */
    private final ApplicationMapRepository capplaRepository;
    /** 신청서 마스터(TPRMPP_CAPPLM) 리포지토리: 결재상태 조회용 */
    private final ApplicationRepository capplmRepository;
    /** 조직(TPRMPP_CORGNI) 리포지토리: 부서명 조회용 */
    private final OrganizationRepository corgnIRepository;
    /** 사용자(TPRMPP_CUSERI) 리포지토리: 담당자명 조회용 */
    private final UserRepository cuserIRepository;
    /** 결재자(TPRMPP_CDECIM) 리포지토리: 결재선 조회용 */
    private final ApproverRepository cdecimRepository;
    /** 공통코드(TPRMPP_CCODEM) 리포지토리: 코드명 배치 조회용 */
    private final CodeRepository ccodemRepository;

    /** 공통코드 서비스: 예산 신청 기간 검증용 */
    private final com.kdb.it.common.code.service.CodeService codeService;

    /** 편성예산(BBUGTM) 리포지토리: 일괄 조회 시 itMngcNo별 DUP_BG 합계 조회용 */
    private final BbugtmRepository bbugtmRepository;

    /** 환율 표준 조회 헬퍼: 외화 저장 전 Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7) */
    private final XcrLookupService xcrLookupService;

    /** 일반관리비 대상 코드값구분 */
    private static final Set<String> COST_CTT_TPS = Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE");

    /** 자본예산 세부 코드타입: 개발비/기계장치/기타무형자산 */
    private static final String IOE_DVC = "IOE_DVC";
    private static final String IOE_HW = "IOE_HW";
    private static final String IOE_SW = "IOE_SW";
    private static final Set<String> CAPITAL_DETAIL_CTPS = Set.of(IOE_DVC, IOE_HW, IOE_SW);

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
     * @param condition 검색 조건 DTO (apfSts, biceDpmC, biceTemC, infPrtYn)
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
            itMngcNo = String.format("COST-%s-%04d", year, seq);
            request.setItMngcNo(itMngcNo);
        }

        Integer nextSno = costRepository.getNextSnoValue(itMngcNo);
        if (nextSno == null) {
            nextSno = 1;
        }

        // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));

        // 외화 재계산: 클라 itMngcBgAmt를 fcAmt × xcr로 덮어씀 (CONTEXT.md 결정 C)
        BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                request.getFcAmt(), request.getItMngcBgAmt(), request.getCurC(), request.getXcr());
        request.setItMngcBgAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);

        Bcostm bcostm = request.toEntity(nextSno);
        costRepository.save(bcostm);

        if (request.getTerminals() != null && !request.getTerminals().isEmpty()) {
            for (CostDto.TerminalDto tDto : request.getTerminals()) {
                if (tDto.getTmnMngNo() == null || tDto.getTmnMngNo().isEmpty()) {
                    tDto.setTmnMngNo(generateTmnMngNo());
                }
                if (tDto.getTmnSno() == null) {
                    tDto.setTmnSno(1);
                }

                // XCR 표준 조회 (단말기): Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                tDto.setXcr(xcrLookupService.resolveXcr(tDto.getCurC(), LocalDate.now()));

                // 단말기 외화 재계산 (CONTEXT.md 결정 C)
                BigDecimal[] tReconciled = BudgetAmountCalculator.reconcileAmount(
                        tDto.getFcAmt(), tDto.getTmlAmt(), tDto.getCurC(), tDto.getXcr());
                tDto.setTmlAmt(tReconciled[0]);
                tDto.setFcAmt(tReconciled[1]);

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

        validateModifyPermission(target.getFstEnrUsid(), target.getBiceDpmC());

        // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));

        // 외화 재계산: 클라 itMngcBgAmt를 fcAmt × xcr로 덮어씀 (CONTEXT.md 결정 C)
        BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                request.getFcAmt(), request.getItMngcBgAmt(), request.getCurC(), request.getXcr());
        request.setItMngcBgAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);

        target.update(
                request.getIoeC(), request.getCttNm(), request.getCttOppNm(),
                request.getItMngcBgAmt(), request.getDfrCleC(), request.getFstDfrDt(),
                request.getCurC(), request.getXcr(), request.getXcrBseDt(),
                request.getInfPrtYn(), request.getIndRsn(), request.getCgprEno(),
                request.getBiceDpmC(), request.getBiceTemC(), request.getAbusC(),
                request.getItMngcTp(), request.getPulDtt(), request.getBgYy(), request.getCncdItMngcNo(),
                request.getFcAmt());

        /* 연관된 단말기 목록 업데이트: 기존 Soft Delete 후 재등록 */
        List<Btermm> existingTerminals = btermmRepository.findByItMngcNoAndItMngcSno(target.getItMngcNo(), target.getItMngcSno());
        for (Btermm et : existingTerminals) {
            et.delete();
        }

        if (request.getTerminals() != null && !request.getTerminals().isEmpty()) {
            for (CostDto.TerminalDto tDto : request.getTerminals()) {
                /* 새 PK를 발급하여 Soft Delete된 기존 레코드와 충돌 방지 */
                tDto.setTmnMngNo(generateTmnMngNo());
                tDto.setTmnSno(1);

                // XCR 표준 조회 (단말기): Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                tDto.setXcr(xcrLookupService.resolveXcr(tDto.getCurC(), LocalDate.now()));

                // 단말기 외화 재계산 (CONTEXT.md 결정 C)
                BigDecimal[] tReconciled = BudgetAmountCalculator.reconcileAmount(
                        tDto.getFcAmt(), tDto.getTmlAmt(), tDto.getCurC(), tDto.getXcr());
                tDto.setTmlAmt(tReconciled[0]);
                tDto.setFcAmt(tReconciled[1]);

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

        validateModifyPermission(costs.get(0).getFstEnrUsid(), costs.get(0).getBiceDpmC());

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
                        // FIXME: [B-H-04] null 필터 패턴 제거, 조회 실패시 예외 전파 또는 warn 로그 필요
                        // 현재 null → filter(Objects::nonNull) 패턴으로 실패 비용 항목이 silently 손실됨.
                        // FIXME: [B-C-05] B-C-03 참조. 실패 비용 ID warn 로그 및 호출자 통지 필요
                        return null;
                    }
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());

        // TPRMPP_BBUGTM 기준 편성예산(DUP_BG) 일괄 조회 후 각 응답에 설정
        String bgYy = request.getBgYy();
        if (bgYy != null && !bgYy.isBlank() && !responses.isEmpty()) {
            List<String> itMngcNos = responses.stream()
                    .map(CostDto.Response::getItMngcNo)
                    .toList();
            Map<String, BigDecimal> dupBgMap = bbugtmRepository.sumDupBgByItMngcNos(itMngcNos, bgYy);
            // 전산업무비는 ioeC가 IOE_CPIT이면 자본예산, 나머지면 일반관리비 단일 분류
            responses.forEach(r -> {
                BigDecimal dupBgAmt = dupBgMap.getOrDefault(r.getItMngcNo(), BigDecimal.ZERO);
                r.setDupBgAmt(dupBgAmt);
                boolean isAsset = r.getAssetBg() != null && r.getAssetBg().compareTo(BigDecimal.ZERO) > 0;
                r.setAssetDupBg(isAsset ? dupBgAmt : BigDecimal.ZERO);
                r.setCostDupBg(isAsset ? BigDecimal.ZERO : dupBgAmt);
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
                .findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfMngNoDesc("BCOSTM", itMngcNo, itMngcSno);

        if (!capplas.isEmpty()) {
            Cappla cappla = capplas.get(0);
            response.setApfMngNo(cappla.getApfMngNo());

            capplmRepository.findById(cappla.getApfMngNo())
                    .ifPresent(capplm -> {
                        response.setApfSts(capplm.getApfStsC() == null ? null
                            : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getApfStsC()).label());
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
     * <li>자본예산: cttTp가 IOE_DVC/IOE_HW/IOE_SW인 경우 → assetBg = itMngcBgAmt, costBg = 0</li>
     * <li>일반관리비: cttTp가 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE인 경우 → assetBg = 0, costBg = itMngcBgAmt</li>
     * </ul>
     *
     * @param response 예산 구분을 설정할 응답 DTO
     */
    private void setBudgetCategory(CostDto.Response response) {
        BigDecimal totalBg = response.getItMngcBgAmt() != null ? response.getItMngcBgAmt() : BigDecimal.ZERO;
        BigDecimal zero = BigDecimal.ZERO;

        // 세부 자본예산 필드 초기화
        response.setAssetBg(zero);
        response.setDvcBg(zero);
        response.setHwBg(zero);
        response.setSwBg(zero);
        response.setCostBg(zero);

        if (response.getIoeC() == null || response.getIoeC().isEmpty()) {
            return;
        }

        Optional<Ccodem> codeOpt = ccodemRepository.findByCIdWithValidDate("IOE", null)
                .stream()
                .filter(c -> response.getIoeC().equals(c.getCdva()))
                .findFirst();

        if (codeOpt.isPresent()) {
            Ccodem code = codeOpt.get();
            String cTp = code.getCTp();
            if (CAPITAL_DETAIL_CTPS.contains(cTp) || "IOE_CPIT".equals(cTp)) {
                response.setAssetBg(totalBg);
                // 신규 기준은 C_TP, 구 IOE_CPIT 데이터는 CDVA_DES 한글명으로 보정
                switch (cTp) {
                    case IOE_DVC -> response.setDvcBg(totalBg);
                    case IOE_HW -> response.setHwBg(totalBg);
                    case IOE_SW -> response.setSwBg(totalBg);
                    case "IOE_CPIT" -> {
                        String cdvaDes = code.getCdvaDes() != null ? code.getCdvaDes() : "";
                        if ("개발비".equals(cdvaDes)) response.setDvcBg(totalBg);
                        else if ("기계장치".equals(cdvaDes)) response.setHwBg(totalBg);
                        else if ("기타무형자산".equals(cdvaDes)) response.setSwBg(totalBg);
                    }
                    default -> {
                        // 위 CAPITAL_DETAIL_CTPS 조건과 switch 분기가 어긋나는 경우 금액만 자본예산으로 유지
                    }
                }
                return;
            }
            if (COST_CTT_TPS.contains(cTp)) {
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
        List<Cappla> allCapplas = capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfMngNoDesc("BCOSTM", itMngcNos);

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

        // --- 4. 부서코드·사원번호·공통코드 CDVA 수집 ---
        Set<String> orgCodes = new java.util.HashSet<>();
        Set<String> userEnos = new java.util.HashSet<>();
        Set<String> abusCdvas = new java.util.HashSet<>();
        Set<String> dfrCleCCdvas = new java.util.HashSet<>();
        Set<String> itMngcTpCdvas = new java.util.HashSet<>();
        Set<String> pulDttCdvas = new java.util.HashSet<>();
        Set<String> ioeCCdvas = new java.util.HashSet<>();
        for (CostDto.Response r : responses) {
            if (r.getBiceDpmC() != null && !r.getBiceDpmC().isEmpty()) orgCodes.add(r.getBiceDpmC());
            if (r.getBiceTemC() != null && !r.getBiceTemC().isEmpty()) orgCodes.add(r.getBiceTemC());
            if (r.getCgprEno() != null && !r.getCgprEno().isEmpty()) userEnos.add(r.getCgprEno());
            if (r.getAbusC() != null && !r.getAbusC().isEmpty()) abusCdvas.add(r.getAbusC());
            if (r.getDfrCleC() != null && !r.getDfrCleC().isEmpty()) dfrCleCCdvas.add(r.getDfrCleC());
            if (r.getItMngcTp() != null && !r.getItMngcTp().isEmpty()) itMngcTpCdvas.add(r.getItMngcTp());
            if (r.getPulDtt() != null && !r.getPulDtt().isEmpty()) pulDttCdvas.add(r.getPulDtt());
            if (r.getIoeC() != null && !r.getIoeC().isEmpty()) ioeCCdvas.add(r.getIoeC());
        }

        // --- 5. 배치 조회 ---
        Map<String, String> orgNameMap = corgnIRepository.findAllById(orgCodes).stream()
                .collect(Collectors.toMap(CorgnI::getPrlmOgzCCone, CorgnI::getBbrNm));
        Map<String, String> userNameMap = cuserIRepository.findAllById(userEnos).stream()
                .collect(Collectors.toMap(CuserI::getEno, CuserI::getUsrNm));
        Map<String, String> abusCNameMap = abusCdvas.isEmpty() ? Map.of()
                : buildCodeNameMap("ABUS_C", abusCdvas);
        Map<String, String> dfrCleCNameMap = dfrCleCCdvas.isEmpty() ? Map.of()
                : buildCodeNameMap("DFR_CLE", dfrCleCCdvas);
        Map<String, String> itMngcTpNameMap = itMngcTpCdvas.isEmpty() ? Map.of()
                : buildCodeNameMap("IT_MNGC_TP", itMngcTpCdvas);
        Map<String, String> pulDttNameMap = pulDttCdvas.isEmpty() ? Map.of()
                : buildCodeNameMap("PUL_DTT", pulDttCdvas);
        Map<String, String> ioeCNameMap = ioeCCdvas.isEmpty() ? Map.of()
                : buildIoeCNameMap(ioeCCdvas);

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
                    response.setApfSts(capplm.getApfStsC() == null ? null
                            : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getApfStsC()).label());
                    List<Cdecim> decisions = decisionMap.getOrDefault(cappla.getApfMngNo(), List.of());
                    response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                }
            }

            if (response.getBiceDpmC() != null) response.setBiceDpmNm(orgNameMap.get(response.getBiceDpmC()));
            if (response.getBiceTemC() != null) response.setBiceTemNm(orgNameMap.get(response.getBiceTemC()));
            if (response.getCgprEno() != null) response.setCgprNm(userNameMap.get(response.getCgprEno()));
            if (response.getAbusC() != null) response.setAbusCNm(abusCNameMap.get(response.getAbusC()));
            if (response.getDfrCleC() != null) response.setDfrCleCNm(dfrCleCNameMap.get(response.getDfrCleC()));
            if (response.getItMngcTp() != null) response.setItMngcTpNm(itMngcTpNameMap.get(response.getItMngcTp()));
            if (response.getPulDtt() != null) response.setPulDttNm(pulDttNameMap.get(response.getPulDtt()));
            if (response.getIoeC() != null) response.setIoeCNm(ioeCNameMap.get(response.getIoeC()));

            setBudgetCategory(response);

            if ("002".equals(cost.getItMngcTp())) {
                attachTerminals(response);
            }
        }

        // --- 7. 전년도 예산(prevBgAmt) 배치 조회 (계속 항목만) ---
        List<String> continuingNos = responses.stream()
                .filter(r -> "002".equals(r.getPulDtt()))
                .map(CostDto.Response::getItMngcNo)
                .distinct()
                .collect(Collectors.toList());
        if (!continuingNos.isEmpty()) {
            String bgYy = responses.stream()
                    .map(CostDto.Response::getBgYy)
                    .filter(y -> y != null && !y.isBlank())
                    .findFirst().orElse(null);
            if (bgYy != null) {
                String prevYear = String.valueOf(Integer.parseInt(bgYy) - 1);
                Map<String, BigDecimal> prevBgMap = costRepository.sumPrevBgByItMngcNos(continuingNos, prevYear);
                responses.forEach(r -> {
                    if ("002".equals(r.getPulDtt())) {
                        r.setPrevBgAmt(prevBgMap.getOrDefault(r.getItMngcNo(), BigDecimal.ZERO));
                    } else {
                        r.setPrevBgAmt(BigDecimal.ZERO);
                    }
                });
            }
        }

        // --- 8. 전년도 BBUGTM 편성예산(prevDupBg) 배치 조회 (cncdItMngcNo 기준) ---
        List<String> cncdNos = responses.stream()
                .filter(r -> r.getCncdItMngcNo() != null && !r.getCncdItMngcNo().isBlank())
                .map(CostDto.Response::getCncdItMngcNo)
                .distinct()
                .collect(Collectors.toList());
        if (!cncdNos.isEmpty()) {
            String bgYy8 = responses.stream()
                    .map(CostDto.Response::getBgYy)
                    .filter(y -> y != null && !y.isBlank())
                    .findFirst().orElse(null);
            if (bgYy8 != null) {
                String prevYear8 = String.valueOf(Integer.parseInt(bgYy8) - 1);
                Map<String, BigDecimal> prevDupBgMap = bbugtmRepository.sumDupBgByItMngcNos(cncdNos, prevYear8);
                responses.forEach(r -> {
                    if (r.getCncdItMngcNo() != null && !r.getCncdItMngcNo().isBlank()) {
                        r.setPrevDupBg(prevDupBgMap.getOrDefault(r.getCncdItMngcNo(), BigDecimal.ZERO));
                    } else {
                        r.setPrevDupBg(BigDecimal.ZERO);
                    }
                });
            } else {
                responses.forEach(r -> r.setPrevDupBg(BigDecimal.ZERO));
            }
        } else {
            responses.forEach(r -> r.setPrevDupBg(BigDecimal.ZERO));
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

    /** 부서코드→부서명, 사원번호→사용자명, 사업코드→사업코드명 조회 및 설정 */
    private void setCodeNames(CostDto.Response response) {
        if (response.getBiceDpmC() != null && !response.getBiceDpmC().isEmpty()) {
            corgnIRepository.findById(response.getBiceDpmC())
                    .ifPresent(org -> response.setBiceDpmNm(org.getBbrNm()));
        }
        if (response.getBiceTemC() != null && !response.getBiceTemC().isEmpty()) {
            corgnIRepository.findById(response.getBiceTemC())
                    .ifPresent(org -> response.setBiceTemNm(org.getBbrNm()));
        }
        if (response.getCgprEno() != null && !response.getCgprEno().isEmpty()) {
            cuserIRepository.findById(response.getCgprEno())
                    .ifPresent(user -> response.setCgprNm(user.getUsrNm()));
        }
        if (response.getAbusC() != null && !response.getAbusC().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate("ABUS_C", response.getAbusC(), null)
                    .ifPresent(code -> response.setAbusCNm(code.getCNm()));
        }
        if (response.getDfrCleC() != null && !response.getDfrCleC().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate("DFR_CLE", response.getDfrCleC(), null)
                    .ifPresent(code -> response.setDfrCleCNm(code.getCNm()));
        }
        if (response.getItMngcTp() != null && !response.getItMngcTp().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate("IT_MNGC_TP", response.getItMngcTp(), null)
                    .ifPresent(code -> response.setItMngcTpNm(code.getCNm()));
        }
        if (response.getPulDtt() != null && !response.getPulDtt().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate("PUL_DTT", response.getPulDtt(), null)
                    .ifPresent(code -> response.setPulDttNm(code.getCNm()));
        }
        if (response.getIoeC() != null && !response.getIoeC().isEmpty()) {
            String ioeCNm = buildIoeCNameMap(Set.of(response.getIoeC())).get(response.getIoeC());
            response.setIoeCNm(ioeCNm);
        }
    }

    /** 단말기 DTO 목록에 담당자명(cgprNm) 일괄 설정 (배치 조회로 N+1 방지) */
    private void setTerminalCodeNames(List<CostDto.TerminalDto> terminalDtos) {
        Set<String> enos = terminalDtos.stream()
                .map(CostDto.TerminalDto::getCgprEno)
                .filter(cgprEno -> cgprEno != null && !cgprEno.isEmpty())
                .collect(Collectors.toSet());
        if (enos.isEmpty()) return;

        Map<String, String> nameMap = cuserIRepository.findByEnoIn(enos).stream()
                .collect(Collectors.toMap(
                        CuserI::getEno,
                        CuserI::getUsrNm));
        terminalDtos.forEach(tDto -> {
            if (tDto.getCgprEno() != null) {
                tDto.setCgprNm(nameMap.get(tDto.getCgprEno()));
            }
        });
    }

    /** C_ID 기준 cdva→C_NM 맵 생성 (지정 cdva만 필터링) */
    private Map<String, String> buildCodeNameMap(String cId, Set<String> cdvas) {
        return ccodemRepository.findByCIdWithValidDate(cId, null).stream()
                .filter(c -> cdvas.contains(c.getCdva()))
                .collect(Collectors.toMap(Ccodem::getCdva, Ccodem::getCNm, (a, b) -> a));
    }

    /** IOE 코드 cdva → CDVA_NM 우선 표시명 맵 생성 */
    private Map<String, String> buildIoeCNameMap(Set<String> cdvas) {
        return ccodemRepository.findByCIdWithValidDate("IOE", null).stream()
                .filter(c -> cdvas.contains(c.getCdva()))
                .collect(Collectors.toMap(
                        Ccodem::getCdva,
                        c -> {
                            String dtl = c.getCdvaNm() != null ? c.getCdvaNm()
                                    : (c.getCdvaDtl() != null ? c.getCdvaDtl() : (c.getCNm() != null ? c.getCNm() : c.getCdva()));
                            String[] parts = dtl.split(" - ");
                            return parts[parts.length - 1].trim();
                        },
                        (a, b) -> a));
    }

    /** 단말기관리번호 자동 생성 (형식: TER-{yyyy}-{seq:04d}) */
    private String generateTmnMngNo() {
        Long seq = btermmRepository.getNextSequenceValue();
        String year = String.valueOf(LocalDate.now().getYear());
        return String.format("TER-%s-%04d", year, seq);
    }

    /**
     * RBAC 수정/삭제 권한 검증 헬퍼 ({@link com.kdb.it.domain.budget.project.service.ProjectService}와 동일 규칙)
     *
     * <p>SecurityContext에서 현재 인증된 사용자를 조회하고 자격등급 기반 3단계 권한을 검증합니다.</p>
     *
     * <ol>
     *   <li>시스템관리자(ITPAD001): 모든 리소스 수정 허용</li>
     *   <li>기획통할담당자(ITPZZ002): 소속 부서(bbrC) == 리소스 부서(resourceBbrC)인 경우 허용</li>
     *   <li>일반사용자(ITPZZ001): 본인 작성 리소스(creatorEno == 요청자 eno)만 허용</li>
     * </ol>
     *
     * @param creatorEno   리소스 최초 작성자 사번 (FST_ENR_USID)
     * @param resourceBbrC 리소스 소속 부서코드 (부서 단위 권한 범위 결정용)
     * @throws org.springframework.security.access.AccessDeniedException 수정 권한이 없는 경우
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
