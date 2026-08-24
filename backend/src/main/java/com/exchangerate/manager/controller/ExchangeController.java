package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.ExchangeApi;
import com.exchangerate.manager.api.model.ExchangeRateResponse;
import com.exchangerate.manager.api.model.ExchangeRateTrendResponse;
import com.exchangerate.manager.api.model.TrendInsightResponse;
import com.exchangerate.manager.api.model.UsageAnalyticsResponse;
import com.exchangerate.manager.mapper.ExchangeRateResponseMapper;
import com.exchangerate.manager.mapper.ExchangeRateTrendResponseMapper;
import com.exchangerate.manager.mapper.TrendInsightResponseMapper;
import com.exchangerate.manager.mapper.UsageAnalyticsMapper;
import com.exchangerate.manager.service.CollectionInProgressException;
import com.exchangerate.manager.service.CurrencyUsageSummary;
import com.exchangerate.manager.service.ExchangeRateLookupResult;
import com.exchangerate.manager.service.ExchangeRateService;
import com.exchangerate.manager.service.RateCollectionService;
import com.exchangerate.manager.service.RateTrendPoint;
import com.exchangerate.manager.service.TrendInsightResult;
import com.exchangerate.manager.service.TrendInsightService;
import com.exchangerate.manager.service.UsageAnalyticsService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class ExchangeController implements ExchangeApi {

    private final RateCollectionService rateCollectionService;
    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateResponseMapper exchangeRateResponseMapper;
    private final UsageAnalyticsService usageAnalyticsService;
    private final UsageAnalyticsMapper usageAnalyticsMapper;
    private final ExchangeRateTrendResponseMapper exchangeRateTrendResponseMapper;
    private final TrendInsightService trendInsightService;
    private final TrendInsightResponseMapper trendInsightResponseMapper;

    @Override
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(String from, String to, LocalDate date) {
        ExchangeRateLookupResult result = exchangeRateService.lookup(from, to, date);
        ExchangeRateResponse body = exchangeRateResponseMapper.toResponse(result);
        return ResponseEntity.ok(body);
    }

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

    @Override
    public ResponseEntity<ExchangeRateTrendResponse> getExchangeRateTrend(
            String from, String to, LocalDate startDate, LocalDate endDate) {
        List<RateTrendPoint> points = exchangeRateService.getTrend(from, to, startDate, endDate);
        ExchangeRateTrendResponse body = exchangeRateTrendResponseMapper.toResponse(from, to, points);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<TrendInsightResponse> getExchangeRateTrendInsight(
            String from, String to, LocalDate startDate, LocalDate endDate) {
        TrendInsightResult result = trendInsightService.generateInsight(from, to, startDate, endDate);
        TrendInsightResponse body = trendInsightResponseMapper.toResponse(result);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<UsageAnalyticsResponse> getUsageAnalytics(Integer limit, Integer recentDays) {
        List<CurrencyUsageSummary> summaries = usageAnalyticsService.getUsageAnalytics(limit, recentDays);
        UsageAnalyticsResponse body = usageAnalyticsMapper.toResponse(summaries);
        return ResponseEntity.ok(body);
    }
}
