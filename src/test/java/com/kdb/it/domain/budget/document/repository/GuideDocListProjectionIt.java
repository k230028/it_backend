package com.kdb.it.domain.budget.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 가이드 문서 목록 경량 프로젝션({@link GuideDocRepository.GuideDocListView})의 Oracle 통합 테스트.
 *
 * <p>목록 조회 프로젝션이 본문(CLOB) 없이 기존 엔티티 조회와 동등한 7개 필드·delYn 필터 결과를 반환하는지 검증합니다.
 */
@DisplayName("가이드 문서 목록 경량 프로젝션")
class GuideDocListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired private GuideDocRepository guideDocRepository;

    @Test
    @DisplayName("목록 프로젝션은 findAllByDelYn 엔티티 조회와 7개 필드가 동등하고 본문(CLOB)은 포함하지 않는다")
    void findListViewsByDelYn_matchesEntityQueryWithoutBody() {
        guideDocRepository.saveAll(
                List.of(
                        document("GDOC-BE03LIST-01", "첫번째 가이드문서", "N"),
                        document("GDOC-BE03LIST-02", "두번째 가이드문서", "N"),
                        document("GDOC-BE03LIST-DL", "삭제된 가이드문서", "Y")));
        guideDocRepository.flush();

        List<Bgdocm> activeEntities =
                guideDocRepository.findAllByDelYn("N").stream()
                        .filter(e -> e.getDocMngNo().startsWith("GDOC-BE03LIST-"))
                        .toList();
        List<GuideDocRepository.GuideDocListView> activeViews =
                guideDocRepository.findListViewsByDelYn("N").stream()
                        .filter(v -> v.getDocMngNo().startsWith("GDOC-BE03LIST-"))
                        .toList();

        // delYn='N' 필터는 엔티티 조회와 프로젝션 조회에서 동일하게 삭제행(DL)을 제외하고 2건만 남긴다
        assertThat(activeEntities).hasSize(2);
        assertThat(activeViews).hasSize(2);
        assertThat(activeViews).noneMatch(v -> v.getDocMngNo().equals("GDOC-BE03LIST-DL"));

        for (Bgdocm entity : activeEntities) {
            GuideDocRepository.GuideDocListView view =
                    activeViews.stream()
                            .filter(v -> v.getDocMngNo().equals(entity.getDocMngNo()))
                            .findFirst()
                            .orElseThrow();
            assertThat(view.getDocTtlCone()).isEqualTo(entity.getDocTtlCone());
            assertThat(view.getDelYn()).isEqualTo(entity.getDelYn());
            assertThat(view.getFstEnrDtm()).isEqualTo(entity.getFstEnrDtm());
            assertThat(view.getFstEnrUsid()).isEqualTo(entity.getFstEnrUsid());
            assertThat(view.getLstChgDtm()).isEqualTo(entity.getLstChgDtm());
            assertThat(view.getLstChgUsid()).isEqualTo(entity.getLstChgUsid());
        }

        // delYn='Y' 필터는 삭제된 문서만 반환한다 (본문 없는 프로젝션 필터 동등성)
        List<GuideDocRepository.GuideDocListView> deletedViews =
                guideDocRepository.findListViewsByDelYn("Y").stream()
                        .filter(v -> v.getDocMngNo().startsWith("GDOC-BE03LIST-"))
                        .toList();
        assertThat(deletedViews)
                .singleElement()
                .satisfies(v -> assertThat(v.getDocMngNo()).isEqualTo("GDOC-BE03LIST-DL"));

        // 프로젝션 인터페이스에는 본문(nacTxtInf) getter가 존재하지 않고 정확히 7개 getter만 선언되어 있다
        assertThat(
                        Arrays.stream(
                                        GuideDocRepository.GuideDocListView.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .containsExactlyInAnyOrder(
                        "getDocMngNo",
                        "getDocTtlCone",
                        "getDelYn",
                        "getFstEnrDtm",
                        "getFstEnrUsid",
                        "getLstChgDtm",
                        "getLstChgUsid");
        assertThat(GuideDocRepository.GuideDocListView.class.getDeclaredMethods()).hasSize(7);
    }

    private Bgdocm document(String docMngNo, String docTtlCone, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        return Bgdocm.builder()
                .docMngNo(docMngNo)
                .docTtlCone(docTtlCone)
                .nacTxtInf("<p>본문 " + docMngNo + "</p>")
                .delYn(delYn)
                .fstEnrUsid("BE03-TEST")
                .fstEnrDtm(now)
                .lstChgUsid("BE03-TEST")
                .lstChgDtm(now)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .build();
    }
}
