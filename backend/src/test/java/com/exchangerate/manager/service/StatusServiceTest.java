package com.exchangerate.manager.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusServiceTest {

    @Test
    void reportsUpWhenDatabaseConnectionIsValid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        StatusService service = new StatusService(dataSource);
        OffsetDateTime before = OffsetDateTime.now();

        ServiceStatusResult result = service.getStatus();

        assertThat(result.status()).isEqualTo(ServiceStatusValue.UP);
        assertThat(result.databaseConnected()).isTrue();
        assertThat(result.timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void reportsDownWhenDatabaseCannotBeReached() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));
        StatusService service = new StatusService(dataSource);

        ServiceStatusResult result = service.getStatus();

        assertThat(result.status()).isEqualTo(ServiceStatusValue.DOWN);
        assertThat(result.databaseConnected()).isFalse();
        assertThat(result.timestamp()).isNotNull();
    }
}
