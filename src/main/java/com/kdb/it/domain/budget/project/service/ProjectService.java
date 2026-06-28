package com.kdb.it.domain.budget.project.service;
import com.kdb.it.common.code.CommonCodeGroups;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.time.LocalDate;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보화사업(IT 프로젝트) 서비스
 *
 * <p>
 * 정보화사업(TPRMPP_BPROJM) 엔티티의 CRUD 및 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm})
 * 동기화
 * 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * 결재 연동:
 * </p>
 * <ul>
 * <li>수정/삭제 시 해당 프로젝트에 연결된 신청서(CAPPLA)의 결재 상태를 확인합니다</li>
 * <li>"결재중" 또는 "결재완료" 상태인 경우 수정/삭제가 불가합니다</li>
 * <li>원본 테이블 코드: {@code "BPROJM"}</li>
 * </ul>
 *
 * <p>
 * 품목(Bitemm) 동기화 로직 (수정 시):
 * </p>
 * <ol>
 * <li>요청의 {@code gclMngNo}가 있으면 기존 레코드 Soft Delete + 동일 관리번호·일련번호(+1)로 신규 레코드 저장</li>
 * <li>요청의 {@code gclMngNo}가 없으면 신규 항목 추가</li>
 * <li>요청에 없는 기존 항목은 Soft Delete</li>
 * </ol>
 *
 * <p>
 * Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다.
 * </p>
 *
 * <p>
 * {@code @Transactional(readOnly = true)}: 조회 메서드의 기본값.
 * 쓰기 메서드는 {@code @Transactional}로 오버라이드합니다.
 * </p>
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 기본 읽기 전용 트랜잭션
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    /** 정보화사업 데이터 접근 리포지토리 (TPRMPP_BPROJM) */
    private final ProjectRepository projectRepository;

    /** 신청서-원본 데이터 연결 리포지토리 (TPRMPP_CAPPLA): 결재 상태 확인용 */
    private final com.kdb.it.common.approval.repository.ApplicationMapRepository capplaRepository;

    /** 신청서 마스터 리포지토리 (TPRMPP_CAPPLM): 결재 상태 조회용 */
    private final com.kdb.it.common.approval.repository.ApplicationRepository capplmRepository;

    /** 품목 데이터 접근 리포지토리 (TPRMPP_BITEMM) */
    private final com.kdb.it.domain.budget.project.repository.ProjectItemRepository bitemmRepository;

    /** 조직(부점) 정보 리포지토리 (TPRMPP_CORGNI): 부서코드→부서명 조회용 */
    private final com.kdb.it.common.iam.repository.OrganizationRepository corgnIRepository;

    /** 사용자 정보 리포지토리 (TPRMPP_CUSERI): 사원번호→사용자명 조회용 */
    private final com.kdb.it.common.iam.repository.UserRepository cuserIRepository;

    /** 결재 정보 리포지토리 (TPRMPP_CDECIM): 결재선 목록 조회용 */
    private final com.kdb.it.common.approval.repository.ApproverRepository cdecimRepository;

    /** 공통코드 서비스: 예산 신청 기간 검증용 */
    private final com.kdb.it.common.code.service.CodeService codeService;

    /** 공통코드 리포지토리: 코드값→코드명 변환용 (TPRMPP_CCODEM) */
    private final CodeRepository ccodemRepository;

    /** 편성예산(BBUGTM) 리포지토리: 일괄 조회 시 prjMngNo별 DUP_BG 합계 조회용 */
    private final BbugtmRepository bbugtmRepository;

    /** 환율 표준 조회 헬퍼: 외화 품목 저장 전 Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7) */
    private final XcrLookupService xcrLookupService;

    /** 품목 기준 예산 합계 계산 서비스 */
    private final ProjectBudgetSummaryService projectBudgetSummaryService;

    /** 정보화사업관계(TPRMPP_BPROJA) 리포지토리: 프로젝트 대표상태(MAX IT_PTL_STS_TC) 계산용 */
    private final com.kdb.it.domain.budget.project.repository.BprojaRepository bprojaRepository;

    /** 정보화사업관계(TPRMPP_BPROJA) 동기화 서비스: 예산편성 단계 상태(작성중 01) 적재용 */
    private final BprojaSyncService bprojaSyncService;

    /** 공통코드 cId→cdva→코드명 맵 생성 공통 헬퍼 (Cost/Project 서비스 공용) */
    private final CodeNameMapBuilder codeNameMapBuilder;

    /**
     * 전체 정보화사업 목록 조회
     *
     * <p>
     * 삭제되지 않은({@code DEL_YN='N'}) 모든 프로젝트를 조회합니다.
     * 각 프로젝트에 연결된 최신 신청서 정보(신청관리번호, 결재상태)를 포함합니다.
     * </p>
     *
     * <p>
     * 목록 조회에서는 품목(Bitemm) 정보를 포함하지 않습니다 (성능 최적화).
     * </p>
     *
     * @return 전체 정보화사업 응답 DTO 목록 (신청서 정보 포함, 품목 제외)
     */
    public List<ProjectDto.Response> getProjectList() {
        List<Bprojm> projects = projectRepository.findAllByDelYn("N");
        List<ProjectDto.Response> responses = projects.stream()
                .map(ProjectDto.Response::fromEntity)
                .toList();
        enrichProjectListBatch(projects, responses);
        return responses;
    }

    /**
     * 검색 조건으로 정보화사업 목록 조회
     *
     * <p>
     * {@link ProjectDto.SearchCondition}의 조건이 모두 비어있으면 전체
     * 조회({@link #getProjectList()})와 동일합니다.
     * </p>
     *
     * <p>
     * {@code apfSts} 필터 처리:
     * </p>
     * <ul>
     * <li>{@code "none"}: 신청서가 없는 프로젝트만 조회 (CAPPLA 연결 없음)</li>
     * <li>그 외 값: 최신 신청서의 결재상태가 해당 값인 프로젝트만 조회</li>
     * <li>null/미입력: 결재상태 필터 없음</li>
     * </ul>
     *
     * <p>
     * 목록 조회에서는 품목(Bitemm) 정보를 포함하지 않습니다 (성능 최적화).
     * </p>
     *
     * @param condition 검색 조건 DTO (apfSts, bgYy, prjSts, prjTp, itDpm, svnDpm)
     * @return 조건에 맞는 정보화사업 응답 DTO 목록 (신청서 정보 포함, 품목 제외)
     */
    public List<ProjectDto.Response> searchProjectList(ProjectDto.SearchCondition condition) {
        List<Bprojm> projects = projectRepository.searchByCondition(condition);
        List<ProjectDto.Response> responses = projects.stream()
                .map(ProjectDto.Response::fromEntity)
                .toList();
        enrichProjectListBatch(projects, responses);
        return responses;
    }

    /**
     * 단건 정보화사업 상세 조회
     *
     * <p>
     * 프로젝트관리번호로 삭제되지 않은 프로젝트를 조회합니다.
     * 신청서 정보와 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm}) 목록을 포함합니다.
     * </p>
     *
     * @param prjMngNo 조회할 프로젝트관리번호
     * @return 정보화사업 상세 응답 DTO (신청서 정보, 품목 목록 포함)
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     */
    public ProjectDto.Response getProject(String prjMngNo) {
        // 프로젝트 조회 (삭제되지 않은 항목만)
        Bprojm project = projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        ProjectDto.Response response = ProjectDto.Response.fromEntity(project);
        // 최신 신청서 정보 조회 및 설정
        setApplicationInfo(response, prjMngNo, project.getSno());
        // 부서코드→부서명, 사원번호→사용자명 조회 및 설정
        setCodeNames(response);

        // BPROJA 단계 상태 조회(1회). 대표상태(MAX)와 단계별 코드 목록에 함께 사용.
        java.util.List<com.kdb.it.domain.budget.project.entity.Bproja> bprojaRows =
                bprojaRepository.findByAbusMngNoAndDelYn(prjMngNo, "N");
        // 프로젝트 대표상태(BPROJA 중 IT_PTL_STS_TC 최댓값). BPROJA 미적재면 null.
        response.setStsTc(representativeStatus(bprojaRows));
        // 단계별 카드용: 활성 BPROJA 상태코드 목록(진행 현황 섹션이 대역별로 판정).
        response.setBprojaStsCodes(bprojaRows.stream()
                .map(value -> value.getStsTc())
                .filter(java.util.Objects::nonNull)
                .toList());

        // 품목 정보 조회 및 설정 (삭제되지 않은 항목만)
        // ABUS_MNG_NO(프로젝트관리번호), SNO(프로젝트일련번호) 기준, DEL_YN='N'인 품목 조회
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms = bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo,
                project.getSno(), "N");

        // 품목 엔티티를 DTO로 변환하여 응답 객체에 설정
        List<ProjectDto.BitemmDto> itemDtos = bitemms.stream()
                .map(ProjectDto.BitemmDto::fromEntity)
                .toList();
        // 품목구분명(ioeCNm) IOE 코드 표시명 설정
        enrichItemIoeCNames(itemDtos);
        response.setItems(itemDtos);

        // 품목 기준 자본예산/일반관리비 합계 계산 및 설정 (이미 조회한 bitemms 재활용)
        projectBudgetSummaryService.applyBudgetSummary(response, bitemms);

        return response;
    }

    /**
     * 신규 정보화사업 생성
     *
     * <p>
     * 프로젝트관리번호({@code PRJ_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     * 제공된 경우 중복 여부를 확인합니다.
     * </p>
     *
     * <p>
     * 자동 채번 로직:
     * </p>
     * <ul>
     * <li>요청 객체에 사업연도({@code bgYy})가 없으면 현재 연도를 사용합니다.</li>
     * <li>데이터베이스 시퀀스({@code SQ_PRJMNGNO})에서 다음 값을 가져옵니다.</li>
     * <li>관리번호 자동 생성 형식: {@code PRJ-{bgYy}-{seq:04d}}
     * (예: {@code PRJ-2026-0001})</li>
     * </ul>
     * <p>예: {@code PRJ-2026-0001}</p>
     *
     * @param request 정보화사업 생성 요청 DTO (프로젝트명, 예산, 기간, 담당자 등)
     * @return 생성된 프로젝트관리번호
     * @throws IllegalArgumentException 제공된 관리번호가 이미 존재하는 경우
     */
    @Transactional
    public String createProject(ProjectDto.CreateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

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

        // 엔티티 생성 및 저장
        Bprojm project = request.toEntity();
        projectRepository.save(project);

        // ===== 품목(Bitemm) 저장 =====
        // 신규 등록 시 요청에 포함된 모든 품목은 신규 추가 대상
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            int gclSno = 0; // 품목일련번호 (1부터 시작)
            for (ProjectDto.BitemmDto itemDto : request.getItems()) {
                Long gclSeq = bitemmRepository.getNextSequenceValue(); // Oracle 시퀀스 채번
                String gclMngNo = String.format("GCL-%s-%04d", java.time.LocalDate.now().getYear(), gclSeq);

                // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));

                // 외화 재계산: gclAmt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                        itemDto.getFcAmt(), itemDto.getAmt(), itemDto.getCurC(), itemDto.getXcr());

                com.kdb.it.domain.budget.project.entity.Bitemm newItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                        .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                        .sno(++gclSno) // 품목일련번호
                        .abusMngNo(project.getAbusMngNo()) // 프로젝트관리번호
                        .fntTbCrySno(project.getSno()) // 프로젝트순번
                        .ioeC(itemDto.getIoeC()) // 품목구분
                        .gclNm(itemDto.getGclNm()) // 품목명
                        .qty(itemDto.getQty()) // 품목수량
                        .curC(itemDto.getCurC()) // 통화
                        .xcr(itemDto.getXcr()) // 환율
                        .xcrBseDt(DateFormatUtil.toYmd8(itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                        .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                        .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                        .dfrCleC(itemDto.getDfrCleC()) // 지급주기
                        .sectSysUtzYn(itemDto.getSectSysUtzYn() == null ? "N" : itemDto.getSectSysUtzYn()) // 정보보호여부
                        .itrInfrYn(itemDto.getItrInfrYn() == null ? "N" : itemDto.getItrInfrYn()) // 통합인프라여부
                        .lstYn("Y") // 최종여부
                        .amt(reconciled[0]) // 품목금액 (서버 재계산)
                        .fcAmt(reconciled[1]) // 외화금액 (외화 행에서만 유효)
                        .mplAmt(clampMpl(itemDto.getMplAmt(), reconciled[0])) // 예정금액 (0 ≤ mplAmt ≤ amt)
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
     * <p>
     * 프로젝트 기본 정보를 수정하고, 품목(Bitemm) 목록을 동기화합니다.
     * </p>
     *
     * <p>
     * 결재 상태 확인: "결재중" 또는 "결재완료" 상태인 경우 수정이 불가합니다.
     * </p>
     *
     * <p>
     * 품목 동기화 로직:
     * </p>
     * <ol>
     * <li>기존 품목 목록 조회 (DEL_YN='N')</li>
     * <li>요청 품목 처리:
     * <ul>
     * <li>{@code gclMngNo}가 있는 경우: 기존 레코드 Soft Delete 후 동일 관리번호 + 일련번호(+1)로 신규 레코드 저장</li>
     * <li>{@code gclMngNo}가 없는 경우: 신규 항목으로 시퀀스 채번 후 추가</li>
     * </ul>
     * </li>
     * <li>요청에 없는 기존 항목: Soft Delete ({@code DEL_YN='Y'})</li>
     * </ol>
     *
     * @param prjMngNo 수정할 프로젝트관리번호
     * @param request  수정 요청 DTO (변경할 필드들, 품목 목록)
     * @return 수정된 프로젝트관리번호
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     * @throws IllegalStateException    결재중/결재완료 상태여서 수정 불가한 경우
     */
    @Transactional
    public String updateProject(String prjMngNo, ProjectDto.UpdateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        // 프로젝트 조회 (삭제되지 않은 항목만)
        Bprojm project = projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        validateModifyPermission(project.getFstEnrUsid(), project.getSvnDpmC());

        // 결재 상태 확인 (BPROJM 테이블 코드로 신청서 연결 여부 조회)
        // 결재중 또는 결재완료 상태인 경우 수정 불가
        boolean isProcessingOrApproved = capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                "BPROJM", prjMngNo, project.getSno(), java.util.List.of(
                        com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code(),
                        com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 수정할 수 없습니다.");
        }

        // Rich Text 필드 XSS 새니타이징 (서버 측 방어)
        request.setAbusCone(HtmlSanitizer.sanitize(request.getAbusCone()));
        request.setAbusRngCone(HtmlSanitizer.sanitize(request.getAbusRngCone()));

        // 프로젝트 기본 정보 수정 (JPA Dirty Checking으로 자동 반영)
        project.update(new Bprojm.UpdateCommand(
                request.getAbusNm(), request.getBzTpC(), request.getSvnDpmC(), request.getDvmDpmC(),
                request.getSttDtm(), request.getEndDtm(),
                request.getUsid(), request.getDvmUsid(), request.getTlrUsid(), request.getDvmTlrUsid(),
                request.getEdrtTc(), request.getAbusCone(), request.getCpnSafCone(), request.getAbusNcsCone(),
                request.getDgogPpoCone(), request.getPlmDes(), request.getAbusRngCone(), request.getMnPrgCone(), request.getHrfPlnCone(),
                request.getBzDttNm(), request.getSklTpTc(), request.getCstTpTc(), request.getDplYn(),
                DateFormatUtil.toYmd8(request.getFlfFsgDt()), request.getRprStsTc(), request.getExePttYn(),
                request.getBseYy(), request.getPrlmHrkOgzCCone(),
                request.getOdnYn(), request.getAbusTc(), request.getCncdRfrNo()));

        // ===== 품목 정보 동기화 (CUD) =====
        if (request.getItems() != null) {
            // 1. 기존 품목 조회 (DEL_YN='N')
            List<com.kdb.it.domain.budget.project.entity.Bitemm> existingItems = bitemmRepository
                    .findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, project.getSno(), "N");

            // 처리된 품목 관리번호 추적 (삭제 대상 식별용)
            java.util.Set<String> processedGclMngNos = new java.util.HashSet<>();
            // 현재 최대 SNO 계산 (신규 추가 시 MAX+1로 설정)
            int maxGclSno = existingItems.stream()
                    .mapToInt(value -> value.getSno())
                    .max().orElse(0);

            // 2. 요청 품목 처리 (수정 또는 신규 추가)
            for (ProjectDto.BitemmDto itemDto : request.getItems()) {
                if (itemDto.getGclMngNo() != null && !itemDto.getGclMngNo().isEmpty()) {
                    // === 기존 항목 수정 ===
                    // gclMngNo로 현재 활성(DEL_YN='N') 항목 찾기 (existingItems는 이미 DEL_YN='N' 필터됨)
                    com.kdb.it.domain.budget.project.entity.Bitemm existingItem = existingItems.stream()
                            .filter(item -> item.getGclMngNo().equals(itemDto.getGclMngNo()))
                            .findFirst()
                            .orElse(null);

                    if (existingItem != null) {
                        // 변경된 필드가 있을 때만 버저닝 (변경 없으면 D/C 로그 생성 생략)
                        if (isItemChanged(existingItem, itemDto)) {
                            // 기존 레코드 Soft Delete (이전 버전으로 처리)
                            existingItem.delete();
                            // 기존 관리번호 유지 + 일련번호 1 증가하여 신규 레코드 저장
                            int newGclSno = existingItem.getSno() + 1;
                            // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                            itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));
                            // 외화 재계산: amt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                            BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                                    itemDto.getFcAmt(), itemDto.getAmt(), itemDto.getCurC(), itemDto.getXcr());
                            com.kdb.it.domain.budget.project.entity.Bitemm updatedItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                    .gclMngNo(existingItem.getGclMngNo()) // 품목관리번호 유지 (기존 번호)
                                    .sno(newGclSno) // 품목일련번호 1 증가
                                    .abusMngNo(existingItem.getAbusMngNo()) // 프로젝트관리번호 유지
                                    .fntTbCrySno(existingItem.getFntTbCrySno()) // 프로젝트순번 유지
                                    .ioeC(itemDto.getIoeC()) // 품목구분
                                    .gclNm(itemDto.getGclNm()) // 품목명
                                    .qty(itemDto.getQty()) // 품목수량
                                    .curC(itemDto.getCurC()) // 통화
                                    .xcr(itemDto.getXcr()) // 환율
                                    .xcrBseDt(DateFormatUtil.toYmd8(itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                                    .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                                    .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                                    .dfrCleC(itemDto.getDfrCleC()) // 지급주기
                                    .sectSysUtzYn(defaultYn(itemDto.getSectSysUtzYn()))
                                    .itrInfrYn(defaultYn(itemDto.getItrInfrYn()))
                                    .lstYn("Y") // 최종여부
                                    .amt(reconciled[0]) // 품목금액 (서버 재계산)
                                    .fcAmt(reconciled[1]) // 외화금액 (외화 행에서만 유효)
                                    .mplAmt(clampMpl(itemDto.getMplAmt(), reconciled[0])) // 예정금액 (0 ≤ mplAmt ≤ amt)
                                    .build();
                            bitemmRepository.save(updatedItem);
                            maxGclSno = Math.max(maxGclSno, newGclSno);
                        }
                        processedGclMngNos.add(existingItem.getGclMngNo()); // 변경 여부와 무관하게 처리 완료 표시
                    }
                } else {
                    // === 신규 품목 추가 ===
                    // Oracle 시퀀스로 품목관리번호 채번
                    Long gclSeq = bitemmRepository.getNextSequenceValue();
                    String gclMngNo = String.format("GCL-%s-%04d", java.time.LocalDate.now().getYear(), gclSeq);

                    // XCR 표준 조회: 클라 xcr 무시, Ccodem 단일 원천으로 덮어쓰기 (CONTEXT.md 결정 E / R3.7)
                    itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));

                    // 외화 재계산: amt = fcAmt × xcr 정규화 (CONTEXT.md 결정 C)
                    BigDecimal[] reconciled = BudgetAmountCalculator.reconcileAmount(
                            itemDto.getFcAmt(), itemDto.getAmt(), itemDto.getCurC(), itemDto.getXcr());

                    com.kdb.it.domain.budget.project.entity.Bitemm newItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                            .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                            .sno(++maxGclSno) // 품목일련번호 (MAX+1)
                            .abusMngNo(prjMngNo) // 프로젝트관리번호
                            .fntTbCrySno(project.getSno()) // 프로젝트순번
                            .ioeC(itemDto.getIoeC()) // 품목구분
                            .gclNm(itemDto.getGclNm()) // 품목명
                            .qty(itemDto.getQty()) // 품목수량
                            .curC(itemDto.getCurC()) // 통화
                            .xcr(itemDto.getXcr()) // 환율
                            .xcrBseDt(DateFormatUtil.toYmd8(itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                            .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                            .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                            .dfrCleC(itemDto.getDfrCleC()) // 지급주기
                            .sectSysUtzYn(itemDto.getSectSysUtzYn() == null ? "N" : itemDto.getSectSysUtzYn()) // 정보보호여부
                            .itrInfrYn(itemDto.getItrInfrYn() == null ? "N" : itemDto.getItrInfrYn()) // 통합인프라여부
                            .lstYn("Y") // 최종여부
                            .amt(reconciled[0]) // 품목금액 (서버 재계산)
                            .fcAmt(reconciled[1]) // 외화금액 (외화 행에서만 유효)
                            .mplAmt(clampMpl(itemDto.getMplAmt(), reconciled[0])) // 예정금액 (0 ≤ mplAmt ≤ amt)
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
     * <p>기존 엔티티와 요청 DTO의 업무 필드를 비교하여, 하나라도 다르면 {@code true}를 반환합니다.</p>
     * <p>변경이 없는 품목은 버저닝(D→C 로그)을 건너뜁니다.</p>
     * <p>BigDecimal 필드(xcr, gclQty, gclAmt)는 scale 무관한 수치 비교를 위해 compareTo를 사용합니다.</p>
     *
     * @param existing 현재 활성 품목 엔티티 (DEL_YN='N')
     * @param dto      클라이언트로부터 전달된 수정 요청 DTO
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
     * 도입시기를 DB 컬럼 형식(YYYYMM, 6자)으로 변환.
     * 프론트에서 "YYYY-MM-DD" 또는 "YYYY-MM" 형식이 올 수 있으므로
     * 하이픈을 제거한 뒤 앞 6자만 사용한다. 빈값/null은 그대로 반환.
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
     * @param amt    품목금액(서버 재계산값, null이면 상한 미적용)
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
     * <p>
     * 프로젝트와 연결된 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm}) 모두를
     * {@code DEL_YN='Y'}로 논리 삭제합니다.
     * </p>
     *
     * <p>
     * 결재 상태 확인: "결재중" 또는 "결재완료" 상태인 경우 삭제가 불가합니다.
     * </p>
     *
     * @param prjMngNo 삭제할 프로젝트관리번호
     * @throws IllegalArgumentException 해당 관리번호의 프로젝트가 없는 경우
     * @throws IllegalStateException    결재중/결재완료 상태여서 삭제 불가한 경우
     */
    @Transactional
    public void deleteProject(String prjMngNo) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        // 프로젝트 조회 (삭제되지 않은 항목만)
        Bprojm project = projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        validateModifyPermission(project.getFstEnrUsid(), project.getSvnDpmC());

        // 결재 상태 확인 (결재중/결재완료이면 삭제 불가)
        boolean isProcessingOrApproved = capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                "BPROJM", prjMngNo, project.getSno(), java.util.List.of(
                        com.kdb.it.common.approval.domain.ApprovalStatus.IN_PROGRESS.code(),
                        com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 삭제할 수 없습니다.");
        }

        // 1. 프로젝트 Soft Delete (DEL_YN='Y')
        project.delete();

        // 2. 관련 품목 전체 Soft Delete (DEL_YN 무관하게 모든 품목 조회 후 삭제)
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms = bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo,
                project.getSno());
        for (com.kdb.it.domain.budget.project.entity.Bitemm bitemm : bitemms) {
            bitemm.delete(); // BaseEntity.delete() 호출 (DEL_YN='Y')
        }
    }

    /**
     * 정보화사업 일괄 조회
     *
     * <p>
     * 여러 프로젝트관리번호를 한 번에 조회합니다.
     * 존재하지 않는 항목은 결과에서 제외하되, 누락된 프로젝트관리번호를
     * {@code failedIds}로 호출자에게 함께 노출합니다 (부분 성공).
     * </p>
     *
     * @param request 일괄 조회 요청 DTO (프로젝트관리번호 목록)
     * @return 조회 성공 항목({@code items})과 미존재 프로젝트관리번호({@code failedIds})를 함께 담은 결과 DTO
     */
    public ProjectDto.BulkResponse getProjectsByIds(ProjectDto.BulkGetRequest request) {
        List<ProjectDto.Response> responses = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (String prjMngNo : request.getPrjMngNos()) {
            try {
                responses.add(getProject(prjMngNo)); // 개별 상세 조회 (품목 포함)
            } catch (IllegalArgumentException e) {
                failedIds.add(prjMngNo); // 미존재 항목은 failedIds로 수집
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("bulk-get 누락: type=project, failedIds={}", failedIds);
        }

        // TPRMPP_BBUGTM 기준 편성예산(DUP_BG) 일괄 조회 후 각 응답에 설정
        String bgYy = request.getBseYy();
        if (bgYy != null && !bgYy.isBlank() && !responses.isEmpty()) {
            List<String> prjMngNos = responses.stream()
                    .map(value -> value.getAbusMngNo())
                    .toList();
            Map<String, BigDecimal> dupBgMap = bbugtmRepository.sumDupBgByPrjMngNos(prjMngNos, bgYy);

            // 자본예산/일반관리비 편성예산 분류 (마이그레이션 후 cId=CommonCodeGroups.IOE, cTp 필드로 분류)
            // 자본예산 비목의 cTp는 IOE_HW(기계장치)/IOE_DVC(개발비)/IOE_SW(무형자산) 계열이다.
            // (구코드 IOE_CPIT만 보던 버그로 assetTypes가 비어 자본 편성예산이 항상 0이 되던 문제 수정.
            //  BudgetWorkService.CAPITAL_CTPS와 동일 집합으로 정렬)
            Set<String> capitalCTps = java.util.Set.of("IOE_DVC", "IOE_HW", "IOE_SW", "IOE_CPIT");
            List<com.kdb.it.common.code.entity.Ccodem> allIoeForBugt =
                    codeService.findCodeEntitiesByCIdWithoutCache(CommonCodeGroups.IOE);
            Set<String> assetTypes = allIoeForBugt.stream()
                    .filter(c -> capitalCTps.contains(c.getCTp()))
                    .map(c -> c.getCdva())
                    .collect(Collectors.toSet());
            Set<String> costTypes = allIoeForBugt.stream()
                    .filter(c -> java.util.Set.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE").contains(c.getCTp()))
                    .map(c -> c.getCdva())
                    .collect(Collectors.toSet());
            Map<String, BigDecimal> assetDupBgMap = bbugtmRepository.sumAssetDupBgByPrjMngNos(prjMngNos, bgYy, assetTypes);
            Map<String, BigDecimal> costDupBgMap = bbugtmRepository.sumCostDupBgByPrjMngNos(prjMngNos, bgYy, costTypes);

            responses.forEach(r -> {
                r.setDupBgAmt(dupBgMap.getOrDefault(r.getAbusMngNo(), BigDecimal.ZERO));
                r.setAssetDupBg(assetDupBgMap.getOrDefault(r.getAbusMngNo(), BigDecimal.ZERO));
                r.setCostDupBg(costDupBgMap.getOrDefault(r.getAbusMngNo(), BigDecimal.ZERO));
            });
        }
        return new ProjectDto.BulkResponse(responses, failedIds);
    }

    /**
     * 프로젝트 목록 응답에 신청서 정보·코드명·예산 합계를 배치로 주입 (N+1 방지)
     *
     * <p>
     * 개별 조회(N×8 쿼리) 대신 배치 조회(5 쿼리)로 처리합니다:
     * CAPPLA 1회, CAPPLM 1회, CDECIM 1회, CORGNI 1회, CUSERI 1회.
     * </p>
     */
    private void enrichProjectListBatch(List<Bprojm> projects, List<ProjectDto.Response> responses) {
        if (projects.isEmpty()) return;

        // --- 1. CAPPLA 배치 조회 (BPROJM에 연결된 모든 신청서) ---
        List<String> prjMngNos = projects.stream().map(value -> value.getAbusMngNo()).toList();
        List<Cappla> allCapplas = capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc("BPROJM", prjMngNos);

        // prjMngNo → 최신 Cappla (이미 DESC 정렬이므로 첫 번째가 최신)
        Map<String, Cappla> latestCappla = new java.util.LinkedHashMap<>();
        for (Cappla c : allCapplas) {
            latestCappla.putIfAbsent(c.getPkColNm(), c);
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

        // --- 4. 부서코드·사원번호·공통코드 수집 ---
        Set<String> orgCodes = new java.util.HashSet<>();
        Set<String> userEnos = new java.util.HashSet<>();
        Set<String> rprStsCdvas = new java.util.HashSet<>();
        Set<String> prjPulPttCdvas = new java.util.HashSet<>();
        Set<String> pulDttCdvas = new java.util.HashSet<>();
        for (ProjectDto.Response r : responses) {
            if (r.getDvmDpmC() != null && !r.getDvmDpmC().isEmpty()) orgCodes.add(r.getDvmDpmC());
            if (r.getSvnDpmC() != null && !r.getSvnDpmC().isEmpty()) orgCodes.add(r.getSvnDpmC());
            if (r.getDvmUsid() != null && !r.getDvmUsid().isEmpty()) userEnos.add(r.getDvmUsid());
            if (r.getTlrUsid() != null && !r.getTlrUsid().isEmpty()) userEnos.add(r.getTlrUsid());
            if (r.getUsid() != null && !r.getUsid().isEmpty()) userEnos.add(r.getUsid());
            if (r.getDvmTlrUsid() != null && !r.getDvmTlrUsid().isEmpty()) userEnos.add(r.getDvmTlrUsid());
            // 사업유형/업무구분/기술분야/고객유형은 컬럼에 코드값명을 직접 저장 → 별도 코드 해석 불필요
            if (r.getRprStsTc() != null && !r.getRprStsTc().isEmpty()) rprStsCdvas.add(r.getRprStsTc());
            if (r.getExePttYn() != null && !r.getExePttYn().isEmpty()) prjPulPttCdvas.add(r.getExePttYn());
            if (r.getAbusTc() != null && !r.getAbusTc().isEmpty()) pulDttCdvas.add(r.getAbusTc());
        }

        // --- 5. 부서명·사용자명·공통코드명 배치 조회 ---
        Map<String, String> orgNameMap = corgnIRepository.findAllById(orgCodes).stream()
                .collect(Collectors.toMap(value -> value.getPrlmOgzCCone(), value -> value.getBbrNm()));
        Map<String, String> userNameMap = cuserIRepository.findAllById(userEnos).stream()
                .collect(Collectors.toMap(value -> value.getEno(), value -> value.getUsrNm()));
        Map<String, String> rprStsNameMap = rprStsCdvas.isEmpty() ? Map.of() : codeNameMapBuilder.build(CommonCodeGroups.REPORT_STS, rprStsCdvas);
        Map<String, String> prjPulPttNameMap = prjPulPttCdvas.isEmpty() ? Map.of() : codeNameMapBuilder.build(CommonCodeGroups.EXE_POSSIBLE, prjPulPttCdvas);
        Map<String, String> pulDttNameMap = pulDttCdvas.isEmpty() ? Map.of() : codeNameMapBuilder.build(CommonCodeGroups.ABUS, pulDttCdvas);

        // 목록 파생 합산: 대상 프로젝트들의 활성 품목 1회 배치 조회 후 프로젝트별 그룹핑
        Map<String, List<com.kdb.it.domain.budget.project.entity.Bitemm>> itemsByPrj =
                bitemmRepository.findByAbusMngNoInAndDelYn(prjMngNos, "N").stream()
                        .collect(Collectors.groupingBy(
                                value -> value.getAbusMngNo()));

        // 대표상태 배치 조회: 대상 프로젝트들의 BPROJA를 1회 조회 후 프로젝트별 MAX(IT_PTL_STS_TC) 계산.
        Map<String, String> repStatusByPrj = bprojaRepository
                .findByAbusMngNoInAndDelYn(prjMngNos, "N").stream()
                .collect(Collectors.groupingBy(
                        value -> value.getAbusMngNo(),
                        Collectors.collectingAndThen(Collectors.toList(), this::representativeStatus)));

        // --- 6. 응답 DTO에 일괄 주입 ---
        for (int i = 0; i < projects.size(); i++) {
            Bprojm project = projects.get(i);
            ProjectDto.Response response = responses.get(i);

            Cappla cappla = latestCappla.get(project.getAbusMngNo());
            if (cappla != null) {
                response.setApfMngNo(cappla.getApfDcmNo());
                Capplm capplm = capplmMap.get(cappla.getApfDcmNo());
                if (capplm != null) {
                    response.setApfSts(capplm.getApfPrgStsC() == null ? null
                            : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getApfPrgStsC()).label());
                    List<Cdecim> decisions = decisionMap.getOrDefault(cappla.getApfDcmNo(), List.of());
                    response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                }
            }

            if (response.getDvmDpmC() != null) response.setDvmDpmCNm(orgNameMap.get(response.getDvmDpmC()));
            if (response.getSvnDpmC() != null) response.setSvnDpmCNm(orgNameMap.get(response.getSvnDpmC()));
            if (response.getDvmUsid() != null) response.setDvmUsidNm(userNameMap.get(response.getDvmUsid()));
            if (response.getTlrUsid() != null) response.setTlrUsidNm(userNameMap.get(response.getTlrUsid()));
            if (response.getUsid() != null) response.setUsidNm(userNameMap.get(response.getUsid()));
            if (response.getDvmTlrUsid() != null) response.setDvmTlrUsidNm(userNameMap.get(response.getDvmTlrUsid()));
            // 사업유형/업무구분/기술분야/고객유형: 컬럼값이 곧 코드값명 → 원본값 그대로 사용
            response.setBzTpCNm(response.getBzTpC());
            response.setBzDttNmNm(response.getBzDttNm());
            response.setSklTpTcNm(response.getSklTpTc());
            response.setCstTpTcNm(response.getCstTpTc());
            if (response.getRprStsTc() != null) response.setRprStsTcNm(rprStsNameMap.get(response.getRprStsTc()));
            if (response.getExePttYn() != null) response.setExePttYnNm(prjPulPttNameMap.get(response.getExePttYn()));
            if (response.getAbusTc() != null) response.setAbusTcNm(pulDttNameMap.get(response.getAbusTc()));

            // 프로젝트 대표상태 주입(없으면 null)
            response.setStsTc(repStatusByPrj.get(project.getAbusMngNo()));

            setBudgetSummary(response, project.getAbusMngNo(), project.getSno());

            // 파생 예산 3종(totRqmAmt/mplCpitAmt/mplMngcAmt) 주입
            projectBudgetSummaryService.applyBudgetSummary(
                    response,
                    itemsByPrj.getOrDefault(project.getAbusMngNo(), java.util.List.of()));
        }
    }

    /**
     * 프로젝트 응답 DTO에 신청서 정보 설정 (내부 헬퍼 메서드)
     *
     * <p>
     * 프로젝트관리번호와 순번으로 연결된 신청서(CAPPLA) 중 가장 최신 신청서를 조회하여
     * 응답 DTO에 신청관리번호({@code apfMngNo})와 결재상태({@code apfSts})를 설정합니다.
     * </p>
     *
     * <p>
     * 조회 기준:
     * </p>
     * <ul>
     * <li>{@code ORC_TB_CD = 'BPROJM'}: 프로젝트 원본 테이블 코드</li>
     * <li>{@code ORC_PK_VL = prjMngNo}: 프로젝트관리번호</li>
     * <li>{@code ORC_SNO_VL = prjSno}: 프로젝트순번</li>
     * <li>최신순 정렬 ({@code APF_REL_SNO DESC})</li>
     * </ul>
     *
     * @param response 신청서 정보를 설정할 응답 DTO
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno   프로젝트순번
     */
    private void setApplicationInfo(ProjectDto.Response response, String prjMngNo, Integer prjSno) {
        // BPROJM 테이블 코드와 프로젝트 관리번호/순번으로 연결된 신청서 목록 조회 (최신순)
        List<com.kdb.it.common.approval.entity.Cappla> capplas = capplaRepository
                .findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc("BPROJM", prjMngNo, (Integer) prjSno);

        if (!capplas.isEmpty()) {
            com.kdb.it.common.approval.entity.Cappla cappla = capplas.get(0); // 가장 최신 신청서
            response.setApfMngNo(cappla.getApfDcmNo()); // 신청관리번호 설정

            // 신청서 마스터에서 결재상태 및 상세 정보 조회
            capplmRepository.findById(cappla.getApfDcmNo())
                    .ifPresent(capplm -> {
                        response.setApfSts(capplm.getApfPrgStsC() == null ? null
                            : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(capplm.getApfPrgStsC()).label()); // 결재상태 설정 (코드→라벨)

                        // 결재자 목록 조회 (결재순서 오름차순)
                        List<com.kdb.it.common.approval.entity.Cdecim> decisions = cdecimRepository
                                .findByDcdMngNoOrderByDcrSqnSnoAsc(cappla.getApfDcmNo());

                        // ApplicationInfoDto 생성 및 설정
                        response.setApplicationInfo(
                                com.kdb.it.common.approval.dto.ApplicationInfoDto.fromEntities(capplm, decisions));
                    });
        }
    }

    /**
     * 프로젝트 응답 DTO에 부서명/사용자명 설정 (내부 헬퍼 메서드)
     *
     * <p>
     * 부서코드(itDpm, svnDpm)로 TPRMPP_CORGNI에서 부서명(BBR_NM)을 조회하고,
     * 사원번호(itDpmCgpr, itDpmTlr, svnDpmCgpr, svnDpmTlr)로
     * TPRMPP_CUSERI에서 사용자명(USR_NM)을 조회하여 응답 DTO에 설정합니다.
     * </p>
     *
     * <p>
     * 코드 값이 null 또는 빈 문자열이면 조회를 건너뛰고,
     * 조회 결과가 없으면 해당 이름 필드는 null로 유지됩니다.
     * </p>
     *
     * @param response 코드명을 설정할 응답 DTO
     */
    private void setCodeNames(ProjectDto.Response response) {
        // === 부서코드 → 부서명 변환 (TPRMPP_CORGNI) ===

        // IT부서코드 → IT부서명
        if (response.getDvmDpmC() != null && !response.getDvmDpmC().isEmpty()) {
            corgnIRepository.findById(response.getDvmDpmC())
                    .ifPresent(org -> response.setDvmDpmCNm(org.getBbrNm()));
        }

        // 주관부서코드 → 주관부서명
        if (response.getSvnDpmC() != null && !response.getSvnDpmC().isEmpty()) {
            corgnIRepository.findById(response.getSvnDpmC())
                    .ifPresent(org -> response.setSvnDpmCNm(org.getBbrNm()));
        }

        // === 사원번호 → 사용자명 변환 (TPRMPP_CUSERI) ===

        // IT담당자 사번 → IT담당자명
        if (response.getDvmUsid() != null && !response.getDvmUsid().isEmpty()) {
            cuserIRepository.findById(response.getDvmUsid())
                    .ifPresent(user -> response.setDvmUsidNm(user.getUsrNm()));
        }

        // 주관부서담당팀장 사번 → 주관부서담당팀장명
        if (response.getTlrUsid() != null && !response.getTlrUsid().isEmpty()) {
            cuserIRepository.findById(response.getTlrUsid())
                    .ifPresent(user -> response.setTlrUsidNm(user.getUsrNm()));
        }

        // 주관부서담당자 사번 → 주관부서담당자명
        if (response.getUsid() != null && !response.getUsid().isEmpty()) {
            cuserIRepository.findById(response.getUsid())
                    .ifPresent(user -> response.setUsidNm(user.getUsrNm()));
        }

        // IT부서담당팀장 사번 → IT부서담당팀장명
        if (response.getDvmTlrUsid() != null && !response.getDvmTlrUsid().isEmpty()) {
            cuserIRepository.findById(response.getDvmTlrUsid())
                    .ifPresent(user -> response.setDvmTlrUsidNm(user.getUsrNm()));
        }

        // === 공통코드 코드값 → 코드명 변환 (TPRMPP_CCODEM) ===

        // 사업유형/업무구분/기술분야/고객유형: 컬럼에 코드값명을 직접 저장 → 원본값을 그대로 노출
        response.setBzTpCNm(response.getBzTpC());
        response.setBzDttNmNm(response.getBzDttNm());
        response.setSklTpTcNm(response.getSklTpTc());
        response.setCstTpTcNm(response.getCstTpTc());
        if (response.getRprStsTc() != null && !response.getRprStsTc().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.REPORT_STS, response.getRprStsTc(), null)
                    .ifPresent(code -> response.setRprStsTcNm(code.getCdvaNm()));
        }
        if (response.getExePttYn() != null && !response.getExePttYn().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.EXE_POSSIBLE, response.getExePttYn(), null)
                    .ifPresent(code -> response.setExePttYnNm(code.getCdvaNm()));
        }
        if (response.getAbusTc() != null && !response.getAbusTc().isEmpty()) {
            ccodemRepository.findByCIdAndCdvaWithValidDate(CommonCodeGroups.ABUS, response.getAbusTc(), null)
                    .ifPresent(code -> response.setAbusTcNm(code.getCdvaNm()));
        }
    }

    /**
     * BPROJA 관계 행 목록에서 대표상태(IT_PTL_STS_TC 최댓값)를 계산한다.
     *
     * <p>2자리 zero-pad 코드이므로 문자열 사전식 비교 = 숫자 비교. null 상태는 무시.
     * 행이 없거나 모두 null이면 null 반환.</p>
     *
     * @param rows 단일 프로젝트의 미삭제 BPROJA 행 목록
     * @return 대표상태 코드 또는 null
     */
    private String representativeStatus(java.util.List<com.kdb.it.domain.budget.project.entity.Bproja> rows) {
        return rows.stream()
                .map(value -> value.getStsTc())
                .filter(s -> s != null && !s.isEmpty())
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * 프로젝트 응답 DTO에 자본예산/일반관리비 합계 설정 (품목 조회 포함)
     *
     * <p>
     * 프로젝트의 품목(Bitemm)을 조회하여 gclDtt(품목구분) 기준으로
     * 자본예산과 일반관리비를 계산합니다.
     * </p>
     *
     * @param response 예산 합계를 설정할 응답 DTO
     * @param prjMngNo 프로젝트관리번호
     * @param prjSno   프로젝트순번
     */
    private void setBudgetSummary(ProjectDto.Response response, String prjMngNo, Integer prjSno) {
        // 삭제되지 않은 품목 목록 조회
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms = bitemmRepository
                .findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, (Integer) prjSno, "N");
        // 목록 조회 시 items 가 아직 설정되지 않은 경우 DTO 변환 및 enrichment 수행
        if (response.getItems() == null) {
            List<ProjectDto.BitemmDto> itemDtos = bitemms.stream()
                    .map(ProjectDto.BitemmDto::fromEntity)
                    .toList();
            enrichItemIoeCNames(itemDtos);
            response.setItems(itemDtos);
        }
        projectBudgetSummaryService.applyBudgetSummary(response, bitemms);
    }

    /**
     * IOE 계층 코드(구형 C_ID 형식) 집합을 받아 표시명 맵을 배치 조회합니다.
     *
     * <p>
     * ioeC 값은 구형 IOE C_ID 형식 (예: "IOE-351-1100-1")입니다.
     * 새 CCODEM 에서는 하이픈을 언더스코어로 치환한 뒤 마지막 세그먼트를 CDVA 로,
     * 나머지 앞부분을 C_ID 로 사용합니다.
     * </p>
     *
     * @param ioeCodes 조회할 품목구분 코드 집합 (IOE 계층 코드)
     * @return 원본 코드값 → 표시명 맵
     */
    private Map<String, String> buildIoeCNameMap(Set<String> ioeCodes) {
        Map<String, String> result = new java.util.HashMap<>();
        // C_ID 별로 그룹화하여 배치 조회
        Map<String, List<String>> byCId = new java.util.HashMap<>();
        for (String ioeC : ioeCodes) {
            if (ioeC == null) continue;
            String normalized = ioeC.replace('-', '_');
            int lastUnderscore = normalized.lastIndexOf('_');
            String cId = lastUnderscore >= 0 ? normalized.substring(0, lastUnderscore) : CommonCodeGroups.IOE;
            byCId.computeIfAbsent(cId, k -> new java.util.ArrayList<>()).add(ioeC);
        }
        for (Map.Entry<String, List<String>> entry : byCId.entrySet()) {
            String cId = entry.getKey();
            List<Ccodem> codes = ccodemRepository.findByCIdWithValidDate(cId, null);
            for (Ccodem code : codes) {
                for (String orig : entry.getValue()) {
                    String normalized = orig.replace('-', '_');
                    int lastUnderscore = normalized.lastIndexOf('_');
                    String cdva = lastUnderscore >= 0 ? normalized.substring(lastUnderscore + 1) : normalized;
                    if (cdva.equals(code.getCdva())) {
                        String displayName = code.getCdvaNm() != null ? code.getCdvaNm()
                                : (code.getCdvaDtl() != null ? code.getCdvaDtl() : code.getCdvaNm());
                        if (displayName != null) {
                            String[] parts = displayName.split(" - ");
                            result.put(orig, parts[parts.length - 1].trim());
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 품목 DTO 목록의 ioeCNm(품목구분명)을 일괄 설정합니다.
     *
     * <p>
     * 품목구분 코드(ioeC)가 IOE 계층 코드인 경우 배치 조회하여
     * 표시명을 ioeCNm 에 설정합니다.
     * </p>
     *
     * @param items 품목 DTO 목록
     */
    private void enrichItemIoeCNames(List<ProjectDto.BitemmDto> items) {
        if (items == null || items.isEmpty()) return;
        Set<String> ioeCSet = items.stream()
                .map(value -> value.getIoeC())
                .filter(v -> v != null && !v.isEmpty())
                .collect(Collectors.toSet());
        if (ioeCSet.isEmpty()) return;
        Map<String, String> nameMap = buildIoeCNameMap(ioeCSet);
        items.forEach(item -> {
            if (item.getIoeC() != null) item.setIoeCNm(nameMap.get(item.getIoeC()));
        });
    }

    /**
     * RBAC 수정/삭제 권한 검증 헬퍼 (내부 메서드)
     *
     * <p>
     * SecurityContext에서 현재 인증된 사용자({@link CustomUserDetails})를 조회하고,
     * 자격등급 기반으로 리소스 수정 권한을 3단계로 검증합니다.
     * </p>
     *
     * <p>
     * 권한 계층:
     * </p>
     * <ol>
     * <li>시스템관리자(ITPAD001): 모든 리소스 수정 허용</li>
     * <li>기획통할담당자(ITPZZ002): 소속 부서(bbrC) == 리소스 부서(resourceBbrC) 인 경우 허용</li>
     * <li>일반사용자(ITPZZ001): 본인 작성 리소스(creatorEno == 요청자 eno) 인 경우만 허용</li>
     * </ol>
     *
     * @param creatorEno   리소스 최초 작성자 사번 (FST_ENR_USID)
     * @param resourceBbrC 리소스 소속 부서코드 (부서 단위 권한 범위 결정용)
     * @throws AccessDeniedException 수정 권한이 없는 경우
     */
    private void validateModifyPermission(String creatorEno, String resourceBbrC) {
        // SecurityContext에서 현재 인증 주체 조회
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 인증 주체가 CustomUserDetails가 아닌 경우 (비정상 접근) 거부
        if (!(principal instanceof CustomUserDetails currentUser)) {
            throw new AccessDeniedException("인증 정보를 확인할 수 없습니다.");
        }

        // 1단계: 시스템관리자는 모든 리소스 수정 허용
        if (currentUser.isAdmin()) {
            return;
        }

        // 2단계: 기획통할담당자는 소속 부서 리소스 수정 허용
        if (currentUser.isDeptManager()) {
            if (currentUser.getBbrC() != null && currentUser.getBbrC().equals(resourceBbrC)) {
                return;
            }
            throw new AccessDeniedException("소속 부서의 리소스만 수정할 수 있습니다.");
        }

        // 3단계: 일반사용자는 본인 작성 리소스만 수정 허용
        if (currentUser.getEno().equals(creatorEno)) {
            return;
        }

        throw new AccessDeniedException("본인이 작성한 리소스만 수정할 수 있습니다.");
    }
}
