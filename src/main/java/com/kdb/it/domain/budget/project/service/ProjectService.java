package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보화사업(IT 프로젝트) 서비스
 *
 * <p>정보화사업(TPRMPP_BPROJM) 엔티티의 CRUD 및 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm})
 * 동기화 비즈니스 로직을 처리합니다.
 *
 * <p>결재 연동:
 *
 * <ul>
 *   <li>수정/삭제 시 해당 프로젝트에 연결된 신청서(CAPPLA)의 결재 상태를 확인합니다
 *   <li>"결재중" 또는 "결재완료" 상태인 경우 수정/삭제가 불가합니다
 *   <li>원본 테이블 코드: {@code "BPROJM"}
 * </ul>
 *
 * <p>품목(Bitemm) 동기화 로직 (수정 시):
 *
 * <ol>
 *   <li>요청의 {@code gclMngNo}가 있으면 기존 활성 레코드를 제자리 수정(Dirty Checking) — 새 레코드를 추가하지 않는다
 *   <li>요청의 {@code gclMngNo}가 없으면 신규 항목 추가
 *   <li>요청에 없는 기존 항목은 Soft Delete
 * </ol>
 *
 * <p>Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다.
 *
 * <p>{@code @Transactional(readOnly = true)}: 조회 메서드의 기본값. 쓰기 메서드는 {@code @Transactional}로
 * 오버라이드합니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 기본 읽기 전용 트랜잭션
public class ProjectService {

    /** 정보화사업 데이터 접근 리포지토리 (TPRMPP_BPROJM) */
    private final ProjectRepository projectRepository;

    /** 신청서-원본 데이터 연결 리포지토리 (TPRMPP_CAPPLA): 결재 상태 확인용 */
    private final com.kdb.it.common.approval.repository.ApplicationMapRepository capplaRepository;

    /** 품목 데이터 접근 리포지토리 (TPRMPP_BITEMM) */
    private final com.kdb.it.domain.budget.project.repository.ProjectItemRepository
            bitemmRepository;

    /** 사용자 정보 리포지토리 (TPRMPP_CUSERI): 사원번호→사용자명 조회용 */
    private final com.kdb.it.common.iam.repository.UserRepository cuserIRepository;

    /** 조직코드→조직명 해석기: 주관부서명/주관팀명 스냅샷 저장용 */
    private final com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;

    /** 공통코드 서비스: 예산 신청 기간 검증용 */
    private final com.kdb.it.common.code.service.CodeService codeService;

    /** 환율 표준 조회 헬퍼: 외화 품목 저장 전 Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7) */
    private final XcrLookupService xcrLookupService;

    /** 정보화사업관계(TPRMPP_BPROJA) 동기화 서비스: 예산편성 단계 상태(작성중 01) 적재용 */
    private final BprojaSyncService bprojaSyncService;

    /** 정보화사업 조회 전용 서비스 */
    private final ProjectQueryService projectQueryService;

    /**
     * 삭제되지 않은 모든 정보화사업을 조회합니다.
     *
     * @return 신청서·코드명·예산 정보가 조립된 목록
     */
    public List<ProjectDto.Response> getProjectList() {
        return projectQueryService.getProjectList();
    }

    /**
     * 검색 조건에 맞는 정보화사업을 조회합니다.
     *
     * @param condition 검색 조건
     * @return 조건에 맞고 연관 정보가 조립된 목록
     */
    public List<ProjectDto.Response> searchProjectList(ProjectDto.SearchCondition condition) {
        return projectQueryService.searchProjectList(condition);
    }

    /**
     * 관리번호에 해당하는 정보화사업 상세를 조회합니다.
     *
     * @param prjMngNo 프로젝트관리번호
     * @return 신청서·품목·대표상태가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 프로젝트가 없는 경우
     */
    public ProjectDto.Response getProject(String prjMngNo) {
        return projectQueryService.getProject(prjMngNo);
    }

