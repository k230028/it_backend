package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.entity.QCmenum;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
/** QueryDSL 하위 트리 조회와 Oracle 시퀀스 기반 메뉴 ID 채번을 구현합니다. */
public class CmenumRepositoryImpl implements CmenumRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Override
    public List<Cmenum> findSubtreeByPathPrefix(String pathPrefix) {
        QCmenum m = QCmenum.cmenum;
        return queryFactory
                .selectFrom(m)
                .where(m.delYn.eq("N"), m.whlMnuPth.startsWith(pathPrefix))
                .fetch();
    }

    @Override
    public String nextMnuId() {
        Object val =
                entityManager
                        .createNativeQuery("SELECT SQ_TPRMPP_CMENUM_1.NEXTVAL FROM DUAL")
                        .getSingleResult();
        long n = ((Number) val).longValue();
        return "MNU" + String.format("%07d", n);
    }

    private static final String PROBE_ICON_COLUMN_SQL =
            """
            SELECT COUNT(*) FROM ALL_TAB_COLUMNS
             WHERE OWNER = SYS_CONTEXT('USERENV','CURRENT_SCHEMA')
               AND TABLE_NAME = 'TPRMPP_CMENUM'
               AND COLUMN_NAME = 'IMK_NM'
            """;

    /**
     * IMK_NM 컬럼 존재 여부 캐시. 스키마는 런타임에 바뀌지 않으므로 실제 판정에 성공한 값(true/false 무관)만 최초 1회 캐시한다. 판정 자체가 실패한
     * 경우는 캐시하지 않되, 아래 {@link #probeFailureCooldownNanos} 동안은 재시도를 억제하고 다음 호출에서 재시도한다(아래 {@link
     * #probeIconColumn()} 참조).
     */
    private volatile Boolean iconColumnPresent;

    /**
     * 판정 실패 후 재시도를 억제하는 기본 냉각 시간.
     *
     * <p>실패를 전혀 캐시하지 않고 매번 재시도하면, 커넥션 풀 장애처럼 지속되는 DB 문제 상황에서 `/api/menus`가 호출될 때마다(페이지 로드마다) 이미
     * 트랜잭션 커넥션을 쥔 채로 두 번째 커넥션을 기다리게 되어 Hikari {@code connectionTimeout}(기본 30초)까지 블로킹하며 풀 고갈을 스스로
     * 심화시킨다. 반대로 실패를 영구 캐시하면(이전 동작) 일시적 순단 한 번이 프로세스 수명 내내 폴백 모드에 고정된다. 60초는 일시적 장애가 1분 안에 스스로
     * 회복되도록 하면서도, 장애가 이어지는 동안에는 매 요청이 매번 풀을 두드리지 않도록 억제하는 절충값이다.
     */
    private static final long DEFAULT_PROBE_FAILURE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(60);

    /** 냉각 시간(나노초). 테스트에서만 {@link #setProbeFailureCooldownNanos(long)}로 조정한다. */
    private long probeFailureCooldownNanos = DEFAULT_PROBE_FAILURE_COOLDOWN_NANOS;

    /** 마지막 판정 실패 시각({@link System#nanoTime()} 기준). 실패한 적이 없으면 {@code null}. */
    private volatile Long lastProbeFailureNanos;

    /**
     * 테스트에서 냉각 시간을 조정하기 위한 패키지 전용 설정자. 운영 코드 경로에서는 호출하지 않는다.
     *
     * @param probeFailureCooldownNanos 새 냉각 시간(나노초)
     */
    void setProbeFailureCooldownNanos(long probeFailureCooldownNanos) {
        this.probeFailureCooldownNanos = probeFailureCooldownNanos;
    }

    @Override
    public boolean isIconColumnPresent() {
        Boolean cached = iconColumnPresent;
        if (cached != null) return cached;
        if (isWithinFailureCooldown()) return false;
        return probeIconColumn();
    }

    /**
     * 직전 판정 실패로부터 냉각 시간이 지나지 않았는지 확인한다.
     *
     * @return 냉각 시간 내이면 {@code true}. 이 경우 호출자는 커넥션을 열지 않고 곧바로 '없음'을 반환해야 한다
     */
    private boolean isWithinFailureCooldown() {
        Long failedAtNanos = lastProbeFailureNanos;
        if (failedAtNanos == null) return false;
        return System.nanoTime() - failedAtNanos < probeFailureCooldownNanos;
    }

    /**
     * 데이터 사전에서 TPRMPP_CMENUM.IMK_NM을 찾는다.
     *
     * <p>JPA {@code EntityManager}가 아니라 {@link DataSource}에서 직접 얻은 커넥션으로 조회한다. {@code
     * EntityManager#createNativeQuery}로 조회하면 예외 발생 시 JPA가 영속성 컨텍스트가 속한 트랜잭션을 rollback-only로 표시해,
     * {@code MenuQueryService}처럼 {@code @Transactional(readOnly = true)}인 호출부에서 "판정 실패도 메뉴 조회 자체는
     * 성공한다"는 계약이 커밋 시점에 깨진다. 커넥션을 직접 열고 닫으면 이 문제가 없다.
     *
     * <p>접속 계정(ITPAPP)과 객체 소유 스키마(ITPOWN)가 달라 USER_TAB_COLUMNS로는 보이지 않는다. 세션 CURRENT_SCHEMA를 소유자로
     * 놓고 ALL_TAB_COLUMNS를 본다.
     *
     * <p>조회 실패를 이번 호출 한정으로만 '없음'으로 접는 것은 의도적이다("Repository는 DB 예외를 전파한다" it_backend/CLAUDE.md §4의
     * 예외). 업무 조회가 아니라 카탈로그 탐지이며, 반대로 판정하면 메뉴 조회 전체가 ORA-00904로 죽는다. 다만 실패를 전혀 캐시하지 않으면 지속되는 DB 장애
     * 동안 매 호출이 새 커넥션을 기다리며 풀 고갈을 심화시키므로, 실패 시점만 기록해 {@link #probeFailureCooldownNanos} 동안 재판정을
     * 억제한다({@link #isWithinFailureCooldown()} 참조). 실제 판정에 성공한 값(true/false 모두)은 냉각 없이 프로세스 수명 동안
     * 캐시한다.
     */
    private boolean probeIconColumn() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(PROBE_ICON_COLUMN_SQL);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                // COUNT(*)는 항상 한 행을 반환하므로 정상 상황에서는 도달하지 않는다. 방어적으로 '없음'을 확정값으로 캐시해
                // getInt(1) 호출이 예외를 던져 정상 판정이 실패로 오분류되는 것을 막는다.
                iconColumnPresent = false;
                return false;
            }
            boolean present = rs.getInt(1) > 0;
            iconColumnPresent = present;
            if (!present) {
                log.warn(
                        "TPRMPP_CMENUM.IMK_NM 컬럼이 없습니다. 마이그레이션(V20260806_002)이 적용될 때까지 메뉴 아이콘은"
                                + " MenuIconDefaults 스냅샷으로 대체됩니다.");
            }
            return present;
        } catch (SQLException | RuntimeException e) {
            lastProbeFailureNanos = System.nanoTime();
            log.warn(
                    "TPRMPP_CMENUM.IMK_NM 컬럼 존재 판정에 실패했습니다. 냉각 시간 동안 재시도를 억제하고 이번 호출은 '없음'으로 처리합니다.",
                    e);
            return false;
        }
    }

    @Override
    public List<MenuTreeRow> findActiveMenuTreeRows(boolean iconColumnPresent) {
        QCmenum m = QCmenum.cmenum;
        return queryFactory
                .select(menuTreeRowProjection(m, iconColumnPresent))
                .from(m)
                .where(m.delYn.eq("N"))
                .fetch();
    }

    /**
     * IMK_NM 유무에 따라 select 목록이 갈리는 생성자 프로젝션.
     *
     * <p>컬럼이 없을 때 null 리터럴을 select에 넣지 않고 목록에서 아예 뺀다. 생성되는 SQL에 IMK_NM이 등장할 여지가 없어야 ORA-00904가 원천
     * 차단되며, Hibernate의 typed-null 렌더링 동작에 의존하지 않는다. 9인자 보조 생성자가 imkNm을 null로 채운다.
     *
     * <p>분기 조건을 인자로 받는 이유는 테스트 가능성이다. {@code isIconColumnPresent()}를 내부에서 직접 호출하면 컬럼 부재 분기는 실제로 컬럼이
     * 없는 Oracle 스키마에서만 재현되어 단위 테스트로 다다를 수 없다. 이제 이 메서드 자신은 {@code isIconColumnPresent()}를 호출하지 않고,
     * 호출부인 {@code MenuQueryService}가 판정값을 한 번 구해 {@code findActiveMenuTreeRows(boolean)}에 인자로 전달한다.
     *
     * @param iconColumnPresent IMK_NM 컬럼 존재 여부. true면 10인자(imkNm 포함), false면 9인자(imkNm 제외) 프로젝션을
     *     반환한다
     */
    ConstructorExpression<MenuTreeRow> menuTreeRowProjection(QCmenum m, boolean iconColumnPresent) {
        if (iconColumnPresent) {
            return Projections.constructor(
                    MenuTreeRow.class,
                    m.mnuId,
                    m.hrkMnuId,
                    m.mnuNm,
                    m.mnuTpC,
                    m.srePth,
                    m.mnuSotSqnSno,
                    m.hidYn,
                    m.mnuDep,
                    m.whlMnuPth,
                    m.imkNm);
        }
        return Projections.constructor(
                MenuTreeRow.class,
                m.mnuId,
                m.hrkMnuId,
                m.mnuNm,
                m.mnuTpC,
                m.srePth,
                m.mnuSotSqnSno,
                m.hidYn,
                m.mnuDep,
                m.whlMnuPth);
    }
}
