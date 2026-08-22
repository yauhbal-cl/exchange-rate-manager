package com.exchangerate.manager.service;

import com.exchangerate.manager.api.model.ServiceStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;

@Service
public class StatusService {

    @Autowired
    private DataSource dataSource;

    public ServiceStatus getStatus() {
        boolean databaseConnected = isDatabaseConnected();
        ServiceStatus.StatusEnum status = databaseConnected ? ServiceStatus.StatusEnum.UP : ServiceStatus.StatusEnum.DOWN;

        ServiceStatus result = new ServiceStatus();
        result.setStatus(status);
        result.setDatabaseConnected(databaseConnected);
        result.setTimestamp(OffsetDateTime.now());

        return result;
    }

    private boolean isDatabaseConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
