package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.ServiceStatus;
import com.exchangerate.manager.service.ServiceStatusResult;

import org.mapstruct.Mapper;

/** Maps the application health result to the generated HTTP response model. */
@Mapper(componentModel = "spring")
public interface StatusResponseMapper {

    ServiceStatus toResponse(ServiceStatusResult result);
}
