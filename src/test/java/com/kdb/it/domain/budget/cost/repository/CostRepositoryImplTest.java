package com.kdb.it.domain.budget.cost.repository;

import static com.kdb.it.support.QuerydslExpressionTestSupport.constants;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        given(queryFactory.selectFrom(any())).willReturn(mockQuery);
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

    // -----------------------------------------------------------------------
    // 결재상태 스코프별 버전 노출 — 상신된 재상신 초안(LST_YN='N')이 사라지지 않아야 한다
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("일반 검색은 최종본(LST_YN=Y)만 조건으로 사용한다")
    void searchByCondition_최종본만_조회한다() {
        sut.searchByCondition(new CostDto.SearchCondition());

        assertThat(capturedPredicate()).contains("bcostm.lstYn = Y");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "1", "3", "4", "결재중", "반려", "회수"})
    @DisplayName("미상신·결재중·반려·회수 스코프는 최종본 조건을 걸지 않아 재상신 초안도 노출된다")
    void searchByCondition_초안노출스코프는_최종본조건을_걸지않는다(String apfSts) {
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        condition.setApfSts(apfSts);

        sut.searchByCondition(condition);

        assertThat(capturedPredicate()).doesNotContain("bcostm.lstYn = Y");
    }

    @Test
    @DisplayName("apfSts=none 신청대상에서 수기등록 상태를 제외한다")
    void searchByCondition_apfStsNone_수기등록제외() {
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        condition.setApfSts("none");

        sut.searchByCondition(condition);

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        org.mockito.Mockito.verify(mockQuery).where(predicate.capture());
        assertThat(constants(predicate.getValue())).contains("1", "2", "9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "결재완료"})
    @DisplayName("결재완료 스코프는 최종본 조건을 유지해 과거 승인본이 중복 노출되지 않는다")
    void searchByCondition_결재완료스코프는_최종본만_조회한다(String apfSts) {
        CostDto.SearchCondition condition = new CostDto.SearchCondition();
        condition.setApfSts(apfSts);

        sut.searchByCondition(condition);

        assertThat(capturedPredicate()).contains("bcostm.lstYn = Y");
    }

    /**
     * 목록 쿼리에 전달된 WHERE 술어의 문자열 표현을 돌려줍니다.
     *
     * <p>EXISTS 서브쿼리는 {@code toString()}에 내부가 드러나지 않으므로(메타데이터 식별자만 출력) 이 헬퍼로는 최상위 조건만 검증할 수 있습니다.
     * 서브쿼리에 들어가는 결재상태 코드 정규화는 {@code BudgetListVersionScopeTest}와 실제 SQL을 도는 통합 테스트가 담당합니다.
     */
    private String capturedPredicate() {
        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        org.mockito.Mockito.verify(mockQuery).where(predicate.capture());
        return predicate.getValue().toString();
    }
}
