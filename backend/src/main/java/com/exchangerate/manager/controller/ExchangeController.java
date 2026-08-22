package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.ExchangeApi;
import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.RateCollectionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ExchangeController implements ExchangeApi {

    private final RateCollectionService rateCollectionService;

    public ExchangeController(RateCollectionService rateCollectionService) {
        this.rateCollectionService = rateCollectionService;
    }

    @Override
    public ResponseEntity<com.exchangerate.manager.api.model.RefreshResult> refreshExchangeRates() {
        com.exchangerate.manager.service.RefreshResult result;
        try {
            result = rateCollectionService.collect();
        } catch (FixerApiException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }

        if (result == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A collection run is already in progress; try again shortly.");
        }

        com.exchangerate.manager.api.model.RefreshResult body = new com.exchangerate.manager.api.model.RefreshResult(
                result.getCurrenciesCollected(), result.getRateDate());
        return ResponseEntity.ok(body);
    }
}
