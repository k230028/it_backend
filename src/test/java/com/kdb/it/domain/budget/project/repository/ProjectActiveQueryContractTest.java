package com.kdb.it.domain.budget.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectActiveQueryContractTest {

    private List<Invocation> invocations;
    private ProjectRepository repository;
    private List<Bprojm> finalProjects;

    @BeforeEach
    void setUp() {
        invocations = new ArrayList<>();
        finalProjects =
                List.of(Bprojm.builder().abusMngNo("PRJ-001").sno(2).lstYn("Y").delYn("N").build());
        repository =
                (ProjectRepository)
                        Proxy.newProxyInstance(
                                ProjectRepository.class.getClassLoader(),
                                new Class<?>[] {ProjectRepository.class},
                                this::invoke);
    }

    @Test
    @DisplayName("일반 사업 일괄 조회는 최종본(LST=Y) 쿼리로만 위임한다")
    void projectBatchQuery_delegatesToFinalVersionQuery() {
        List<String> projectIds = List.of("PRJ-001", "PRJ-002");

        List<Bprojm> result = repository.findByAbusMngNoInAndDelYn(projectIds, "N");

        assertThat(result).isSameAs(finalProjects);
        assertLastCall("findByAbusMngNoInAndDelYnAndLstYn", projectIds, "N", "Y");
    }

    private Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        invocations.add(new Invocation(method.getName(), args == null ? List.of() : List.of(args)));
        if (method.getName().equals("findByAbusMngNoInAndDelYnAndLstYn")) return finalProjects;
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
