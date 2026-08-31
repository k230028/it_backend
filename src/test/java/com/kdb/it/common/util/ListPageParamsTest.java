package com.kdb.it.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 목록 페이지 파라미터의 구간 계산과 입력 검증을 고정합니다. */
class ListPageParamsTest {

    private static final long MAX_ROWS = 500L;

    private static ListPageParams params(Integer page, Integer size) {
        ListPageParams value = new ListPageParams();
        value.setPage(page);
        value.setSize(size);
        return value;
    }

    @Test
    @DisplayName("page·size 미지정이면 페이징 없이 상한까지 조회한다")
    void 미지정이면_상한까지() {
        ListPageParams params = ListPageParams.unpaged();

        assertThat(params.isPaged()).isFalse();
        assertThat(params.slice(MAX_ROWS)).isEqualTo(new ListPageParams.Slice(0L, MAX_ROWS));
    }

    @Test
    @DisplayName("size만 지정하면 첫 페이지로 간주한다")
    void size만_지정하면_첫페이지() {
        ListPageParams params = params(null, 100);

        assertThat(params.isPaged()).isTrue();
        assertThat(params.slice(MAX_ROWS)).isEqualTo(new ListPageParams.Slice(0L, 100L));
    }

    @Test
    @DisplayName("page와 size를 함께 지정하면 해당 구간을 계산한다")
    void page와_size로_구간계산() {
        assertThat(params(3, 100).slice(MAX_ROWS)).isEqualTo(new ListPageParams.Slice(300L, 100L));
    }

    @Test
    @DisplayName("size 없이 page만 지정하면 조용히 무시하지 않고 예외로 알린다")
    void page만_지정하면_예외() {
        assertThatThrownBy(() -> params(2, null).slice(MAX_ROWS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("size가 0 이하이거나 상한을 넘으면 상한으로 되돌리지 않고 예외로 알린다")
    void size_범위밖이면_예외() {
        assertThatThrownBy(() -> params(0, 0).slice(MAX_ROWS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params(0, (int) MAX_ROWS + 1).slice(MAX_ROWS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("page가 음수이면 0으로 보정하지 않고 예외로 알린다")
    void page_음수면_예외() {
        assertThatThrownBy(() -> params(-1, 10).slice(MAX_ROWS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
