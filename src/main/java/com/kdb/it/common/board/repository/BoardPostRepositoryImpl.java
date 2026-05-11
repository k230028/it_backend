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

@RequiredArgsConstructor
public class BoardPostRepositoryImpl implements BoardPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Cblbcm> searchPosts(
            String blbMngNo,
            BoardPostDto.SearchCondition cond,
            boolean isAdmin,
            String userBbrC,
            String bbrLmtnUseYn) {

        QCblbcm p = QCblbcm.cblbcm;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(p.blbMngNo.eq(blbMngNo));
        builder.and(p.delYn.eq("N"));

        if (!isAdmin) {
            LocalDate today = LocalDate.now();
            builder.and(p.sreYn.eq("Y"));
            builder.and(p.sttDt.isNull().or(p.sttDt.loe(today)));
            builder.and(p.endDt.isNull().or(p.endDt.goe(today)));
            if ("Y".equals(bbrLmtnUseYn) && StringUtils.hasText(userBbrC)) {
                builder.and(p.bbrC.isNull().or(p.bbrC.eq(userBbrC)));
            }
        }

        if (StringUtils.hasText(cond.getKeyword())) {
            builder.and(
                p.nacNm.containsIgnoreCase(cond.getKeyword())
                .or(p.nacCone.containsIgnoreCase(cond.getKeyword()))
                .or(p.fstEnrUsid.containsIgnoreCase(cond.getKeyword()))
            );
        }
        if (StringUtils.hasText(cond.getNacTp()))  builder.and(p.nacTp.eq(cond.getNacTp()));
        if (StringUtils.hasText(cond.getKdC()))    builder.and(p.kdC.eq(cond.getKdC()));
        if (StringUtils.hasText(cond.getPritC()))  builder.and(p.pritC.eq(cond.getPritC()));
        if (StringUtils.hasText(cond.getBbrC()))   builder.and(p.bbrC.eq(cond.getBbrC()));

        int offset = cond.getPage() * cond.getSize();

        return queryFactory.selectFrom(p)
            .where(builder)
            .orderBy(p.hrkFxnYn.desc(), p.nacGrpNo.desc(), p.nacGrpSqn.asc())
            .offset(offset)
            .limit(cond.getSize())
            .fetch();
    }
}
