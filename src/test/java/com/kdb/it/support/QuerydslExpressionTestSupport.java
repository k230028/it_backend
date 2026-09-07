package com.kdb.it.support;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Constant;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.FactoryExpression;
import com.querydsl.core.types.Operation;
import com.querydsl.core.types.SubQueryExpression;
import com.querydsl.core.types.TemplateExpression;
import java.util.LinkedHashSet;
import java.util.Set;

/** QueryDSL 식과 서브쿼리에 포함된 상수값을 회귀 테스트에서 확인하는 도우미입니다. */
public final class QuerydslExpressionTestSupport {

    private QuerydslExpressionTestSupport() {}

    /** 최상위 식과 모든 서브쿼리 WHERE 절에 포함된 상수값을 반환합니다. */
    public static Set<Object> constants(Expression<?> expression) {
        Set<Object> values = new LinkedHashSet<>();
        collect(expression, values);
        return values;
    }

    private static void collect(Expression<?> expression, Set<Object> values) {
        if (expression == null) {
            return;
        }
        if (expression instanceof BooleanBuilder builder) {
            collect(builder.getValue(), values);
        } else if (expression instanceof Constant<?> constant) {
            Object value = constant.getConstant();
            if (value instanceof Iterable<?> iterable) {
                iterable.forEach(values::add);
            } else {
                values.add(value);
            }
        } else if (expression instanceof Operation<?> operation) {
            operation.getArgs().forEach(argument -> collect(argument, values));
        } else if (expression instanceof SubQueryExpression<?> subQuery) {
            collect(subQuery.getMetadata().getWhere(), values);
        } else if (expression instanceof FactoryExpression<?> factoryExpression) {
            factoryExpression.getArgs().forEach(argument -> collect(argument, values));
        } else if (expression instanceof TemplateExpression<?> templateExpression) {
            templateExpression.getArgs().stream()
                    .filter(Expression.class::isInstance)
                    .map(Expression.class::cast)
                    .forEach(argument -> collect(argument, values));
        }
    }
}
