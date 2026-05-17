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

    /**
     * cId + cdva + 유효일 기준 단건 조회. 삭제된(DEL_YN='Y') 코드는 제외.
     * @param targetDate null이면 현재 날짜 기준
     */
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

    /**
     * cId 기준 전체 코드 목록 조회. cSqn 오름차순 → cdva 오름차순 정렬. 삭제 제외.
     * @param targetDate null이면 현재 날짜 기준
     */
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

    /**
     * 상위코드(HRK_C) 기준 하위 코드 목록 조회. 유효일 필터 없음 (하위 코드는 상위 범위 내 처리).
     */
    @Override
    public List<Ccodem> findChildrenOfHrkC(String hrkC) {
        QCcodem q = QCcodem.ccodem;
        return queryFactory.selectFrom(q)
                .where(q.hrkC.eq(hrkC), q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast())
                .fetch();
    }

    /**
     * 삭제되지 않은(DEL_YN='N') 전체 공통코드 조회. 관리 화면용 — 유효일 필터 미적용.
     */
    @Override
    public List<Ccodem> findAllActive() {
        QCcodem q = QCcodem.ccodem;
        return queryFactory.selectFrom(q)
                .where(q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast(), q.cId.asc(), q.cdva.asc())
                .fetch();
    }

    /**
     * 코드타입(C_TP) 기준 코드 목록 조회. IOE 비목 그룹(IOE_LEAFE 등) 조회에 사용.
     * @param targetDate null이면 현재 날짜 기준
     */
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
