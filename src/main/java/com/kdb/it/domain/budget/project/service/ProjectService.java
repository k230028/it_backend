package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.common.approval.dto.ApplicationInfoDto;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보화사업(IT 프로젝트) 서비스
 *
 * <p>
 * 정보화사업(TAAABB_BPROJM) 엔티티의 CRUD 및 품목({@link com.kdb.it.domain.budget.project.entity.Bitemm})
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

    /** 정보화사업 데이터 접근 리포지토리 (TAAABB_BPROJM) */
    private final ProjectRepository projectRepository;

    /** 신청서-원본 데이터 연결 리포지토리 (TAAABB_CAPPLA): 결재 상태 확인용 */
    private final com.kdb.it.common.approval.repository.ApplicationMapRepository capplaRepository;

    /** 신청서 마스터 리포지토리 (TAAABB_CAPPLM): 결재 상태 조회용 */
    private final com.kdb.it.common.approval.repository.ApplicationRepository capplmRepository;

    /** 품목 데이터 접근 리포지토리 (TAAABB_BITEMM) */
    private final com.kdb.it.domain.budget.project.repository.ProjectItemRepository bitemmRepository;

    /** 조직(부점) 정보 리포지토리 (TAAABB_CORGNI): 부서코드→부서명 조회용 */
    private final com.kdb.it.common.iam.repository.OrganizationRepository corgnIRepository;

    /** 사용자 정보 리포지토리 (TAAABB_CUSERI): 사원번호→사용자명 조회용 */
    private final com.kdb.it.common.iam.repository.UserRepository cuserIRepository;

    /** 결재 정보 리포지토�� (TAAABB_CDECIM): 결재선 목록 조회용 */
    private final com.kdb.it.common.approval.repository.ApproverRepository cdecimRepository;

    /** 공통코드 리포지토리 (TAAABB_CCODEM): 비목코드 → 자본예산/일반관리비 구분용 */
    private final com.kdb.it.common.code.repository.CodeRepository ccodemRepository;

    /** 공통코드 서비스: 예산 신청 기간 검증용 */
    private final com.kdb.it.common.code.service.CodeService codeService;

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
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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
        Bprojm project = projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        ProjectDto.Response response = ProjectDto.Response.fromEntity(project);
        // 최신 신청서 정보 조회 및 설정
        setApplicationInfo(response, prjMngNo, project.getPrjSno());
        // 부서코드→부서명, 사원번호→사용자명 조회 및 설정
        setCodeNames(response);

        // 품목 정보 조회 및 설정 (삭제되지 않은 항목만)
        // PRJ_MNG_NO(프로젝트관리번호), PRJ_SNO(프로젝트일련번호) 기준, DEL_YN='N'인 품목 조회
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms = bitemmRepository.findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo,
                project.getPrjSno(), "N");

        // 품목 엔티티를 DTO로 변환하여 응답 객체에 설정
        List<ProjectDto.BitemmDto> itemDtos = bitemms.stream()
                .map(ProjectDto.BitemmDto::fromEntity)
                .toList();
        response.setItems(itemDtos);

        // 품목 기준 자본예산/일반관리비 합계 계산 및 설정 (이미 조회한 bitemms 재활용)
        setBudgetSummaryFromItems(response, bitemms);

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
     * 예: {@code PRJ-2026-0001}
     * </p>
     *
     * @param request 정보화사업 생성 요청 DTO (프로젝트명, 예산, 기간, 담당자 등)
     * @return 생성된 프로젝트관리번호
     * @throws IllegalArgumentException 제공된 관리번호가 이미 존재하는 경우
     */
    @Transactional
    public String createProject(ProjectDto.CreateRequest request) {
        // 예산 신청 기간 검증 (기간 외 → 400 Bad Request)
        codeService.validateBudgetPeriod();

        String prjMngNo = request.getPrjMngNo();

        // 프로젝트관리번호가 없으면 자동 채번
        if (prjMngNo == null || prjMngNo.isEmpty()) {
            Long nextVal = projectRepository.getNextSequenceValue(); // Oracle 시퀀스 채번

            // 사업연도 결정 (요청값 없으면 현재 연도 사용)
            String year = request.getBgYy();
            if (year == null || year.isEmpty()) {
                year = String.valueOf(java.time.LocalDate.now().getYear());
                request.setBgYy(year);
            }

            // 형식: PRJ-{bgYy}-{seq:04d}
            prjMngNo = String.format("PRJ-%s-%04d", year, nextVal);
            request.setPrjMngNo(prjMngNo);

        } else {
            // 제공된 관리번호 중복 확인 (복합키이므로 prjMngNo 기준으로 존재 여부 확인)
            if (projectRepository.existsByPrjMngNoAndDelYn(prjMngNo, "N")) {
                throw new IllegalArgumentException("Project already exists with id: " + prjMngNo);
            }
        }

        // Rich Text 필드 XSS 새니타이징 (서버 측 방어)
        request.setPrjDes(HtmlSanitizer.sanitize(request.getPrjDes()));
        request.setPrjRng(HtmlSanitizer.sanitize(request.getPrjRng()));

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

                com.kdb.it.domain.budget.project.entity.Bitemm newItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                        .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                        .gclSno(++gclSno) // 품목일련번호
                        .prjMngNo(project.getPrjMngNo()) // 프로젝트관리번호
                        .prjSno(project.getPrjSno()) // 프로젝트순번
                        .gclDtt(itemDto.getGclDtt()) // 품목구분
                        .gclNm(itemDto.getGclNm()) // 품목명
                        .gclQtt(itemDto.getGclQtt()) // 품목수량
                        .cur(itemDto.getCur()) // 통화
                        .xcr(itemDto.getXcr()) // 환율
                        .xcrBseDt(itemDto.getXcrBseDt()) // 환율기준일자
                        .bgFdtn(itemDto.getBgFdtn()) // 예산근거
                        .itdDt(itemDto.getItdDt()) // 도입시기
                        .dfrCle(itemDto.getDfrCle()) // 지급주기
                        .infPrtYn(itemDto.getInfPrtYn() == null ? "N" : itemDto.getInfPrtYn()) // 정보보호여부
                        .itrInfrYn(itemDto.getItrInfrYn() == null ? "N" : itemDto.getItrInfrYn()) // 통합인프라여부
                        .lstYn("Y") // 최종여부
                        .gclAmt(itemDto.getGclAmt()) // 품목금액
                        .build();
                bitemmRepository.save(newItem);
            }
        }

        return project.getPrjMngNo(); // 저장된 관리번호 반환
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
        Bprojm project = projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        validateModifyPermission(project.getFstEnrUsid(), project.getSvnDpm());

        // 결재 상태 확인 (BPROJM 테이블 코드로 신청서 연결 여부 조회)
        // 결재중 또는 결재완료 상태인 경우 수정 불가
        boolean isProcessingOrApproved = capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                "BPROJM", prjMngNo, project.getPrjSno(), java.util.List.of("결재중", "결재완료"));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 수정할 수 없습니다.");
        }

        // Rich Text 필드 XSS 새니타이징 (서버 측 방어)
        request.setPrjDes(HtmlSanitizer.sanitize(request.getPrjDes()));
        request.setPrjRng(HtmlSanitizer.sanitize(request.getPrjRng()));

        // 프로젝트 기본 정보 수정 (JPA Dirty Checking으로 자동 반영)
        project.update(
                request.getPrjNm(), // 프로젝트명
                request.getPrjTp(), // 프로젝트유형
                request.getSvnDpm(), // 주관부서
                request.getItDpm(), // IT부서
                request.getPrjBg(), // 프로젝트예산
                request.getNyyPrjBg(), // 익년프로젝트예산
                request.getSttDt(), // 시작일자
                request.getEndDt(), // 종료일자
                request.getSvnDpmCgpr(), // 주관부서담당자
                request.getItDpmCgpr(), // IT부서담당자
                request.getSvnDpmTlr(), // 주관부서담당팀장
                request.getItDpmTlr(), // IT부서담당팀장
                request.getEdrt(), // 전결권
                request.getPrjDes(), // 사업설명
                request.getSaf(), // 현황
                request.getNcs(), // 필요성
                request.getXptEff(), // 기대효과
                request.getPlm(), // 문제
                request.getPrjRng(), // 사업범위
                request.getPulPsg(), // 추진경과
                request.getHrfPln(), // 향후계획
                request.getBzDtt(), // 업무구분
                request.getTchnTp(), // 기술유형
                request.getMnUsr(), // 주요사용자
                request.getDplYn(), // 중복여부
                request.getLblFsgTlm(), // 의무완료기한
                request.getRprSts(), // 보고상태
                request.getPrjPulPtt(), // 프로젝트추진가능성
                request.getPrjSts(), // 프로젝트상태
                request.getBgYy(), // 사업연도
                request.getSvnHdq(), // 주관본부/부문
                request.getOrnYn(), // 경상여부
                request.getPulDtt()); // 사업구분

        // ===== 품목 정보 동기화 (CUD) =====
        if (request.getItems() != null) {
            // 1. 기존 품목 조회 (DEL_YN='N')
            List<com.kdb.it.domain.budget.project.entity.Bitemm> existingItems = bitemmRepository
                    .findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, project.getPrjSno(), "N");

            // 처리된 품목 관리번호 추적 (삭제 대상 식별용)
            java.util.Set<String> processedGclMngNos = new java.util.HashSet<>();
            // 현재 최대 SNO 계산 (신규 추가 시 MAX+1로 설정)
            int maxGclSno = existingItems.stream()
                    .mapToInt(com.kdb.it.domain.budget.project.entity.Bitemm::getGclSno)
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
                            int newGclSno = existingItem.getGclSno() + 1;
                            com.kdb.it.domain.budget.project.entity.Bitemm updatedItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                                    .gclMngNo(existingItem.getGclMngNo()) // 품목관리번호 유지 (기존 번호)
                                    .gclSno(newGclSno) // 품목일련번호 1 증가
                                    .prjMngNo(existingItem.getPrjMngNo()) // 프로젝트관리번호 유지
                                    .prjSno(existingItem.getPrjSno()) // 프로젝트순번 유지
                                    .gclDtt(itemDto.getGclDtt()) // 품목구분
                                    .gclNm(itemDto.getGclNm()) // 품목명
                                    .gclQtt(itemDto.getGclQtt()) // 품목수량
                                    .cur(itemDto.getCur()) // 통화
                                    .xcr(itemDto.getXcr()) // 환율
                                    .xcrBseDt(itemDto.getXcrBseDt()) // 환율기준일자
                                    .bgFdtn(itemDto.getBgFdtn()) // 예산근거
                                    .itdDt(itemDto.getItdDt()) // 도입시기
                                    .dfrCle(itemDto.getDfrCle()) // 지급주기
                                    .infPrtYn(defaultYn(itemDto.getInfPrtYn()))
                                    .itrInfrYn(defaultYn(itemDto.getItrInfrYn()))
                                    .lstYn("Y") // 최종여부
                                    .gclAmt(itemDto.getGclAmt()) // 품목금액
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

                    com.kdb.it.domain.budget.project.entity.Bitemm newItem = com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                            .gclMngNo(gclMngNo) // 품목관리번호 (신규 채번)
                            .gclSno(++maxGclSno) // 품목일련번호 (MAX+1)
                            .prjMngNo(prjMngNo) // 프로젝트관리번호
                            .prjSno(project.getPrjSno()) // 프로젝트순번
                            .gclDtt(itemDto.getGclDtt()) // 품목구분
                            .gclNm(itemDto.getGclNm()) // 품목명
                            .gclQtt(itemDto.getGclQtt()) // 품목수량
                            .cur(itemDto.getCur()) // 통화
                            .xcr(itemDto.getXcr()) // 환율
                            .xcrBseDt(itemDto.getXcrBseDt()) // 환율기준일자
                            .bgFdtn(itemDto.getBgFdtn()) // 예산근거
                            .itdDt(itemDto.getItdDt()) // 도입시기
                            .dfrCle(itemDto.getDfrCle()) // 지급주기
                            .infPrtYn(itemDto.getInfPrtYn() == null ? "N" : itemDto.getInfPrtYn()) // 정보보호여부
                            .itrInfrYn(itemDto.getItrInfrYn() == null ? "N" : itemDto.getItrInfrYn()) // 통합인프라여부
                            .lstYn("Y") // 최종여부
                            .gclAmt(itemDto.getGclAmt()) // 품목금액
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

        return project.getPrjMngNo(); // 수정된 관리번호 반환
    }

    /**
     * 품목 변경 여부 판단
     *
     * <p>기존 엔티티와 요청 DTO의 업무 필드를 비교하여, 하나라도 다르면 {@code true}를 반환합니다.</p>
     * <p>변경이 없는 품목은 버저닝(D→C 로그)을 건너뜁니다.</p>
     * <p>BigDecimal 필드(xcr, gclQtt, gclAmt)는 scale 무관한 수치 비교를 위해 compareTo를 사용합니다.</p>
     *
     * @param existing 현재 활성 품목 엔티티 (DEL_YN='N')
     * @param dto      클라이언트로부터 전달된 수정 요청 DTO
     * @return 변경된 필드가 하나라도 있으면 {@code true}
     */
    private boolean isItemChanged(Bitemm existing, ProjectDto.BitemmDto dto) {
        return !Objects.equals(existing.getGclDtt(), dto.getGclDtt())
                || !Objects.equals(existing.getGclNm(), dto.getGclNm())
                || bigDecimalChanged(existing.getGclQtt(), dto.getGclQtt())
                || !Objects.equals(existing.getCur(), dto.getCur())
                || bigDecimalChanged(existing.getXcr(), dto.getXcr())
                || !Objects.equals(existing.getXcrBseDt(), dto.getXcrBseDt())
                || !Objects.equals(existing.getBgFdtn(), dto.getBgFdtn())
                || !Objects.equals(existing.getItdDt(), dto.getItdDt())
                || !Objects.equals(existing.getDfrCle(), dto.getDfrCle())
                || !Objects.equals(existing.getInfPrtYn(), defaultYn(dto.getInfPrtYn()))
                || !Objects.equals(existing.getItrInfrYn(), defaultYn(dto.getItrInfrYn()))
                || bigDecimalChanged(existing.getGclAmt(), dto.getGclAmt());
    }

    /** null이면 "N"으로 정규화 (infPrtYn, itrInfrYn 공통 기본값 처리) */
    private static String defaultYn(String value) {
        return value == null ? "N" : value;
    }

    /** BigDecimal 수치 비교 (scale 무시). 둘 다 null이면 동일, 한쪽만 null이면 변경으로 간주 */
    private static boolean bigDecimalChanged(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
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
        Bprojm project = projectRepository.findByPrjMngNoAndDelYn(prjMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + prjMngNo));

        // RBAC 수정 권한 검증 (Admin/DeptManager/작성자 여부 확인)
        validateModifyPermission(project.getFstEnrUsid(), project.getSvnDpm());

        // 결재 상태 확인 (결재중/결재완료이면 삭제 불가)
        boolean isProcessingOrApproved = capplaRepository.existsByOrcTbCdAndOrcPkVlAndOrcSnoVlAndApfStsIn(
                "BPROJM", prjMngNo, project.getPrjSno(), java.util.List.of("결재중", "결재완료"));

        if (isProcessingOrApproved) {
            throw new IllegalStateException("결재중이거나 결재완료된 프로젝트는 삭제할 수 없습니다.");
        }

        // 1. 프로젝트 Soft Delete (DEL_YN='Y')
        project.delete();

        // 2. 관련 품목 전체 Soft Delete (DEL_YN 무관하게 모든 품목 조회 후 삭제)
        List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms = bitemmRepository.findByPrjMngNoAndPrjSno(prjMngNo,
                project.getPrjSno());
        for (com.kdb.it.domain.budget.project.entity.Bitemm bitemm : bitemms) {
            bitemm.delete(); // BaseEntity.delete() 호출 (DEL_YN='Y')
        }
    }

    /**
     * 정보화사업 일괄 조회
     *
     * <p>
     * 여러 프로젝트관리번호를 한 번에 조회합니다.
     * 존재하지 않는 항목은 결과에서 제외합니다 (null 필터링).
     * </p>
     *
     * @param request 일괄 조회 요청 DTO (프로젝트관리번호 목록)
     * @return 존재하는 프로젝트의 응답 DTO 목록 (품목 정보 포함, 없는 항목 제외)
     */
    public List<ProjectDto.Response> getProjectsByIds(ProjectDto.BulkGetRequest request) {
        return request.getPrjMngNos().stream()
                .map(prjMngNo -> {
                    try {
                        return getProject(prjMngNo); // 개별 상세 조회 (품목 포함)
                    } catch (IllegalArgumentException e) {
                        return null; // 존재하지 않는 항목은 null로 처리
                    }
                })
                .filter(response -> response != null) // null 제거 (존재하지 않는 항목 제외)
                .toList();
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
        List<String> prjMngNos = projects.stream().map(Bprojm::getPrjMngNo).collect(Collectors.toList());
        List<Cappla> allCapplas = capplaRepository.findByOrcTbCdAndOrcPkVlInOrderByApfRelSnoDesc("BPROJM", prjMngNos);

        // prjMngNo → 최신 Cappla (이미 DESC 정렬이므로 첫 번째가 최신)
        Map<String, Cappla> latestCappla = new java.util.LinkedHashMap<>();
        for (Cappla c : allCapplas) {
            latestCappla.putIfAbsent(c.getOrcPkVl(), c);
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
        for (ProjectDto.Response r : responses) {
            if (r.getItDpm() != null && !r.getItDpm().isEmpty()) orgCodes.add(r.getItDpm());
            if (r.getSvnDpm() != null && !r.getSvnDpm().isEmpty()) orgCodes.add(r.getSvnDpm());
            if (r.getItDpmCgpr() != null && !r.getItDpmCgpr().isEmpty()) userEnos.add(r.getItDpmCgpr());
            if (r.getItDpmTlr() != null && !r.getItDpmTlr().isEmpty()) userEnos.add(r.getItDpmTlr());
            if (r.getSvnDpmCgpr() != null && !r.getSvnDpmCgpr().isEmpty()) userEnos.add(r.getSvnDpmCgpr());
            if (r.getSvnDpmTlr() != null && !r.getSvnDpmTlr().isEmpty()) userEnos.add(r.getSvnDpmTlr());
        }

        // --- 5. 부서명·사용자명 배치 조회 ---
        Map<String, String> orgNameMap = corgnIRepository.findAllById(orgCodes).stream()
                .collect(Collectors.toMap(CorgnI::getPrlmOgzCCone, CorgnI::getBbrNm));
        Map<String, String> userNameMap = cuserIRepository.findAllById(userEnos).stream()
                .collect(Collectors.toMap(CuserI::getEno, CuserI::getUsrNm));

        // --- 6. 응답 DTO에 일괄 주입 ---
        for (int i = 0; i < projects.size(); i++) {
            Bprojm project = projects.get(i);
            ProjectDto.Response response = responses.get(i);

            Cappla cappla = latestCappla.get(project.getPrjMngNo());
            if (cappla != null) {
                response.setApfMngNo(cappla.getApfMngNo());
                Capplm capplm = capplmMap.get(cappla.getApfMngNo());
                if (capplm != null) {
                    response.setApfSts(capplm.getApfSts());
                    List<Cdecim> decisions = decisionMap.getOrDefault(cappla.getApfMngNo(), List.of());
                    response.setApplicationInfo(ApplicationInfoDto.fromEntities(capplm, decisions));
                }
            }

            if (response.getItDpm() != null) response.setItDpmNm(orgNameMap.get(response.getItDpm()));
            if (response.getSvnDpm() != null) response.setSvnDpmNm(orgNameMap.get(response.getSvnDpm()));
            if (response.getItDpmCgpr() != null) response.setItDpmCgprNm(userNameMap.get(response.getItDpmCgpr()));
            if (response.getItDpmTlr() != null) response.setItDpmTlrNm(userNameMap.get(response.getItDpmTlr()));
            if (response.getSvnDpmCgpr() != null) response.setSvnDpmCgprNm(userNameMap.get(response.getSvnDpmCgpr()));
            if (response.getSvnDpmTlr() != null) response.setSvnDpmTlrNm(userNameMap.get(response.getSvnDpmTlr()));

            setBudgetSummary(response, project.getPrjMngNo(), project.getPrjSno());
        }
    }

    private void setApplicationInfo(ProjectDto.Response response, String prjMngNo, Integer prjSno) {
        // BPROJM 테이블 코드와 프로젝트 관리번호/순번으로 연결된 신청서 목록 조회 (최신순)
        List<com.kdb.it.common.approval.entity.Cappla> capplas = capplaRepository
                .findByOrcTbCdAndOrcPkVlAndOrcSnoVlOrderByApfRelSnoDesc("BPROJM", prjMngNo, prjSno);

        if (!capplas.isEmpty()) {
            com.kdb.it.common.approval.entity.Cappla cappla = capplas.get(0); // 가장 최신 신청서
            response.setApfMngNo(cappla.getApfMngNo()); // 신청관리번호 설정

            // 신청서 마스터에서 결재상태 및 상세 정보 조회
            capplmRepository.findById(cappla.getApfMngNo())
                    .ifPresent(capplm -> {
                        response.setApfSts(capplm.getApfSts()); // 결재상태 설정 (하위 호환)

                        // 결재자 목록 조회 (결재순서 오름차순)
                        List<com.kdb.it.common.approval.entity.Cdecim> decisions = cdecimRepository
                                .findByDcdMngNoOrderByDcdSqnAsc(cappla.getApfMngNo());

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
     * 부서코드(itDpm, svnDpm)로 TAAABB_CORGNI에서 부서명(BBR_NM)을 조회하고,
     * 사원번호(itDpmCgpr, itDpmTlr, svnDpmCgpr, svnDpmTlr)로
     * TAAABB_CUSERI에서 사용자명(USR_NM)을 조회하여 응답 DTO에 설정합니다.
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
        // === 부서코드 → 부서명 변환 (TAAABB_CORGNI) ===

        // IT부서코드 → IT부서명
        if (response.getItDpm() != null && !response.getItDpm().isEmpty()) {
            corgnIRepository.findById(response.getItDpm())
                    .ifPresent(org -> response.setItDpmNm(org.getBbrNm()));
        }

        // 주관부서코드 → 주관부서명
        if (response.getSvnDpm() != null && !response.getSvnDpm().isEmpty()) {
            corgnIRepository.findById(response.getSvnDpm())
                    .ifPresent(org -> response.setSvnDpmNm(org.getBbrNm()));
        }

        // === 사원번호 → 사용자명 변환 (TAAABB_CUSERI) ===

        // IT담당자 사번 → IT담당자명
        if (response.getItDpmCgpr() != null && !response.getItDpmCgpr().isEmpty()) {
            cuserIRepository.findById(response.getItDpmCgpr())
                    .ifPresent(user -> response.setItDpmCgprNm(user.getUsrNm()));
        }

        // IT담당팀장 사번 → IT담당팀장명
        if (response.getItDpmTlr() != null && !response.getItDpmTlr().isEmpty()) {
            cuserIRepository.findById(response.getItDpmTlr())
                    .ifPresent(user -> response.setItDpmTlrNm(user.getUsrNm()));
        }

        // 주관부서담당자 사번 → 주관부서담당자명
        if (response.getSvnDpmCgpr() != null && !response.getSvnDpmCgpr().isEmpty()) {
            cuserIRepository.findById(response.getSvnDpmCgpr())
                    .ifPresent(user -> response.setSvnDpmCgprNm(user.getUsrNm()));
        }

        // 주관부서담당팀장 사번 → 주관부서담당팀장명
        if (response.getSvnDpmTlr() != null && !response.getSvnDpmTlr().isEmpty()) {
            cuserIRepository.findById(response.getSvnDpmTlr())
                    .ifPresent(user -> response.setSvnDpmTlrNm(user.getUsrNm()));
        }
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
                .findByPrjMngNoAndPrjSnoAndDelYn(prjMngNo, prjSno, "N");
        setBudgetSummaryFromItems(response, bitemms);
    }

    /**
     * 품목 목록으로부터 자본예산/일반관리비 합계를 계산하여 응답 DTO에 설정
     *
     * <p>
     * 자본예���(assetBg): 품목구분(gclDtt)이 공통코드 코드값구분 IOE_CPIT에 해당하는 품목의 gclAmt 합계
     * </p>
     * <p>
     * 일반관리비(costBg): 품목구분(gclDtt)이 공통코드 코드값구분 IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE에 해당하는 품목의 gclAmt 합계
     * </p>
     *
     * @param response 예산 ���계를 설정할 응답 DTO
     * @param bitemms  합계 계산 대상 품목 목록
     */
    private void setBudgetSummaryFromItems(ProjectDto.Response response,
            List<com.kdb.it.domain.budget.project.entity.Bitemm> bitemms) {
        // 공통코드에서 자본예산 대상 비목코드 조회 (cttTp = IOE_CPIT) — 캐시 적용
        List<com.kdb.it.common.code.entity.Ccodem> assetCodes = codeService.findCodeEntitiesByCttTp("IOE_CPIT");
        java.util.Set<String> assetTypes = assetCodes.stream()
                .map(com.kdb.it.common.code.entity.Ccodem::getCdId)
                .collect(java.util.stream.Collectors.toSet());

        // 자본예산 비목코드를 코드설명(cdDes) 기준으로 세부 분류 (개발비/기계장치/기타무형자산)
        java.util.Map<String, java.util.Set<String>> assetSubTypes = assetCodes.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getCdDes() != null ? c.getCdDes() : "",
                        java.util.stream.Collectors.mapping(
                                com.kdb.it.common.code.entity.Ccodem::getCdId,
                                java.util.stream.Collectors.toSet())));
        java.util.Set<String> devTypes = assetSubTypes.getOrDefault("개발비", java.util.Collections.emptySet());
        java.util.Set<String> machTypes = assetSubTypes.getOrDefault("기계장치", java.util.Collections.emptySet());
        java.util.Set<String> intanTypes = assetSubTypes.getOrDefault("기타무형자산", java.util.Collections.emptySet());

        // 공통코드에서 일반관리비 대상 비목코드(cdId) 조회 (cttTp = IOE_IDR, IOE_SEVS, IOE_XPN, IOE_LEAFE) — 캐시 적용
        java.util.Set<String> costTypes = java.util.stream.Stream.of("IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE")
                .flatMap(cttTp -> codeService.findCodeEntitiesByCttTp(cttTp).stream())
                .map(com.kdb.it.common.code.entity.Ccodem::getCdId)
                .collect(java.util.stream.Collectors.toSet());

        // 품목별 금액 계산 헬퍼 (gclAmt × xcr, xcr이 null이거나 0이면 1로 간주)
        java.util.function.Function<com.kdb.it.domain.budget.project.entity.Bitemm, java.math.BigDecimal> calcAmt =
                item -> {
                    java.math.BigDecimal xcr = (item.getXcr() != null && item.getXcr().compareTo(java.math.BigDecimal.ZERO) != 0)
                            ? item.getXcr() : java.math.BigDecimal.ONE;
                    return item.getGclAmt().multiply(xcr);
                };

        // 유효한 품목만 필터링 (gclDtt, gclAmt가 null이 아닌 항목)
        List<com.kdb.it.domain.budget.project.entity.Bitemm> validItems = bitemms.stream()
                .filter(item -> item.getGclDtt() != null && item.getGclAmt() != null)
                .collect(java.util.stream.Collectors.toList());

        // 자본예산 합계 계산
        java.math.BigDecimal assetBg = validItems.stream()
                .filter(item -> assetTypes.contains(item.getGclDtt()))
                .map(calcAmt)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // 자본예산 세부 분류 합계 계산
        java.math.BigDecimal devBg = validItems.stream()
                .filter(item -> devTypes.contains(item.getGclDtt()))
                .map(calcAmt)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal machBg = validItems.stream()
                .filter(item -> machTypes.contains(item.getGclDtt()))
                .map(calcAmt)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal intanBg = validItems.stream()
                .filter(item -> intanTypes.contains(item.getGclDtt()))
                .map(calcAmt)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // 일반관리비 합계 계산
        java.math.BigDecimal costBg = validItems.stream()
                .filter(item -> costTypes.contains(item.getGclDtt()))
                .map(calcAmt)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        response.setBudgetAmounts(assetBg, devBg, machBg, intanBg, costBg);
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
