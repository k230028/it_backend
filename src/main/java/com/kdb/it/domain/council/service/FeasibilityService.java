package com.kdb.it.domain.council.service;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bperfm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 타당성검토표 서비스 (Step 1)
 *
 * <p>타당성검토표는 3개 엔티티로 구성됩니다:</p>
 * <ul>
 *   <li>{@code BPOVWM}: 사업개요 (1:1)</li>
 *   <li>{@code BPERFM}: 성과관리 자체계획 (1:N, 동적 추가/삭제)</li>
 * </ul>
 *
 * <p>저장 방식:</p>
 * <ul>
 *   <li>KPN_TC=001(임시저장): 상태 DRAFT 유지, 빈 값 허용</li>
 *   <li>KPN_TC=002(저장/작성완료): 상태 SUBMITTED 전이, 첨부파일 필수</li>
 * </ul>
 *
 * <p>성과지표 저장 전략: 요청에 포함된 전체 목록으로 교체 (기존 삭제 + 신규 저장)</p>
 *
 * <p>Design Ref: §2.1 FeasibilityService — Step 1 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeasibilityService {

    /** 사업개요 리포지토리 (TPRMPP_BPOVWM) */
    private final ProjectOverviewRepository projectOverviewRepository;


    /** 성과지표 리포지토리 (TPRMPP_BPERFM) */
    private final PerformanceRepository performanceRepository;

    /** 협의회 기본 서비스 — 상태 전이 및 협의회 존재 확인용 */
    private final CouncilService councilService;

    /** JPA EntityManager — 성과지표 하드 딜리트 및 persist용 */
    @PersistenceContext
    private EntityManager entityManager;



    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 타당성검토표 조회 (사업개요 + 자체점검 + 성과지표 통합)
     *
     * @param asctId 협의회ID
     * @return 타당성검토표 전체 데이터
     * @throws IllegalArgumentException 존재하지 않는 협의회이거나 아직 미작성인 경우
     */
    public CouncilDto.FeasibilityResponse getFeasibility(String asctId) {
        // 협의회 존재 확인
        councilService.findActiveCouncil(asctId);

        // 사업개요 조회 — 최초 진입 시 미작성 상태이면 null 반환 (프론트에서 DEFAULT_FORM으로 초기화)
        java.util.Optional<Bpovwm> overviewOpt = projectOverviewRepository.findByItPtlAsctIdAndDelYn(asctId, "N");
        if (overviewOpt.isEmpty()) {
            return null;
        }

        // 성과지표 목록 조회 (순번 오름차순)
        List<Bperfm> performances = performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(asctId, "N");

        return toFeasibilityResponse(overviewOpt.get(), performances);
    }

    // =========================================================================
    // 저장 (신규 + 수정 통합)
    // =========================================================================

    /**
     * 타당성검토표 저장 (임시저장 / 작성완료 공통)
     *
     * <p>신규 작성이면 INSERT, 기존 데이터가 있으면 UPDATE합니다.</p>
     *
     * <p>작성완료(KPN_TC=002) 시 처리:</p>
     * <ul>
     *   <li>첨부파일(hwp/hwpx/pdf) 필수 확인</li>
     *   <li>협의회 상태를 SUBMITTED로 전이</li>
     * </ul>
     *
     * @param asctId  협의회ID
     * @param request 타당성검토표 저장 요청
     */
    @Transactional
    public void saveFeasibility(String asctId, CouncilDto.FeasibilityRequest request) {
        // 협의회 존재 확인
        councilService.findActiveCouncil(asctId);

        // 작성완료 시 첨부파일 필수 검증
        if ("02".equals(request.kpnTc())) { // KPN_TC 002 = 저장(작성완료)
            validateAttachment(request.flMngNo());
        }

        // 사업개요 저장 (upsert)
        saveOrUpdateOverview(asctId, request);

        // 성과지표 저장 (전체 교체)
        if (request.performances() != null && !request.performances().isEmpty()) {
            replacePerformances(asctId, request.performances());
        }

        // 작성완료 시 상태 전이: DRAFT → SUBMITTED
        if ("02".equals(request.kpnTc())) { // KPN_TC 002 = 저장(작성완료)
            councilService.changeStatus(asctId, "02");
        }
    }

    // =========================================================================
    // 내부 헬퍼 — 저장 로직
    // =========================================================================

    /**
     * 사업개요 신규 저장 또는 업데이트 (upsert)
     */
    private void saveOrUpdateOverview(String asctId, CouncilDto.FeasibilityRequest req) {
        projectOverviewRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .ifPresentOrElse(
                    // 기존 데이터 있으면 update
                    existing -> existing.update(
                            req.prjNm(), req.prjTrm(), req.ncs(), req.prjBg(), req.edrt(),
                            req.prjDes(), req.lglRglYn(), req.lglRglNm(), req.xptEff(),
                            req.kpnTc(), req.flMngNo()),
                    // 없으면 신규 INSERT
                    () -> {
                        Bpovwm overview = Bpovwm.builder()
                                .itPtlAsctId(asctId)
                                .abusNm(req.prjNm())
                                .abusTrmCone(req.prjTrm())
                                .abusNcsCone(req.ncs())
                                .rqmBgAmt(req.prjBg())
                                .itPtlEdrtTc(req.edrt())
                                .abusCone(req.prjDes())
                                .lwRglYn(req.lglRglYn() != null ? req.lglRglYn() : "N")
                                .lwFdtn(req.lglRglNm())
                                .dgogPpoCone(req.xptEff())
                                .kpnTpTc(req.kpnTc())
                                .flMpnId(req.flMngNo())
                                .build();
                        projectOverviewRepository.save(overview);
                    }
                );
    }


    /**
     * 성과지표 전체 교체 (기존 하드 삭제 + 신규 INSERT)
     *
     * <p>동적 추가/삭제를 지원하기 위해 요청 목록으로 완전 교체합니다.</p>
     *
     * <p>소프트 딜리트(del_yn='Y') 방식을 사용하지 않는 이유:
     * BPERFM은 복합 PK(itPtlAsctId, evlDtpSno)를 가지며 DEL_YN이 NOT NULL입니다.
     * soft-delete 후 동일 PK로 재삽입 시 JPA merge()가 del_yn=null로 덮어써
     * NOT NULL 제약 위반이 발생합니다. 하드 딜리트로 PK 충돌을 방지합니다.</p>
     */
    private void replacePerformances(String asctId, List<CouncilDto.PerformanceRequest> requests) {
        // 기존 성과지표 전체 하드 삭제 (JPQL — persistence context 및 DB 동시 반영)
        entityManager.createQuery("DELETE FROM Bperfm b WHERE b.itPtlAsctId = :asctId")
                .setParameter("asctId", asctId)
                .executeUpdate();

        // 새 성과지표 INSERT (persist — @PrePersist 확실히 실행됨)
        for (CouncilDto.PerformanceRequest req : requests) {
            Bperfm perf = Bperfm.builder()
                    .itPtlAsctId(asctId)
                    .evlDtpSno(req.dtpSno())
                    .evlDtpNm(req.dtpNm())
                    .evlDtpDfntCone(req.dtpCone())
                    .evlDtpClfCone(req.clf())
                    .evlDtpMsmPtmCone(req.msmTpm())
                    .evlDtpMsmCleCone(req.msmCle())
                    .build();
            entityManager.persist(perf);
        }
    }

    // =========================================================================
    // 내부 헬퍼 — 검증 및 변환
    // =========================================================================

    /**
     * 첨부파일 확장자 검증 (작성완료 시 필수)
     *
     * <p>Plan SC: 첨부파일은 hwp/hwpx/pdf만 허용 (보안 정책)</p>
     *
     * @param flMngNo 첨부파일관리번호
     * @throws IllegalArgumentException 첨부파일 없거나 허용되지 않는 확장자인 경우
     */
    private void validateAttachment(String flMngNo) {
        if (flMngNo == null || flMngNo.isBlank()) {
            throw new IllegalArgumentException("작성완료 시 첨부파일(hwp/hwpx/pdf)은 필수입니다.");
        }
        // 파일관리번호 기반 확장자 확인은 FileService와 연동 (M4 범위 내 기본 검증만)
    }

    /**
     * 엔티티 → FeasibilityResponse 변환
     */
    private CouncilDto.FeasibilityResponse toFeasibilityResponse(
            Bpovwm overview, List<Bperfm> performances) {

        // 성과지표 변환
        List<CouncilDto.PerformanceResponse> perfResponses = performances.stream()
                .map(p -> new CouncilDto.PerformanceResponse(
                        p.getEvlDtpSno(), p.getEvlDtpNm(), p.getEvlDtpDfntCone(),
                        p.getEvlDtpClfCone(), p.getEvlDtpMsmPtmCone(), p.getEvlDtpMsmCleCone()))
                .toList();

        return new CouncilDto.FeasibilityResponse(
                overview.getAbusNm(), overview.getAbusTrmCone(), overview.getAbusNcsCone(),
                overview.getRqmBgAmt(), overview.getItPtlEdrtTc(), overview.getAbusCone(),
                overview.getLwRglYn(), overview.getLwFdtn(), overview.getDgogPpoCone(),
                overview.getKpnTpTc(), perfResponses, overview.getFlMpnId()
        );
    }
}
