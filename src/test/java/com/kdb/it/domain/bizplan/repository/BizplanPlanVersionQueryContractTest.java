package com.kdb.it.domain.bizplan.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사업계획 목록이 재신청 초안의 BPLANA 관계를 업무 대상으로 읽지 않는지 고정합니다. */
class BizplanPlanVersionQueryContractTest {

    private static final Path REPOSITORY_SOURCE =
            Path.of(
                    "src",
                    "main",
                    "java",
                    "com",
                    "kdb",
                    "it",
                    "domain",
                    "bizplan",
                    "repository",
                    "BizplanRepositoryImpl.java");

    @Test
    @DisplayName("사업계획 목록은 BPLANA와 정확한 BPLANM 최종본 순번을 함께 조인한다")
    void 사업계획목록은계획최종본관계만읽는다() throws IOException {
        String source = Files.readString(REPOSITORY_SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("QBplanm plan = QBplanm.bplanm")
                .contains(".join(plan)")
                .contains("plan.reqDocNo.eq(pa.reqDocNo)")
                .contains("plan.sno.eq(pa.sno)")
                .contains("plan.lstYn.eq(\"Y\")")
                .contains("plan.delYn.eq(\"N\")");
    }
}
