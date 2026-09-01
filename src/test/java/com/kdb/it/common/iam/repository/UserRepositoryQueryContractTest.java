package com.kdb.it.common.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.Param;

/** 사용자 저장소의 명명 파라미터 쿼리 계약을 검증한다. */
@DisplayName("사용자 저장소 명명 파라미터 쿼리 계약")
class UserRepositoryQueryContractTest {

    @Test
    @DisplayName("결재자 조직 일괄 조회는 컴파일 파라미터 메타데이터와 무관하게 enos를 명시적으로 바인딩한다")
    void findByEnoInWithOrganization_bindsEnosExplicitly() throws NoSuchMethodException {
        Method method =
                UserRepository.class.getMethod("findByEnoInWithOrganization", Collection.class);

        Param param = method.getParameters()[0].getAnnotation(Param.class);

        assertThat(param).isNotNull();
        assertThat(param.value()).isEqualTo("enos");
    }
}
