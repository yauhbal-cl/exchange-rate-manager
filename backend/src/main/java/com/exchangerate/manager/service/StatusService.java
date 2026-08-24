package com.exchangerate.manager.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class StatusService {

    private final DataSource dataSource;

    public ServiceStatusResult getStatus() {
        boolean databaseConnected = isDatabaseConnected();
        ServiceStatusValue status = databaseConnected ? ServiceStatusValue.UP : ServiceStatusValue.DOWN;
        return new ServiceStatusResult(status, databaseConnected, OffsetDateTime.now());
    }

    private boolean isDatabaseConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
