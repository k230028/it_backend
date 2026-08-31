package com.kdb.it.common.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationApprovalMarkerTest {

    @Test
    @DisplayName("생성자는 상수 컨테이너 인스턴스 생성을 차단한다")
    void constructor_인스턴스화시_예외발생() throws Exception {
        var constructor = MigrationApprovalMarker.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(catchThrowable(constructor::newInstance))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("이관 표식은 저장된 고정 문구와 정확히 일치한다")
    void note_고정문구() {
        assertThat(MigrationApprovalMarker.NOTE)
                .isEqualTo("수기등록");
    }

    @Test
    @DisplayName("등록자결재요청내용이 이관 표식과 정확히 같으면 이관 기록이다")
    void isMigrated_고정문구이면_true() {
        assertThat(MigrationApprovalMarker.isMigrated(MigrationApprovalMarker.NOTE)).isTrue();
    }

    @Test
    @DisplayName("등록자결재요청내용이 다른 문구이거나 null이면 이관 기록이 아니다")
    void isMigrated_그밖이면_false() {
        assertThat(MigrationApprovalMarker.isMigrated("수기 엑셀 이관으로 생성된 결재완료 기록입니다.")).isFalse();
        assertThat(MigrationApprovalMarker.isMigrated(null)).isFalse();
    }
}
