package com.kdb.it.domain.menu.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.menu.entity.QCmenum;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CmenumRepositoryImpl#menuTreeRowProjection}의 컬럼 존재/부재 분기를 Oracle 없이 검증한다.
 *
 * <p>local 스키마에는 항상 {@code IMK_NM}이 존재하므로 Oracle 통합 테스트만으로는 컬럼 부재 분기(9인자 생성자, imkNm 미포함 select)를
 * 실제로 타지 못한다. 분기 조건이 인자로 분리되어 있어 QueryDSL·Spring 없이도 두 분기를 모두 호출하고 생성된 {@link
 * ConstructorExpression}의 인자 목록을 직접 검사할 수 있다.
 */
@DisplayName("메뉴 트리 프로젝션의 IMK_NM 컬럼 존재/부재 분기")
class MenuTreeRowProjectionTest {

    // 생성자 인자만 사용하며 queryFactory/entityManager/dataSource는 이 메서드에서 참조되지 않는다.
    private final CmenumRepositoryImpl repository = new CmenumRepositoryImpl(null, null, null);

    private final QCmenum m = QCmenum.cmenum;

    @Test
    @DisplayName("컬럼이 있으면 imkNm을 포함한 10개 인자를 select한다")
    void columnPresent_includesImkNmAsTenthArgument() {
        ConstructorExpression<MenuTreeRow> projection = repository.menuTreeRowProjection(m, true);

        List<Expression<?>> args = projection.getArgs();

        assertThat(args).hasSize(10);
        assertThat(args).contains(m.imkNm);
    }

    @Test
    @DisplayName("컬럼이 없으면 imkNm 없이 9개 인자만 select한다 — ORA-00904 방지 보증")
    void columnAbsent_excludesImkNmAndHasNineArguments() {
        ConstructorExpression<MenuTreeRow> projection = repository.menuTreeRowProjection(m, false);

        List<Expression<?>> args = projection.getArgs();

        assertThat(args).hasSize(9);
        assertThat(args).doesNotContain(m.imkNm);
        assertThat(args)
                .extracting(arg -> ((Path<?>) arg).getMetadata().getName())
                .doesNotContain("imkNm");
    }

    @Test
    @DisplayName("두 분기의 앞 9개 인자는 같은 순서의 같은 경로다")
    void bothBranches_shareSameFirstNineArgumentsInOrder() {
        List<Expression<?>> present = repository.menuTreeRowProjection(m, true).getArgs();
        List<Expression<?>> absent = repository.menuTreeRowProjection(m, false).getArgs();

        assertThat(present.subList(0, 9)).containsExactlyElementsOf(absent);
    }
}
