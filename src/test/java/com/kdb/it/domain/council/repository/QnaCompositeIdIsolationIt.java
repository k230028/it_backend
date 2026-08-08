package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.council.entity.Bmqnam;
import com.kdb.it.domain.council.entity.BmqnamId;
import com.kdb.it.domain.council.entity.Bpqnam;
import com.kdb.it.domain.council.entity.BpqnamId;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 동일 부분키를 가진 협의회 질의 행의 조회·수정·삭제 격리를 실제 Oracle에서 검증한다. */
class QnaCompositeIdIsolationIt extends AbstractOracleRepositoryTest {

    @Autowired QnaRepository qnaRepository;
    @Autowired MainQnaRepository mainQnaRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("사전 질의는 동일 질의ID라도 협의회ID별로 조회·수정·삭제가 격리된다")
    void preQna_isolatedByCouncilAndQuestionId() {
        String suffix = suffix();
        String councilA = "BE25-PA-" + suffix;
        String councilB = "BE25-PB-" + suffix;
        String questionId = "BE25-PQ-" + suffix;
        qnaRepository.saveAll(
                List.of(
                        preQna(councilA, questionId, "A 질문"),
                        preQna(councilB, questionId, "B 질문")));
        flushAndClear();

        Bpqnam rowA = qnaRepository.findById(new BpqnamId(councilA, questionId)).orElseThrow();
        Bpqnam rowB = qnaRepository.findById(new BpqnamId(councilB, questionId)).orElseThrow();
        rowA.updateQuestion("A 수정");
        qnaRepository.flush();
        entityManager.clear();

        assertThat(qnaRepository.findById(new BpqnamId(councilA, questionId)))
                .get()
                .extracting(Bpqnam::getQtnCone)
                .isEqualTo("A 수정");
        assertThat(qnaRepository.findById(new BpqnamId(councilB, questionId)))
                .get()
                .extracting(Bpqnam::getQtnCone)
                .isEqualTo("B 질문");

        qnaRepository.deleteById(new BpqnamId(councilA, questionId));
        flushAndClear();

        assertThat(qnaRepository.findById(new BpqnamId(councilA, questionId))).isEmpty();
        assertThat(qnaRepository.findById(new BpqnamId(councilB, questionId))).isPresent();
    }

    @Test
    @DisplayName("본회의 질의는 동일 질의ID라도 협의회ID별로 조회·수정·삭제가 격리된다")
    void mainQna_isolatedByCouncilAndQuestionId() {
        String suffix = suffix();
        String councilA = "BE25-MA-" + suffix;
        String councilB = "BE25-MB-" + suffix;
        String questionId = "BE25-MQ-" + suffix;
        mainQnaRepository.saveAll(
                List.of(
                        mainQna(councilA, questionId, "A 질문"),
                        mainQna(councilB, questionId, "B 질문")));
        flushAndClear();

        Bmqnam rowA = mainQnaRepository.findById(new BmqnamId(councilA, questionId)).orElseThrow();
        rowA.updateQuestion("A 수정");
        mainQnaRepository.flush();
        entityManager.clear();

        assertThat(mainQnaRepository.findById(new BmqnamId(councilA, questionId)))
                .get()
                .extracting(Bmqnam::getQtnCone)
                .isEqualTo("A 수정");
        assertThat(mainQnaRepository.findById(new BmqnamId(councilB, questionId)))
                .get()
                .extracting(Bmqnam::getQtnCone)
                .isEqualTo("B 질문");

        mainQnaRepository.deleteById(new BmqnamId(councilA, questionId));
        flushAndClear();

        assertThat(mainQnaRepository.findById(new BmqnamId(councilA, questionId))).isEmpty();
        assertThat(mainQnaRepository.findById(new BmqnamId(councilB, questionId))).isPresent();
    }

    private Bpqnam preQna(String councilId, String questionId, String question) {
        return Bpqnam.builder()
                .itPtlAsctId(councilId)
                .qtnId(questionId)
                .qtnCone(question)
                .qtnDwuUsid("BE25-TEST")
                .qtnRpdRltYn("N")
                .fstEnrDtm(LocalDateTime.now())
                .fstEnrUsid("BE25-TEST")
                .lstChgDtm(LocalDateTime.now())
                .lstChgUsid("BE25-TEST")
                .build();
    }

    private Bmqnam mainQna(String councilId, String questionId, String question) {
        return Bmqnam.builder()
                .itPtlAsctId(councilId)
                .qtnId(questionId)
                .qtnCone(question)
                .qtnDwuUsid("BE25-TEST")
                .qtnRpdRltYn("N")
                .fstEnrDtm(LocalDateTime.now())
                .fstEnrUsid("BE25-TEST")
                .lstChgDtm(LocalDateTime.now())
                .lstChgUsid("BE25-TEST")
                .build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
