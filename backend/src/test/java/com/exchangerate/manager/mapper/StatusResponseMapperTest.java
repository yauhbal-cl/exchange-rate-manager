package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.ServiceStatus;
import com.exchangerate.manager.service.ServiceStatusResult;
import com.exchangerate.manager.service.ServiceStatusValue;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StatusResponseMapperTest {

    private final StatusResponseMapper mapper = Mappers.getMapper(StatusResponseMapper.class);

    @Test
    void mapsServiceOwnedResultToApiResponse() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-24T12:00:00+02:00");

        ServiceStatus response = mapper.toResponse(
                new ServiceStatusResult(ServiceStatusValue.DOWN, false, timestamp));

        assertThat(response.getStatus()).isEqualTo(ServiceStatus.StatusEnum.DOWN);
        assertThat(response.getDatabaseConnected()).isFalse();
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
    }
}
