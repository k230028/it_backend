package com.kdb.it.domain.migration.commondata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MenuSequenceSynchronizerTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private static CommonDataMigrationDto.MenuRow menu(String mnuId) {
        return new CommonDataMigrationDto.MenuRow(
                0, mnuId, null, "메뉴", "GRP", null, null, 1, "N", 1, "/" + mnuId);
    }

    @Test
    void 파일최대번호이상이될때까지_NEXTVAL을소비한다() {
        AtomicLong seq = new AtomicLong(20);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(inv -> seq.incrementAndGet());
        MenuSequenceSynchronizer synchronizer = new MenuSequenceSynchronizer(jdbcTemplate);

        var warning = synchronizer.advanceTo(List.of(menu("MNU0000023"), menu("MNU0000005")));

        assertThat(warning).isEmpty();
        assertThat(seq.get()).isEqualTo(23);
    }

    @Test
    void 이관대상메뉴가없으면_시퀀스를건드리지않는다() {
        MenuSequenceSynchronizer synchronizer = new MenuSequenceSynchronizer(jdbcTemplate);

        var warning = synchronizer.advanceTo(List.of());

        assertThat(warning).isEmpty();
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void MNU형식이아닌ID는_무시한다() {
        MenuSequenceSynchronizer synchronizer = new MenuSequenceSynchronizer(jdbcTemplate);

        var warning = synchronizer.advanceTo(List.of(menu("0000001"), menu("LEGACY")));

        assertThat(warning).isEmpty();
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void 조회실패시_예외대신경고를돌려준다() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("DB down"));
        MenuSequenceSynchronizer synchronizer = new MenuSequenceSynchronizer(jdbcTemplate);

        var warning = synchronizer.advanceTo(List.of(menu("MNU0000023")));

        assertThat(warning).isPresent();
        assertThat(warning.get()).contains("시퀀스");
    }
}
