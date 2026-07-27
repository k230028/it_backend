package com.kdb.it.common.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("게시판 메타 목록 경량 프로젝션 조건과 정렬 동등성")
class BoardMetaListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired BoardMetaRepository metaRepository;

    @Test
    @DisplayName("findAllActiveOrderedRows는 findAllActiveOrdered와 동일한 useYn/delYn 필터·정렬·10개 필드값을 반환한다")
    void findAllActiveOrderedRows_matchesEntityQuery() {
        metaRepository.saveAllAndFlush(
                List.of(
                        board("BE03RW-02", "두번째", 2, "Y", "N"),
                        board("BE03RW-01", "첫번째", 1, "Y", "N"),
                        board("BE03RW-NU", "미사용", 1, "N", "N"), // useYn='N' → 제외
                        board("BE03RW-DL", "삭제됨", 1, "Y", "Y"))); // delYn='Y' → 제외

        List<Cblbmm> entities = metaRepository.findAllActiveOrdered();
        List<BoardMetaListRow> rows = metaRepository.findAllActiveOrderedRows();

        // 동일 스키마에 이미 존재할 수 있는 다른 게시판과 섞이지 않도록 본 테스트 데이터만 추출
        List<Cblbmm> filteredEntities =
                entities.stream().filter(e -> e.getBlbMngNo().startsWith("BE03RW-")).toList();
        List<BoardMetaListRow> filteredRows =
                rows.stream().filter(r -> r.blbMngNo().startsWith("BE03RW-")).toList();

        // useYn='N', delYn='Y' 게시판은 필터에서 제외되어 2건만 남는다
        assertThat(filteredEntities).hasSize(2);
        assertThat(filteredRows).hasSize(2);

        // SRE_SQN_NO 오름차순 정렬이 엔티티·프로젝션 양쪽에서 동일하다
        assertThat(filteredEntities)
                .extracting(Cblbmm::getBlbMngNo)
                .containsExactly("BE03RW-01", "BE03RW-02");
        assertThat(filteredRows)
                .extracting(BoardMetaListRow::blbMngNo)
                .containsExactly("BE03RW-01", "BE03RW-02");

        // 프로젝션 10개 필드가 엔티티 값과 위치별로 정확히 일치한다
        for (int i = 0; i < filteredEntities.size(); i++) {
            Cblbmm e = filteredEntities.get(i);
            BoardMetaListRow r = filteredRows.get(i);
            assertThat(r.blbMngNo()).isEqualTo(e.getBlbMngNo());
            assertThat(r.blbNm()).isEqualTo(e.getBlbNm());
            assertThat(r.itPtlBlbTc()).isEqualTo(e.getItPtlBlbTc());
            assertThat(r.repUseYn()).isEqualTo(e.getRepUseYn());
            assertThat(r.cmmtUseYn()).isEqualTo(e.getCmmtUseYn());
            assertThat(r.flEsnYn()).isEqualTo(e.getFlEsnYn());
            assertThat(r.hedTagUseYn()).isEqualTo(e.getHedTagUseYn());
            assertThat(r.sreSqnNo()).isEqualTo(e.getSreSqnNo());
            assertThat(r.useYn()).isEqualTo(e.getUseYn());
            assertThat(r.rmk()).isEqualTo(e.getRmk());
        }
    }

    private Cblbmm board(String id, String name, int sreSqnNo, String useYn, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        return Cblbmm.builder()
                .blbMngNo(id)
                .blbNm(name)
                .itPtlBlbTc("001")
                .repUseYn("N")
                .cmmtUseYn("N")
                .flEsnYn("N")
                .hedTagUseYn("N")
                .sreSqnNo(sreSqnNo)
                .useYn(useYn)
                .delYn(delYn)
                .rmk("비고-" + id)
                .fstEnrUsid("TEST")
                .fstEnrDtm(now)
                .lstChgUsid("TEST")
                .lstChgDtm(now)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .build();
    }
}