    /**
     * 신규 정보화사업 생성
     *
     * <p>프로젝트관리번호({@code PRJ_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다. 제공된 경우 중복 여부를 확인합니다.
     *
     * <p>자동 채번 로직:
     *
     * <ul>
     *   <li>요청 객체에 기준연도({@code bseYy})가 없으면 현재 연도를 사용합니다.
     *   <li>데이터베이스 시퀀스({@code SQ_TPRMPP_BPROJM_1})에서 다음 값을 가져옵니다.
     *   <li>관리번호 자동 생성 형식: {@code PRJ-{bseYy}-{seq:04d}} (예: {@code PRJ-2026-0001})
     * </ul>
     *
     * <p>예: {@code PRJ-2026-0001}
     *
     * @param request 정보화사업 생성 요청 DTO (프로젝트명, 예산, 기간, 담당자 등)
     * @return 생성된 프로젝트관리번호
     * @throws IllegalArgumentException 제공된 관리번호가 이미 존재하는 경우
     * @throws com.kdb.it.exception.CustomGeneralException 예산 신청 기간이 아닌 경우
     */
    // 프로젝트 생성 시 Tiptap 변수 카탈로그(활성 사업 목록 포함)가 stale → 전체 evict (P5/T13).
    // 이 메서드가 2-인자 메서드를 같은 빈 내부에서 호출(this.createProject(request, false))하므로
    // Spring 프록시를 우회한다. @CacheEvict는 실제 로직을 담은 2-인자 메서드뿐 아니라 외부에서
    // 직접 호출되는 이 1-인자 진입점에도 함께 붙여야 두 진입점 모두에서 evict가 발화한다
    // (allEntries=true라 중복 evict는 안전하다). ProjectServiceCacheEvictTest가 이 계약을 고정한다.
    @CacheEvict(cacheNames = "tiptapMetadata", allEntries = true)
    @Transactional
    public String createProject(ProjectDto.CreateRequest request) {
        return createProject(request, false);
    }

