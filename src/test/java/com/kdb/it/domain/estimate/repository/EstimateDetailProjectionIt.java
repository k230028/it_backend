package com.kdb.it.domain.estimate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 소요예산 상세 응답 전용 프로젝션({@link EstimateRepository.EstimateDetailView}) Oracle 통합 테스트.
 *
 * <p>같은 데이터로 기존 엔티티 조회({@link EstimateRepository#findByRqmBgReqDocNoAndLstYnAndDelYn})와 view
 * 조회({@link EstimateRepository#findDetailViewByRqmBgReqDocNoAndLstYnAndDelYn})의 결과가 응답이 실제 사용하는 7개
 * 필드에서 동등한지, 그리고 문서 미존재 시 두 조회 모두 empty 계약을 지키는지 검증한다.
 */
class EstimateDetailProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired private EstimateRepository estimateRepository;

    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("엔티티 조회와 view 조회가 상세 응답용 7개 필드에서 동일한 값을 반환한다")
    void entityAndViewReturnEquivalentDetailFields() {
        // Arrange
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String docNo = "REQ-TST-" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 9, 0);
        Bestim bestim =
                Bestim.builder()
                        .rqmBgReqDocNo(docNo)
                        .docVrsSno(1)
                        .lstYn("Y")
                        .cncdRfrNo("PRJ-TST-" + suffix)
                        .stsTc("55")
                        .reqCone("BE-03 프로젝션 검증용 요청내용")
                        .fstEnrDtm(now)
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(now)
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build();
        entityManager.persist(bestim);
        entityManager.flush();
        entityManager.clear();

        // Act
        Bestim entity =
                estimateRepository
                        .findByRqmBgReqDocNoAndLstYnAndDelYn(docNo, "Y", "N")
                        .orElseThrow();
        EstimateRepository.EstimateDetailView view =
                estimateRepository
                        .findDetailViewByRqmBgReqDocNoAndLstYnAndDelYn(docNo, "Y", "N")
                        .orElseThrow();

        // Assert: 응답이 실제 사용하는 7개 필드 동등성
        assertThat(view.getRqmBgReqDocNo()).isEqualTo(entity.getRqmBgReqDocNo());
        assertThat(view.getDocVrsSno()).isEqualTo(entity.getDocVrsSno());
        assertThat(view.getCncdRfrNo()).isEqualTo(entity.getCncdRfrNo());
        assertThat(view.getStsTc()).isEqualTo(entity.getStsTc());
        assertThat(view.getReqCone()).isEqualTo(entity.getReqCone());
        assertThat(view.getFstEnrUsid()).isEqualTo(entity.getFstEnrUsid());
        assertThat(view.getFstEnrDtm()).isEqualTo(entity.getFstEnrDtm());
    }

    @Test
    @DisplayName("존재하지 않는 문서번호는 엔티티 조회와 view 조회 모두 empty를 반환한다")
    void bothQueriesReturnEmptyWhenDocumentMissing() {
        String missingDocNo = "REQ-TST-NOT-EXISTS-" + UUID.randomUUID();

        Optional<Bestim> entity =
                estimateRepository.findByRqmBgReqDocNoAndLstYnAndDelYn(missingDocNo, "Y", "N");
        Optional<EstimateRepository.EstimateDetailView> view =
                estimateRepository.findDetailViewByRqmBgReqDocNoAndLstYnAndDelYn(
                        missingDocNo, "Y", "N");

        assertThat(entity).isEmpty();
        assertThat(view).isEmpty();
    }
}
