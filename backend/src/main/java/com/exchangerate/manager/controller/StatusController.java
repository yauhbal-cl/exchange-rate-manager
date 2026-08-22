package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.StatusApi;
import com.exchangerate.manager.api.model.ServiceStatus;
import com.exchangerate.manager.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController implements StatusApi {

    @Autowired
    private StatusService statusService;

    @Override
    public ResponseEntity<ServiceStatus> getStatus() {
        ServiceStatus status = statusService.getStatus();
        int httpStatus = status.getStatus() == ServiceStatus.StatusEnum.UP ? 200 : 503;
        return new ResponseEntity<>(status, HttpStatus.valueOf(httpStatus));
    }
}
