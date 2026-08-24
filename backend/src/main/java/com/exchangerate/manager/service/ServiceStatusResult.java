package com.exchangerate.manager.service;

import java.time.OffsetDateTime;

/** Result of checking the service and its database dependency. */
public record ServiceStatusResult(
        ServiceStatusValue status, boolean databaseConnected, OffsetDateTime timestamp) {}
