package com.kdb.it.common.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("게시글 목록 경량 프로젝션 조건과 정렬")
class BoardPostListProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired BoardPostRepository postRepository;

    @Test
    @DisplayName("일반 사용자 검색은 삭제·비공개·공개기간 외 게시물을 제외하고 공지와 그룹 순서로 정렬한다")
    void searchPostRows_filtersAndOrdersForNormalUser() {
        LocalDate today = LocalDate.now();
        postRepository.saveAllAndFlush(
                List.of(
                        post(
                                "BE03-A",
                                "alpha 공지",
                                "본문",
                                "writer-a",
                                "Y",
                                "Y",
                                300,
                                2,
                                null,
                                null,
                                "N"),
                        post(
                                "BE03-B",
                                "일반 B",
                                "alpha 본문",
                                "writer-b",
                                "N",
                                "Y",
                                200,
                                2,
                                null,
                                null,
                                "N"),
                        post(
                                "BE03-C",
                                "일반 C",
                                "본문",
                                "alpha-author",
                                "N",
                                "Y",
                                200,
                                1,
                                null,
                                null,
                                "N"),
                        post(
                                "BE03-D",
                                "alpha 삭제",
                                "본문",
                                "writer-d",
                                "N",
                                "Y",
                                100,
                                1,
                                null,
                                null,
                                "Y"),
                        post(
                                "BE03-E",
                                "alpha 비공개",
                                "본문",
                                "writer-e",
                                "N",
                                "N",
                                100,
                                1,
                                null,
                                null,
                                "N"),
                        post(
                                "BE03-F",
                                "alpha 미래",
                                "본문",
                                "writer-f",
                                "N",
                                "Y",
                                100,
                                1,
                                today.plusDays(1),
                                null,
                                "N"),
                        post(
                                "BE03-G",
                                "alpha 종료",
                                "본문",
                                "writer-g",
                                "N",
                                "Y",
                                100,
                                1,
                                null,
                                today.minusDays(1),
                                "N")));

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

    @Test
    @DisplayName("관리자의 publicOnly 검색은 비공개와 공개기간 외 게시물을 페이지·총계 전에 제외한다")
    void searchPostRows_publicOnlyFiltersAdminBeforePaginationAndCount() {
        LocalDate today = LocalDate.now();
        postRepository.saveAllAndFlush(
                List.of(
                        post("FE01-V1", "공개 1", "본문", "writer", "N", "Y", 9300, 1, null, null, "N"),
                        post("FE01-V2", "공개 2", "본문", "writer", "N", "Y", 9200, 1, null, null, "N"),
                        post("FE01-V3", "공개 3", "본문", "writer", "N", "Y", 9100, 1, null, null, "N"),
                        post(
                                "FE01-HIDDEN",
                                "숨김",
                                "본문",
                                "writer",
                                "N",
                                "N",
                                9500,
                                1,
                                null,
                                null,
                                "N"),
                        post(
                                "FE01-FUTURE",
                                "미래",
                                "본문",
                                "writer",
                                "N",
                                "Y",
                                9400,
                                1,
                                today.plusDays(1),
                                null,
                                "N"),
                        post(
                                "FE01-EXPIRED",
                                "종료",
                                "본문",
                                "writer",
                                "N",
                                "Y",
                                9350,
                                1,
                                null,
                                today.minusDays(1),
                                "N")));

        BoardPostDto.SearchCondition publicOnly = new BoardPostDto.SearchCondition();
        publicOnly.setPublicOnly(true);
        publicOnly.setPage(0);
        publicOnly.setSize(2);

        var firstPage = postRepository.searchPostRows("BLB-BE03", publicOnly, true);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(BoardPostDto.ListRow::nacMngNo)
                .containsExactly("FE01-V1", "FE01-V2");

        publicOnly.setPage(1);
        var secondPage = postRepository.searchPostRows("BLB-BE03", publicOnly, true);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getContent())
                .extracting(BoardPostDto.ListRow::nacMngNo)
                .containsExactly("FE01-V3");
    }

    @Test
    @DisplayName("관리자의 기본 검색은 publicOnly를 지정하지 않아도 기존처럼 비공개와 기간 외 게시물을 포함한다")
    void searchPostRows_adminDefaultStillIncludesPrivateAndOutOfPeriodPosts() {
        LocalDate today = LocalDate.now();
        postRepository.saveAllAndFlush(
                List.of(
                        post(
                                "FE01-D-VIS",
                                "공개",
                                "본문",
                                "writer",
                                "N",
                                "Y",
                                8300,
                                1,
                                null,
                                null,
                                "N"),
                        post(
                                "FE01-D-HIDE",
                                "숨김",
                                "본문",
                                "writer",
                                "N",
                                "N",
                                8500,
                                1,
                                null,
                                null,
                                "N"),
                        post(
                                "FE01-D-FUT",
                                "미래",
                                "본문",
                                "writer",
                                "N",
                                "Y",
                                8400,
                                1,
                                today.plusDays(1),
                                null,
                                "N"),
                        post(
                                "FE01-D-EXP",
                                "종료",
                                "본문",
                                "writer",
                                "N",
                                "Y",
                                8600,
                                1,
                                null,
                                today.minusDays(1),
                                "N")));

        BoardPostDto.SearchCondition condition = new BoardPostDto.SearchCondition();
        condition.setPage(0);
        condition.setSize(20);

        var result = postRepository.searchPostRows("BLB-BE03", condition, true);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent())
                .extracting(BoardPostDto.ListRow::nacMngNo)
                .containsExactly("FE01-D-EXP", "FE01-D-HIDE", "FE01-D-FUT", "FE01-D-VIS");
    }

    private Cblbcm post(
            String nacMngNo,
            String title,
            String body,
            String author,
            String notice,
            String visible,
            int uniqueId,
            int groupSequence,
            LocalDate startDate,
            LocalDate endDate,
            String deleted) {
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
