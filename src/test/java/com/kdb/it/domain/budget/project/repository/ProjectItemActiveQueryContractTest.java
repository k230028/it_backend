package com.kdb.it.domain.budget.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectItemActiveQueryContractTest {

    private List<Invocation> invocations;
    private ProjectItemRepository repository;

    @BeforeEach
    void setUp() {
        invocations = new ArrayList<>();
        repository =
                (ProjectItemRepository)
                        Proxy.newProxyInstance(
                                ProjectItemRepository.class.getClassLoader(),
                                new Class<?>[] {ProjectItemRepository.class},
                                this::invoke);
    }

    @Test
    @DisplayName("사업 순번 조회는 DEL=N과 부모 순번으로 개정본을 고정한다")
    void projectSequenceQuery_delegatesToLatestActiveQuery() {
        repository.findByAbusMngNoAndFntTbCrySnoAndDelYn("P1", 1, "N");

        assertLastCall("findAllByAbusMngNoAndFntTbCrySnoAndDelYn", "P1", 1, "N");
    }

    @Test
    @DisplayName("사업 활성 조회는 과거 품목을 제외하는 LST=Y 쿼리로 위임한다")
    void projectQuery_delegatesToLatestActiveQuery() {
        repository.findByAbusMngNoAndDelYn("P1", "N");

        assertLastCall("findByAbusMngNoAndDelYnAndLstYn", "P1", "N", "Y");
    }

    @Test
    @DisplayName("사업 집합 조회는 후속 조립이 부모 순번으로 나눌 수 있게 전체 개정 품목을 반환한다")
    void projectBatchQuery_delegatesToLatestActiveQuery() {
        repository.findByAbusMngNoInAndDelYn(List.of("P1", "P2"), "N");

        assertLastCall("findAllByAbusMngNoInAndDelYn", List.of("P1", "P2"), "N");
    }

    @Test
    @DisplayName("예산 프로젝션 활성 조회는 과거 품목을 제외하는 LST=Y 쿼리로 위임한다")
    void budgetViewQuery_delegatesToLatestActiveQuery() {
        repository.findBudgetViewsByAbusMngNoInAndDelYn(List.of("P1"), "N");

        assertLastCall("findBudgetViewsByAbusMngNoInAndDelYnAndLstYn", List.of("P1"), "N", "Y");
    }

    @Test
    @DisplayName("정보보호 품목 존재 조회도 과거 품목을 제외하는 LST=Y 쿼리로 위임한다")
    void securityItemExistsQuery_delegatesToLatestActiveQuery() {
        repository.existsByAbusMngNoAndSectSysUtzYnAndDelYn("P1", "Y", "N");

        assertLastCall("existsByAbusMngNoAndSectSysUtzYnAndDelYnAndLstYn", "P1", "Y", "N", "Y");
    }

    private Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        invocations.add(new Invocation(method.getName(), args == null ? List.of() : List.of(args)));
        if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
        if (Collection.class.isAssignableFrom(method.getReturnType())) return List.of();
        if (method.getReturnType() == boolean.class) return false;
        return null;
    }

    private void assertLastCall(String methodName, Object... args) {
        assertThat(invocations)
                .last()
                .satisfies(
                        invocation -> {
                            assertThat(invocation.methodName()).isEqualTo(methodName);
                            assertThat(invocation.arguments()).containsExactly(args);
                        });
    }

    private record Invocation(String methodName, List<Object> arguments) {}
}
