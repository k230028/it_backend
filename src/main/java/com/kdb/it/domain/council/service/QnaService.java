package com.kdb.it.domain.council.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bpqnam;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.QnaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사전질의응답 서비스 (Step 2 — 협의회 개최준비)
 *
 * <p>협의회 개최 전 평가위원이 사전 질의를 등록하고,
 * 추진부서 담당자(ITPZZ001)가 답변합니다.</p>
 *
 * <p>QTN_ID 형식: {@code QTN-{asctId}-{2자리순번}}
 * (예: QTN-ASCT-2026-0001-01)</p>
 *
 * <p>설계 참조: §2.5 API 설계 — 질의응답 엔드포인트</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnaService {

    /** 사전질의응답 리포지토리 */
    private final QnaRepository qnaRepository;

    /** 협의회 기본정보 리포지토리 (존재 여부 검증용) */
    private final CouncilRepository councilRepository;

    /** 정보화사업 리포지토리 — 답변 권한(주관부서) 검증용 */
    private final ProjectRepository projectRepository;

    /** JPA EntityManager — 질의 신규 INSERT persist용 (§5.12.1.1) */
    @PersistenceContext
    private EntityManager entityManager;

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 사전질의응답 목록 조회
     *
     * <p>삭제되지 않은 항목을 등록일시 오름차순으로 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 질의응답 목록 (미답변 + 답변완료 포함)
     */
    public List<CouncilDto.QnaResponse> getQnaList(String asctId) {
        /* 협의회 존재 여부 검증 */
        if (!councilRepository.existsById(asctId)) {
            throw new IllegalArgumentException("존재하지 않는 협의회입니다: " + asctId);
        }

        return qnaRepository
                .findByItPtlAsctIdAndDelYnOrderByFstEnrDtmAsc(asctId, "N")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================================
    // 등록/답변
    // =========================================================================

    /**
     * 사전 질의 등록 (평가위원)
     *
     * <p>QTN_ID를 자동 채번하여 새 질의를 저장합니다.
     * REP_YN='N' (미답변) 상태로 등록됩니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     질의 등록 요청
     * @param userDetails 로그인한 평가위원
     * @return 생성된 질의응답ID
     */
    @Transactional
    public String createQna(String asctId, CouncilDto.QnaCreateRequest request, CustomUserDetails userDetails) {
        /* 협의회 존재 검증 + 채번 직렬화: 부모 협의회 행 비관적 잠금
         * (동일 협의회 동시 등록 시 QTN_ID 순번 충돌 방지) */
        councilRepository.findByIdForUpdate(asctId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 협의회입니다: " + asctId));

        /* QTN_ID 채번: QTN-{asctId}-{2자리순번} */
        int seq = qnaRepository.getNextQtnSeq(asctId);
        String qtnId = String.format("QTN-%s-%02d", asctId, seq);

        Bpqnam qna = Bpqnam.builder()
                .qtnId(qtnId)
                .itPtlAsctId(asctId)
                .qtnDwuUsid(userDetails.getEno())
                .qtnCone(request.qtnCone())
                .qtnRpdRltYn("N")
                .build();

        // 신규 INSERT는 persist()로 @PrePersist 발화 보장 (merge 분기 회귀 방지, §5.12.1.1)
        entityManager.persist(qna);
        return qtnId;
    }

    /**
     * 사전 질의 수정 (질의 등록자)
     *
     * <p>질의 내용(QTN_CONE)을 업데이트합니다.
     * 본인이 등록한 질의만 수정할 수 있습니다.</p>
     *
     * @param asctId      협의회ID
     * @param qtnId       질의응답ID
     * @param request     질의 수정 요청
     * @param userDetails 로그인한 사용자
     */
    @Transactional
    public void updateQna(String asctId, String qtnId, CouncilDto.QnaUpdateRequest request, CustomUserDetails userDetails) {
        Bpqnam qna = qnaRepository.findById(qtnId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 질의응답입니다: " + qtnId));

        if (!qna.getItPtlAsctId().equals(asctId)) {
            throw new IllegalArgumentException("협의회ID가 일치하지 않습니다.");
        }

        /* 본인 또는 관리자만 수정 가능 */
        OwnershipVerifier.verifyOwnerOrAdmin(qna.getQtnDwuUsid(), userDetails);

        qna.updateQuestion(request.qtnCone());
    }

    /**
     * 사전 질의 답변 (추진부서 담당자)
     *
     * <p>REP_ENO, REP_CONE을 업데이트하고 REP_YN='Y'로 변경합니다.</p>
     *
     * @param asctId      협의회ID
     * @param qtnId       질의응답ID
     * @param request     답변 요청
     * @param userDetails 로그인한 담당자
     */
    @Transactional
    public void replyQna(String asctId, String qtnId, CouncilDto.QnaReplyRequest request, CustomUserDetails userDetails) {
        Bpqnam qna = qnaRepository.findById(qtnId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 질의응답입니다: " + qtnId));

        /* 해당 협의회 소속 여부 검증 */
        if (!qna.getItPtlAsctId().equals(asctId)) {
            throw new IllegalArgumentException("협의회ID가 일치하지 않습니다.");
        }

        /*
         * 답변 권한 검증 — 답변은 사업 주관부서(추진부서) 담당자만 등록 가능. (리뷰 1-4)
         * 판정 가능할 때만(주관부서·요청자 부서가 모두 확인될 때) 부서 일치를 강제하고,
         * 데이터가 불완전하면(부서 미확인) 기존 동작을 보존한다.
         */
        Basctm council = councilRepository.findByItPtlAsctIdAndDelYn(asctId, "N").orElse(null);
        String svnDpm = council == null ? null
                : projectRepository.findByAbusMngNoAndLstYnAndDelYn(council.getAbusMngNo(), "Y", "N")
                        .map(project -> project.getSvnDpmC()).orElse(null);
        String bbrC = userDetails.getBbrC();
        if (svnDpm != null && bbrC != null && !svnDpm.equals(bbrC)) {
            throw new AccessDeniedException("답변은 사업 주관부서 담당자만 등록할 수 있습니다.");
        }

        qna.reply(userDetails.getEno(), request.repCone());
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * Bpqnam 엔티티 → QnaResponse DTO 변환
     *
     * @param qna 질의응답 엔티티
     * @return 응답 DTO
     */
    private CouncilDto.QnaResponse toResponse(Bpqnam qna) {
        return new CouncilDto.QnaResponse(
                qna.getQtnId(),
                qna.getQtnDwuUsid(),
                null,  // 사용자명은 별도 조회 (M10 UI에서 필요 시 추가)
                qna.getQtnCone(),
                qna.getRepDwuUsid(),
                null,  // 답변자명
                qna.getRepCone(),
                qna.getQtnRpdRltYn()
        );
    }
}
