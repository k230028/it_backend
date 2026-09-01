package com.kdb.it.domain.budget.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectResponseMapperTest {

    @Test
    @DisplayName("BPROJM 전체 다운로드에 필요한 조직 코드와 스냅샷 이름을 응답에 보존한다")
    void mapsOrganizationFieldsNeededByFullProjectExport() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnTemC("T100")
                        .svnDpmNm("IT기획부")
                        .svnTemNm("전략팀")
                        .dvmTemC("T200")
                        .build();

        ProjectDto.Response response = ProjectResponseMapper.fromEntity(project);

        assertThat(response.getSvnTemC()).isEqualTo("T100");
        assertThat(response.getSvnDpmNm()).isEqualTo("IT기획부");
        assertThat(response.getSvnTemNm()).isEqualTo("전략팀");
        assertThat(response.getDvmTemC()).isEqualTo("T200");
    }
}
