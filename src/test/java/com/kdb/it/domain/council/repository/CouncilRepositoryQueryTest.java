package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class CouncilRepositoryQueryTest {

    @Test
    @DisplayName("findByDepartment: BPROJM 주관부서 컬럼(SVN_DPM_C)으로 필터링한다")
    void findByDepartment_usesSvnDpmC() throws NoSuchMethodException {
        Method method = CouncilRepository.class.getMethod("findByDepartment", String.class, String.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("p.SVN_DPM_C = :svnDpmC");
        assertThat(query.value()).doesNotContain("p.BBR_C");
    }

    @Test
    @DisplayName("findProjectsForCouncilAll: BPROJM에 없는 상태/사업유형 컬럼을 참조하지 않는다")
    void findProjectsForCouncilAll_usesExistingColumns() throws NoSuchMethodException {
        Method method = CouncilRepository.class.getMethod("findProjectsForCouncilAll", String.class, String.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("TPRMPP_BPROJA");
        assertThat(query.value()).contains("p.ABUS_PPO_CONE");
        assertThat(query.value()).contains("ps.IT_PTL_STS_TC");
        assertThat(query.value()).doesNotContain("p.BZ_TP_C");
        assertThat(query.value()).doesNotContain("p.IT_PTL_STS_TC");
    }

    @Test
    @DisplayName("findProjectsForCouncilByDepartment: BPROJM에 없는 상태/사업유형 컬럼을 참조하지 않는다")
    void findProjectsForCouncilByDepartment_usesExistingColumns() throws NoSuchMethodException {
        Method method = CouncilRepository.class.getMethod(
                "findProjectsForCouncilByDepartment", String.class, String.class, String.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("TPRMPP_BPROJA");
        assertThat(query.value()).contains("p.ABUS_PPO_CONE");
        assertThat(query.value()).contains("p.SVN_DPM_C = :svnDpm");
        assertThat(query.value()).contains("ps.IT_PTL_STS_TC");
        assertThat(query.value()).doesNotContain("p.BZ_TP_C");
        assertThat(query.value()).doesNotContain("p.IT_PTL_STS_TC");
    }
}
