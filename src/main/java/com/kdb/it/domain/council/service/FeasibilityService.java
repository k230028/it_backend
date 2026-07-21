package com.kdb.it.domain.council.service;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bchklm;
import com.kdb.it.domain.council.entity.Bperfm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import com.kdb.it.domain.council.repository.SelfCheckRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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
 *   <li>KPN_TP_TC=10(임시저장): 상태 DRAFT 유지, 빈 값 허용</li>
 *   <li>KPN_TP_TC=20(저장완료): 상태 SUBMITTED 전이, 첨부파일 필수</li>
 * </ul>
 *
 * <p>성과지표 저장 전략: 요청에 포함된 전체 목록으로 교체 (기존 삭제 + 신규 저장)</p>
 *
 * <p>설계 참조: §2.1 FeasibilityService — 1단계 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeasibilityService {

    /** 사업개요 리포지토리 (TPRMPP_BPOVWM) */
    private final ProjectOverviewRepository projectOverviewRepository;


    /** 성과지표 리포지토리 (TPRMPP_BPERFM) */
    private final PerformanceRepository performanceRepository;

    /** 자체점검 리포지토리 (TPRMPP_BCHKLM) */
    private final SelfCheckRepository selfCheckRepository;

    /** 협의회 기본 서비스 — 상태 전이 및 협의회 존재 확인용 */
    private final CouncilService councilService;

    /** JPA EntityManager — 성과지표 하드 딜리트 및 persist용 */
    @PersistenceContext
    private EntityManager entityManager;

    /** 점검항목코드 → 한글명 (자체점검 검증 메시지용, BEVALM 평가와 동일 체계) */
    private static final Map<String, String> CHECK_ITEM_NAMES = Map.of(
            "01", "경영전략/계획 부합",
            "02", "재무 효과",
            "03", "리스크 개선 효과",
            "04", "평판/이미지 개선 효과",
            "05", "유사/중복 시스템 유무",
            "06", "기타");



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

        // 자체점검 목록 조회 (항목 정렬은 프론트에서 고정 순서로 처리)
        List<Bchklm> selfChecks = selfCheckRepository.findByItPtlAsctIdAndDelYn(asctId, "N");

        // 성과지표 목록 조회 (순번 오름차순)
        List<Bperfm> performances = performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(asctId, "N");

        return toFeasibilityResponse(overviewOpt.get(), selfChecks, performances);
    }

    // =========================================================================
    // 저장 (신규 + 수정 통합)
    // =========================================================================

    /**
     * 타당성검토표 저장 (임시저장 / 작성완료 공통)
     *
     * <p>신규 작성이면 INSERT, 기존 데이터가 있으면 UPDATE합니다.</p>
     *
     * <p>작성완료(KPN_TP_TC=20) 시 처리:</p>
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
        if ("20".equals(request.kpnTc())) { // KPN_TP_TC 20 = 저장완료
            validateAttachment(request.flMngNo());
        }

        // 사업개요 저장 (upsert)
        saveOrUpdateOverview(asctId, request);

        // 자체점검 저장 (항목별 upsert)
        saveOrUpdateSelfChecks(asctId, request);

        // 성과지표 저장 (전체 교체)
        if (request.performances() != null && !request.performances().isEmpty()) {
            replacePerformances(asctId, request.performances());
        }

        // 작성완료 시 상태 전이: DRAFT → SUBMITTED
        if ("20".equals(request.kpnTc())) { // KPN_TP_TC 20 = 저장완료
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
                        // 신규 INSERT는 persist()로 @PrePersist 발화 보장 (merge 분기 회귀 방지, §5.12.1.1)
                        entityManager.persist(overview);
                    }
                );
    }

    /**
     * 자체점검 항목별 신규 저장 또는 업데이트 (upsert)
     *
     * <p>협의회 단위로 점검항목당 1행(BCHKLM)을 유지한다. 평가위원 평가(BEVALM)와 달리
     * 사번이 없다. 작성완료(kpnTc='20') 시 모든 항목의 점검점수와 점검의견 입력이 필수이며,
     * 임시저장('10')은 부분 입력을 허용한다.</p>
     */
    private void saveOrUpdateSelfChecks(String asctId, CouncilDto.FeasibilityRequest req) {
        if (req.selfChecks() == null || req.selfChecks().isEmpty()) {
            return;
        }
        // 작성완료(kpnTc='20') 시 전 항목 점검점수+점검의견 필수. 임시저장('10')은 부분 입력 허용.
        boolean isComplete = "20".equals(req.kpnTc());

        // 기존 자체점검을 항목코드 기준으로 1회 배치 조회 (항목별 개별 SELECT N+1 제거)
        Map<String, Bchklm> existingByItem = selfCheckRepository
                .findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                .collect(Collectors.toMap(c -> c.getItPtlCkgItmTc(), c -> c, (a, b) -> a));

        for (CouncilDto.SelfCheckItem item : req.selfChecks()) {
            // 작성완료 시 모든 항목 점검점수+점검의견 필수
            if (isComplete) {
                String itemNm = CHECK_ITEM_NAMES.getOrDefault(item.ckgItmC(), item.ckgItmC());
                if (item.ckgRcrd() == null) {
                    throw new IllegalArgumentException(
                            "자체점검은 모든 항목의 점검점수 입력이 필수입니다. 항목: " + itemNm);
                }
                if (item.ckgOpnn() == null || item.ckgOpnn().isBlank()) {
                    throw new IllegalArgumentException(
                            "자체점검은 모든 항목의 점검의견 입력이 필수입니다. 항목: " + itemNm);
                }
            }

            // upsert: 기존 있으면 update, 없으면 신규 INSERT
            Bchklm existing = existingByItem.get(item.ckgItmC());
            if (existing != null) {
                existing.update(item.ckgRcrd(), item.ckgOpnn());
            } else {
                Bchklm selfCheck = Bchklm.builder()
                        .itPtlAsctId(asctId)
                        .itPtlCkgItmTc(item.ckgItmC())
                        .quelRcrd(item.ckgRcrd())
                        .ckgOpnn(item.ckgOpnn())
                        .build();
                // 신규 INSERT는 persist()로 @PrePersist 발화 보장 (merge 분기 회귀 방지, §5.12.1.1)
                entityManager.persist(selfCheck);
            }
        }
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

        // P1 #2: DELETE를 INSERT 이전에 DB로 flush — 영속성 컨텍스트 동기화 순서를 명시 강제하여
        // 동일 복합 PK 재삽입 시 DELETE가 INSERT 뒤로 밀려 발생하는 PK 충돌/유령 행을 방지한다.
        entityManager.flush();

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
        // 신규 성과지표 INSERT를 즉시 flush — 제약 위반을 본 트랜잭션에서 조기 표면화
        entityManager.flush();
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
            Bpovwm overview, List<Bchklm> selfChecks, List<Bperfm> performances) {

        // 자체점검 변환
        List<CouncilDto.SelfCheckItemResponse> selfCheckResponses = selfChecks.stream()
                .map(c -> new CouncilDto.SelfCheckItemResponse(
                        c.getItPtlCkgItmTc(), c.getQuelRcrd(), c.getCkgOpnn()))
                .toList();

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
                overview.getKpnTpTc(), perfResponses, overview.getFlMpnId(), selfCheckResponses
        );
    }
}
