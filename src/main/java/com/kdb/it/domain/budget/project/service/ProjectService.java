package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.List;
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
 *   <li>"결재중" 상태인 경우 수정/삭제가 불가합니다
 *   <li>"결재완료" 상태는 시스템관리자만 사후 정정을 위해 수정/삭제할 수 있습니다
 *   <li>원본 테이블 코드: {@code "BPROJM"}
 * </ul>
 *
 * <p>품목(Bitemm) 동기화의 규칙과 구현은 {@link ProjectItemSynchronizer}에 있습니다. 이 서비스는 사업 본문 저장·권한·결재 상태 검증과 금액
 * 스냅샷 기록을 맡고, 품목 채번·환율 정규화·CUD 병합은 그 협력자에 위임합니다.
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

    /** 정보화사업 품목 기준 예산 합계 계산 서비스: 저장 시점 금액 스냅샷 계산용 */
    private final ProjectBudgetSummaryService budgetSummaryService;

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
        // 주관팀/개발팀은 각 담당자(주관=USID, IT=DVM_USID) 소속 팀 스냅샷(팀코드+팀명)으로 채움.
        // 단 요청이 팀코드를 명시했으면(수기 엑셀 이관은 시트의 담당팀·담당IT팀 열을 해석해 넘긴다)
        // 그 값을 우선한다 — 담당자 소속으로 덮어쓰면 시트가 지정한 팀이 조용히 사라진다.
        TeamSnapshot svnTeam = resolveTeam(project.getUsid());
        TeamSnapshot dvmTeam = resolveTeam(project.getDvmUsid());
        String svnTemC = firstNonBlank(request.getSvnTemC(), svnTeam.temC());
        project.assignTeamCodes(svnTemC, firstNonBlank(request.getDvmTemC(), dvmTeam.temC()));
        // 주관부서명은 CORGNI 조회 스냅샷, 주관팀명은 담당자(CUSERI) 팀명 스냅샷으로 저장
        // (팀코드는 CORGNI에 없어 CORGNI 조회로는 팀명을 얻지 못하므로 담당자 팀명을 사용).
        // 요청이 팀코드를 명시한 경우에만 그 코드로 조직명을 한 번 더 조회해 본다.
        // 두 근거로도 팀명을 못 얻으면(팀코드도 없고 담당자가 사번이 아닌 경우) 요청이 준 팀명을 쓴다
        String svnTemNm =
                firstNonBlank(
                        isBlank(request.getSvnTemC())
                                ? svnTeam.temNm()
                                : firstNonBlank(
                                        orgNameResolver.resolveName(svnTemC), svnTeam.temNm()),
                        request.getSvnTemNm());
        project.assignSvnOrgNames(orgNameResolver.resolveName(project.getSvnDpmC()), svnTemNm);
        // 담당팀장명·담당자명 스냅샷 — 퇴사 후에도 남기기 위해 저장한다(BE-63)
        project.assignPersonNames(
                resolvePersonName(project.getTlrUsid()), resolvePersonName(project.getUsid()));
        // 반환값을 반드시 재대입한다: 요청이 관리번호를 이미 채워 보낸 경우(auto-채번 포함, 위에서
        // request.setAbusMngNo로 채움) ID가 non-null이라 Spring Data의 isNew() 판정이 false가 되고
        // SimpleJpaRepository.save가 entityManager.merge()를 타 별도의 영속 인스턴스를 반환한다.
        // 로컬 project는 detached 상태로 남으므로, 이후 applyAmountSnapshot 등 저장 이후 변경은
        // 반드시 이 반환값(managed 인스턴스)에 대해 이뤄져야 Dirty Checking으로 UPDATE가 발생한다.
        project = projectRepository.save(project);

        // ===== 품목(Bitemm) 저장 =====
        // 신규 등록 시 요청에 포함된 모든 품목은 신규 추가 대상
        itemSynchronizer().createAll(project, request.getItems());

        // 품목 저장이 끝난 뒤 사업 단위 금액 스냅샷(총소요·예정·지급금액) 기록
        applyAmountSnapshot(project, request.getDfrAmt());

        // 정보화사업관계(BPROJA) 적재: 예산편성 요청 작성중(IT_PTL_STS_TC='01').
        // 예산편성 단계는 별도 단계 문서가 없으므로 단계 key(CNCD_RFR_NO)는 프로젝트관리번호 자신으로 둔다.
        // 이후 결재 상신('02')/완료('09')가 동일 BPROJA 행을 멱등 upsert 하여 대표상태(MAX)에 반영된다.
        bprojaSyncService.upsert(prjMngNo, prjMngNo, "01");

        return project.getAbusMngNo(); // 저장된 관리번호 반환
    }

    /**
     * 품목 동기화 협력자를 만듭니다.
     *
     * <p>Spring 빈으로 주입하지 않고 호출 시점에 만듭니다 — 빈으로 두면 {@code ProjectServiceTest}가 mock으로 대체해 품목 동기화 검증이
     * 조용히 무력화됩니다. 상태가 없어 매 호출 생성 비용은 무시할 수 있습니다.
     */
    private ProjectItemSynchronizer itemSynchronizer() {
        return new ProjectItemSynchronizer(bitemmRepository, xcrLookupService);
    }

    /**
     * 연결된 신청서의 결재 상태 때문에 쓰기(수정·삭제)가 막히는지 판정합니다.
     *
     * <p>결재중(01)은 결재선이 지금 검토 중인 내용이라 누구도 바꿀 수 없습니다. 결재완료(02)는 확정 기록이지만 사후 정정이 필요한 경우가 있어 시스템관리자에게만
     * 열어 둡니다 — 관리자 판정은 {@link OwnershipVerifier#isCurrentUserAdmin()}에 위임합니다.
     *
     * @param prjMngNo 프로젝트관리번호 (CAPPLA.PK_COL_NM)
     * @param sno 원본 테이블 일련번호 (CAPPLA.FNT_TB_CRY_SNO)
     * @return 현재 사용자 기준으로 쓰기가 막히면 true
     */
    private boolean isBlockedByApproval(String prjMngNo, Integer sno) {
        List<String> blockingStatuses =
                OwnershipVerifier.isCurrentUserAdmin()
                        ? List.of(ApprovalStatus.IN_PROGRESS.code())
                        : List.of(
                                ApprovalStatus.IN_PROGRESS.code(), ApprovalStatus.COMPLETED.code());
        return capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                "BPROJM", prjMngNo, sno, blockingStatuses);
    }

    /**
     * 결재 상태 차단 안내 문구를 만듭니다. 관리자는 결재완료가 차단 사유에서 빠지므로 사유를 결재중으로만 알립니다.
     *
     * @param action 막힌 동작 이름 ("수정" 또는 "삭제")
     * @return 사용자에게 보일 안내 문구
     */
    private static String approvalBlockMessage(String action) {
        return OwnershipVerifier.isCurrentUserAdmin()
                ? "결재중인 프로젝트는 " + action + "할 수 없습니다."
                : "결재중이거나 결재완료된 프로젝트는 " + action + "할 수 없습니다.";
    }

    /**
     * 정보화사업 수정
     *
     * <p>프로젝트 기본 정보를 수정하고, 품목(Bitemm) 목록을 동기화합니다.
     *
     * <p>결재 상태 확인: "결재중" 상태인 경우 수정이 불가합니다. "결재완료" 상태는 시스템관리자만 수정할 수 있습니다.
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
     * @throws IllegalStateException 결재중(또는 비관리자의 결재완료) 상태여서 수정 불가한 경우
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
        if (isBlockedByApproval(prjMngNo, project.getSno())) {
            throw new IllegalStateException(approvalBlockMessage("수정"));
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
        // 담당자 변경 시 이름 스냅샷도 갱신. 해석 실패(퇴사)면 기존 값을 그대로 둔다(BE-63)
        project.assignPersonNames(
                resolvePersonName(project.getTlrUsid()), resolvePersonName(project.getUsid()));

        // ===== 품목 정보 동기화 (CUD) =====
        itemSynchronizer().sync(project, request.getItems());

        // 품목 동기화가 끝난 뒤 사업 단위 금액 스냅샷(총소요·예정·지급금액) 기록
        applyAmountSnapshot(project, request.getDfrAmt());

        return project.getAbusMngNo(); // 수정된 관리번호 반환
    }

    /**
     * 품목 저장이 끝난 뒤 사업 단위 금액 스냅샷을 기록합니다.
     *
     * <p>활성 품목을 다시 조회해 합산합니다. 영속성 컨텍스트가 조회 전에 flush 되므로 방금 저장·수정·논리삭제한 품목이 모두 반영됩니다. 반환값을 쓰지 않고
     * 엔티티에 바로 반영하며, Dirty Checking으로 UPDATE가 실행됩니다.
     *
     * @param project 대상 사업 엔티티 (영속 상태)
     * @param requestedDfrAmt 요청이 보낸 원화 지급금액 (null이면 0)
     * @throws IllegalArgumentException 지급금액이 음수인 경우
     */
    private void applyAmountSnapshot(Bprojm project, BigDecimal requestedDfrAmt) {
        BigDecimal dfrAmt = requestedDfrAmt == null ? BigDecimal.ZERO : requestedDfrAmt;
        if (dfrAmt.signum() < 0) {
            throw new IllegalArgumentException("지급금액은 0 이상이어야 합니다.");
        }
        ProjectAmountSummary snapshot = sumActiveItems(project, dfrAmt);
        project.assignAmountSnapshot(
                snapshot.totalRequiredAmt(), snapshot.plannedAmt(), snapshot.paidAmt());
    }

    /**
     * 활성 품목을 다시 조회해 사업 단위 금액 합계를 계산합니다.
     *
     * <p>{@link #applyAmountSnapshot}(사용자 입력 지급금액을 검증하는 저장 경로)이 쓰는 합산 단계입니다. 활성 품목 집합의 조회 조건을 이 메서드
     * 하나로 고정해, 합계를 다시 계산하는 경로가 늘어도 같은 집합을 보게 합니다.
     *
     * @param project 대상 사업 엔티티 (영속 상태)
     * @param paidAmt 원화 지급금액
     * @return 활성 품목과 지급금액 기준 사업 금액 합계
     */
    private ProjectAmountSummary sumActiveItems(Bprojm project, BigDecimal paidAmt) {
        List<Bitemm> activeItems =
                bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        project.getAbusMngNo(), project.getSno(), "N");
        return budgetSummaryService.calculateAmountSnapshot(activeItems, paidAmt);
    }

    /**
     * 구 이관 호출의 선언 지급금액을 품목 중앙 계산 스냅샷에 반영합니다.
     *
     * <p>호환 시그니처의 선언 total/MPL은 신뢰하지 않습니다. 활성 최신 품목의 AMT/MPL을 다시 계산하고 선언 DFR만 더해 저장하므로 1-1과 1-2가
     * 불일치해도 master 스냅샷이 품목 정본에서 벗어나지 않습니다. 신규 importer는 이 메서드를 호출하지 않고 생성 요청에 DFR만 전달합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param totRqmAmt 호환용 선언 총소요금액(저장에는 사용하지 않음)
     * @param mplAmt 호환용 선언 예정금액(저장에는 사용하지 않음)
     * @param dfrAmt 원화 지급금액
     * @throws IllegalArgumentException 사업관리번호에 해당하는 활성 사업이 없는 경우
     */
    @Transactional
    public void assignDeclaredAmounts(
            String abusMngNo, BigDecimal totRqmAmt, BigDecimal mplAmt, BigDecimal dfrAmt) {
        Bprojm project =
                projectRepository
                        .findByAbusMngNoAndDelYn(abusMngNo, "N")
                        .orElseThrow(
                                () -> new IllegalArgumentException("사업을 찾을 수 없습니다: " + abusMngNo));
        applyAmountSnapshot(project, dfrAmt);
    }

    /** 공백·null이 아닌 첫 값을 반환합니다. 둘 다 비었으면 null. */
    private static String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? (isBlank(fallback) ? null : fallback) : preferred;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
    /**
     * 담당자 표시명을 해석한다.
     *
     * <p>이 컬럼들은 사번 <b>또는 이름</b>을 담으므로({@code Bprojm} 주석) {@link UserNameResolver}에 판정을 맡긴다. 퇴사 등으로
     * 조회에 실패한 사번은 이름으로 노출하지 않고 {@code null}을 돌려주며, 그 경우 스냅샷은 기존 값을 유지한다(BE-63).
     *
     * @param storedValue 담당자 컬럼 저장값 — 사번 또는 이름
     * @return 표시명. 해석 실패 시 null
     */
    private String resolvePersonName(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        String lookedUp =
                cuserIRepository.findByEno(storedValue).map(user -> user.getUsrNm()).orElse(null);
        return UserNameResolver.resolve(storedValue, lookedUp);
    }

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
     * 정보화사업 삭제 (Soft Delete)
     *
     * <p>프로젝트와 연결된 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm}) 모두를 {@code
     * DEL_YN='Y'}로 논리 삭제합니다.
     *
     * <p>결재 상태 확인: "결재중" 또는 "결재완료" 상태인 경우 삭제가 불가합니다.
     *
     * @param prjMngNo 삭제할 프로젝트관리번호
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     * @throws IllegalStateException 결재중(또는 비관리자의 결재완료) 상태여서 삭제 불가한 경우
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

        // 결재 상태 확인 (BPROJM 테이블 코드로 신청서 연결 여부 조회)
        if (isBlockedByApproval(prjMngNo, project.getSno())) {
            throw new IllegalStateException(approvalBlockMessage("삭제"));
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
