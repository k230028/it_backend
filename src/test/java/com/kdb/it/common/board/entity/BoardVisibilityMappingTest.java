package com.kdb.it.common.board.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.domain.log.entity.CblbcmL;
import com.kdb.it.domain.log.entity.CcmmtmL;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("게시판 노출 여부 컬럼 매핑")
class BoardVisibilityMappingTest {

    @Test
    @DisplayName("게시물과 변경로그는 노출여부를 XPO_YN에 매핑한다")
    void postAndLogMapExposureToXpoYn() {
        assertThat(mappedColumnNames(Cblbcm.class)).contains("XPO_YN").doesNotContain("SRE_USE_YN");
        assertThat(mappedColumnNames(CblbcmL.class))
                .contains("XPO_YN")
                .doesNotContain("SRE_USE_YN");
    }

    @Test
    @DisplayName("댓글과 변경로그 및 응답에는 별도 노출여부가 없다")
    void commentAndLogDoNotExposeVisibilityField() {
        assertThat(mappedColumnNames(Ccmmtm.class)).doesNotContain("SRE_USE_YN", "XPO_YN");
        assertThat(mappedColumnNames(CcmmtmL.class)).doesNotContain("SRE_USE_YN", "XPO_YN");
        assertThat(
                        Arrays.stream(BoardCommentDto.Response.class.getDeclaredFields())
                                .map(Field::getName))
                .doesNotContain("sreYn", "xpoYn");
    }

    private String[] mappedColumnNames(Class<?> entityType) {
        return Arrays.stream(entityType.getDeclaredFields())
                .map(field -> field.getAnnotation(Column.class))
                .filter(column -> column != null)
                .map(Column::name)
                .toArray(String[]::new);
    }
}
