package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.entity.QCauthI;
import com.kdb.it.common.iam.entity.QCorgnI;
import com.kdb.it.common.iam.entity.QCroleI;
import com.kdb.it.common.iam.entity.QCuserI;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 사용자(CuserI) 커스텀 리포지토리 구현 클래스
 *
 * <p>{@link UserRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다. QueryDSL의 타입 안전(Type-Safe) 쿼리 빌더를 사용하여 동적
 * 쿼리를 작성합니다.
 *
 * <p>클래스 명명 규칙: Spring Data JPA가 자동으로 감지하려면 반드시 {@code [CustomInterface명]Impl} 형태여야 합니다. ({@code
 * UserRepositoryImpl})
 *
 * <p>의존성:
 *
 * <ul>
 *   <li>{@link JPAQueryFactory}: QueryDSL 쿼리 실행기 ({@link com.kdb.it.config.QuerydslConfig}에서 빈 등록)
 *   <li>{@link QCuserI}: QueryDSL이 자동 생성한 Q 타입 클래스 (컴파일 시 생성)
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class UserRepositoryImpl implements UserRepositoryCustom {

    /** 그룹 안에서 직위코드를 내림차순으로 표시하는 직위코드 접두사 */
    private static final String DESCENDING_POSITION_CODE_PREFIX = "B6";

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<UserRepository.AdminUserView> findAdminUserPage(String search, Pageable pageable) {
        QCuserI user = QCuserI.cuserI;
        QCorgnI organization = new QCorgnI("adminUserOrganization");
        BooleanExpression predicate = adminUserPredicate(user, organization, search);

        List<AdminUserProjection> rows =
                selectAdminUsers(user, organization)
                        .where(user.delYn.eq("N"), predicate)
                        .orderBy(adminUserOrder(user, organization, pageable.getSort()))
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .fetch();
        Long total =
                queryFactory
                        .select(user.count())
                        .from(user)
                        .leftJoin(organization)
                        .on(organization.prlmOgzCCone.eq(user.bbrC))
                        .where(user.delYn.eq("N"), predicate)
                        .fetchOne();
        return new PageImpl<>(new ArrayList<>(rows), pageable, total == null ? 0 : total);
    }

    @Override
    public List<UserRepository.AdminUserView> findAdminUsersForExport(String search, Sort sort) {
        QCuserI user = QCuserI.cuserI;
        QCorgnI organization = new QCorgnI("adminUserExportOrganization");
        return new ArrayList<>(
                selectAdminUsers(user, organization)
                        .where(user.delYn.eq("N"), adminUserPredicate(user, organization, search))
                        .orderBy(adminUserOrder(user, organization, sort))
                        .fetch());
    }

    private JPAQuery<AdminUserProjection> selectAdminUsers(QCuserI user, QCorgnI organization) {
        return queryFactory
                .select(
                        Projections.bean(
                                AdminUserProjection.class,
                                user.eno,
                                user.usrNm,
                                user.ptCNm,
                                user.temC,
                                user.temNm,
                                user.bbrC,
                                organization.bbrNm,
                                user.etrMilAddrNm,
                                user.inleNo,
                                user.cpnTpn,
                                user.fstEnrDtm,
                                user.lstChgDtm))
                .from(user)
                .leftJoin(organization)
                .on(organization.prlmOgzCCone.eq(user.bbrC));
    }

    private BooleanExpression adminUserPredicate(
            QCuserI user, QCorgnI organization, String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String keyword = search.trim();
        return user.eno
                .containsIgnoreCase(keyword)
                .or(user.usrNm.containsIgnoreCase(keyword))
                .or(user.ptCNm.containsIgnoreCase(keyword))
                .or(user.bbrC.containsIgnoreCase(keyword))
                .or(organization.bbrNm.containsIgnoreCase(keyword))
                .or(user.temC.containsIgnoreCase(keyword))
                .or(user.temNm.containsIgnoreCase(keyword))
                .or(user.etrMilAddrNm.containsIgnoreCase(keyword))
                .or(user.inleNo.containsIgnoreCase(keyword))
                .or(user.cpnTpn.containsIgnoreCase(keyword));
    }

    private OrderSpecifier<?>[] adminUserOrder(QCuserI user, QCorgnI organization, Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            boolean ascending = order.isAscending();
            OrderSpecifier<?> specifier =
                    switch (order.getProperty()) {
                        case "usrNm" -> ascending ? user.usrNm.asc() : user.usrNm.desc();
                        case "ptCNm" -> ascending ? user.ptCNm.asc() : user.ptCNm.desc();
                        case "bbrNm" ->
                                ascending ? organization.bbrNm.asc() : organization.bbrNm.desc();
                        case "temNm" -> ascending ? user.temNm.asc() : user.temNm.desc();
                        case "temC" -> ascending ? user.temC.asc() : user.temC.desc();
                        case "inleNo" -> ascending ? user.inleNo.asc() : user.inleNo.desc();
                        case "cpnTpn" -> ascending ? user.cpnTpn.asc() : user.cpnTpn.desc();
                        case "etrMilAddrNm" ->
                                ascending ? user.etrMilAddrNm.asc() : user.etrMilAddrNm.desc();
                        case "fstEnrDtm" ->
                                ascending ? user.fstEnrDtm.asc() : user.fstEnrDtm.desc();
                        case "lstChgDtm" ->
                                ascending ? user.lstChgDtm.asc() : user.lstChgDtm.desc();
                        default -> ascending ? user.eno.asc() : user.eno.desc();
                    };
            orders.add(specifier);
        }
        if (orders.isEmpty()) {
            orders.add(user.eno.asc());
        }
        return orders.toArray(OrderSpecifier[]::new);
    }

    @Getter
    @Setter
    public static class AdminUserProjection implements UserRepository.AdminUserView {
        private String eno;
        private String usrNm;
        private String ptCNm;
        private String temC;
        private String temNm;
        private String bbrC;
        private String bbrNm;
        private String etrMilAddrNm;
        private String inleNo;
        private String cpnTpn;
        private LocalDateTime fstEnrDtm;
        private LocalDateTime lstChgDtm;
    }

    @Override
    public List<UserDto.ListRow> findListRowsByBbrC(String bbrC, String enoPrefix) {
        QCuserI user = QCuserI.cuserI;
        QCorgnI organization = new QCorgnI("listOrganization");
        return selectListRows(user, organization)
                .where(user.bbrC.eq(bbrC), enoPrefixFilter(user, enoPrefix))
                .orderBy(employeeDisplayOrder(user))
                .fetch();
    }

    @Override
    public List<UserDto.ListRow> searchListRowsByKeyword(
            String keyword, String enoPrefix, int limit) {
        QCuserI user = QCuserI.cuserI;
        QCorgnI organization = new QCorgnI("searchOrganization");
        return selectListRows(user, organization)
                // 이름·사번·직위명·팀명 중 하나라도 부분 일치하면 결과에 포함한다 (대소문자 무시)
                .where(
                        user.usrNm
                                .containsIgnoreCase(keyword)
                                .or(user.eno.containsIgnoreCase(keyword))
                                .or(user.ptCNm.containsIgnoreCase(keyword))
                                .or(user.temNm.containsIgnoreCase(keyword)),
                        // 행번 접두사 필터를 상한 절단보다 먼저 DB에서 적용해
                        // 접두사에 맞는 사용자만 limit건을 채우게 한다.
                        enoPrefixFilter(user, enoPrefix))
                // 전체 조직이 대상이므로 표시 순서를 고정하고 반환 건수를 제한한다.
                // 정렬이 상한 절단보다 먼저 적용되므로 K 행번과 상위 직위가 먼저 살아남는다.
                .orderBy(employeeDisplayOrder(user))
                .limit(limit)
                .fetch();
    }

    /**
     * 행번({@code ENO}) 접두사 조회 조건을 만듭니다.
     *
     * <p>담당자·결재자 지정 화면처럼 특정 행번 체계(예: {@code K}로 시작하는 행번)만 선택해야 하는 조회에서 사용합니다.
     *
     * @param user 사용자 Q 타입
     * @param enoPrefix 행번 접두사. null·공백이면 조건을 만들지 않습니다(전체 조회)
     * @return 접두사 일치 조건, 접두사가 없으면 {@code null}
     */
    private BooleanExpression enoPrefixFilter(QCuserI user, String enoPrefix) {
        if (enoPrefix == null || enoPrefix.isBlank()) {
            return null;
        }
        return user.eno.startsWith(enoPrefix.trim());
    }

    @Override
    public Optional<UserDto.DetailRow> findDetailRowByEno(String eno) {
        QCuserI user = QCuserI.cuserI;
        QCorgnI organization = new QCorgnI("detailOrganization");
        QCorgnI parent = new QCorgnI("parentOrganization");
        UserDto.DetailRow row =
                queryFactory
                        .select(
                                Projections.constructor(
                                        UserDto.DetailRow.class,
                                        user.eno,
                                        user.bbrC,
                                        organization.bbrNm,
                                        user.temC,
                                        user.temNm,
                                        user.usrNm,
                                        user.ptCNm,
                                        user.etrMilAddrNm,
                                        user.inleNo,
                                        user.cpnTpn,
                                        user.cadrTpn,
                                        user.dtsDtlCone,
                                        organization.prlmHrkOgzCCone,
                                        parent.bbrNm))
                        .from(user)
                        .leftJoin(organization)
                        .on(organization.prlmOgzCCone.eq(user.bbrC))
                        .leftJoin(parent)
                        .on(parent.prlmOgzCCone.eq(organization.prlmHrkOgzCCone))
                        .where(user.eno.eq(eno))
                        .fetchFirst();
        return Optional.ofNullable(row);
    }

    @Override
    public List<String> findActiveQualificationGradeNamesByEno(String eno) {
        QCroleI role = QCroleI.croleI;
        QCauthI qualification = QCauthI.cauthI;
        return queryFactory
                .selectDistinct(qualification.qlfGrNm)
                .from(role)
                .join(qualification)
                .on(qualification.athId.eq(role.id.athId))
                .where(
                        role.id.eno.eq(eno),
                        role.useYn.eq("Y"),
                        role.delYn.eq("N"),
                        qualification.useYn.eq("Y"),
                        qualification.delYn.eq("N"))
                .orderBy(qualification.qlfGrNm.asc())
                .fetch();
    }

    private JPAQuery<UserDto.ListRow> selectListRows(QCuserI user, QCorgnI organization) {
        return queryFactory
                .select(
                        Projections.constructor(
                                UserDto.ListRow.class,
                                user.eno,
                                user.bbrC,
                                organization.bbrNm,
                                user.temC,
                                user.temNm,
                                user.usrNm,
                                user.ptCNm))
                .from(user)
                .leftJoin(organization)
                .on(organization.prlmOgzCCone.eq(user.bbrC));
    }

    /**
     * 직원 목록(직원 검색 다이얼로그·자동완성)의 표시 정렬 순서를 만듭니다.
     *
     * <p>우선순위: ① K로 시작하는 행번({@code ENO}) 우선(K*** &gt; O***) → ② 직위코드({@code PT_C}) 오름차순 → ③ 사용자명·행번
     * 오름차순. ③은 동순위 결과의 표시 순서를 고정하기 위한 보조 키입니다.
     *
     * <p>단, {@code B6}으로 시작하는 직위코드는 하나의 그룹으로 묶어 오름차순 위치({@code B5* 뒤, B7* 앞})는 유지하되 그룹 안에서는 직위코드
     * 내림차순으로 표시합니다.
     *
     * <p>직위코드가 없는 사용자는 같은 행번 그룹의 마지막에 표시합니다.
     *
     * @param user 사용자 Q 타입
     * @return 표시 정렬 OrderSpecifier 배열
     */
    private OrderSpecifier<?>[] employeeDisplayOrder(QCuserI user) {
        return new OrderSpecifier<?>[] {
            enoPrefixPriority(user).asc(),
            positionCodeGroup(user).asc().nullsLast(),
            // B6 그룹 안에서만 내림차순이 적용된다. 그 외 직위코드는 그룹 키가 직위코드 자체라 이 키의 영향을 받지 않는다.
            user.ptC.desc(),
            user.usrNm.asc(),
            user.eno.asc()
        };
    }

    /** B6으로 시작하는 직위코드를 {@code B6} 하나로 묶고, 그 외는 직위코드 그대로 두는 1차 정렬 키를 만듭니다. */
    private StringExpression positionCodeGroup(QCuserI user) {
        return new CaseBuilder()
                .when(user.ptC.startsWith(DESCENDING_POSITION_CODE_PREFIX))
                .then(DESCENDING_POSITION_CODE_PREFIX)
                .otherwise(user.ptC);
    }

    /** K로 시작하는 행번을 0, 그 외(O 행번 등)를 1로 매겨 K 행번을 앞세우는 정렬 키를 만듭니다. */
    private NumberExpression<Integer> enoPrefixPriority(QCuserI user) {
        return new CaseBuilder().when(user.eno.startsWith("K")).then(0).otherwise(1);
    }

    /**
     * 사용자명으로 사용자 검색 (QueryDSL 부분 일치 검색)
     *
     * <p>QueryDSL의 {@code contains()} 메서드를 사용하여 SQL의 {@code LIKE '%name%'} 조건을 타입 안전하게 표현합니다.
     *
     * <p>생성되는 SQL (예시):
     *
     * <pre>{@code
     * SELECT * FROM TPRMPP_CUSERI WHERE USR_NM LIKE '%홍%'
     * }</pre>
     *
     * @param name 검색할 사용자명 (부분 일치)
     * @return 이름에 해당 문자열을 포함하는 사용자 목록
     */
    @Override
    public List<CuserI> searchByName(String name) {
        // Q 타입: QueryDSL이 컴파일 시 CuserI 엔티티로부터 자동 생성한 메타 클래스
        QCuserI cuserI = QCuserI.cuserI;

        return queryFactory
                .selectFrom(cuserI) // SELECT * FROM TPRMPP_CUSERI
                .where(cuserI.usrNm.contains(name)) // WHERE USR_NM LIKE '%name%'
                .fetch(); // 결과 목록 반환 (비어있으면 빈 리스트)
    }
}
