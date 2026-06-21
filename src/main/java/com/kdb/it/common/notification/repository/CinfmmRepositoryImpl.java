package com.kdb.it.common.notification.repository;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.entity.QCinfmm;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공통알림기본 QueryDSL 구현체.
 *
 * <p>Spring Data JPA 네이밍 규약({@code *Impl})에 따라 {@link CinfmmRepository} 인터페이스와
 * 자동 합성되며, 본 클래스는 별도 {@code @Repository} 등록 없이 발견된다.</p>
 */
@RequiredArgsConstructor
public class CinfmmRepositoryImpl implements CinfmmRepositoryCustom {

    private final JPAQueryFactory query;

    private static final QCinfmm c = QCinfmm.cinfmm;

    /**
     * 수신자 기준 알림 목록을 페이지 단위로 조회합니다.
     *
     * <p>삭제된 알림({@code DEL_YN='Y'})은 항상 제외됩니다.
     * {@code unreadOnly=true}이면 미조회({@code INQ_YN='N'})만 추가 필터링합니다.</p>
     */
    @Override
    public Page<Cinfmm> findInbox(String rmsEno, Boolean unreadOnly, Pageable pageable) {
        var where = c.rmsEno.eq(rmsEno)
            .and(c.delYn.eq("N"));
        if (Boolean.TRUE.equals(unreadOnly)) {
            where = where.and(c.inqYn.eq("N"));
        }

        List<Cinfmm> rows = query
            .selectFrom(c)
            .where(where)
            .orderBy(c.fstEnrDtm.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = query
            .select(c.count())
            .from(c)
            .where(where)
            .fetchOne();

        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Override
    public long countUnread(String rmsEno) {
        Long count = query
            .select(c.count())
            .from(c)
            .where(
                c.rmsEno.eq(rmsEno),
                c.delYn.eq("N"),
                c.inqYn.eq("N")
            )
            .fetchOne();
        return count == null ? 0L : count;
    }

    // 벌크 UPDATE는 1차 캐시를 우회하나, 호출자(NotificationService.markAllRead)는 갱신 건수만
    // 반환하고 동일 트랜잭션에서 해당 알림 엔티티를 재조회하지 않으므로 clear가 불필요하다.
    @Override
    public long markAllReadByRmsEno(String rmsEno) {
        return query
            .update(c)
            .set(c.inqYn, "Y")
            .set(c.inqDtm, LocalDateTime.now())
            .where(
                c.rmsEno.eq(rmsEno),
                c.delYn.eq("N"),
                c.inqYn.eq("N")
            )
            .execute();
    }
}
