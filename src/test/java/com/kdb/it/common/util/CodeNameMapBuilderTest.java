package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CodeNameMapBuilder 단위 테스트 — cdva 필터, null 코드명 제외, 빈 입력 가드 검증. */
@ExtendWith(MockitoExtension.class)
class CodeNameMapBuilderTest {

    @Mock private CodeRepository codeRepository;

    private CodeNameMapBuilder sut() {
        return new CodeNameMapBuilder(codeRepository);
    }

    /** Ccodem은 @SuperBuilder만 제공(@Setter 없음)하므로 빌더로 픽스처를 만든다. */
    private Ccodem code(String cdva, String cdvaNm) {
        return Ccodem.builder().cdva(cdva).cdvaNm(cdvaNm).build();
    }

    @Test
    @DisplayName("cdvas가 비어 있으면 빈 맵을 반환한다")
    void emptyCdvas_returnsEmptyMap() {
        assertThat(sut().build("IOE_C", Set.of())).isEmpty();
    }

    @Test
    @DisplayName("cdvas가 null이면 빈 맵을 반환한다")
    void nullCdvas_returnsEmptyMap() {
        assertThat(sut().build("IOE_C", null)).isEmpty();
    }

    @Test
    @DisplayName("지정한 cdva만 cdva→cdvaNm으로 매핑한다")
    void filtersByCdva() {
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(code("100", "사업"), code("200", "전산업무비")));

        Map<String, String> result = sut().build("IOE_C", Set.of("100"));

        assertThat(result).containsExactlyEntriesOf(Map.of("100", "사업"));
    }

    @Test
    @DisplayName("코드명이 null인 항목은 맵에서 제외한다")
    void excludesNullCodeName() {
        given(codeRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(List.of(code("100", null), code("200", "전산업무비")));

        Map<String, String> result = sut().build("IOE_C", Set.of("100", "200"));

        assertThat(result).containsExactlyEntriesOf(Map.of("200", "전산업무비"));
    }
}
