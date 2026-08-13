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
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CmenumRepositoryImpl#isIconColumnPresent}의 캐시 정책을 검증한다.
 *
 * <p>판정 실패(DB 접속 예외 등)는 캐시하지 않고 다음 호출에서 재시도해야 한다 — 실패를 캐시하면 기동 직후의 일시적 DB 장애 한 번이 프로세스 수명 내내 폴백 모드에
 * 고정되기 때문이다. 실제 Oracle 없이 {@link DataSource}를 모킹해 이 계약만 좁게 확인한다.
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

        // 1차 호출: 접속 실패 → false를 돌려주지만 캐시하지 않는다
        assertThat(repository.isIconColumnPresent()).isFalse();

        // 2차 호출: 재시도로 접속에 성공해 실제 판정값(true)을 얻고 그때부터 캐시한다
        assertThat(repository.isIconColumnPresent()).isTrue();

        // 3차 호출: 캐시된 값을 반환하므로 DataSource를 다시 건드리지 않는다
        assertThat(repository.isIconColumnPresent()).isTrue();
        verify(dataSource, times(2)).getConnection();
    }
}
