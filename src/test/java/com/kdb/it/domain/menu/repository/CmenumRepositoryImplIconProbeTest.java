package com.kdb.it.domain.menu.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CmenumRepositoryImpl#isIconColumnPresent}의 캐시·냉각 정책을 검증한다.
 *
 * <p>판정 성공(true/false 무관)은 프로세스 수명 동안 캐시하고, 판정 실패(DB 접속 예외 등)는 캐시하지 않되 짧은 냉각 시간 동안만 재시도를 억제한다. 실패를
 * 영구 캐시하면 기동 직후의 일시적 DB 장애 한 번이 프로세스 수명 내내 폴백 모드에 고정되고, 반대로 전혀 억제하지 않으면 지속 장애 동안 매 요청이 커넥션 풀을 두드리게
 * 된다. 실제 Oracle 없이 {@link DataSource}를 모킹해 이 계약만 좁게 확인한다.
 */
class CmenumRepositoryImplIconProbeTest {

    @Test
    @DisplayName("판정이 실패하면 캐시하지 않고 다음 호출에서 다시 판정한다")
    void failedProbe_isNotCached_andRetriedOnNextCall() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection okConnection = mock(Connection.class);
        PreparedStatement okStatement = mock(PreparedStatement.class);
        ResultSet okResultSet = mock(ResultSet.class);
        given(okConnection.prepareStatement(anyString())).willReturn(okStatement);
        given(okStatement.executeQuery()).willReturn(okResultSet);
        given(okResultSet.next()).willReturn(true);
        given(okResultSet.getInt(1)).willReturn(1);

        given(dataSource.getConnection())
                .willThrow(new SQLException("일시적 접속 실패"))
                .willReturn(okConnection);

        CmenumRepositoryImpl repository = new CmenumRepositoryImpl(null, null, dataSource);
        // 냉각 시간을 0으로 두어 이 테스트의 취지(실패를 캐시하지 않고 즉시 재시도)를 그대로 검증한다.
        // 냉각 시간 자체의 억제 효과는 failedProbe_suppressesRetryDuringCooldown_thenRetriesAfter에서 별도 확인한다.
        repository.setProbeFailureCooldownNanos(0);

        // 1차 호출: 접속 실패 → false를 돌려주지만 캐시하지 않는다
        assertThat(repository.isIconColumnPresent()).isFalse();

        // 2차 호출: 재시도로 접속에 성공해 실제 판정값(true)을 얻고 그때부터 캐시한다
        assertThat(repository.isIconColumnPresent()).isTrue();

        // 3차 호출: 캐시된 값을 반환하므로 DataSource를 다시 건드리지 않는다
        assertThat(repository.isIconColumnPresent()).isTrue();
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    @DisplayName("판정이 성공해 '없음'을 반환해도 그 값을 캐시해 다시 판정하지 않는다")
    void successfulFalseAnswer_isCachedToo() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willReturn(statement);
        given(statement.executeQuery()).willReturn(resultSet);
        given(resultSet.next()).willReturn(true);
        given(resultSet.getInt(1)).willReturn(0);

        CmenumRepositoryImpl repository = new CmenumRepositoryImpl(null, null, dataSource);

        // 1차 호출: 컬럼이 없다는 판정 자체는 성공했으므로 false를 캐시한다
        assertThat(repository.isIconColumnPresent()).isFalse();

        // 2차 호출: 캐시된 false를 반환하므로 DataSource를 다시 건드리지 않는다
        assertThat(repository.isIconColumnPresent()).isFalse();
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    @DisplayName("판정 실패 후에는 냉각 시간 동안 재시도를 억제하고, 냉각 시간이 지나면 다시 판정한다")
    void failedProbe_suppressesRetryDuringCooldown_thenRetriesAfter()
            throws SQLException, InterruptedException {
        DataSource dataSource = mock(DataSource.class);
        Connection okConnection = mock(Connection.class);
        PreparedStatement okStatement = mock(PreparedStatement.class);
        ResultSet okResultSet = mock(ResultSet.class);
        given(okConnection.prepareStatement(anyString())).willReturn(okStatement);
        given(okStatement.executeQuery()).willReturn(okResultSet);
        given(okResultSet.next()).willReturn(true);
        given(okResultSet.getInt(1)).willReturn(1);

        given(dataSource.getConnection())
                .willThrow(new SQLException("일시적 접속 실패"))
                .willReturn(okConnection);

        CmenumRepositoryImpl repository = new CmenumRepositoryImpl(null, null, dataSource);
        long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(50);
        repository.setProbeFailureCooldownNanos(cooldownNanos);

        // 1차 호출: 접속 실패 → false, 이후 냉각 시간 동안 재시도를 억제한다
        assertThat(repository.isIconColumnPresent()).isFalse();
        verify(dataSource, times(1)).getConnection();

        // 2차 호출: 냉각 시간 내이므로 DataSource를 건드리지 않고 곧바로 false를 반환한다
        assertThat(repository.isIconColumnPresent()).isFalse();
        verify(dataSource, times(1)).getConnection();

        // 냉각 시간이 지날 때까지 대기(짧은 테스트 전용 냉각 시간이므로 60초 전체를 기다리지 않는다)
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(cooldownNanos) + 20);

        // 3차 호출: 냉각 시간이 지났으므로 다시 판정을 시도해 재시도로 성공한다
        assertThat(repository.isIconColumnPresent()).isTrue();
        verify(dataSource, times(2)).getConnection();
    }
}
