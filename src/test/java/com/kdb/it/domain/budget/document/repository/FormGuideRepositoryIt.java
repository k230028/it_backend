package com.kdb.it.domain.budget.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.support.AbstractOracleRepositoryTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class FormGuideRepositoryIt extends AbstractOracleRepositoryTest {

    @Autowired private GuideDocRepository guideDocRepository;

    @Test
    void
            findActiveFormGuides_returnsOnlyActiveFdocDocumentsWithNonBlankBodiesInTheRequestedScope() {
        guideDocRepository.saveAll(
                List.of(
                        document("FDOC-FG-INFO", "info.basic.abusNm", "<p>사업명 안내</p>", "N"),
                        document("FDOC-FG-COST", "cost.basic.abusNm", "<p>경상 사업명 안내</p>", "N"),
                        document("GDOC-FG-INFO", "info.basic.abusNm", "<p>기존 단계 가이드</p>", "N"),
                        document("FDOC-FG-DELETED", "info.overview.prjDes", "<p>삭제됨</p>", "Y"),
                        document("FDOC-FG-BLANK", "info.resource.ioe", "   ", "N")));
        guideDocRepository.flush();

        assertThat(guideDocRepository.findActiveFormGuides("FDOC-", "info."))
                .extracting(Bgdocm::getDocMngNo)
                .containsExactly("FDOC-FG-INFO");
    }

    @Test
    void findByDocTtlConeAndDocMngNoStartingWithAndDelYn_findsTheActiveFdocForItsCatalogId() {
        guideDocRepository.save(document("FDOC-FG-UNIQUE", "info.basic.abusNm", "<p>본문</p>", "N"));
        guideDocRepository.flush();

        assertThat(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                "info.basic.abusNm", "FDOC-", "N"))
                .hasValueSatisfying(
                        document -> assertThat(document.getDocMngNo()).isEqualTo("FDOC-FG-UNIQUE"));
    }

    private Bgdocm document(String docMngNo, String guideId, String content, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 12, 0);
        return Bgdocm.builder()
                .docMngNo(docMngNo)
                .docTtlCone(guideId)
                .nacTxtInf(content)
                .delYn(delYn)
                .fstEnrUsid("FORM-GUIDE")
                .fstEnrDtm(now)
                .lstChgUsid("FORM-GUIDE")
                .lstChgDtm(now)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .build();
    }
}
