package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.ExchangeApi;
import com.exchangerate.manager.service.CollectionInProgressException;
import com.exchangerate.manager.service.RateCollectionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExchangeController implements ExchangeApi {

    private final RateCollectionService rateCollectionService;

    @Override
    public ResponseEntity<com.exchangerate.manager.api.model.RefreshResult> refreshExchangeRates() {
        com.exchangerate.manager.service.RefreshResult result = rateCollectionService.collect();

        if (result == null) {
            throw new CollectionInProgressException(
                    "A collection run is already in progress; try again shortly.");
        }

        com.exchangerate.manager.api.model.RefreshResult body = new com.exchangerate.manager.api.model.RefreshResult(
                result.getCurrenciesCollected(), result.getRateDate());
        return ResponseEntity.ok(body);
    }
}
