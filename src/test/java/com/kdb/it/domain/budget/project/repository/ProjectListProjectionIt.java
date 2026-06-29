package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("#7 정보화사업 목록 경량 프로젝션 동등성 + 대용량 텍스트 제외")
class ProjectListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired
    ProjectRepository projectRepository;

    @Test
    @DisplayName("경량 목록 행의 식별/요약 필드가 엔티티 경로와 일치한다")
    void lightList_matches_entityPath_onListedFields() {
        // 전체 조건(필터 없음) — 동일 WHERE(DEL_YN='N')에서 두 경로 비교
        ProjectDto.SearchCondition cond = new ProjectDto.SearchCondition();
        List<Bprojm> entities = projectRepository.searchByCondition(cond);
        List<ProjectDto.ProjectListRow> rows = projectRepository.searchListByCondition(cond);

        assertThat(rows).hasSameSizeAs(entities);

        Map<String, Bprojm> byKey = entities.stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getAbusMngNo() + "#" + e.getSno(), Function.identity(), (a, b) -> a));
        for (ProjectDto.ProjectListRow row : rows) {
            Bprojm e = byKey.get(row.abusMngNo() + "#" + row.sno());
            assertThat(e).as("동일 키 엔티티 존재").isNotNull();
            assertThat(row.abusNm()).isEqualTo(e.getAbusNm());
            assertThat(row.bzTpC()).isEqualTo(e.getBzTpC());
            assertThat(row.svnDpmC()).isEqualTo(e.getSvnDpmC());
            assertThat(row.dvmDpmC()).isEqualTo(e.getDvmDpmC());
            assertThat(row.sttDtm()).isEqualTo(e.getSttDtm());
            assertThat(row.endDtm()).isEqualTo(e.getEndDtm());
            assertThat(row.bseYy()).isEqualTo(e.getBseYy());
            assertThat(row.odnYn()).isEqualTo(e.getOdnYn());
            assertThat(row.abusTc()).isEqualTo(e.getAbusTc());
            assertThat(row.rprStsTc()).isEqualTo(e.getRprStsTc());
            assertThat(row.delYn()).isEqualTo(e.getDelYn());
        }
    }
}
