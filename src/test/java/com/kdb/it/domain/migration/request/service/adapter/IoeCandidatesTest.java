package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 비목 진단에 붙는 후보 선택 규칙을 고정합니다.
 *
 * <p>후보가 빈 진단은 화면에 드롭다운이 그려지지 않아 사용자가 보정할 수단을 잃습니다(MIG-10·MIG-21의 원인). 좁히지 못한 것과 고를 수 없는 것을 구분하는
 * 규칙이라 값 자체를 못 박습니다.
 */
class IoeCandidatesTest {

    @Test
    @DisplayName("생성자는 유틸 클래스 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = IoeCandidates.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("해석기가 좁힌 후보가 있으면 그 후보만 낸다")
    void 좁혀진_후보를_그대로_사용한다() {
        List<MigrationDto.Candidate> narrowed =
                List.of(new MigrationDto.Candidate("103", "개발비(일반)"));

        assertThat(IoeCandidates.orAll(resolution(narrowed), context())).isEqualTo(narrowed);
    }

    @Test
    @DisplayName("좁히지 못하면 비목 전체를 후보로 낸다")
    void 후보가_없으면_비목전체를_낸다() {
        assertThat(IoeCandidates.orAll(resolution(List.of()), context()))
                .hasSize(TestIoeIndex.codes().size())
                .extracting(MigrationDto.Candidate::code)
                .contains("001", "103", "107");
    }

    private static IoeHierarchyIndex.Resolution resolution(
            List<MigrationDto.Candidate> candidates) {
        return new IoeHierarchyIndex.Resolution(null, null, candidates, false);
    }

    private static FormAdapterContext context() {
        return new FormAdapterContext(
                Map.of(),
                "2026",
                null,
                "100",
                "디지털부",
                null,
                TestIoeIndex.snapshot(),
                Map.of(),
                "TESTER");
    }
}
