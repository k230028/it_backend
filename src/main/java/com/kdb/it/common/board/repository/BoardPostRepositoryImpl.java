package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.QCblbcm;
import com.kdb.it.common.iam.entity.QCorgnI;
import com.kdb.it.common.iam.entity.QCuserI;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

/**
 * 게시물 QueryDSL 검색 구현체
 *
 * <p>게시판별 게시물 목록 조회에서 공개 여부, 공개 기간, 검색 조건을 하나의 {@link BooleanBuilder}로 조립합니다.
 */
@RequiredArgsConstructor
public class BoardPostRepositoryImpl implements BoardPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 게시물 목록을 검색합니다.
     *
     * <p>관리자가 아닌 사용자와 {@code publicOnly=true} 요청은 노출여부와 공개 기간을 모두 만족하는 게시물만 조회합니다. 검색어는 제목, 본문, 작성자
     * 사번에 적용합니다.
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건과 페이지 조건
     * @param isAdmin 관리자 여부
     * @return 권한과 검색 조건을 만족하는 게시물 목록
     */
    @Override
    public Page<Cblbcm> searchPosts(
            String blbMngNo, BoardPostDto.SearchCondition cond, boolean isAdmin) {

        QCblbcm p = QCblbcm.cblbcm;
        BooleanBuilder builder = buildPredicate(p, blbMngNo, cond, isAdmin);

        int page = Math.max(cond.getPage(), 0);
        int size = Math.min(Math.max(cond.getSize(), 1), 100);
        var pageable = PageRequest.of(page, size);

        List<Cblbcm> rows =
                queryFactory
                        .selectFrom(p)
                        .where(builder)
                        .orderBy(p.ancYn.desc(), p.nacUnqId.desc(), p.nacGrpSqn.asc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .fetch();

        Long total = queryFactory.select(p.count()).from(p).where(builder).fetchOne();

        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /**
     * 게시물 목록 응답에 필요한 필드만 검색합니다.
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건과 페이지 조건
     * @param isAdmin 관리자 여부
     * @return 권한과 검색 조건을 만족하는 경량 게시물 목록
     */
    @Override
    public Page<BoardPostDto.ListRow> searchPostRows(
            String blbMngNo, BoardPostDto.SearchCondition cond, boolean isAdmin) {
        QCblbcm p = QCblbcm.cblbcm;
        QCuserI writer = new QCuserI("boardPostWriter");
        QCorgnI writerOrganization = new QCorgnI("boardPostWriterOrganization");
        BooleanBuilder builder = buildPredicate(p, blbMngNo, cond, isAdmin);

        int page = Math.max(cond.getPage(), 0);
        int size = Math.min(Math.max(cond.getSize(), 1), 100);
        var pageable = PageRequest.of(page, size);

        List<BoardPostDto.ListRow> rows =
                queryFactory
                        .select(
                                Projections.constructor(
                                        BoardPostDto.ListRow.class,
                                        p.nacMngNo,
                                        p.blbMngNo,
                                        p.nacNm,
                                        p.nacInqNbr,
                                        p.nacUnqId,
                                        p.ancYn,
                                        p.xpoYn,
                                        p.flApgYn,
                                        p.flNbr,
                                        p.nacGrpLev,
                                        p.sttDt,
                                        p.endDt,
                                        p.fstEnrUsid,
                                        writer.usrNm,
                                        writerOrganization.bbrNm,
                                        p.fstEnrDtm))
                        .from(p)
                        .leftJoin(writer)
                        .on(writer.eno.eq(p.fstEnrUsid))
                        .leftJoin(writerOrganization)
                        .on(writerOrganization.prlmOgzCCone.eq(writer.bbrC))
                        .where(builder)
                        .orderBy(p.ancYn.desc(), p.nacUnqId.desc(), p.nacGrpSqn.asc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .fetch();

        Long total = queryFactory.select(p.count()).from(p).where(builder).fetchOne();

        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildPredicate(
            QCblbcm p, String blbMngNo, BoardPostDto.SearchCondition cond, boolean isAdmin) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(p.blbMngNo.eq(blbMngNo));
        builder.and(p.delYn.eq("N"));

        if (!isAdmin || cond.isPublicOnly()) {
            builder.and(p.xpoYn.eq("Y"));
            if (!cond.isIgnorePublicationPeriod()) {
                LocalDate today = LocalDate.now();
                builder.and(p.sttDt.isNull().or(p.sttDt.loe(today)));
                builder.and(p.endDt.isNull().or(p.endDt.goe(today)));
            }
        }

        if (StringUtils.hasText(cond.getKeyword())) {
            builder.and(
                    p.nacNm
                            .containsIgnoreCase(cond.getKeyword())
                            .or(p.nacCone.containsIgnoreCase(cond.getKeyword()))
                            .or(p.fstEnrUsid.containsIgnoreCase(cond.getKeyword())));
        }
        if (StringUtils.hasText(cond.getBbrC())) {
            builder.and(p.bbrC.eq(cond.getBbrC()));
        }
        return builder;
    }
}
