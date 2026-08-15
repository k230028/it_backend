package com.kdb.it.common.i18n.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClangmRepositoryIt extends AbstractOracleRepositoryTest {

    @Autowired ClangmRepository repository;

    @Test
    void 실제_Oracle에서_복합키와_활성번역_필터를_검증한다() {
        String targetKey = "IT-I18N-" + UUID.randomUUID();
        repository.saveAllAndFlush(
                List.of(
                        translation(targetKey, TranslationColumns.MNU_NM, "Dashboard", "N"),
                        translation(
                                targetKey + "-DEL", TranslationColumns.MNU_NM, "Deleted", "Y")));

        assertThat(
                        repository.findActiveByTargetAndLanguageAndKeys(
                                "메뉴", "en", List.of(targetKey, targetKey + "-DEL")))
                .extracting(Clangm::getTcDes)
                .containsExactly("Dashboard");
    }

    private static Clangm translation(
            String targetKey, String columnName, String text, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 15, 12, 0);
        return Clangm.builder()
                .tcIdCone(targetKey)
                .dttLanC("en")
                .tcColNm(columnName)
                .tcDes(text)
                .dttNm("메뉴")
                .delYn(delYn)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .fstEnrUsid("TEST")
                .fstEnrDtm(now)
                .lstChgUsid("TEST")
                .lstChgDtm(now)
                .build();
    }
}
