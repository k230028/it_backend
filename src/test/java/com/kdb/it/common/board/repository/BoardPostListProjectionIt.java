package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("게시글 목록 경량 프로젝션 조건과 정렬")
class BoardPostListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired
    BoardPostRepository postRepository;

    @Test
    @DisplayName("일반 사용자 검색은 삭제·비공개·공개기간 외 게시물을 제외하고 공지와 그룹 순서로 정렬한다")
    void searchPostRows_filtersAndOrdersForNormalUser() {
        LocalDate today = LocalDate.now();
        postRepository.saveAllAndFlush(List.of(
            post("BE03-A", "alpha 공지", "본문", "writer-a", "Y", "Y", 300, 2, null, null, "N"),
            post("BE03-B", "일반 B", "alpha 본문", "writer-b", "N", "Y", 200, 2, null, null, "N"),
            post("BE03-C", "일반 C", "본문", "alpha-author", "N", "Y", 200, 1, null, null, "N"),
            post("BE03-D", "alpha 삭제", "본문", "writer-d", "N", "Y", 100, 1, null, null, "Y"),
            post("BE03-E", "alpha 비공개", "본문", "writer-e", "N", "N", 100, 1, null, null, "N"),
            post("BE03-F", "alpha 미래", "본문", "writer-f", "N", "Y", 100, 1, today.plusDays(1), null, "N"),
            post("BE03-G", "alpha 종료", "본문", "writer-g", "N", "Y", 100, 1, null, today.minusDays(1), "N")
        ));

        BoardPostDto.SearchCondition condition = new BoardPostDto.SearchCondition();
        condition.setKeyword("alpha");
        condition.setPage(0);
        condition.setSize(20);

        var result = postRepository.searchPostRows("BLB-BE03", condition, false);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
            .extracting(row -> row.nacMngNo())
            .containsExactly("BE03-A", "BE03-C", "BE03-B");
    }

    private Cblbcm post(
            String nacMngNo, String title, String body, String author,
            String notice, String visible, int uniqueId, int groupSequence,
            LocalDate startDate, LocalDate endDate, String deleted) {
        return Cblbcm.builder()
            .nacMngNo(nacMngNo)
            .blbMngNo("BLB-BE03")
            .nacNm(title)
            .nacCone(body)
            .nacInqNbr(0)
            .nacUnqId(String.valueOf(uniqueId))
            .ancYn(notice)
            .xpoYn(visible)
            .sttDt(startDate)
            .endDt(endDate)
            .flApgYn("N")
            .flNbr(0)
            .nacGrpSqn(groupSequence)
            .nacGrpLev(0)
            .fstEnrUsid(author)
            .fstEnrDtm(LocalDate.of(2026, 7, 20).atStartOfDay())
            .lstChgUsid(author)
            .lstChgDtm(LocalDate.of(2026, 7, 20).atStartOfDay())
            .delYn(deleted)
            .build();
    }
}
