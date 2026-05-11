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
 *
 * <p>
 * {@link CodeRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다.
 * QueryDSL의 타입 안전(Type-Safe) 쿼리 빌더를 사용하여 동적 쿼리를 작성합니다.
 * </p>
 *
 * <p>
 * 클래스 명명 규칙: Spring Data JPA가 자동으로 감지하려면
 * 반드시 {@code [CustomInterface명]Impl} 형태여야 합니다.
 * </p>
 *
 * <p>
 * 의존성:
 * </p>
 * <ul>
 * <li>{@link JPAQueryFactory}: QueryDSL 쿼리 실행기
 * ({@link com.kdb.it.config.QuerydslConfig}에서 빈 등록)</li>
 * <li>{@link QCcodem}: QueryDSL이 자동 생성한 Q 타입 클래스 (컴파일 시 생성)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class CodeRepositoryImpl implements CodeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 기준일자와 코드ID를 바탕으로 유효한 공통코드 단건 조회
     *
     * <p>
     * 삭제되지 않은({@code DEL_YN='N'}) 상태이며,
     * 기준일자가 시작일-종료일 범위 내에 있는 코드만 반환합니다.
     * </p>
     *
     * @param cdId       조회할 코드ID
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜 적용)
     * @return 조회된 공통코드 엔티티 (없으면 {@link java.util.Optional#empty()})
     */
    public Optional<Ccodem> findByCIdWithValidDate(String cdId, LocalDate targetDate) {
        QCcodem qCcodem = QCcodem.ccodem;
        LocalDate effectiveDate = (targetDate != null) ? targetDate : LocalDate.now();

        Ccodem result = queryFactory.selectFrom(qCcodem)
                .where(
                        qCcodem.cId.eq(cdId),
                        qCcodem.delYn.eq("N"),
                        isValidDate(qCcodem, effectiveDate))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 기준일자와 코드값구분을 바탕으로 유효한 공통코드 다건 조회
     *
     * <p>
     * 삭제되지 않은({@code DEL_YN='N'}) 상태이며,
     * 기준일자가 유효 범위 내에 있는 코드만 반환합니다.
     * 코드순서({@code cdSqn}) 오름차순, 코드ID 오름차순으로 정렬됩니다.
     * </p>
     *
     * @param cttTp      조회할 코드값구분
     * @param targetDate 기준일자 (null이면 시스템 현재 날짜 적용)
     * @return 조회된 공통코드 엔티티 목록 (순서번호 오름차순)
     */
    public List<Ccodem> findByCttTpWithValidDate(String cttTp, LocalDate targetDate) {
        QCcodem qCcodem = QCcodem.ccodem;
        LocalDate effectiveDate = (targetDate != null) ? targetDate : LocalDate.now();

        return queryFactory.selectFrom(qCcodem)
                .where(
                        qCcodem.cttTp.eq(cttTp),
                        qCcodem.delYn.eq("N"),
                        isValidDate(qCcodem, effectiveDate))
                .orderBy(qCcodem.cSqn.asc().nullsLast(), qCcodem.cId.asc())
                .fetch();
    }

    /**
     * 논리 삭제되지 않은 전체 공통코드를 코드순서 오름차순(null 마지막)으로 조회
     *
     * <p>관리자 목록 화면용 — 유효기간 필터 없이 DEL_YN='N'인 모든 코드를 반환합니다.</p>
     */
    public List<Ccodem> findAllActive() {
        QCcodem qCcodem = QCcodem.ccodem;
        return queryFactory.selectFrom(qCcodem)
                .where(qCcodem.delYn.eq("N"))
                .orderBy(qCcodem.cSqn.asc().nullsLast(), qCcodem.cId.asc())
                .fetch();
    }

    /**
     * 기준일자(effectiveDate)가 시작일자(sttDt)와 종료일자(endDt) 사이에 존재하는지 검증
     * 각 필드가 null일 경우 검증 패스.
     */
    private BooleanExpression isValidDate(QCcodem ccodem, LocalDate effectiveDate) {
        BooleanExpression afterStartDate = ccodem.sttDt.isNull().or(ccodem.sttDt.loe(effectiveDate));
        BooleanExpression beforeEndDate = ccodem.endDt.isNull().or(ccodem.endDt.goe(effectiveDate));
        return afterStartDate.and(beforeEndDate);
    }
}
