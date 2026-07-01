package com.kdb.it.domain.council.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Baskpm;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.repository.BaskpmRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 협의회 타당성검토 생략 판정 워크플로우 서비스. (PRD_c_20260620 #3)
 *
 * <p>흐름: 정보보호기획(ITPAD002)이 결재완료(04) 정보보호시스템(dbrTc=04) 협의회에 대해
 * 생략 판정 요청을 등록 → IT기획(ITPAD001)이 생략여부를 확인하고 전자결재(팀장→부장) 상신 →
 * 결재 완료 콜백에서 생략(Y)이면 {@code skipCouncil}, 개최(N)이면 {@code startPreparation}으로 분기,
 * 매 분기마다 요청자(정보보호기획)에게 통보합니다.</p>
 *
 * <p>진행상태 컬럼은 두지 않고, CNFM_DTM(확인일시)·PRTY_IVG_OMT_YN(확인결과)·결재/협의회 상태로 파생합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouncilSkipService {

    private static final Logger log = LoggerFactory.getLogger(CouncilSkipService.class);

    /** 전자결재 원본 연결 테이블코드 (Cappla.fntTbNm) */
    static final String ORC_TB_CD = "BASKPM";

    /** 정보보호시스템 심의유형 코드 */
    private static final String DBR_INFO_SEC = "04";
    /** 결재완료(APPROVED) 협의회 상태 */
    private static final String STS_APPROVED = "04";

    private final BaskpmRepository baskpmRepository;
    private final CouncilService councilService;
    private final ApplicationService applicationService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    /**
     * 생략 판정 요청 등록 (정보보호기획).
     *
     * @param asctId      협의회ID
     * @param req         요청 내용 (사유코드/설명/첨부 2종)
     * @param userDetails 요청자 (정보보호관리자 ITPAD002)
     * @throws IllegalStateException 협의회가 정보보호시스템(04)·결재완료(04)가 아니거나 이미 요청이 있는 경우
     * @throws AccessDeniedException 정보보호관리자가 아닌 경우
     */
    @Transactional
    public void createSkipRequest(String asctId, CouncilDto.SkipRequestCreate req, CustomUserDetails userDetails) {
        Basctm council = councilService.findActiveCouncil(asctId);

        if (!DBR_INFO_SEC.equals(council.getItPtlAsctDbrTc())) {
            throw new IllegalStateException("정보보호시스템(04) 협의회만 생략 판정 요청이 가능합니다.");
        }
        if (!STS_APPROVED.equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                "생략 판정 요청은 결재완료(04) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }
        if (!userDetails.isInfoSecAdmin()) {
            throw new AccessDeniedException("정보보호관리자(ITPAD002)만 생략 판정 요청을 보낼 수 있습니다.");
        }
        baskpmRepository.findByItPtlAsctIdAndDelYn(asctId, "N").ifPresent(b -> {
            throw new IllegalStateException("이미 진행 중인 생략 판정 요청이 있습니다.");
        });

        // 타당성검토표는 협의회 구조화 데이터(Bpovwm)로 활용하므로 별도 파일참조 없음. 첨부는 사업계획서 1종.
        // 신규 INSERT 보장(@PrePersist 발화 → 감사로그 스냅샷 정합) — it_backend §5.12.1.1
        Baskpm baskpm = Baskpm.create(
                asctId, req.rsnTc(), req.rsn(), req.flMpnId(), userDetails.getEno());
        entityManager.persist(baskpm);
        log.info("[생략판정요청] 등록 - asctId={}, rqsUsid={}, rsnTc={}", asctId, userDetails.getEno(), req.rsnTc());
    }

    /**
     * 생략여부 판정 + 전자결재 상신 (IT기획).
     *
     * @param asctId      협의회ID
     * @param req         판정 내용 (생략여부/확인사유/결재선)
     * @param userDetails 판정자 (IT관리자 ITPAD001)
     * @throws AccessDeniedException IT관리자가 아닌 경우
     * @throws IllegalStateException 요청이 없거나 이미 판정된 경우
     */
    @Transactional
    public void submitDecision(String asctId, CouncilDto.SkipDecisionRequest req, CustomUserDetails userDetails) {
        if (!userDetails.isAdmin()) {
            throw new AccessDeniedException("IT관리자(ITPAD001)만 생략 판정을 할 수 있습니다.");
        }
        Baskpm baskpm = baskpmRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .orElseThrow(() -> new IllegalArgumentException("생략 판정 요청이 존재하지 않습니다: " + asctId));
        if (baskpm.getCnfmDtm() != null) {
            throw new IllegalStateException("이미 판정·상신된 요청입니다.");
        }
        if (req.approverEnos() == null || req.approverEnos().isEmpty()) {
            throw new IllegalArgumentException("결재선(IT기획팀장·부장)을 지정해야 합니다.");
        }

        // 전자결재 상신: 원본 연결 BASKPM → asctId, 결재선 = 요청에 지정된 팀장→부장
        ApplicationDto.OrcItem orcItem = new ApplicationDto.OrcItem();
        orcItem.setFntTbNm(ORC_TB_CD);
        orcItem.setPkColNm(asctId);
        orcItem.setFntTbCrySno(null);

        ApplicationDto.CreateRequest createRequest = new ApplicationDto.CreateRequest();
        createRequest.setApfNm("협의회 타당성검토 생략 판정 - " + asctId);
        createRequest.setApfDtlCone(null);
        createRequest.setRqsEno(userDetails.getEno());
        createRequest.setRqsOpnn(req.cnfmCone());
        createRequest.setOrcItems(List.of(orcItem));
        createRequest.setApproverEnos(req.approverEnos());

        String apfMngNo = applicationService.submit(createRequest);

        baskpm.submitForDecision(req.omtYn(), req.cnfmCone(), userDetails.getEno(), apfMngNo);
        log.info("[생략판정요청] 판정·상신 - asctId={}, omtYn={}, apfMngNo={}", asctId, req.omtYn(), apfMngNo);
    }

    /**
     * 전자결재 완료 콜백 처리 (이벤트 리스너에서 호출).
     *
     * <p>결재완료 시 생략여부에 따라 분기:
     * 생략(Y) → {@code skipCouncil}, 개최(N) → {@code startPreparation}. 반려 시 통보만.</p>
     *
     * @param asctId   협의회ID
     * @param approved 결재완료=true / 반려=false
     */
    @Transactional
    public void handleApprovalCompleted(String asctId, boolean approved) {
        Baskpm baskpm = baskpmRepository.findByItPtlAsctIdAndDelYn(asctId, "N").orElse(null);
        if (baskpm == null) {
            log.warn("[생략판정요청] 콜백 대상 요청 없음 - asctId={}", asctId);
            return;
        }
        String recipient = baskpm.getRqsUsid();

        if (!approved) {
            notify(recipient, "생략 판정 반려", "타당성검토 생략 판정 요청이 반려되었습니다.", asctId);
            return;
        }

        if ("Y".equals(baskpm.getPrtyIvgOmtYn())) {
            councilService.skipCouncil(asctId);
            notify(recipient, "협의회 생략 확정",
                    "타당성검토 생략 판정이 결재 완료되어 협의회가 생략 처리되었습니다.", asctId);
        } else {
            councilService.startPreparation(asctId);
            notify(recipient, "협의회 개최 진행",
                    "타당성검토 생략 판정 결과 '개최'로 결재 완료되어 개최준비로 진행합니다.", asctId);
        }
    }

    /**
     * 협의회별 생략 판정 요청 단건 조회. 없으면 null.
     *
     * @param asctId 협의회ID
     */
    public CouncilDto.SkipRequestResponse getSkipRequest(String asctId) {
        return baskpmRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * 활성 생략 판정 요청 전체 조회 (IT기획 판정함 — 협의회 목록 배지/판정용).
     */
    public List<CouncilDto.SkipRequestResponse> getActiveSkipRequests() {
        return baskpmRepository.findByDelYn("N").stream()
                .map(this::toResponse)
                .toList();
    }

    /** Baskpm → 응답 DTO 변환. */
    private CouncilDto.SkipRequestResponse toResponse(Baskpm b) {
        return new CouncilDto.SkipRequestResponse(
                b.getItPtlAsctId(), b.getPrtyIvgOmtRsnTc(), b.getCgprOpnnCone(),
                b.getFlMpnId(), b.getRqsUsid(), b.getRqsDtm(),
                b.getCnfmDtm() != null,
                b.getPrtyIvgOmtYn(), b.getCgprRpdCone(), b.getCnfmUsid(), b.getCnfmDtm(), b.getApfMngNo());
    }

    /** 요청자(정보보호기획)에게 결재 결과 통보. */
    private void notify(String recipientEno, String ttl, String msg, String asctId) {
        if (recipientEno == null || recipientEno.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new NotificationEvent(
                recipientEno,
                NotificationEvent.TYPE_APPROVAL_RESULT,
                ttl,
                msg,
                "/info/council-request/" + asctId,
                null));
    }
}
