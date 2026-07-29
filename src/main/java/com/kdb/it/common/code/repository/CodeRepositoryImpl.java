package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.QCcodem;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 공통코드 QueryDSL 커스텀 리포지토리 구현체 */
@Repository
@RequiredArgsConstructor
public class CodeRepositoryImpl implements CodeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * cId + cdva + 유효일 기준 단건 조회. 삭제된(DEL_YN='Y') 코드는 제외.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public Optional<Ccodem> findByCIdAndCdvaWithValidDate(
            String cId, String cdva, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return Optional.ofNullable(
                queryFactory
                        .selectFrom(q)
                        .where(
                                q.cId.eq(cId),
                                q.cdva.eq(cdva),
                                q.delYn.eq("N"),
                                isValidDate(q, date))
                        .fetchOne());
    }

    /**
     * cId 기준 전체 코드 목록 조회. cSqn 오름차순 → cdva 오름차순 정렬. 삭제 제외.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public List<Ccodem> findByCIdWithValidDate(String cId, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return queryFactory
                .selectFrom(q)
                .where(q.cId.eq(cId), q.delYn.eq("N"), isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    /** 상위코드(HRK_C) 기준 하위 코드 목록 조회. 유효일 필터 없음 (하위 코드는 상위 범위 내 처리). */
    @Override
    public List<Ccodem> findChildrenOfHrkC(String hrkC) {
        QCcodem q = QCcodem.ccodem;
        return queryFactory
                .selectFrom(q)
                .where(q.hrkC.eq(hrkC), q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast())
                .fetch();
    }

    /** 삭제되지 않은(DEL_YN='N') 전체 공통코드 조회. 관리 화면용 — 유효일 필터 미적용. */
    @Override
    public List<Ccodem> findAllActive() {
        QCcodem q = QCcodem.ccodem;
        return queryFactory
                .selectFrom(q)
                .where(q.delYn.eq("N"))
                .orderBy(q.cSqn.asc().nullsLast(), q.cId.asc(), q.cdva.asc())
                .fetch();
    }

    /**
     * 코드타입(C_TP) 기준 코드 목록 조회. IOE 비목 그룹(IOE_LEAFE 등) 조회에 사용.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public List<Ccodem> findByCTpWithValidDate(String cTp, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return queryFactory
                .selectFrom(q)
                .where(q.cTp.eq(cTp), q.delYn.eq("N"), isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    /**
     * cId 기준 다건 조회 — REST 응답 전용 경량 프로젝션(guid, guidPrgSno 제외). {@link
     * #findByCIdWithValidDate(String, LocalDate)}와 동일한 where·정렬을 재사용합니다.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public List<CcodemResponseRow> findResponseRowsByCIdWithValidDate(
            String cId, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return queryFactory
                .select(responseRowProjection(q))
                .from(q)
                .where(q.cId.eq(cId), q.delYn.eq("N"), isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    /**
     * cId + cdva + 유효일 기준 단건 조회 — REST 응답 전용 경량 프로젝션(guid, guidPrgSno 제외). {@link
     * #findByCIdAndCdvaWithValidDate(String, String, LocalDate)}와 동일한 where를 재사용합니다.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public Optional<CcodemResponseRow> findResponseRowByCIdAndCdvaWithValidDate(
            String cId, String cdva, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return Optional.ofNullable(
                queryFactory
                        .select(responseRowProjection(q))
                        .from(q)
                        .where(
                                q.cId.eq(cId),
                                q.cdva.eq(cdva),
                                q.delYn.eq("N"),
                                isValidDate(q, date))
                        .fetchOne());
    }

    /**
     * 코드타입(C_TP) 기준 다건 조회 — REST 응답 전용 경량 프로젝션(guid, guidPrgSno 제외). {@link
     * #findByCTpWithValidDate(String, LocalDate)}와 동일한 where·정렬을 재사용합니다.
     *
     * @param targetDate null이면 현재 날짜 기준
     */
    @Override
    public List<CcodemResponseRow> findResponseRowsByCTpWithValidDate(
            String cTp, LocalDate targetDate) {
        QCcodem q = QCcodem.ccodem;
        String date = toYmd(targetDate);

        return queryFactory
                .select(responseRowProjection(q))
                .from(q)
                .where(q.cTp.eq(cTp), q.delYn.eq("N"), isValidDate(q, date))
                .orderBy(q.cSqn.asc().nullsLast(), q.cdva.asc())
                .fetch();
    }

    /**
     * {@link CcodemResponseRow} 18개 필드에 대한 QueryDSL 생성자 프로젝션. 컴포넌트 순서와 select 인자 순서가 정확히 일치해야 합니다.
     */
    private ConstructorExpression<CcodemResponseRow> responseRowProjection(QCcodem q) {
        return Projections.constructor(
                CcodemResponseRow.class,
                q.cId,
                q.cdva,
                q.cdvaNm,
                q.cNm,
                q.cdvaDes,
                q.cdvaDtl,
                q.cdvaDtlC,
                q.cTp,
                q.cTpDes,
                q.hrkC,
                q.cSqn,
                q.sttDt,
                q.endDt,
                q.delYn,
                q.fstEnrDtm,
                q.fstEnrUsid,
                q.lstChgDtm,
                q.lstChgUsid);
    }

    /** 기준일자가 시작~종료 범위 내인지 검증. 시작·종료일자는 'YYYYMMDD' 문자열이므로 사전식 비교가 곧 날짜 비교와 일치합니다. */
    private BooleanExpression isValidDate(QCcodem ccodem, String date) {
        BooleanExpression afterStart = ccodem.sttDt.isNull().or(ccodem.sttDt.loe(date));
        BooleanExpression beforeEnd = ccodem.endDt.isNull().or(ccodem.endDt.goe(date));
        return afterStart.and(beforeEnd);
    }

    /** 기준일자(null이면 오늘)를 'YYYYMMDD' 문자열로 변환 */
    private String toYmd(LocalDate targetDate) {
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
