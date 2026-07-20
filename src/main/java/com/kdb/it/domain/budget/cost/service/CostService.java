package com.kdb.it.domain.budget.cost.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

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
 * <li>{@code BG_SNO} (예산일련번호): 동일 관리번호 내의 이력 순번</li>
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

    private static final Logger log = LoggerFactory.getLogger(CostService.class);

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
    /** 조직코드→조직명 해석기: 주관부서명/주관팀명 스냅샷 저장용 */
    private final com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;
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

    /** 공통코드 cId→cdva→코드명 맵 생성 공통 헬퍼 (Cost/Project 서비스 공용) */
    private final CodeNameMapBuilder codeNameMapBuilder;

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
     * 동일 관리번호에 여러 이력(SNO)이 있으면 {@link CostRepresentativeSelector#pick(List)}이
     * 최신·활성 조건(LST_YN='Y' 우선, BG_SNO 내림차순)으로 대표 행을 결정적으로 선택합니다.
     * </p>
     *
     * @param itMngcNo 조회할 전산관리비관리번호
     * @return 전산관리비 응답 DTO
     * @throws IllegalArgumentException 해당 관리번호의 항목이 없는 경우
     */
    public CostDto.Response getCost(String itMngcNo) {
        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }
        Bcostm primary = CostRepresentativeSelector.pick(costs);
        CostDto.Response response = CostDto.Response.fromEntity(primary);
        enrichResponse(response, primary);
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
                .toList();
        enrichCostListBatch(costs, responses);
        return responses;
    }

    /**
     * 검색 조건으로 전산관리비 목록 조회
     *
     * <p>
     * {@link CostDto.SearchCondition}의 조건이 모두 비어있으면 전체 조회({@link #getCostList()})와
     * 동일합니다.
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
                .toList();
        enrichCostListBatch(costs, responses);
        return responses;
    }

    /**
     * 신규 전산관리비 생성
     *
     * <p>
     * 전산관리비관리번호({@code IT_MNGC_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     * 일련번호({@code BG_SNO})는 기존 데이터 기준 MAX+1로 설정합니다.
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
     * <li>기존 데이터가 있으면 MAX(BG_SNO) + 1</li>
     * </ul>
     *
     * @param request 전산관리비 생성 요청 DTO (관리번호, 비목명, 계약 정보 등)
     * @return 생성된 전산관리비관리번호
     */
    @Transactional
    public String createCost(CostDto.CreateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        String costBgNo = request.getCostBgNo();

        if (costBgNo == null || costBgNo.isEmpty()) {
            Long seq = costRepository.getNextSequenceValue();
            String year = String.valueOf(LocalDate.now().getYear());
            costBgNo = String.format("COST-%s-%04d", year, seq);
            request.setCostBgNo(costBgNo);
        }

        Integer nextSno = costRepository.getNextSnoValue(costBgNo);
        if (nextSno == null) {
            nextSno = 1;
        }

        // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));

        // 외화 재계산: 클라 costTotXpAmt를 fcAmt × xcr로 덮어씀 (CONTEXT.md 결정 C)
        BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                request.getFcAmt(), request.getCostTotXpAmt(), request.getCurC(), request.getXcr());
        request.setCostTotXpAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);

        Bcostm bcostm = request.toEntity(nextSno);
        // 상위조직명(PRLM_HRK_OGZ_C_CONE)/주관팀명(SVN_TEM_NM)은 담당자(CGPR_ID) 소속 CUSERI 스냅샷으로 저장
        // (프로젝트 저장 로직과 동일하게 담당자 소속 상위조직명을 저장. 팀코드/상위조직은 CORGNI로 명칭을 못 얻음)
        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(bcostm.getCgprId());
        bcostm.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        // 주관부서명은 CORGNI 조회 스냅샷, 주관팀명은 담당자(CUSERI) 팀명 스냅샷으로 저장
        bcostm.assignSvnOrgNames(
                orgNameResolver.resolveName(bcostm.getCostSvnDpmC()),
                orgSnapshot.svnTemNm());
        costRepository.save(bcostm);

        // 단말기 팀/부서 코드 정정: 담당자(CGPR_ID) 소속 CUSERI 스냅샷으로 SVN_TEM_C=팀코드, SVN_DPM_C=부서코드 보장
        applyTerminalOrgCodes(request.getTerminals());

        if (request.getTerminals() != null && !request.getTerminals().isEmpty()) {
            for (CostDto.TerminalDto tDto : request.getTerminals()) {
                if (tDto.getTmnMngNo() == null || tDto.getTmnMngNo().isEmpty()) {
                    tDto.setTmnMngNo(generateTmnMngNo());
                }
                if (tDto.getSno() == null) {
                    tDto.setSno(1);
                }

                // XCR 표준 조회 (단말기): Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                tDto.setXcr(xcrLookupService.resolveXcr(tDto.getCurC(), LocalDate.now()));

                // 단말기 외화 재계산 (CONTEXT.md 결정 C)
                BigDecimal[] tReconciled = BudgetAmountCalculator.reconcileAmount(
                        tDto.getFcAmt(), tDto.getTermRqmBgAmt(), tDto.getCurC(), tDto.getXcr());
                tDto.setTermRqmBgAmt(tReconciled[0]);
                tDto.setFcAmt(tReconciled[1]);

                Btermm btermm = tDto.toEntity();
                btermm.setBcostmInfo(bcostm.getCostBgNo(), bcostm.getBgSno());
                btermmRepository.save(btermm);
            }
        }

        return bcostm.getCostBgNo();
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

        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }

        Bcostm target = CostRepresentativeSelector.pick(costs);

        OwnershipVerifier.verifyModifiable(target.getFstEnrUsid(), target.getCostSvnDpmC());

        // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));

        // 외화 재계산: 클라 costTotXpAmt를 fcAmt × xcr로 덮어씀 (CONTEXT.md 결정 C)
        BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                request.getFcAmt(), request.getCostTotXpAmt(), request.getCurC(), request.getXcr());
        request.setCostTotXpAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);

        target.update(
                request.getIoeC(), request.getCttNm(), request.getCttOppNm(),
                request.getCostTotXpAmt(), request.getDfrCleC(), DateFormatUtil.toYmd8(request.getFstDfrDt()),
                request.getCurC(), request.getXcr(), DateFormatUtil.toYmd8(request.getXcrBseDt()),
                request.getSectSysUtzYn(), request.getIndRsn(), request.getCgprId(),
                request.getCostSvnDpmC(), request.getSvnTemC(), request.getBgUntAbusC(),
                request.getTmnYn(), request.getAbusTc(), request.getBseYy(), request.getCncdRfrNo(),
                request.getFcAmt());

        // 수정으로 담당부서/담당자가 바뀔 수 있으므로 이름 스냅샷도 같은 시점 기준으로 갱신
        // 상위조직명(PRLM_HRK_OGZ_C_CONE)/주관팀명은 담당자(CUSERI), 주관부서명은 CORGNI 조회 스냅샷
        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(target.getCgprId());
        target.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        target.assignSvnOrgNames(
                orgNameResolver.resolveName(target.getCostSvnDpmC()),
                orgSnapshot.svnTemNm());

        /*
         * 연관된 단말기 목록 병합(제자리 수정):
         *  - 기존 활성(DEL_YN='N') 레코드를 PK(TMN_MNG_NO+SNO) 기준으로 매칭해 Dirty Checking으로 수정
         *  - 요청에만 있는(PK 없는) 행은 채번 후 신규 저장
         *  - 요청에서 빠진 기존 활성 행만 Soft Delete
         * (기존 delete+재등록 방식은 매 저장마다 새 PK 레코드를 생성해 이력이 무한 증식하므로 폐기)
         */
        List<Btermm> existingTerminals = btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(
                target.getCostBgNo(), target.getBgSno(), "N");
        Map<String, Btermm> existingByPk = existingTerminals.stream()
                .collect(Collectors.toMap(t -> terminalPk(t.getTmnMngNo(), t.getSno()), t -> t, (a, b) -> a));

        List<CostDto.TerminalDto> requestTerminals = request.getTerminals() != null
                ? request.getTerminals()
                : List.of();
        // 단말기 팀/부서 코드 정정: 담당자(CGPR_ID) 소속 CUSERI 스냅샷으로 SVN_TEM_C=팀코드, SVN_DPM_C=부서코드 보장
        applyTerminalOrgCodes(requestTerminals);

        Set<String> keptPks = new java.util.HashSet<>();
        for (CostDto.TerminalDto tDto : requestTerminals) {
            // XCR 표준 조회 (단말기): Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
            tDto.setXcr(xcrLookupService.resolveXcr(tDto.getCurC(), LocalDate.now()));

            // 단말기 외화 재계산 (CONTEXT.md 결정 C)
            BigDecimal[] tReconciled = BudgetAmountCalculator.reconcileAmount(
                    tDto.getFcAmt(), tDto.getTermRqmBgAmt(), tDto.getCurC(), tDto.getXcr());
            tDto.setTermRqmBgAmt(tReconciled[0]);
            tDto.setFcAmt(tReconciled[1]);

            Btermm existing = (tDto.getTmnMngNo() != null && tDto.getSno() != null)
                    ? existingByPk.get(terminalPk(tDto.getTmnMngNo(), tDto.getSno()))
                    : null;

            if (existing != null) {
                /* 기존 레코드 제자리 수정 (Dirty Checking으로 트랜잭션 종료 시 UPDATE) */
                existing.update(tDto.getSpfTmnNm(), tDto.getTmnKdTc(), tDto.getNsfUsgCone(), tDto.getTmnClsfC(),
                        tDto.getTermRqmBgAmt(), tDto.getCurC(), tDto.getXcr(),
                        DateFormatUtil.toYmd8(tDto.getXcrBseDt()), tDto.getDfrCleC(), tDto.getIndRsn(),
                        tDto.getCgprId(), tDto.getTermSvnTemC(), tDto.getTermSvnDpmC(), tDto.getRmk(), tDto.getFcAmt());
                keptPks.add(terminalPk(existing.getTmnMngNo(), existing.getSno()));
            } else {
                /* 신규 행: 관리번호 채번 후 저장 */
                if (tDto.getTmnMngNo() == null || tDto.getTmnMngNo().isEmpty()) {
                    tDto.setTmnMngNo(generateTmnMngNo());
                }
                if (tDto.getSno() == null) {
                    tDto.setSno(1);
                }
                Btermm btermm = tDto.toEntity();
                btermm.setBcostmInfo(target.getCostBgNo(), target.getBgSno());
                btermmRepository.save(btermm);
                keptPks.add(terminalPk(tDto.getTmnMngNo(), tDto.getSno()));
            }
        }

        /* 요청에서 빠진 기존 활성 단말기만 Soft Delete */
        for (Btermm et : existingTerminals) {
            if (!keptPks.contains(terminalPk(et.getTmnMngNo(), et.getSno()))) {
                et.delete();
            }
        }

        return target.getCostBgNo();
    }

    /**
     * 담당자 사번(cgprId)으로 소속 조직 이름 스냅샷(주관팀명 + 상위조직명)을 조회한다.
     *
     * <p>주관팀명(SVN_TEM_NM)/상위조직명(PRLM_HRK_OGZ_C_CONE) 스냅샷용. 팀코드(SVN_TEM_C)는 조직마스터(CORGNI)에
     * 등재되지 않고 상위조직도 코드/명 매핑이 달라 CORGNI 조회로는 명칭을 얻기 어렵다. 따라서 담당자(CUSERI) 레코드의
     * 팀명·상위조직명을 그대로 저장 시점 스냅샷으로 사용한다(프로젝트 저장 로직과 동일). 담당팀·담당자는 화면에서
     * 동일 직원 기준으로 함께 설정되므로 담당자 기준 값이 화면 선택과 일치한다.
     * 담당자 미지정(사번 null/공백)이거나 CUSERI 미조회 시 두 값 모두 {@code null}이다(대상 컬럼 nullable).</p>
     *
     * @param cgprId 담당자 사번 (null/공백 허용)
     * @return 소속 조직 이름 스냅샷. cgprId가 비었거나 사용자 미조회 시 {@link CostOrgSnapshot#EMPTY}
     */
    private CostOrgSnapshot resolveAuthorOrgNames(String cgprId) {
        if (cgprId == null || cgprId.isBlank()) {
            return CostOrgSnapshot.EMPTY;
        }
        return cuserIRepository.findByEno(cgprId)
                .map(user -> new CostOrgSnapshot(user.getTemNm(), user.getPrlmHrkOgzCNm()))
                .orElse(CostOrgSnapshot.EMPTY);
    }

    /**
     * 담당자 소속 조직 이름 스냅샷(주관팀명 + 상위조직명).
     *
     * @param svnTemNm      주관팀명 (CUSERI.TEM_NM, 미조회 시 null)
     * @param prlmHrkOgzCNm 상위조직명 (CUSERI 상위조직 부점명, 미조회 시 null)
     */
    private record CostOrgSnapshot(String svnTemNm, String prlmHrkOgzCNm) {
        /** 담당자 미지정·미조회 시 사용할 빈 스냅샷(두 값 모두 null). */
        private static final CostOrgSnapshot EMPTY = new CostOrgSnapshot(null, null);
    }

    /**
     * 단말기 목록의 팀/부서 코드를 담당자(CGPR_ID) 소속 CUSERI 스냅샷으로 정정한다.
     *
     * <p>물리 컬럼 {@code SVN_TEM_C}(주관팀코드)에는 담당자의 팀코드({@code CUSERI.TEM_C}),
     * {@code SVN_DPM_C}(주관부서코드)에는 담당자의 부서코드({@code CUSERI.BBR_C})를 채워,
     * 프론트가 넘긴 값이나 과거 잘못 저장된 값(부서코드가 팀코드 컬럼에 유입되던 문제)을
     * 저장 시점에 교정한다. 단말기 화면에서 팀/부서는 담당자 선택과 항상 함께 설정되므로
     * 담당자 기준 값이 화면 의도와 일치한다.</p>
     *
     * <p>담당자 사번(cgprId)이 비었거나 CUSERI 미조회인 행은 DTO 값을 그대로 둔다(대상 컬럼 nullable).
     * 담당자 사번 배치 조회(findByEnoIn)로 N+1을 방지한다.</p>
     *
     * @param terminals 정정 대상 단말기 DTO 목록 (null/빈 목록이면 no-op)
     */
    private void applyTerminalOrgCodes(List<CostDto.TerminalDto> terminals) {
        if (terminals == null || terminals.isEmpty()) {
            return;
        }
        Set<String> enos = terminals.stream()
                .map(CostDto.TerminalDto::getCgprId)
                .filter(eno -> eno != null && !eno.isBlank())
                .collect(Collectors.toSet());
        if (enos.isEmpty()) {
            return;
        }
        Map<String, CuserI> userByEno = cuserIRepository.findByEnoIn(enos).stream()
                .collect(Collectors.toMap(CuserI::getEno, user -> user, (a, b) -> a));
        for (CostDto.TerminalDto tDto : terminals) {
            CuserI user = tDto.getCgprId() == null ? null : userByEno.get(tDto.getCgprId());
            if (user != null) {
                tDto.setTermSvnTemC(user.getTemC());
                tDto.setTermSvnDpmC(user.getBbrC());
            }
        }
    }

    /** 단말기 PK(단말관리번호+일련번호) 매칭용 합성 키. */
    private static String terminalPk(String tmnMngNo, Integer sno) {
        return tmnMngNo + "_" + sno;
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

        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }

        Bcostm primary = CostRepresentativeSelector.pick(costs);
        OwnershipVerifier.verifyModifiable(primary.getFstEnrUsid(), primary.getCostSvnDpmC());

        // 단말기 일괄 조회 (N+1 제거): 미삭제 단말기를 IN 조회로 1회만 적재.
        // DEL_YN='N'만 대상으로 한다 — 이미 삭제(DEL_YN='Y')된 단말기는 재삭제가 불필요하므로 의도적으로 제외(멱등).
        List<String> costNos = costs.stream().map(value -> value.getCostBgNo()).distinct().toList();
        Map<String, List<Btermm>> terminalsByKey = btermmRepository
                .findByTermBgNoInAndDelYn(costNos, "N").stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTermBgNo() + "_" + t.getTermBgSno()));
        for (Bcostm cost : costs) {
            cost.delete();
            terminalsByKey.getOrDefault(cost.getCostBgNo() + "_" + cost.getBgSno(), List.of())
                    .forEach(value -> value.delete());
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
     * @return 조회 성공 항목과 미존재(failedIds)를 함께 담은 부분 성공 결과
     */
    public CostDto.BulkResponse getCostsByIds(CostDto.BulkGetRequest request) {
        List<CostDto.Response> responses = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (String costBgNo : request.getCostBgNos()) {
            try {
                responses.add(getCost(costBgNo));
            } catch (IllegalArgumentException e) {
                failedIds.add(costBgNo);
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("bulk-get 누락: type=cost, failedIds={}", failedIds);
        }

        // TPRMPP_BBUGTM 기준 편성예산(DUP_BG) 일괄 조회 후 각 응답에 설정
        String bseYy = request.getBseYy();
        if (bseYy != null && !bseYy.isBlank() && !responses.isEmpty()) {
            List<String> costBgNos = responses.stream()
                    .map(value -> value.getCostBgNo())
                    .toList();
            Map<String, BigDecimal> dupBgMap = bbugtmRepository.sumDupBgByItMngcNos(costBgNos, bseYy);
            // 전산업무비는 ioeC가 IOE_CPIT이면 자본예산, 나머지면 일반관리비 단일 분류
            responses.forEach(r -> {
                BigDecimal dupBgAmt = dupBgMap.getOrDefault(r.getCostBgNo(), BigDecimal.ZERO);
                r.setDupBgAmt(dupBgAmt);
                boolean isAsset = r.getAssetBg() != null && r.getAssetBg().compareTo(BigDecimal.ZERO) > 0;
                r.setAssetDupBg(isAsset ? dupBgAmt : BigDecimal.ZERO);
                r.setCostDupBg(isAsset ? BigDecimal.ZERO : dupBgAmt);
            });
        }
        return new CostDto.BulkResponse(responses, failedIds);
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
    private void setApplicationInfo(CostDto.Response response, String costBgNo, Integer bgSno) {
        List<Cappla> capplas = capplaRepository
                .findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc("BCOSTM", costBgNo, bgSno);

        if (!capplas.isEmpty()) {
            Cappla cappla = capplas.get(0);
            response.setApfMngNo(cappla.getApfDcmNo());

            capplmRepository.findById(cappla.getApfDcmNo())
                    .ifPresent(capplm -> {
                        response.setApfSts(capplm.getItPtlApfPrgStsC() == null ? null
                                : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getItPtlApfPrgStsC())
                                        .label());
                        List<Cdecim> decisions = cdecimRepository
                                .findByDcdMngNoOrderByDcrSqnSnoAsc(cappla.getApfDcmNo());
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
     * <li>자본예산: cttTp가 IOE_DVC/IOE_HW/IOE_SW인 경우 → assetBg = itMngcBgAmt, costBg =
     * 0</li>
     * <li>일반관리비: cttTp가 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE인 경우 → assetBg = 0,
     * costBg = itMngcBgAmt</li>
     * </ul>
     *
     * @param response 예산 구분을 설정할 응답 DTO
     */
    private void setBudgetCategory(CostDto.Response response) {
        BigDecimal totalBg = response.getCostTotXpAmt() != null ? response.getCostTotXpAmt() : BigDecimal.ZERO;
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

        Optional<Ccodem> codeOpt = ccodemRepository.findByCIdWithValidDate(CommonCodeGroups.IOE, null)
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
                        if ("단말기".equals(cdvaDes))
                            response.setDvcBg(totalBg);
                        else if ("기계장치".equals(cdvaDes))
                            response.setHwBg(totalBg);
                        else if ("기타무형자산".equals(cdvaDes))
                            response.setSwBg(totalBg);
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
     * <p>
     * CAPPLA 1회, CAPPLM 1회, CDECIM 1회, CORGNI 1회, CUSERI 1회 — 총 5 쿼리로 처리합니다.
     * </p>
     */
    private void enrichCostListBatch(List<Bcostm> costs, List<CostDto.Response> responses) {
        if (costs.isEmpty())
            return;

        // --- 1. CAPPLA 배치 조회 ---
        List<String> costBgNos = costs.stream().map(value -> value.getCostBgNo()).distinct().toList();
        List<Cappla> allCapplas = capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc("BCOSTM", costBgNos);

        // costBgNo+sno 복합키 → 최신 Cappla
        Map<String, Cappla> latestCappla = new java.util.LinkedHashMap<>();
        for (Cappla c : allCapplas) {
            String key = c.getPkColNm() + "_" + c.getFntTbCrySno();
            latestCappla.putIfAbsent(key, c);
        }

        // --- 2. CAPPLM 배치 조회 ---
        List<String> apfMngNos = latestCappla.values().stream()
                .map(value -> value.getApfDcmNo()).toList();
        Map<String, Capplm> capplmMap = capplmRepository.findAllById(apfMngNos).stream()
                .collect(Collectors.toMap(value -> value.getApfMngNo(), m -> m));

        // --- 3. CDECIM 배치 조회 ---
        List<Cdecim> allDecisions = cdecimRepository.findByDcdMngNoInOrderByDcrSqnSnoAsc(apfMngNos);
        Map<String, List<Cdecim>> decisionMap = allDecisions.stream()
                .collect(Collectors.groupingBy(value -> value.getDcdMngNo()));

        // --- 4. 부서코드·사원번호·공통코드 CDVA 수집 ---
        Set<String> orgCodes = new java.util.HashSet<>();
        Set<String> userEnos = new java.util.HashSet<>();
        Set<String> bgUntAbusCdvas = new java.util.HashSet<>();
        Set<String> dfrCleCCdvas = new java.util.HashSet<>();
        // 단말여부(Y/N) → 구 IT_MNGC_TP 코드(002/001) 매핑값. 표시명(tmnYnNm) 조회용.
        Set<String> tmnYnMngcCodes = new java.util.HashSet<>();
        Set<String> abusTcCdvas = new java.util.HashSet<>();
        Set<String> ioeCCdvas = new java.util.HashSet<>();
        for (CostDto.Response r : responses) {
            if (r.getCostSvnDpmC() != null && !r.getCostSvnDpmC().isEmpty())
                orgCodes.add(r.getCostSvnDpmC());
            if (r.getSvnTemC() != null && !r.getSvnTemC().isEmpty())
                orgCodes.add(r.getSvnTemC());
            if (r.getCgprId() != null && !r.getCgprId().isEmpty())
                userEnos.add(r.getCgprId());
            if (r.getBgUntAbusC() != null && !r.getBgUntAbusC().isEmpty())
                bgUntAbusCdvas.add(r.getBgUntAbusC());
            if (r.getDfrCleC() != null && !r.getDfrCleC().isEmpty())
                dfrCleCCdvas.add(r.getDfrCleC());
            if ("Y".equals(r.getTmnYn()))
                tmnYnMngcCodes.add("1");
            else if ("N".equals(r.getTmnYn()))
                tmnYnMngcCodes.add("0");
            if (r.getAbusTc() != null && !r.getAbusTc().isEmpty())
                abusTcCdvas.add(r.getAbusTc());
            if (r.getIoeC() != null && !r.getIoeC().isEmpty())
                ioeCCdvas.add(r.getIoeC());
        }

        // --- 5. 배치 조회 ---
        Map<String, String> orgNameMap = corgnIRepository.findAllById(orgCodes).stream()
                .collect(Collectors.toMap(value -> value.getPrlmOgzCCone(), value -> value.getBbrNm()));
        Map<String, String> userNameMap = cuserIRepository.findAllById(userEnos).stream()
                .collect(Collectors.toMap(value -> value.getEno(), value -> value.getUsrNm()));
        Map<String, String> bgUntAbusCNameMap = bgUntAbusCdvas.isEmpty() ? Map.of()
                : codeNameMapBuilder.build(CommonCodeGroups.ABUS_UNIT, bgUntAbusCdvas);
        Map<String, String> dfrCleCNameMap = dfrCleCCdvas.isEmpty() ? Map.of()
                : codeNameMapBuilder.build(CommonCodeGroups.DFR_CLE, dfrCleCCdvas);
        Map<String, String> tmnYnNameMap = tmnYnMngcCodes.isEmpty() ? Map.of()
                : codeNameMapBuilder.build(CommonCodeGroups.TMN_YN, tmnYnMngcCodes);
        Map<String, String> abusTcNameMap = abusTcCdvas.isEmpty() ? Map.of()
                : codeNameMapBuilder.build(CommonCodeGroups.ABUS, abusTcCdvas);
        Map<String, String> ioeCNameMap = ioeCCdvas.isEmpty() ? Map.of()
                : buildIoeCNameMap(ioeCCdvas);

        // --- 5.5 단말기 일괄 조회 (N+1 제거): tmnYn='Y' 행만 대상 ---
        List<String> terminalCostNos = costs.stream()
                .filter(c -> "Y".equals(c.getTmnYn()))
                .map(value -> value.getCostBgNo())
                .distinct()
                .toList();
        Map<String, List<Btermm>> terminalsByKey = terminalCostNos.isEmpty() ? Map.of()
                : btermmRepository.findByTermBgNoInAndDelYn(terminalCostNos, "N").stream()
                        .collect(Collectors.groupingBy(
                                t -> t.getTermBgNo() + "_" + t.getTermBgSno()));

        // --- 6. 응답 DTO에 일괄 주입 ---
        for (int i = 0; i < costs.size(); i++) {
            Bcostm cost = costs.get(i);
            CostDto.Response response = responses.get(i);

            String key = cost.getCostBgNo() + "_" + cost.getBgSno();
            Cappla cappla = latestCappla.get(key);
            if (cappla != null) {
                response.setApfMngNo(cappla.getApfDcmNo());
                Capplm capplm = capplmMap.get(cappla.getApfDcmNo());
                if (capplm != null) {
                    response.setApfSts(capplm.getItPtlApfPrgStsC() == null ? null
                            : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getItPtlApfPrgStsC()).label());
                    List<Cdecim> decisions = decisionMap.getOrDefault(cappla.getApfDcmNo(), List.of());
                    response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                }
            }

            if (cost.getSvnDpmNm() != null)
                response.setCostSvnDpmNm(cost.getSvnDpmNm()); // 저장 스냅샷 우선
            else if (response.getCostSvnDpmC() != null)
                response.setCostSvnDpmNm(orgNameMap.get(response.getCostSvnDpmC())); // 구데이터 폴백
            if (cost.getSvnTemNm() != null)
                response.setSvnTemNm(cost.getSvnTemNm());
            else if (response.getSvnTemC() != null)
                response.setSvnTemNm(orgNameMap.get(response.getSvnTemC()));
            if (response.getCgprId() != null)
                response.setCgprNm(userNameMap.get(response.getCgprId()));
            if (response.getBgUntAbusC() != null)
                response.setBgUntAbusCNm(bgUntAbusCNameMap.get(response.getBgUntAbusC()));
            if (response.getDfrCleC() != null)
                response.setDfrCleCNm(dfrCleCNameMap.get(response.getDfrCleC()));
            if ("Y".equals(response.getTmnYn()))
                response.setTmnYnNm(tmnYnNameMap.get("1"));
            else if ("N".equals(response.getTmnYn()))
                response.setTmnYnNm(tmnYnNameMap.get("0"));
            if (response.getAbusTc() != null)
                response.setAbusTcNm(abusTcNameMap.get(response.getAbusTc()));
            if (response.getIoeC() != null)
                response.setIoeCNm(ioeCNameMap.get(response.getIoeC()));

            setBudgetCategory(response);

            if ("Y".equals(cost.getTmnYn())) {
                List<Btermm> terminals = terminalsByKey.getOrDefault(
                        cost.getCostBgNo() + "_" + cost.getBgSno(), List.of());
                List<CostDto.TerminalDto> dtos = terminals.stream()
                        .map(CostDto.TerminalDto::fromEntity).toList();
                setTerminalCodeNames(dtos);
                response.setTerminals(dtos);
            }
        }

        // --- 7. 전년도 예산(prevBgAmt)·전년도 편성예산(prevDupBg) 배치 조회 ---
        // 목록에는 여러 예산연도가 섞일 수 있으므로(전체 조회) 행별 bseYy 기준으로
        // 연도 그룹을 나눠 각 그룹의 전년도(bseYy-1)로 조회한다.
        // (과거: 첫 행의 bseYy 하나로 전년도를 일괄 계산 → 혼합 연도 목록에서 전 행이 0이 되는 버그)
        responses.forEach(r -> {
            r.setPrevBgAmt(BigDecimal.ZERO);
            r.setPrevDupBg(BigDecimal.ZERO);
        });
        Map<String, List<CostDto.Response>> responsesByYear = responses.stream()
                .filter(r -> r.getBseYy() != null && r.getBseYy().matches("\\d{4}"))
                .collect(Collectors.groupingBy(value -> value.getBseYy()));
        for (Map.Entry<String, List<CostDto.Response>> entry : responsesByYear.entrySet()) {
            String prevYear = String.valueOf(Integer.parseInt(entry.getKey()) - 1);
            List<CostDto.Response> yearGroup = entry.getValue();

            // 전년도 예산(BCOSTM AMT 합계): 계속(abusTc='20') 항목만.
            // 전년도 항목은 cncdRfrNo(관련전산업무비번호)로 연결되므로 cncdRfrNo 우선,
            // 미연결(동일 관리번호 연차 데이터)은 costBgNo로 폴백 조회한다.
            List<String> prevAmtKeys = yearGroup.stream()
                    .filter(r -> "20".equals(r.getAbusTc()))
                    .map(CostService::prevBudgetLookupKey)
                    .filter(k -> k != null && !k.isBlank())
                    .distinct()
                    .toList();
            if (!prevAmtKeys.isEmpty()) {
                Map<String, BigDecimal> prevBgMap = costRepository.sumPrevBgByCostBgNos(prevAmtKeys, prevYear);
                yearGroup.stream()
                        .filter(r -> "20".equals(r.getAbusTc()))
                        .forEach(r -> r.setPrevBgAmt(
                                prevBgMap.getOrDefault(prevBudgetLookupKey(r), BigDecimal.ZERO)));
            }

            // 전년도 BBUGTM 편성예산(DUP_BG 합계): cncdRfrNo 연결 항목만
            List<String> cncdNos = yearGroup.stream()
                    .map(value -> value.getCncdRfrNo())
                    .filter(v -> v != null && !v.isBlank())
                    .distinct()
                    .toList();
            if (!cncdNos.isEmpty()) {
                Map<String, BigDecimal> prevDupBgMap = bbugtmRepository.sumDupBgByItMngcNos(cncdNos, prevYear);
                yearGroup.forEach(r -> {
                    if (r.getCncdRfrNo() != null && !r.getCncdRfrNo().isBlank()) {
                        r.setPrevDupBg(prevDupBgMap.getOrDefault(r.getCncdRfrNo(), BigDecimal.ZERO));
                    }
                });
            }
        }
    }

    /**
     * 계속 항목의 전년도 예산 조회 키를 반환합니다.
     * 전년도 항목이 cncdRfrNo로 연결된 경우 그 관리번호, 아니면 자기 관리번호(연차 데이터 호환).
     */
    private static String prevBudgetLookupKey(CostDto.Response r) {
        return (r.getCncdRfrNo() != null && !r.getCncdRfrNo().isBlank())
                ? r.getCncdRfrNo()
                : r.getCostBgNo();
    }

    /** 응답 DTO에 신청서 정보, 코드명, 예산 구분, 전년도 예산을 일괄 설정 */
    private void enrichResponse(CostDto.Response response, Bcostm cost) {
        setApplicationInfo(response, cost.getCostBgNo(), cost.getBgSno());
        // 저장 스냅샷 우선 — setCodeNames의 CORGNI 조회는 null일 때만 폴백으로 동작
        if (cost.getSvnDpmNm() != null) {
            response.setCostSvnDpmNm(cost.getSvnDpmNm());
        }
        if (cost.getSvnTemNm() != null) {
            response.setSvnTemNm(cost.getSvnTemNm());
        }
        setCodeNames(response);
        setBudgetCategory(response);
        setPrevBudget(response);
    }

    /**
     * 단건 응답에 전년도 예산(prevBgAmt)을 설정합니다.
     *
     * <p>계속(abusTc='20') 항목만 대상이며, cncdRfrNo(전년도 관리번호) 우선 키로
     * 전년도(bseYy-1) BCOSTM 예산금액 합계를 조회합니다. 목록 배치 보강
     * ({@code enrichCostListBatch})과 동일한 기준입니다.</p>
     */
    private void setPrevBudget(CostDto.Response response) {
        response.setPrevBgAmt(BigDecimal.ZERO);
        if (!"20".equals(response.getAbusTc())) {
            return;
        }
        String bseYy = response.getBseYy();
        if (bseYy == null || !bseYy.matches("\\d{4}")) {
            return;
        }
        String key = prevBudgetLookupKey(response);
        if (key == null || key.isBlank()) {
            return;
        }
        String prevYear = String.valueOf(Integer.parseInt(bseYy) - 1);
        Map<String, BigDecimal> prevBgMap = costRepository.sumPrevBgByCostBgNos(List.of(key), prevYear);
        response.setPrevBgAmt(prevBgMap.getOrDefault(key, BigDecimal.ZERO));
    }

    /**
     * 응답 DTO에 연관된 단말기 목록을 조회·변환하여 설정
     */
    private void attachTerminals(CostDto.Response response) {
        List<Btermm> terminals = btermmRepository
                .findByTermBgNoAndTermBgSnoAndDelYn(response.getCostBgNo(), response.getBgSno(), "N");
        List<CostDto.TerminalDto> dtos = terminals.stream().map(CostDto.TerminalDto::fromEntity).toList();
        setTerminalCodeNames(dtos);
        response.setTerminals(dtos);
    }

    /** 부서코드→부서명, 사원번호→사용자명, 사업코드→사업코드명 조회 및 설정 */
    private void setCodeNames(CostDto.Response response) {
        // 담당부서/팀명: 스냅샷이 이미 세팅됐으면 건너뛰고, null일 때만 CORGNI 폴백 조회
        if (response.getCostSvnDpmNm() == null
                && response.getCostSvnDpmC() != null && !response.getCostSvnDpmC().isEmpty()) {
            corgnIRepository.findById(response.getCostSvnDpmC())
                    .ifPresent(org -> response.setCostSvnDpmNm(org.getBbrNm()));
        }
        if (response.getSvnTemNm() == null
                && response.getSvnTemC() != null && !response.getSvnTemC().isEmpty()) {
            corgnIRepository.findById(response.getSvnTemC())
                    .ifPresent(org -> response.setSvnTemNm(org.getBbrNm()));
        }
        if (response.getCgprId() != null && !response.getCgprId().isEmpty()) {
            cuserIRepository.findById(response.getCgprId())
                    .ifPresent(user -> response.setCgprNm(user.getUsrNm()));
        }
        if (response.getBgUntAbusC() != null && !response.getBgUntAbusC().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.ABUS_UNIT, response.getBgUntAbusC(), null)
                    .ifPresent(code -> response.setBgUntAbusCNm(code.getCdvaNm()));
        }
        if (response.getDfrCleC() != null && !response.getDfrCleC().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.DFR_CLE, response.getDfrCleC(), null)
                    .ifPresent(code -> response.setDfrCleCNm(code.getCdvaNm()));
        }
        if (response.getTmnYn() != null && !response.getTmnYn().isEmpty()) {
            // 단말여부(Y/N) → 구 IT_MNGC_TP 코드(002/001)로 환산하여 표시명 조회
            String mngcTpCode = "Y".equals(response.getTmnYn()) ? "1"
                    : "N".equals(response.getTmnYn()) ? "0" : null;
            if (mngcTpCode != null) {
                ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.TMN_YN, mngcTpCode, null)
                        .ifPresent(code -> response.setTmnYnNm(code.getCdvaNm()));
            }
        }
        if (response.getAbusTc() != null && !response.getAbusTc().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.ABUS, response.getAbusTc(), null)
                    .ifPresent(code -> response.setAbusTcNm(code.getCdvaNm()));
        }
        if (response.getIoeC() != null && !response.getIoeC().isEmpty()) {
            String ioeCNm = buildIoeCNameMap(Set.of(response.getIoeC())).get(response.getIoeC());
            response.setIoeCNm(ioeCNm);
        }
    }

    /**
     * 단말기 DTO 목록에 담당자명(cgprNm)과 코드명(단말기종류·이용방법·지급주기)을 일괄 설정.
     *
     * <p>
     * 담당자명은 사번 배치 조회, 코드명은 그룹별 1회 조회로 N+1을 방지한다.
     * 코드명은 cost-level(setCodeNames)과 동일한 CCODEM 유효일자 기준 조회를 사용한다.
     * </p>
     */
    private void setTerminalCodeNames(List<CostDto.TerminalDto> terminalDtos) {
        if (terminalDtos.isEmpty())
            return;

        // 담당자명: 사번 배치 조회
        Set<String> enos = terminalDtos.stream()
                .map(value -> value.getCgprId())
                .filter(cgprId -> cgprId != null && !cgprId.isEmpty())
                .collect(Collectors.toSet());
        if (!enos.isEmpty()) {
            Map<String, String> nameMap = cuserIRepository.findByEnoIn(enos).stream()
                    .collect(Collectors.toMap(
                            value -> value.getEno(),
                            value -> value.getUsrNm()));
            terminalDtos.forEach(tDto -> {
                if (tDto.getCgprId() != null) {
                    tDto.setCgprNm(nameMap.get(tDto.getCgprId()));
                }
            });
        }

        // 코드명: 단말기종류(tmnClsfC)/이용방법(tmnKdTc)/지급주기(dfrCleC) 그룹별 배치 조회
        Map<String, String> svcMap = codeNameMapBuilder.build(CommonCodeGroups.TERM_SERVICE,
                collectCdvas(terminalDtos, value -> value.getTmnClsfC()));
        Map<String, String> kindMap = codeNameMapBuilder.build(CommonCodeGroups.TERM_KIND,
                collectCdvas(terminalDtos, value -> value.getTmnKdTc()));
        Map<String, String> dfrMap = codeNameMapBuilder.build(CommonCodeGroups.DFR_CLE,
                collectCdvas(terminalDtos, value -> value.getDfrCleC()));
        terminalDtos.forEach(tDto -> {
            if (tDto.getTmnClsfC() != null)
                tDto.setTmnClsfCNm(svcMap.get(tDto.getTmnClsfC()));
            if (tDto.getTmnKdTc() != null)
                tDto.setTmnKdTcNm(kindMap.get(tDto.getTmnKdTc()));
            if (tDto.getDfrCleC() != null)
                tDto.setDfrCleCNm(dfrMap.get(tDto.getDfrCleC()));
        });
    }

    /** 단말기 DTO 목록에서 지정 코드 추출자로 비어있지 않은 cdva 집합 수집 */
    private Set<String> collectCdvas(List<CostDto.TerminalDto> dtos,
            java.util.function.Function<CostDto.TerminalDto, String> getter) {
        return dtos.stream()
                .map(getter)
                .filter(v -> v != null && !v.isEmpty())
                .collect(Collectors.toSet());
    }

    /** IOE 코드 cdva → CDVA_NM 우선 표시명 맵 생성 */
    private Map<String, String> buildIoeCNameMap(Set<String> cdvas) {
        return ccodemRepository.findByCIdWithValidDate(CommonCodeGroups.IOE, null).stream()
                .filter(c -> cdvas.contains(c.getCdva()))
                .collect(Collectors.toMap(
                        value -> value.getCdva(),
                        c -> {
                            String dtl = c.getCdvaNm() != null ? c.getCdvaNm()
                                    : (c.getCdvaDtl() != null ? c.getCdvaDtl()
                                            : (c.getCNm() != null ? c.getCNm() : c.getCdva()));
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
}
