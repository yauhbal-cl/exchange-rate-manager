package com.exchangerate.manager.controller;

import com.exchangerate.manager.api.model.TrendInsightResponse;
import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.exception.AiInsightUnavailableException;
import com.exchangerate.manager.exception.RateDataNotFoundException;
import com.exchangerate.manager.exception.TrendRangeTooLargeException;
import com.exchangerate.manager.mapper.ExchangeRateResponseMapper;
import com.exchangerate.manager.mapper.ExchangeRateTrendResponseMapper;
import com.exchangerate.manager.mapper.TrendInsightResponseMapper;
import com.exchangerate.manager.mapper.UsageAnalyticsMapper;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.service.ExchangeRateService;
import com.exchangerate.manager.service.RateCollectionService;
import com.exchangerate.manager.service.RefreshResult;
import com.exchangerate.manager.service.TrendInsightResult;
import com.exchangerate.manager.service.TrendInsightService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the {@code /exchange/refresh} endpoint of {@code ExchangeController} — which also
 * implements the generated {@code com.exchangerate.manager.api.ExchangeApi} interface for the
 * {@code /exchange} and {@code /exchange/usage} endpoints (see {@code ExchangeControllerIT} for
 * those). Written test-first per the TDD workflow for this feature.
 *
 * <p>Uses a standard {@code @WebMvcTest} slice (no datasource, no repository layer loaded) with
 * {@code @MockitoBean} to stub every constructor collaborator of {@code ExchangeController}
 * ({@link RateCollectionService}, {@link ExchangeRateService}, {@link ExchangeRateResponseMapper},
 * {@link CurrencyUsageRepository}, {@link UsageAnalyticsMapper}) — only {@code
 * rateCollectionService} is actually exercised by the tests below — following the same MockMvc
 * conventions as the rest of this codebase's REST layer (see {@link StatusController}).
 */
@WebMvcTest(ExchangeController.class)
class ExchangeControllerTest {

    private static final String REFRESH_ENDPOINT = "/api/v1/exchange/refresh";
    private static final String EXCHANGE_ENDPOINT = "/api/v1/exchange";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateCollectionService rateCollectionService;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private ExchangeRateResponseMapper exchangeRateResponseMapper;

    @MockitoBean
    private CurrencyUsageRepository currencyUsageRepository;

    @MockitoBean
    private UsageAnalyticsMapper usageAnalyticsMapper;

    @MockitoBean
    private ExchangeRateTrendResponseMapper exchangeRateTrendResponseMapper;

    @MockitoBean
    private TrendInsightService trendInsightService;

    @MockitoBean
    private TrendInsightResponseMapper trendInsightResponseMapper;

    @Test
    void refreshReturns200WithResultOnSuccess() throws Exception {
        RefreshResult result = new RefreshResult(5, LocalDate.of(2026, 8, 22));
        when(rateCollectionService.collect()).thenReturn(result);

        mockMvc.perform(post(REFRESH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currenciesCollected").value(5))
                .andExpect(jsonPath("$.rateDate").value("2026-08-22"));
    }

    @Test
    void refreshReturns502OnProviderFailure() throws Exception {
        when(rateCollectionService.collect()).thenThrow(new FixerApiException("simulated failure"));

        mockMvc.perform(post(REFRESH_ENDPOINT))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getExchangeRateTrendInsightReturns200WithNarrative() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 22);
        TrendInsightResult result = new TrendInsightResult(
                "EUR", "USD", startDate, endDate, "The EUR/USD rate held broadly steady over the period.");
        TrendInsightResponse response = new TrendInsightResponse(
                "EUR", "USD", startDate, endDate, "The EUR/USD rate held broadly steady over the period.");

        when(trendInsightService.generateInsight(anyString(), anyString(), any(), any())).thenReturn(result);
        when(trendInsightResponseMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/exchange/trend/insight")
                        .param("from", "EUR")
                        .param("to", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("EUR"))
                .andExpect(jsonPath("$.toCurrency").value("USD"))
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.narrative").value("The EUR/USD rate held broadly steady over the period."));
    }

    @Test
    void getExchangeRateTrendInsightReturns503WhenAiUnavailable() throws Exception {
        when(trendInsightService.generateInsight(anyString(), anyString(), any(), any()))
                .thenThrow(new AiInsightUnavailableException("AI insight generation is currently unavailable"));

        mockMvc.perform(get("/api/v1/exchange/trend/insight")
                        .param("from", "EUR")
                        .param("to", "USD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("AI insight generation is currently unavailable"));
    }

    @Test
    void getExchangeRateTrendInsightReturns404WhenNoDataFound() throws Exception {
        when(trendInsightService.generateInsight(anyString(), anyString(), any(), any()))
                .thenThrow(new RateDataNotFoundException(
                        "No rate data found for currencies 'EUR' and 'USD' between 2099-01-01 and 2099-01-31"));

        mockMvc.perform(get("/api/v1/exchange/trend/insight")
                        .param("from", "EUR")
                        .param("to", "USD"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "No rate data found for currencies 'EUR' and 'USD' between 2099-01-01 and 2099-01-31"));
    }

    @Test
    void getExchangeRateTrendInsightReturns400WhenRangeTooLarge() throws Exception {
        when(trendInsightService.generateInsight(anyString(), anyString(), any(), any()))
                .thenThrow(new TrendRangeTooLargeException(
                        "Requested range 2024-01-01 to 2026-06-01 spans 883 days, which exceeds the maximum of 365"
                                + " daily points supported for AI trend insight generation"));

        mockMvc.perform(get("/api/v1/exchange/trend/insight")
                        .param("from", "EUR")
                        .param("to", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Requested range 2024-01-01 to 2026-06-01 spans 883 days, which exceeds the maximum of 365"
                                + " daily points supported for AI trend insight generation"));
    }

    @Test
    void getExchangeRateReturns400WithProblemDetailWhenFromMissing() throws Exception {
        mockMvc.perform(get(EXCHANGE_ENDPOINT).param("to", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("from")));
    }

    @Test
    void getExchangeRateReturns400WithProblemDetailWhenToMissing() throws Exception {
        mockMvc.perform(get(EXCHANGE_ENDPOINT).param("from", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("to")));
    }

    /**
     * FR: when a scheduled run already holds the ShedLock lock, a concurrent manual refresh should
     * receive 409 Conflict. Genuine ShedLock contention requires two competing transactions racing
     * against the same lock row in the real database-backed lock provider — that is a concurrency
     * scenario, not something a single-threaded {@code @WebMvcTest} slice (which mocks
     * {@code RateCollectionService} entirely and never touches ShedLock's JDBC lock table) can
     * exercise meaningfully. Simulating it here would only test that the controller maps some
     * chosen sentinel/exception to 409 — not that real lock contention is detected — so it is left
     * to a dedicated integration/concurrency test instead of being faked at this layer.
     */
    @Disabled("True ShedLock contention can't be simulated in a @WebMvcTest slice; belongs in an integration/concurrency test.")
    @Test
    void refreshReturns409WhenScheduledRunHoldsLock() {
        // Intentionally left unimplemented — see class-level Javadoc and @Disabled reason above.
    }
}
