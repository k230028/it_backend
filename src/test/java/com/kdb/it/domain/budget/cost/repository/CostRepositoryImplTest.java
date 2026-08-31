package com.kdb.it.domain.budget.cost.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CostRepositoryImpl 단위 테스트.
 *
 * <p>DB 없이 QueryDSL 호출 모양을 검증해 목록 화면이 상세 전용 컬럼에 의존하지 않도록 막는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostRepositoryImplTest {

    @Mock private JPAQueryFactory queryFactory;

    @Mock
    @SuppressWarnings("rawtypes")
    private JPAQuery mockQuery;

    private CostRepositoryImpl sut;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sut = new CostRepositoryImpl(queryFactory);
        given(queryFactory.select(any(Expression.class))).willReturn(mockQuery);
        given(mockQuery.from(any(EntityPath.class))).willReturn(mockQuery);
        given(mockQuery.where(any(Predicate.class))).willReturn(mockQuery);
        given(mockQuery.orderBy(any(OrderSpecifier[].class))).willReturn(mockQuery);
        given(mockQuery.offset(anyLong())).willReturn(mockQuery);
        given(mockQuery.limit(anyLong())).willReturn(mockQuery);
        given(mockQuery.fetch()).willReturn(List.of());
    }

    @Test
    @DisplayName("목록 프로젝션은 표시 요약 컬럼만 select하고 상세 전용 컬럼을 요구하지 않는다")
    void searchListByCondition_selectsLightweightColumnsOnly() {
        CostDto.SearchCondition condition = new CostDto.SearchCondition();

        List<CostDto.CostListRow> result = sut.searchListByCondition(condition);

        assertThat(result).isEmpty();
        ArgumentCaptor<Expression<?>> projection = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(queryFactory).select(projection.capture());
        String selected = projection.getValue().toString();
        assertThat(selected)
                .contains("bcostm.costBgNo", "bcostm.cttNm", "bcostm.costTotXpAmt")
                .doesNotContain(
                        "dfrCleC",
                        "fstDfrDt",
                        "xcrBseDt",
                        "indRsn",
                        "cgprId",
                        "bgUntAbusC",
                        "cncdRfrNo",
                        "fcAmt");
    }
}
