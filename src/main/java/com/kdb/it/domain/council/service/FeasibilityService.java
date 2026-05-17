package com.kdb.it.domain.council.service;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bchklc;
import com.kdb.it.domain.council.entity.Bperfm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.FeasibilityCheckRepository;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
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
 *   <li>{@code BCHKLC}: 타당성 자체점검 6개 고정 항목 (1:N)</li>
 *   <li>{@code BPERFM}: 성과관리 자체계획 (1:N, 동적 추가/삭제)</li>
 * </ul>
 *
 * <p>저장 방식:</p>
 * <ul>
 *   <li>KPN_TP=TEMP: 임시저장 — 상태 DRAFT 유지, 빈 값 허용</li>
 *   <li>KPN_TP=COMPLETE: 작성완료 — 상태 SUBMITTED 전이, 첨부파일 필수</li>
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

    /** 사업개요 리포지토리 (TAAABB_BPOVWM) */
    private final ProjectOverviewRepository projectOverviewRepository;

    /** 타당성 자체점검 리포지토리 (TAAABB_BCHKLC) */
    private final FeasibilityCheckRepository feasibilityCheckRepository;

    /** 성과지표 리포지토리 (TAAABB_BPERFM) */
    private final PerformanceRepository performanceRepository;

    /** 협의회 기본 서비스 — 상태 전이 및 협의회 존재 확인용 */
    private final CouncilService councilService;

    /** JPA EntityManager — 성과지표 하드 딜리트 및 persist용 */
    @PersistenceContext
    private EntityManager entityManager;

    // 점검항목코드 → 한글명 매핑 (CCODEM CKG_ITM 기준)
    private static final Map<String, String> CHECK_ITEM_NAMES = Map.of(
        "001", "경영전략/계획 부합",
        "002", "재무 효과",
        "003", "리스크 개선 효과",
        "004", "평판/이미지 개선 효과",
        "005", "유사/중복 시스템 유무",
        "006", "기타"
    );

    // 6개 고정 점검항목 순서 (CKG_ITM 숫자코드)
    private static final List<String> CHECK_ITEM_ORDER =
        List.of("001", "002", "003", "004", "005", "006");

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
        java.util.Optional<Bpovwm> overviewOpt = projectOverviewRepository.findByAsctIdAndDelYn(asctId, "N");
        if (overviewOpt.isEmpty()) {
            return null;
        }

        // 자체점검 6개 항목 조회
        List<Bchklc> checkItems = feasibilityCheckRepository.findByAsctIdAndDelYn(asctId, "N");

        // 성과지표 목록 조회 (순번 오름차순)
        List<Bperfm> performances = performanceRepository.findByAsctIdAndDelYnOrderByDtpSnoAsc(asctId, "N");

        return toFeasibilityResponse(overviewOpt.get(), checkItems, performances);
    }

    // =========================================================================
    // 저장 (신규 + 수정 통합)
    // =========================================================================

    /**
     * 타당성검토표 저장 (임시저장 / 작성완료 공통)
     *
     * <p>신규 작성이면 INSERT, 기존 데이터가 있으면 UPDATE합니다.</p>
     *
     * <p>작성완료(KPN_TP=COMPLETE) 시 처리:</p>
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
        if ("COMPLETE".equals(request.kpnTp())) {
            validateAttachment(request.flMngNo());
        }

        // 사업개요 저장 (upsert)
        saveOrUpdateOverview(asctId, request);

        // 자체점검 저장 (upsert — 6개 항목)
        if (request.checkItems() != null && !request.checkItems().isEmpty()) {
            saveOrUpdateCheckItems(asctId, request.checkItems());
        }

        // 성과지표 저장 (전체 교체)
        if (request.performances() != null && !request.performances().isEmpty()) {
            replacePerformances(asctId, request.performances());
        }

        // 작성완료 시 상태 전이: DRAFT → SUBMITTED
        if ("COMPLETE".equals(request.kpnTp())) {
            councilService.changeStatus(asctId, "002");
        }
    }

    // =========================================================================
    // 내부 헬퍼 — 저장 로직
    // =========================================================================

    /**
     * 사업개요 신규 저장 또는 업데이트 (upsert)
     */
    private void saveOrUpdateOverview(String asctId, CouncilDto.FeasibilityRequest req) {
        projectOverviewRepository.findByAsctIdAndDelYn(asctId, "N")
                .ifPresentOrElse(
                    // 기존 데이터 있으면 update
                    existing -> existing.update(
                            req.prjNm(), req.prjTrm(), req.ncs(), req.prjBg(), req.edrt(),
                            req.prjDes(), req.lglRglYn(), req.lglRglNm(), req.xptEff(),
                            req.kpnTp(), req.flMngNo()),
                    // 없으면 신규 INSERT
                    () -> {
                        Bpovwm overview = Bpovwm.builder()
                                .asctId(asctId)
                                .prjNm(req.prjNm())
                                .prjTrm(req.prjTrm())
                                .ncs(req.ncs())
                                .prjBg(req.prjBg())
                                .edrt(req.edrt())
                                .prjDes(req.prjDes())
                                .lglRglYn(req.lglRglYn() != null ? req.lglRglYn() : "N")
                                .lglRglNm(req.lglRglNm())
                                .xptEff(req.xptEff())
                                .kpnTp(req.kpnTp())
                                .flMngNo(req.flMngNo())
                                .build();
                        projectOverviewRepository.save(overview);
                    }
                );
    }

    /**
     * 타당성 자체점검 6개 항목 저장 (upsert)
     *
     * <p>항목별로 기존 데이터 있으면 update, 없으면 신규 INSERT합니다.</p>
     */
    private void saveOrUpdateCheckItems(String asctId, List<CouncilDto.CheckItemRequest> requests) {
        for (CouncilDto.CheckItemRequest req : requests) {
            feasibilityCheckRepository
                    .findByAsctIdAndCkgItmCAndDelYn(asctId, req.ckgItmC(), "N")
                    .ifPresentOrElse(
                        existing -> existing.update(req.ckgCone(), req.ckgRcrd()),
                        () -> {
                            Bchklc item = Bchklc.builder()
                                    .asctId(asctId)
                                    .ckgItmC(req.ckgItmC())
                                    .ckgCone(req.ckgCone())
                                    .ckgRcrd(req.ckgRcrd())
                                    .build();
                            feasibilityCheckRepository.save(item);
                        }
                    );
        }
    }

    /**
     * 성과지표 전체 교체 (기존 하드 삭제 + 신규 INSERT)
     *
     * <p>동적 추가/삭제를 지원하기 위해 요청 목록으로 완전 교체합니다.</p>
     *
     * <p>소프트 딜리트(del_yn='Y') 방식을 사용하지 않는 이유:
     * BPERFM은 복합 PK(asctId, dtpSno)를 가지며 DEL_YN이 NOT NULL입니다.
     * soft-delete 후 동일 PK로 재삽입 시 JPA merge()가 del_yn=null로 덮어써
     * NOT NULL 제약 위반이 발생합니다. 하드 딜리트로 PK 충돌을 방지합니다.</p>
     */
    private void replacePerformances(String asctId, List<CouncilDto.PerformanceRequest> requests) {
        // 기존 성과지표 전체 하드 삭제 (JPQL — persistence context 및 DB 동시 반영)
        entityManager.createQuery("DELETE FROM Bperfm b WHERE b.asctId = :asctId")
                .setParameter("asctId", asctId)
                .executeUpdate();

        // 새 성과지표 INSERT (persist — @PrePersist 확실히 실행됨)
        for (CouncilDto.PerformanceRequest req : requests) {
            Bperfm perf = Bperfm.builder()
                    .asctId(asctId)
                    .dtpSno(req.dtpSno())
                    .dtpNm(req.dtpNm())
                    .dtpCone(req.dtpCone())
                    .msmManr(req.msmManr())
                    .clf(req.clf())
                    .glNv(req.glNv())
                    .msmSttDt(req.msmSttDt())
                    .msmEndDt(req.msmEndDt())
                    .msmTpm(req.msmTpm())
                    .msmCle(req.msmCle())
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
            Bpovwm overview, List<Bchklc> checkItems, List<Bperfm> performances) {

        // 자체점검 항목 변환 (고정 순서 유지)
        Map<String, Bchklc> checkMap = checkItems.stream()
                .collect(Collectors.toMap(Bchklc::getCkgItmC, c -> c));

        List<CouncilDto.CheckItemResponse> checkResponses = CHECK_ITEM_ORDER.stream()
                .map(code -> {
                    Bchklc item = checkMap.get(code);
                    return new CouncilDto.CheckItemResponse(
                            code,
                            CHECK_ITEM_NAMES.getOrDefault(code, code),
                            item != null ? item.getCkgCone() : null,
                            item != null ? item.getCkgRcrd() : null
                    );
                })
                .collect(Collectors.toList());

        // 성과지표 변환
        List<CouncilDto.PerformanceResponse> perfResponses = performances.stream()
                .map(p -> new CouncilDto.PerformanceResponse(
                        p.getDtpSno(), p.getDtpNm(), p.getDtpCone(), p.getMsmManr(),
                        p.getClf(), p.getGlNv(), p.getMsmSttDt(), p.getMsmEndDt(),
                        p.getMsmTpm(), p.getMsmCle()))
                .collect(Collectors.toList());

        return new CouncilDto.FeasibilityResponse(
                overview.getPrjNm(), overview.getPrjTrm(), overview.getNcs(),
                overview.getPrjBg(), overview.getEdrt(), overview.getPrjDes(),
                overview.getLglRglYn(), overview.getLglRglNm(), overview.getXptEff(),
                overview.getKpnTp(), checkResponses, perfResponses, overview.getFlMngNo()
        );
    }
}
