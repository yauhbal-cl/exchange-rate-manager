package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.StatusApi;
import com.exchangerate.manager.api.model.ServiceStatus;
import com.exchangerate.manager.mapper.StatusResponseMapper;
import com.exchangerate.manager.service.ServiceStatusResult;
import com.exchangerate.manager.service.ServiceStatusValue;
import com.exchangerate.manager.service.StatusService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatusController implements StatusApi {

    private final StatusService statusService;
    private final StatusResponseMapper statusResponseMapper;

    @Override
    public ResponseEntity<ServiceStatus> getStatus() {
        ServiceStatusResult result = statusService.getStatus();
        ServiceStatus response = statusResponseMapper.toResponse(result);
        HttpStatus httpStatus = result.status() == ServiceStatusValue.UP
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(response, httpStatus);
    }
}
