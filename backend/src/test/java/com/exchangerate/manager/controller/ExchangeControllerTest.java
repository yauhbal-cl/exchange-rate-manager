package com.exchangerate.manager.controller;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.RateCollectionService;
import com.exchangerate.manager.service.RefreshResult;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the (not-yet-implemented) {@code ExchangeController}, written test-first per the
 * TDD workflow for this feature. {@code ExchangeController} — implementing the generated
 * {@code com.exchangerate.manager.api.ExchangeApi} interface — and the {@link RefreshResult}
 * -returning overload of {@link RateCollectionService#collect()} are expected to be created by
 * concurrent/later tasks (T020/T021/T021a). Until both land, this file will not compile — that is
 * the expected in-progress state for this phase.
 *
 * <p>Uses a standard {@code @WebMvcTest} slice (no datasource, no repository layer loaded) with
 * {@code @MockitoBean} to stub {@link RateCollectionService}, following the same MockMvc
 * conventions as the rest of this codebase's REST layer (see {@link StatusController}).
 *
 * <p>FR-009 ("the endpoint must not write to {@code currency_usage}") is not asserted as a runtime
 * row-count check here: a {@code @WebMvcTest} slice never loads the repository/datasource layer in
 * the first place, so there is nothing to count. The guarantee is structural instead — this
 * controller depends on {@link RateCollectionService} only; it has no
 * {@code CurrencyUsageRepository} dependency at all, so there is no code path by which it could
 * touch that table. That structural fact is what this test's mocking (a single collaborator,
 * {@code RateCollectionService}) demonstrates.
 */
@WebMvcTest(ExchangeController.class)
class ExchangeControllerTest {

    private static final String REFRESH_ENDPOINT = "/api/v1/exchange/refresh";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateCollectionService rateCollectionService;

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
