package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.QCcodem;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공통코드 QueryDSL 커스텀 리포지토리 구현체
 */
@Repository
@RequiredArgsConstructor
public class CodeRepositoryImpl implements CodeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Ccodem> findByCIdAndCdvaWithValidDate(String cId, String cdva, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();

        return Optional.ofNullable(
            queryFactory.selectFrom(q)
                .where(q.cId.eq(cId),
                       q.cdva.eq(cdva),
                       q.delYn.eq("N"),
                       isValidDate(q, date))
                .fetchOne()
        );
    }

    @Override
    public List<Ccodem> findByCIdWithValidDate(String cId, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();

        return queryFactory.selectFrom(q)
                .where(q.cId.eq(cId),
                       q.delYn.eq("N"),
                       isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    @Override
    public List<Ccodem> findChildrenOfHrkC(String hrkC) {
        QCcodem q = QCcodem.ccodem;
        return queryFactory.selectFrom(q)
                .where(q.hrkC.eq(hrkC), q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast())
                .fetch();
    }

    @Override
    public List<Ccodem> findAllActive() {
        QCcodem q = QCcodem.ccodem;
        return queryFactory.selectFrom(q)
                .where(q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast(), q.cId.asc(), q.cdva.asc())
                .fetch();
    }

    @Override
    public List<Ccodem> findByCTpWithValidDate(String cTp, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();

        return queryFactory.selectFrom(q)
                .where(q.cTp.eq(cTp),
                       q.delYn.eq("N"),
                       isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    /** 기준일자가 시작~종료 범위 내인지 검증 */
    private BooleanExpression isValidDate(QCcodem ccodem, LocalDate date) {
        BooleanExpression afterStart = ccodem.sttDt.isNull().or(ccodem.sttDt.loe(date));
        BooleanExpression beforeEnd  = ccodem.endDt.isNull().or(ccodem.endDt.goe(date));
        return afterStart.and(beforeEnd);
    }
}
