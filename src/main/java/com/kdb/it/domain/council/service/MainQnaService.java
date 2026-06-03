package com.kdb.it.domain.council.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bmqnam;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.MainQnaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 본회의 질의응답 서비스 (PRD §26)
 *
 * <p>협의회 본회의 동안 오간 Q&A를 IT관리자(ITPAD001)가 정리해
 * 질문/답변 항목 단위로 등록·수정합니다. 평가위원은 등록된 본회의 Q&A를
 * 참고하여 평가의견(1~5점)을 작성합니다.</p>
 *
 * <p>구조와 메서드 시그니처는 사전질의응답 서비스({@link QnaService})와
 * 동일하게 유지하되, 권한 정책만 IT관리자 단일 작성으로 단순화됩니다.</p>
 *
 * <p>QTN_ID 형식: {@code MQT-{asctId}-{2자리순번}}
 * (예: MQT-ASCT-2026-0001-01)</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainQnaService {

    /** 본회의 질의응답 리포지토리 */
    private final MainQnaRepository mainQnaRepository;

    /** 협의회 기본정보 리포지토리 (존재 여부 검증용) */
    private final CouncilRepository councilRepository;

    /**
     * JPA EntityManager — 신규 INSERT 전용 persist() 호출 (PRD §15 패턴 일관 유지).
     *
     * <p>JpaRepository.save()는 ID 채워진 detached entity에 대해 merge() 분기로 빠져
     * BaseEntity 필드(delYn 등)를 null로 덮어쓰는 회귀가 있어 직접 persist를 사용합니다.</p>
     */
    @PersistenceContext
    private EntityManager entityManager;

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 본회의 질의응답 목록 조회
     *
     * <p>삭제되지 않은 항목을 등록일시 오름차순으로 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 본회의 질의응답 목록
     */
    public List<CouncilDto.QnaResponse> getMainQnaList(String asctId) {
        if (!councilRepository.existsById(asctId)) {
            throw new IllegalArgumentException("존재하지 않는 협의회입니다: " + asctId);
        }
        return mainQnaRepository
                .findByAsctIdAndDelYnOrderByFstEnrDtmAsc(asctId, "N")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================================
    // 등록/수정/답변
    // =========================================================================

    /**
     * 본회의 질의 등록 (IT관리자)
     *
     * <p>QTN_ID를 자동 채번하여 새 질의를 저장합니다.
     * REP_YN='N' (미답변) 상태로 등록되며, 이후 같은 IT관리자가 답변까지 정리할 수 있습니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     질의 등록 요청
     * @param userDetails 로그인한 IT관리자
     * @return 생성된 질의응답ID
     */
    @Transactional
    public String createMainQna(String asctId, CouncilDto.QnaCreateRequest request,
                                CustomUserDetails userDetails) {
        /* 협의회 존재 검증 + 채번 직렬화: 부모 협의회 행 비관적 잠금
         * (동일 협의회 동시 등록 시 MQT_ID 순번 충돌 방지) */
        councilRepository.findByIdForUpdate(asctId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 협의회입니다: " + asctId));

        int seq = mainQnaRepository.getNextQtnSeq(asctId);
        String qtnId = String.format("MQT-%s-%02d", asctId, seq);

        Bmqnam qna = Bmqnam.builder()
                .qtnId(qtnId)
                .asctId(asctId)
                .qtnEno(userDetails.getEno())
                .qtnCone(request.qtnCone())
                .repYn("N")
                .build();

        /* PRD §15 회귀 방지: persist()로 직접 INSERT (@PrePersist 발화) */
        entityManager.persist(qna);
        return qtnId;
    }

    /**
     * 본회의 질의 수정 (IT관리자)
     *
     * <p>질의 내용(QTN_CONE)을 업데이트합니다.</p>
     *
     * @param asctId  협의회ID
     * @param qtnId   질의응답ID
     * @param request 질의 수정 요청
     */
    @Transactional
    public void updateMainQna(String asctId, String qtnId,
                              CouncilDto.QnaUpdateRequest request) {
        Bmqnam qna = mainQnaRepository.findById(qtnId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 본회의 질의응답입니다: " + qtnId));

        if (!qna.getAsctId().equals(asctId)) {
            throw new IllegalArgumentException("협의회ID가 일치하지 않습니다.");
        }

        qna.updateQuestion(request.qtnCone());
    }

    /**
     * 본회의 답변 등록/수정 (IT관리자)
     *
     * <p>REP_ENO, REP_CONE을 업데이트하고 REP_YN='Y'로 변경합니다.</p>
     *
     * @param asctId      협의회ID
     * @param qtnId       질의응답ID
     * @param request     답변 요청
     * @param userDetails 로그인한 IT관리자
     */
    @Transactional
    public void replyMainQna(String asctId, String qtnId,
                             CouncilDto.QnaReplyRequest request,
                             CustomUserDetails userDetails) {
        Bmqnam qna = mainQnaRepository.findById(qtnId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 본회의 질의응답입니다: " + qtnId));

        if (!qna.getAsctId().equals(asctId)) {
            throw new IllegalArgumentException("협의회ID가 일치하지 않습니다.");
        }

        qna.reply(userDetails.getEno(), request.repCone());
    }

    /**
     * 본회의 질의응답 삭제 (Soft Delete, IT관리자)
     *
     * @param asctId 협의회ID
     * @param qtnId  질의응답ID
     */
    @Transactional
    public void deleteMainQna(String asctId, String qtnId) {
        Bmqnam qna = mainQnaRepository.findById(qtnId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 본회의 질의응답입니다: " + qtnId));

        if (!qna.getAsctId().equals(asctId)) {
            throw new IllegalArgumentException("협의회ID가 일치하지 않습니다.");
        }

        qna.delete();
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private CouncilDto.QnaResponse toResponse(Bmqnam qna) {
        return new CouncilDto.QnaResponse(
                qna.getQtnId(),
                qna.getQtnEno(),
                null,
                qna.getQtnCone(),
                qna.getRepEno(),
                null,
                qna.getRepCone(),
                qna.getRepYn()
        );
    }
}
