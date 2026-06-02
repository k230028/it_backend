package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.QCblbcm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.util.List;

/**
 * 게시물 QueryDSL 검색 구현체
 *
 * <p>게시판별 게시물 목록 조회에서 공개 여부, 공개 기간, 검색 조건을
 * 하나의 {@link BooleanBuilder}로 조립합니다.</p>
 */
@RequiredArgsConstructor
public class BoardPostRepositoryImpl implements BoardPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 게시물 목록을 검색합니다.
     *
     * <p>관리자가 아닌 사용자는 {@code SRE_YN='Y'}와 공개 기간을
     * 모두 만족하는 게시물만 조회합니다. 검색어는 제목, 본문, 작성자 사번에 적용합니다.</p>
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건과 페이지 조건
     * @param isAdmin 관리자 여부
     * @return 권한과 검색 조건을 만족하는 게시물 목록
     */
    @Override
    public List<Cblbcm> searchPosts(
            String blbMngNo,
            BoardPostDto.SearchCondition cond,
            boolean isAdmin) {

        QCblbcm p = QCblbcm.cblbcm;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(p.blbMngNo.eq(blbMngNo));
        builder.and(p.delYn.eq("N"));

        if (!isAdmin) {
            LocalDate today = LocalDate.now();
            builder.and(p.sreYn.eq("Y"));
            builder.and(p.sttDt.isNull().or(p.sttDt.loe(today)));
            builder.and(p.endDt.isNull().or(p.endDt.goe(today)));
        }

        if (StringUtils.hasText(cond.getKeyword())) {
            builder.and(
                p.nacNm.containsIgnoreCase(cond.getKeyword())
                .or(p.nacCone.containsIgnoreCase(cond.getKeyword()))
                .or(p.fstEnrUsid.containsIgnoreCase(cond.getKeyword()))
            );
        }
        if (StringUtils.hasText(cond.getKdC()))    builder.and(p.kdC.eq(cond.getKdC()));
        if (StringUtils.hasText(cond.getPritC()))  builder.and(p.pritC.eq(cond.getPritC()));
        if (StringUtils.hasText(cond.getBbrC()))   builder.and(p.bbrC.eq(cond.getBbrC()));

        int offset = cond.getPage() * cond.getSize();

        return queryFactory.selectFrom(p)
            .where(builder)
            .orderBy(p.ancYn.desc(), p.nacId.desc(), p.nacGrpSqn.asc())
            .offset(offset)
            .limit(cond.getSize())
            .fetch();
    }
}
