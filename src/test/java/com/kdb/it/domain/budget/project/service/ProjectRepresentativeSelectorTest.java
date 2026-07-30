package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ProjectRepresentativeSelector 단위 테스트 (BE-17 결정 #3: LST_YN='Y' 행만 대표, 없으면 empty) */
class ProjectRepresentativeSelectorTest {

    @Test
    @DisplayName("pickLatest - LST_YN='Y' 행이 리스트 뒤에 있어도 선택된다")
    void pickLatest_lstYnY행선택() {
        Bprojm oldVersion =
                Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("N").abusNm("구버전명").build();
        Bprojm latest = Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("Y").abusNm("최신명").build();

        Optional<Bprojm> result =
                ProjectRepresentativeSelector.pickLatest(List.of(oldVersion, latest));

        assertThat(result).isPresent();
        assertThat(result.get().getAbusNm()).isEqualTo("최신명");
    }

    @Test
    @DisplayName("pickLatest - LST_YN='Y' 행이 없으면 empty (호출부에서 관리번호 폴백)")
    void pickLatest_lstYnY없음_empty() {
        Bprojm oldVersion =
                Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("N").abusNm("구버전명").build();

        Optional<Bprojm> result = ProjectRepresentativeSelector.pickLatest(List.of(oldVersion));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pickLatest - LST_YN='Y' 행이 2건이면 SNO 최대 행을 채택한다 (WARN 로그, 장애 없음)")
    void pickLatest_lstYnY중복_sno최대채택() {
        Bprojm y1 = Bprojm.builder().abusMngNo("PRJ-1").sno(1).lstYn("Y").abusNm("이름1").build();
        Bprojm y2 = Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("Y").abusNm("이름2").build();

        Optional<Bprojm> result = ProjectRepresentativeSelector.pickLatest(List.of(y1, y2));

        assertThat(result).isPresent();
        assertThat(result.get().getAbusNm()).isEqualTo("이름2");
    }

    @Test
    @DisplayName("pickLatest - 빈 목록이면 empty")
    void pickLatest_빈목록_empty() {
        assertThat(ProjectRepresentativeSelector.pickLatest(List.of())).isEmpty();
    }
}
