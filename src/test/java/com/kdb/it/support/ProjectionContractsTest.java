package com.kdb.it.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝션 계약 도우미가 합성 메서드를 실제로 걸러내는지 고정합니다.
 *
 * <p>이 테스트가 없으면 필터가 조용히 무력화돼도(예: 조건을 반대로 쓰거나 지워도) 계약 단정 쪽에서는 아무 일도 일어나지 않습니다 — 합성 메서드가 섞이는 상황이 드물기
 * 때문입니다. 드물게 터지는 함정이라 그 함정을 여기서 재현해 둡니다(BE-40).
 */
class ProjectionContractsTest {

    /** 제네릭 인터페이스. 좁힌 반환형으로 구현하면 컴파일러가 합성 bridge 메서드를 만듭니다. */
    private interface Box<T> {
        T value();
    }

    /** {@code Object value()} bridge가 추가로 선언되는 구현. 선언 메서드는 2개지만 계약은 1개입니다. */
    private static final class StringBox implements Box<String> {
        @Override
        public String value() {
            return "값";
        }
    }

    /** 본문 있는 메서드를 가진 인터페이스 — JaCoCo 에이전트가 합성 {@code $jacocoInit}을 넣는 대상입니다. */
    private interface WithDefaultMethod {
        String required();

        default String optional() {
            return null;
        }
    }

    @Test
    @DisplayName("컴파일러가 만든 bridge 메서드는 계약에서 제외한다")
    void excludesBridgeMethods() {
        assertThat(StringBox.class.getDeclaredMethods())
                .as("전제: 좁힌 반환형 구현에는 bridge 메서드가 함께 선언된다")
                .hasSizeGreaterThan(1);

        assertThat(ProjectionContracts.declaredMethodNames(StringBox.class))
                .containsExactly("value");
    }

    @Test
    @DisplayName("커버리지 계측이 넣는 합성 메서드는 계약에서 제외한다")
    void excludesCoverageSyntheticMethods() {
        // 커버리지를 켠 실행에서는 $jacocoInit이 선언 메서드에 섞여 있고, 끈 실행에서는 없다.
        // 어느 쪽이든 계약 결과는 같아야 한다 — MIG-13이 실패한 지점이 바로 이 차이였다.
        assertThat(ProjectionContracts.declaredMethodNames(WithDefaultMethod.class))
                .containsExactlyInAnyOrder("required", "optional");
    }

    @Test
    @DisplayName("생성자는 유틸 클래스 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = ProjectionContracts.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