    /**
     * 신규 정보화사업 생성
     *
     * <p>{@code skipBudgetPeriodValidation}은 관리자 전용 수기 엑셀 이관 경로만 사용합니다. 이관 작업은 편성 시즌 밖에서도 실행되어야 하므로
     * 기간 검증을 건너뛸 수 있어야 하지만, 일반 사용자 화면 경로는 반드시 검증을 거쳐야 하므로 기본값 false인 1-인자 시그니처를 남겨 둡니다.
     *
     * <p>프로젝트관리번호({@code PRJ_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다. 제공된 경우 중복 여부를 확인합니다.
     *
     * <p>자동 채번 로직:
     *
     * <ul>
     *   <li>요청 객체에 기준연도({@code bseYy})가 없으면 현재 연도를 사용합니다.
     *   <li>데이터베이스 시퀀스({@code SQ_TPRMPP_BPROJM_1})에서 다음 값을 가져옵니다.
     *   <li>관리번호 자동 생성 형식: {@code PRJ-{bseYy}-{seq:04d}} (예: {@code PRJ-2026-0001})
     * </ul>
     *
     * <p>예: {@code PRJ-2026-0001}
     *
     * @param request 정보화사업 생성 요청 DTO (프로젝트명, 예산, 기간, 담당자 등)
     * @param skipBudgetPeriodValidation true면 예산 신청 기간 검증을 생략 (이관 전용)
     * @return 생성된 프로젝트관리번호
     * @throws IllegalArgumentException 제공된 관리번호가 이미 존재하는 경우
     * @throws com.kdb.it.exception.CustomGeneralException 검증을 수행했고 예산 신청 기간이 아닌 경우
     */
    // 프로젝트 생성 시 Tiptap 변수 카탈로그(활성 사업 목록 포함)가 stale → 전체 evict (P5/T13).
    // 캐시 키가 'ALL'·부서코드별로 분산되어 단일 키로는 무효화 불가하므로 allEntries=true.
    // 실제 로직은 이 2-인자 메서드에 있으므로 이관 전용 skip=true 호출(프록시를 통한 외부 호출) 시에도
    // evict가 발화하도록 여기에도 @CacheEvict를 유지한다. 1-인자 진입점에도 같은 어노테이션이
    // 별도로 붙어 있다(자기 자신 호출은 프록시를 우회하므로 위임만으로는 부족).
    @CacheEvict(cacheNames = "tiptapMetadata", allEntries = true)
    @Transactional
    public String createProject(
            ProjectDto.CreateRequest request, boolean skipBudgetPeriodValidation) {
        if (!skipBudgetPeriodValidation) {
            codeService.validateBudgetPeriod();
        }

        String prjMngNo = request.getAbusMngNo();

        // 프로젝트관리번호가 없으면 자동 채번
        if (prjMngNo == null || prjMngNo.isEmpty()) {
            Long nextVal = projectRepository.getNextSequenceValue(); // Oracle 시퀀스 채번

            // 사업연도 결정 (요청값 없으면 현재 연도 사용)
            String year = request.getBseYy();
            if (year == null || year.isEmpty()) {
                year = String.valueOf(java.time.LocalDate.now().getYear());
                request.setBseYy(year);
            }

            // 형식: PRJ-{bseYy}-{seq:04d}
            prjMngNo = String.format("PRJ-%s-%04d", year, nextVal);
            request.setAbusMngNo(prjMngNo);

        } else {
            // 제공된 관리번호 중복 확인 (복합키이므로 abusMngNo 기준으로 존재 여부 확인)
            if (projectRepository.existsByAbusMngNoAndDelYn(prjMngNo, "N")) {
                throw new IllegalArgumentException("Project already exists with id: " + prjMngNo);
            }
        }

        // Rich Text 필드 XSS 새니타이징 (서버 측 방어)
        request.setAbusCone(HtmlSanitizer.sanitize(request.getAbusCone()));
        request.setAbusRngCone(HtmlSanitizer.sanitize(request.getAbusRngCone()));

        // 의무완료기한(FLF_FSG_DT, VARCHAR2(8)) 정규화: 프론트는 "YYYY-MM-DD"(ISO)로 보내므로
        // 하이픈을 제거해 yyyyMMdd 8자리로 저장 (ORA-12899 방지, 품목 xcrBseDt와 동일 처리)
        request.setFlfFsgDt(DateFormatUtil.toYmd8(request.getFlfFsgDt()));

        // 엔티티 생성
        Bprojm project = request.toEntity();
        // 주관팀/개발팀은 각 담당자(주관=USID, IT=DVM_USID) 소속 팀 스냅샷(팀코드+팀명)으로 채움
        TeamSnapshot svnTeam = resolveTeam(project.getUsid());
        TeamSnapshot dvmTeam = resolveTeam(project.getDvmUsid());
        project.assignTeamCodes(svnTeam.temC(), dvmTeam.temC());
        // 주관부서명은 CORGNI 조회 스냅샷, 주관팀명은 담당자(CUSERI) 팀명 스냅샷으로 저장
        // (팀코드는 CORGNI에 없어 CORGNI 조회로는 팀명을 얻지 못하므로 담당자 팀명을 사용)
        project.assignSvnOrgNames(
                orgNameResolver.resolveName(project.getSvnDpmC()), svnTeam.temNm());
        projectRepository.save(project);

        // ===== 품목(Bitemm) 저장 =====
        // 신규 등록 시 요청에 포함된 모든 품목은 신규 추가 대상
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            int gclSno = 0; // 품목일련번호 (1부터 시작)
            for (ProjectDto.BitemmDto itemDto : request.getItems()) {
                Long gclSeq = bitemmRepository.getNextSequenceValue(); // Oracle 시퀀스 채번
                String gclMngNo =
                        String.format("GCL-%s-%04d", java.time.LocalDate.now().getYear(), gclSeq);

                // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));

                // 외화 재계산: gclAmt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                BigDecimal[] reconciled =
                        BudgetAmountCalculator.reconcileAmount(
                                itemDto.getFcAmt(),
                                itemDto.getAmt(),
                                itemDto.getCurC(),
                                itemDto.getXcr());

                com.kdb.it.domain.budget.project.entity.Bitemm newItem =
                        com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                                .sno(++gclSno) // 품목일련번호
                                .abusMngNo(project.getAbusMngNo()) // 프로젝트관리번호
                                .fntTbCrySno(project.getSno()) // 프로젝트순번
                                .ioeC(itemDto.getIoeC()) // 품목구분
                                .gclNm(itemDto.getGclNm()) // 품목명
                                .qty(itemDto.getQty()) // 품목수량
                                .curC(itemDto.getCurC()) // 통화
                                .xcr(itemDto.getXcr()) // 환율
                                .xcrBseDt(
                                        DateFormatUtil.toYmd8(
                                                itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                                .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                                .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                                .dfrCleC(CodeDefaults.orNotApplicable(itemDto.getDfrCleC())) // 지급주기
                                .sectSysUtzYn(
                                        itemDto.getSectSysUtzYn() == null
                                                ? "N"
                                                : itemDto.getSectSysUtzYn()) // 정보보호여부
                                .itrInfrYn(
                                        itemDto.getItrInfrYn() == null
                                                ? "N"
                                                : itemDto.getItrInfrYn()) // 통합인프라여부
                                .lstYn("Y") // 최종여부
                                .amt(reconciled[0]) // 품목금액 (서버 재계산)
                                .fcAmt(reconciled[1]) // 외화금액 (외화 행에서만 유효)
                                .mplAmt(
                                        clampMpl(
                                                itemDto.getMplAmt(),
                                                reconciled[0])) // 예정금액 (0 ≤ mplAmt ≤ amt)
                                .build();
                bitemmRepository.save(newItem);
            }
        }

        // 정보화사업관계(BPROJA) 적재: 예산편성 요청 작성중(IT_PTL_STS_TC='01').
        // 예산편성 단계는 별도 단계 문서가 없으므로 단계 key(CNCD_RFR_NO)는 프로젝트관리번호 자신으로 둔다.
        // 이후 결재 상신('02')/완료('09')가 동일 BPROJA 행을 멱등 upsert 하여 대표상태(MAX)에 반영된다.
        bprojaSyncService.upsert(prjMngNo, prjMngNo, "01");

        return project.getAbusMngNo(); // 저장된 관리번호 반환
    }

    /**
     * 정보화사업 수정
     *
     * <p>프로젝트 기본 정보를 수정하고, 품목(Bitemm) 목록을 동기화합니다.
     *
     * <p>결재 상태 확인: "결재중" 또는 "결재완료" 상태인 경우 수정이 불가합니다.
     *
     * <p>품목 동기화 로직:
     *
     * <ol>
     *   <li>기존 품목 목록 조회 (DEL_YN='N')
     *   <li>요청 품목 처리:
     *       <ul>
     *         <li>{@code gclMngNo}가 있는 경우: 기존 활성 품목의 업무 필드를 제자리 수정하고 기본키와 연관키 유지
     *         <li>{@code gclMngNo}가 없는 경우: 신규 항목으로 시퀀스 채번 후 추가
     *       </ul>
     *   <li>요청에 없는 기존 항목: Soft Delete ({@code DEL_YN='Y'})
     * </ol>
     *
     * @param prjMngNo 수정할 프로젝트관리번호
     * @param request 수정 요청 DTO (변경할 필드들, 품목 목록)
     * @return 수정된 프로젝트관리번호
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     * @throws IllegalStateException 결재중/결재완료 상태여서 수정 불가한 경우
     */
    // 프로젝트 수정 시 Tiptap 변수 카탈로그가 stale → 전체 evict (P5/T13).
    @CacheEvict(cacheNames = "tiptapMetadata", allEntries = true)
    @Transactional
    public String updateProject(String prjMngNo, ProjectDto.UpdateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        // 프로젝트 조회 (삭제되지 않은 항목만)
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(prjMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        OwnershipVerifier.verifyModifiable(project.getFstEnrUsid(), project.getSvnDpmC());

        // 결재 상태 확인 (BPROJM 테이블 코드로 신청서 연결 여부 조회)
        // 결재중 또는 결재완료 상태인 경우 수정 불가
        boolean isProcessingOrApproved =
                capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        "BPROJM",
                        prjMngNo,
                        project.getSno(),
                        java.util.List.of(
                                com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code(),
                                com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 수정할 수 없습니다.");
        }

        // Rich Text 필드 XSS 새니타이징 (서버 측 방어)
        request.setAbusCone(HtmlSanitizer.sanitize(request.getAbusCone()));
        request.setAbusRngCone(HtmlSanitizer.sanitize(request.getAbusRngCone()));

        // 프로젝트 기본 정보 수정 (JPA Dirty Checking으로 자동 반영)
        project.update(
                new Bprojm.UpdateCommand(
                        request.getAbusNm(),
                        request.getBzTpC(),
                        request.getSvnDpmC(),
                        request.getDvmDpmC(),
                        request.getSttDtm(),
                        request.getEndDtm(),
                        request.getUsid(),
                        request.getDvmUsid(),
                        request.getTlrUsid(),
                        request.getDvmTlrUsid(),
                        request.getEdrtTc(),
                        request.getAbusCone(),
                        request.getCpnSafCone(),
                        request.getAbusNcsCone(),
                        request.getDgogPpoCone(),
                        request.getPlmDes(),
                        request.getAbusRngCone(),
                        request.getMnPrgCone(),
                        request.getHrfPlnCone(),
                        request.getBzDttNm(),
                        request.getSklTpTc(),
                        request.getCstTpTc(),
                        request.getDplYn(),
                        DateFormatUtil.toYmd8(request.getFlfFsgDt()),
                        request.getRprStsTc(),
                        request.getExePttYn(),
                        request.getBseYy(),
                        request.getPrlmHrkOgzCCone(),
                        request.getOdnYn(),
                        request.getAbusTc(),
                        request.getCncdRfrNo()));

        // 담당자(주관=USID, IT=DVM_USID) 변경 시 소속 팀 스냅샷(팀코드+팀명)도 함께 갱신
        TeamSnapshot svnTeam = resolveTeam(project.getUsid());
        TeamSnapshot dvmTeam = resolveTeam(project.getDvmUsid());
        project.assignTeamCodes(svnTeam.temC(), dvmTeam.temC());
        // 주관부서명은 CORGNI 조회 스냅샷, 주관팀명은 담당자(CUSERI) 팀명 스냅샷으로 갱신
        // (팀코드는 CORGNI에 없어 CORGNI 조회로는 팀명을 얻지 못하므로 담당자 팀명을 사용)
        project.assignSvnOrgNames(
                orgNameResolver.resolveName(project.getSvnDpmC()), svnTeam.temNm());

        // ===== 품목 정보 동기화 (CUD) =====
        if (request.getItems() != null) {
            // 1. 기존 품목 조회 (DEL_YN='N')
            List<com.kdb.it.domain.budget.project.entity.Bitemm> existingItems =
                    bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                            prjMngNo, project.getSno(), "N");

            // 처리된 품목 관리번호 추적 (삭제 대상 식별용)
            java.util.Set<String> processedGclMngNos = new java.util.HashSet<>();
            // 현재 최대 SNO 계산 (신규 추가 시 MAX+1로 설정)
            int maxGclSno =
                    existingItems.stream().mapToInt(value -> value.getSno()).max().orElse(0);

            // 2. 요청 품목 처리 (수정 또는 신규 추가)
            for (ProjectDto.BitemmDto itemDto : request.getItems()) {
                if (itemDto.getGclMngNo() != null && !itemDto.getGclMngNo().isEmpty()) {
                    // === 기존 항목 수정 ===
                    // gclMngNo로 현재 활성(DEL_YN='N') 항목 찾기 (existingItems는 이미 DEL_YN='N' 필터됨)
                    com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                            existingItems.stream()
                                    .filter(
                                            item ->
                                                    item.getGclMngNo()
                                                            .equals(itemDto.getGclMngNo()))
                                    .findFirst()
                                    .orElse(null);

                    if (existingItem != null) {
                        // 변경된 필드가 있을 때만 제자리 수정 (변경 없으면 UPDATE·로그 생성 생략)
                        if (isItemChanged(existingItem, itemDto)) {
                            // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                            itemDto.setXcr(
                                    xcrLookupService.resolveXcr(
                                            itemDto.getCurC(), LocalDate.now()));
                            // 외화 재계산: amt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                            BigDecimal[] reconciled =
                                    BudgetAmountCalculator.reconcileAmount(
                                            itemDto.getFcAmt(),
                                            itemDto.getAmt(),
                                            itemDto.getCurC(),
                                            itemDto.getXcr());
                            // 기존 활성 레코드를 제자리 수정 (버저닝 폐기 — 새 레코드를 추가하지 않는다).
                            // PK(GCL_MNG_NO, SNO)와 연관 필드(ABUS_MNG_NO, FNT_TB_CRY_SNO)는 유지하고 업무 필드만
                            // 갱신.
                            // Dirty Checking으로 트랜잭션 종료 시 UPDATE가 실행된다.
                            existingItem.update(
                                    itemDto.getIoeC(), // 품목구분
                                    itemDto.getGclNm(), // 품목명
                                    itemDto.getQty(), // 품목수량
                                    itemDto.getCurC(), // 통화
                                    itemDto.getXcr(), // 환율
                                    DateFormatUtil.toYmd8(
                                            itemDto.getXcrBseDt()), // 환율기준일자(yyyyMMdd 정규화)
                                    itemDto.getCncdFdtnCone(), // 예산근거
                                    toItdYm(itemDto.getBseYm()), // 도입시기
                                    itemDto.getDfrCleC(), // 지급주기
                                    defaultYn(itemDto.getSectSysUtzYn()), // 정보보호여부
                                    defaultYn(itemDto.getItrInfrYn()), // 통합인프라여부
                                    reconciled[0], // 품목금액 (서버 재계산)
                                    reconciled[1], // 외화금액 (외화 행에서만 유효)
                                    clampMpl(
                                            itemDto.getMplAmt(),
                                            reconciled[0])); // 예정금액 (0 ≤ mplAmt ≤ amt)
                        }
                        processedGclMngNos.add(existingItem.getGclMngNo()); // 변경 여부와 무관하게 처리 완료 표시
                    }
                } else {
                    // === 신규 품목 추가 ===
                    // Oracle 시퀀스로 품목관리번호 채번
                    Long gclSeq = bitemmRepository.getNextSequenceValue();
                    String gclMngNo =
                            String.format(
                                    "GCL-%s-%04d", java.time.LocalDate.now().getYear(), gclSeq);

                    // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                    itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));

                    // 외화 재계산: amt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                    BigDecimal[] reconciled =
                            BudgetAmountCalculator.reconcileAmount(
                                    itemDto.getFcAmt(),
                                    itemDto.getAmt(),
                                    itemDto.getCurC(),
                                    itemDto.getXcr());

                    com.kdb.it.domain.budget.project.entity.Bitemm newItem =
                            com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                    .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                                    .sno(++maxGclSno) // 품목일련번호 (MAX+1)
                                    .abusMngNo(prjMngNo) // 프로젝트관리번호
                                    .fntTbCrySno(project.getSno()) // 프로젝트순번
                                    .ioeC(itemDto.getIoeC()) // 품목구분
                                    .gclNm(itemDto.getGclNm()) // 품목명
                                    .qty(itemDto.getQty()) // 품목수량
                                    .curC(itemDto.getCurC()) // 통화
                                    .xcr(itemDto.getXcr()) // 환율
                                    .xcrBseDt(
                                            DateFormatUtil.toYmd8(
                                                    itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                                    .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                                    .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                                    .dfrCleC(
                                            CodeDefaults.orNotApplicable(
                                                    itemDto.getDfrCleC())) // 지급주기
                                    .sectSysUtzYn(
                                            itemDto.getSectSysUtzYn() == null
                                                    ? "N"
                                                    : itemDto.getSectSysUtzYn()) // 정보보호여부
                                    .itrInfrYn(
                                            itemDto.getItrInfrYn() == null
                                                    ? "N"
                                                    : itemDto.getItrInfrYn()) // 통합인프라여부
                                    .lstYn("Y") // 최종여부
                                    .amt(reconciled[0]) // 품목금액 (서버 재계산)
                                    .fcAmt(reconciled[1]) // 외화금액 (외화 행에서만 유효)
                                    .mplAmt(
                                            clampMpl(
                                                    itemDto.getMplAmt(),
                                                    reconciled[0])) // 예정금액 (0 ≤ mplAmt ≤ amt)
                                    .build();
                    bitemmRepository.save(newItem);
                }
            }

            // 3. 요청에 없는 기존 품목 Soft Delete 처리
            // processedGclMngNos에 포함되지 않은 기존 항목은 삭제 대상
            for (com.kdb.it.domain.budget.project.entity.Bitemm existingItem : existingItems) {
                if (!processedGclMngNos.contains(existingItem.getGclMngNo())) {
                    existingItem.delete(); // BaseEntity.delete() → DEL_YN='Y'
                }
            }
        }

        return project.getAbusMngNo(); // 수정된 관리번호 반환
    }

    /**
     * 품목 변경 여부 판단
     *
     * <p>기존 엔티티와 요청 DTO의 업무 필드를 비교하여, 하나라도 다르면 {@code true}를 반환합니다.
     *
     * <p>변경이 없는 품목은 UPDATE와 감사 변경 로그 생성을 건너뜁니다.
     *
     * <p>BigDecimal 필드(xcr, gclQty, gclAmt)는 scale 무관한 수치 비교를 위해 compareTo를 사용합니다.
     *
     * @param existing 현재 활성 품목 엔티티 (DEL_YN='N')
     * @param dto 클라이언트로부터 전달된 수정 요청 DTO
     * @return 변경된 필드가 하나라도 있으면 {@code true}
     */
    private boolean isItemChanged(Bitemm existing, ProjectDto.BitemmDto dto) {
        return !Objects.equals(existing.getIoeC(), dto.getIoeC())
                || !Objects.equals(existing.getGclNm(), dto.getGclNm())
                || bigDecimalChanged(existing.getQty(), dto.getQty())
                || !Objects.equals(existing.getCurC(), dto.getCurC())
                || bigDecimalChanged(existing.getXcr(), dto.getXcr())
                || !Objects.equals(existing.getXcrBseDt(), dto.getXcrBseDt())
                || !Objects.equals(existing.getCncdFdtnCone(), dto.getCncdFdtnCone())
                || !Objects.equals(existing.getBseYm(), dto.getBseYm())
                || !Objects.equals(existing.getDfrCleC(), dto.getDfrCleC())
                || !Objects.equals(existing.getSectSysUtzYn(), defaultYn(dto.getSectSysUtzYn()))
                || !Objects.equals(existing.getItrInfrYn(), defaultYn(dto.getItrInfrYn()))
                || bigDecimalChanged(existing.getAmt(), dto.getAmt())
                || bigDecimalChanged(existing.getMplAmt(), dto.getMplAmt())
                // fcAmt 변경 시 D/C 이력 생성 (null-safe 비교)
                || bigDecimalChanged(existing.getFcAmt(), dto.getFcAmt());
    }

    /** null이면 "N"으로 정규화 (infPrtYn, itrInfrYn 공통 기본값 처리) */
    private static String defaultYn(String value) {
        return value == null ? "N" : value;
    }

    /**
     * 담당자 사번(eno)으로 소속 팀 스냅샷(팀코드 + 팀명)을 조회한다.
     *
     * <p>주관팀코드(SVN_TEM_C)/개발팀코드(DVM_TEM_C)는 {@code CUSERI.TEM_C}, 주관팀명(SVN_TEM_NM)은 {@code
     * CUSERI.TEM_NM}에서 직접 가져온다. 팀코드는 조직마스터(CORGNI)에 등재되지 않아 CORGNI 조회로는 팀명을 얻을 수 없으므로, 담당자 레코드의 팀명을
     * 그대로 저장 시점 스냅샷으로 사용한다. 담당자 미지정(사번 null/공백)이거나 CUSERI 미조회 시 팀코드·팀명 모두 {@code null}이다(대상 컬럼
     * nullable).
     *
     * @param eno 담당자 사번 (null/공백 허용)
     * @return 소속 팀 스냅샷. eno가 비었거나 사용자 미조회 시 {@link TeamSnapshot#EMPTY}
     */
    private TeamSnapshot resolveTeam(String eno) {
        if (eno == null || eno.isBlank()) {
            return TeamSnapshot.EMPTY;
        }
        return cuserIRepository
                .findByEno(eno)
                .map(user -> new TeamSnapshot(user.getTemC(), user.getTemNm()))
                .orElse(TeamSnapshot.EMPTY);
    }

    /**
     * 담당자 소속 팀 스냅샷(팀코드 + 팀명).
     *
     * @param temC 팀코드 (CUSERI.TEM_C, 미조회 시 null)
     * @param temNm 팀명 (CUSERI.TEM_NM, 미조회 시 null)
     */
    private record TeamSnapshot(String temC, String temNm) {
        /** 담당자 미지정·미조회 시 사용할 빈 스냅샷(팀코드·팀명 모두 null). */
        private static final TeamSnapshot EMPTY = new TeamSnapshot(null, null);
    }

    /**
     * 도입시기를 DB 컬럼 형식(YYYYMM, 6자)으로 변환. 프론트에서 "YYYY-MM-DD" 또는 "YYYY-MM" 형식이 올 수 있으므로 하이픈을 제거한 뒤 앞
     * 6자만 사용한다. 빈값/null은 그대로 반환.
     */
    private static String toItdYm(String itdYm) {
        if (itdYm == null || itdYm.isBlank()) {
            return itdYm;
        }
        String normalized = itdYm.replace("-", "");
        return normalized.length() > 6 ? normalized.substring(0, 6) : normalized;
    }

    /** BigDecimal 수치 비교 (scale 무시). 둘 다 null이면 동일, 한쪽만 null이면 변경으로 간주 */
    private static boolean bigDecimalChanged(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    /**
     * 예정금액을 유효 범위 [0, amt]로 보정한다.
     *
     * @param mplAmt 입력 예정금액(null이면 0)
     * @param amt 품목금액(서버 재계산값, null이면 상한 미적용)
     * @return 0 이상, amt 이하로 클램프된 예정금액
     */
    private static BigDecimal clampMpl(BigDecimal mplAmt, BigDecimal amt) {
        BigDecimal v = (mplAmt == null) ? BigDecimal.ZERO : mplAmt;
        if (v.signum() < 0) v = BigDecimal.ZERO;
        if (amt != null && v.compareTo(amt) > 0) v = amt;
        return v;
    }

    /**
     * 정보화사업 삭제 (Soft Delete)
     *
     * <p>프로젝트와 연결된 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm}) 모두를 {@code
     * DEL_YN='Y'}로 논리 삭제합니다.
     *
     * <p>결재 상태 확인: "결재중" 또는 "결재완료" 상태인 경우 삭제가 불가합니다.
     *
     * @param prjMngNo 삭제할 프로젝트관리번호
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     * @throws IllegalStateException 결재중/결재완료 상태여서 삭제 불가한 경우
     */
    // 프로젝트 삭제 시 Tiptap 변수 카탈로그가 stale → 전체 evict (P5/T13).
    @CacheEvict(cacheNames = "tiptapMetadata", allEntries = true)
    @Transactional
    public void deleteProject(String prjMngNo) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        // 프로젝트 조회 (삭제되지 않은 항목만)
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(prjMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        OwnershipVerifier.verifyModifiable(project.getFstEnrUsid(), project.getSvnDpmC());

        // 결재 상태 확인 (결재중/결재완료이면 삭제 불가)
        boolean isProcessingOrApproved =
                capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        "BPROJM",
                        prjMngNo,
                        project.getSno(),
                        java.util.List.of(
                                com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code(),
                                com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 삭제할 수 없습니다.");
        }

        // 1. 프로젝트 Soft Delete (DEL_YN='Y')
        project.delete();

        // 2. 관련 품목 전체 Soft Delete (DEL_YN 무관하게 모든 품목 조회 후 삭제)
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms =
                bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, project.getSno());
        for (com.kdb.it.domain.budget.project.entity.Bitemm bitemm : bitemms) {
            bitemm.delete(); // BaseEntity.delete() 호출 (DEL_YN='Y')
        }
    }

    /**
     * /** 정보화사업을 관리번호 목록으로 일괄 조회합니다.
     *
     * @param request 프로젝트관리번호 목록과 기준연도
     * @return 성공 항목과 누락 관리번호를 분리한 응답
     */
    public ProjectDto.BulkResponse getProjectsByIds(ProjectDto.BulkGetRequest request) {
        return projectQueryService.getProjectsByIds(request);
    }
}
