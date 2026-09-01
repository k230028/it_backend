package com.kdb.it.domain.budget.project.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
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
 * ProjectRepositoryImpl 단위 테스트
 *
 * <p>JPAQueryFactory를 Mock 처리하여 DB 없이 searchByCondition()의 동적 쿼리 분기 로직을 검증합니다.
 *
 * <p>[apfSts 서브쿼리 주의] NOT EXISTS / EXISTS 서브쿼리는 실제 DB에서 실행되는 SQL이므로 서브쿼리 SQL 검증은 @DataJpaTest 통합
 * 테스트 범위입니다. 이 단위 테스트는 분기 진입 여부 및 fetch() 결과 전달에 집중합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectRepositoryImplTest {

    @Mock private JPAQueryFactory queryFactory;

    @Mock
    @SuppressWarnings("rawtypes")
    private JPAQuery mockQuery;

    private ProjectRepositoryImpl sut;

    /**
     * 각 테스트 전: selectFrom -> where -> fetch 체인을 설정합니다.
     *
     * <p>QueryDSL 체인은 같은 JPAQuery 인스턴스를 계속 반환하므로 단일 mockQuery로 전 체인을 대응합니다.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sut = new ProjectRepositoryImpl(queryFactory);
        given(queryFactory.selectFrom(any())).willReturn(mockQuery);
        given(queryFactory.select(any(Expression.class))).willReturn(mockQuery);
        given(mockQuery.from(any(EntityPath.class))).willReturn(mockQuery);
        given(mockQuery.where(any(Predicate.class))).willReturn(mockQuery);
        given(mockQuery.orderBy(any(OrderSpecifier[].class))).willReturn(mockQuery);
        given(mockQuery.offset(anyLong())).willReturn(mockQuery);
        given(mockQuery.limit(anyLong())).willReturn(mockQuery);
        given(mockQuery.fetch()).willReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // 성공 케이스 — 조건 없음 (전체 조회)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("조건이 모두 null이면 fetch 결과가 그대로 반환된다")
    void searchByCondition_noCondition_returnsFetchResult() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetch 결과에 데이터가 있을 때 해당 리스트가 반환된다")
    void searchByCondition_fetchHasData_returnsData() {
        // Arrange
        Bprojm dummy = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).build();
        given(mockQuery.fetch()).willReturn(List.of(dummy));
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("일반 검색은 최종본(LST_YN=Y)만 조건으로 사용한다")
    void searchByCondition_최종본만_조회한다() {
        sut.searchByCondition(new ProjectDto.SearchCondition());

        assertThat(capturedPredicate()).contains("bprojm.lstYn = Y");
    }

    // -----------------------------------------------------------------------
    // 결재상태 스코프별 버전 노출 — 상신된 재상신 초안(LST_YN='N')이 사라지지 않아야 한다
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"none", "1", "3", "4", "결재중", "반려", "회수"})
    @DisplayName("미상신·결재중·반려·회수 스코프는 최종본 조건을 걸지 않아 재상신 초안도 노출된다")
    void searchByCondition_초안노출스코프는_최종본조건을_걸지않는다(String apfSts) {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts(apfSts);

        sut.searchByCondition(condition);

        assertThat(capturedPredicate()).doesNotContain("bprojm.lstYn = Y");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "결재완료"})
    @DisplayName("결재완료 스코프는 최종본 조건을 유지해 과거 승인본이 중복 노출되지 않는다")
    void searchByCondition_결재완료스코프는_최종본만_조회한다(String apfSts) {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts(apfSts);

        sut.searchByCondition(condition);

        assertThat(capturedPredicate()).contains("bprojm.lstYn = Y");
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

    // -----------------------------------------------------------------------
    // 성공 케이스 — 단순 필드 조건
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("bgYy 조건을 입력하면 예외 없이 쿼리가 실행된다")
    void searchByCondition_withBgYy_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setBseYy("2026");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("bgYy+prjSts+itDpm+svnDpm 복합 조건에서도 정상적으로 쿼리가 실행된다")
    void searchByCondition_multipleConditions_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setBseYy("2026");
        condition.setStsTc("계획");
        condition.setDvmDpmC("IT001");
        condition.setSvnDpmC("BIZ001");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 성공 케이스 — ornYn 분기
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ornYn=Y 이면 경상사업 조건(ODN_YN=Y)이 추가되어 쿼리가 실행된다")
    void searchByCondition_ornYnY_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setOdnYn("Y");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("ornYn=N 이면 일반 정보화사업 조건(IS NULL OR != Y)이 추가된다")
    void searchByCondition_ornYnN_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setOdnYn("N");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 성공 케이스 — apfSts 분기 진입
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("apfSts=none 이면 NOT EXISTS 서브쿼리 분기로 진입하여 예외 없이 실행된다")
    void searchByCondition_apfStsNone_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts("none");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("apfSts=none 결재 상신 대상 조회는 재신청 초안(LST_YN=N)을 제외하지 않는다")
    void searchByCondition_apfStsNone_재신청초안포함() {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts("none");

        sut.searchByCondition(condition);

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        org.mockito.Mockito.verify(mockQuery).where(predicate.capture());
        assertThat(predicate.getValue().toString()).doesNotContain("bprojm.lstYn = Y");
    }

    @Test
    @DisplayName("apfSts에 결재완료 값을 입력하면 EXISTS 서브쿼리 분기로 진입한다")
    void searchByCondition_apfStsSpecificValue_executesQuery() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts("결재완료");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 엣지 케이스 — 빈 문자열 조건
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("bgYy가 빈 문자열이면 필터 미적용 — 예외 없이 쿼리가 실행된다")
    void searchByCondition_bgYyBlank_treatedAsNoCondition() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setBseYy("");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("apfSts가 빈 문자열이면 서브쿼리 조건이 추가되지 않고 쿼리가 실행된다")
    void searchByCondition_apfStsBlank_noSubqueryCondition() {
        // Arrange
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setApfSts("");
        // Act
        List<Bprojm> result = sut.searchByCondition(condition);
        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("목록 프로젝션은 대용량 본문 컬럼 없이 요약 컬럼만 select한다")
    void searchListByCondition_selectsLightweightColumnsOnly() {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();

        sut.searchListByCondition(condition);

        ArgumentCaptor<Expression<?>> projection = ArgumentCaptor.captor();
        org.mockito.Mockito.verify(queryFactory).select(projection.capture());
        String selected = projection.getValue().toString();
        assertThat(selected)
                .contains("bprojm.abusMngNo", "bprojm.abusNm", "bprojm.bseYy")
                .doesNotContain(
                        "abusCone",
                        "cpnSafCone",
                        "abusNcsCone",
                        "dgogPpoCone",
                        "plmDes",
                        "abusRngCone",
                        "mnPrgCone",
                        "hrfPlnCone");
    }
}
