package com.kdb.it.domain.budget.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bprojm.UpdateCommand 레코드 단위 테스트 — DB-06
 *
 * <p>TDD RED: UpdateCommand record가 아직 존재하지 않으므로 컴파일 실패 상태.
 * update(UpdateCommand) 위임 메서드 구현 후 GREEN이 됩니다.</p>
 */
class BprojmUpdateCommandTest {

    @Test
    @DisplayName("UpdateCommand: 레코드로 Bprojm.update()를 호출하면 모든 필드가 올바르게 반영된다")
    void updateCommand_모든필드_올바르게설정() {
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-2026-0001")
                .build();

        Bprojm.UpdateCommand cmd = new Bprojm.UpdateCommand(
                "신규 프로젝트명", "신규개발", "주관부서A", "IT부서B",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "주관담당자A", "IT담당자B", "팀장A", "IT팀장B",
                "과장", "사업설명 내용", "현황 내용", "필요성 내용",
                "기대효과 내용", "문제 내용", "사업범위 내용", "추진경과 내용",
                "향후계획 내용", "리테일", "웹", "내부직원", "N",
                "20260630", "정상", "80", "진행중",
                "2026", "디지털본부", "N", "신규", null);

        project.update(cmd);

        assertThat(project.getAbusNm()).isEqualTo("신규 프로젝트명");
        assertThat(project.getBzTpC()).isEqualTo("신규개발");
        assertThat(project.getSvnDpmC()).isEqualTo("주관부서A");
        assertThat(project.getDvmDpmC()).isEqualTo("IT부서B");
        assertThat(project.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(project.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(project.getBseYy()).isEqualTo("2026");
        assertThat(project.getDplYn()).isEqualTo("N");
        assertThat(project.getOdnYn()).isEqualTo("N");
        assertThat(project.getAbusTc()).isEqualTo("신규");
        assertThat(project.getCncdRfrNo()).isNull();
        assertThat(project.getExePttYn()).isEqualTo("80");
    }
}
