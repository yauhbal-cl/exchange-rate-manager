package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.model.ServiceStatus;
import com.exchangerate.manager.mapper.StatusResponseMapper;
import com.exchangerate.manager.service.ServiceStatusResult;
import com.exchangerate.manager.service.ServiceStatusValue;
import com.exchangerate.manager.service.StatusService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    private static final String STATUS_ENDPOINT = "/api/v1/status";
    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.parse("2026-08-24T12:00:00+02:00");
    private static final String SERIALIZED_TIMESTAMP = "2026-08-24T12:00:00+02:00";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private StatusResponseMapper statusResponseMapper;

    @Test
    void returns200AndMappedBodyWhenHealthy() throws Exception {
        ServiceStatusResult result =
                new ServiceStatusResult(ServiceStatusValue.UP, true, TIMESTAMP);
        when(statusService.getStatus()).thenReturn(result);
        when(statusResponseMapper.toResponse(result)).thenReturn(
                new ServiceStatus(ServiceStatus.StatusEnum.UP, true, TIMESTAMP));

        mockMvc.perform(get(STATUS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.databaseConnected").value(true))
                .andExpect(jsonPath("$.timestamp").value(SERIALIZED_TIMESTAMP));
    }

    @Test
    void returns503AndMappedBodyWhenUnhealthy() throws Exception {
        ServiceStatusResult result =
                new ServiceStatusResult(ServiceStatusValue.DOWN, false, TIMESTAMP);
        when(statusService.getStatus()).thenReturn(result);
        when(statusResponseMapper.toResponse(result)).thenReturn(
                new ServiceStatus(ServiceStatus.StatusEnum.DOWN, false, TIMESTAMP));

        mockMvc.perform(get(STATUS_ENDPOINT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.databaseConnected").value(false))
                .andExpect(jsonPath("$.timestamp").value(SERIALIZED_TIMESTAMP));
    }
}
