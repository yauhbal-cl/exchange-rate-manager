package com.exchangerate.manager.controller;

import com.exchangerate.manager.AbstractIntegrationTest;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-HTTP integration test for {@code GET /api/v1/exchange} against a Testcontainers-managed
 * PostgreSQL instance (see {@link AbstractIntegrationTest}), with {@code @Transactional}
 * rollback-per-test for isolation.
 *
 * <p>Covers the US1 happy path (explicit past {@code date}, both currencies present) and US2's
 * three rejected-lookup cases (unknown currency, same currency on both sides, no data for date).
 * Usage-counter assertions are T019/T020's job and are not covered here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ExchangeControllerIT extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/exchange";
    private static final String USAGE_ENDPOINT = "/api/v1/exchange/usage";

    // Neither EUR nor GBP appear in SpreadLookup's explicit tiers, so both fall to the DEFAULT
    // spread (2.75). Keep the math context identical to ExchangeRateService's so the
    // hand-computed expectation matches bit-for-bit under HALF_UP rounding.
    private static final MathContext RATE_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("2.75");

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 1);
    private static final String FROM_CURRENCY = "EUR";
    private static final String TO_CURRENCY = "GBP";
    private static final BigDecimal FROM_RATE_TO_USD = new BigDecimal("1.080000");
    private static final BigDecimal TO_RATE_TO_USD = new BigDecimal("0.860000");

    // Obviously-fake 3-letter code, never seeded into exchange_rates by any test in this class.
    private static final String UNKNOWN_CURRENCY = "ZZZ";

    // A date with no rate data seeded for it, used to exercise the "no rate data for date" path.
    private static final LocalDate NO_DATA_DATE = LocalDate.of(1999, 1, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @Test
    void getExchangeRateReturnsSpreadAdjustedRateForExplicitPastDate() throws Exception {
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);

        BigDecimal rateRatio = TO_RATE_TO_USD.divide(FROM_RATE_TO_USD, RATE_MATH_CONTEXT);
        BigDecimal spreadFactor = BigDecimal.valueOf(100)
                .subtract(DEFAULT_SPREAD)
                .divide(BigDecimal.valueOf(100), RATE_MATH_CONTEXT);
        BigDecimal expectedRate = rateRatio.multiply(spreadFactor, RATE_MATH_CONTEXT);

        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value(FROM_CURRENCY))
                .andExpect(jsonPath("$.toCurrency").value(TO_CURRENCY))
                .andExpect(jsonPath("$.rateDate").value(RATE_DATE.toString()))
                .andExpect(jsonPath("$.rate").exists())
                .andReturn();

        String rateAsText = com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.rate")
                .toString();

        assertThat(new BigDecimal(rateAsText)).isEqualByComparingTo(expectedRate);
    }

    @Test
    void getExchangeRateReturns400ForUnknownCurrency() throws Exception {
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", UNKNOWN_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(UNKNOWN_CURRENCY)));
    }

    @Test
    void getExchangeRateReturns400ForSameCurrencyOnBothSides() throws Exception {
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", FROM_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(FROM_CURRENCY)));
    }

    @Test
    void getExchangeRateReturns404WhenNoRateDataForDate() throws Exception {
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", NO_DATA_DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(NO_DATA_DATE.toString())));
    }

    @Test
    void getExchangeRateRejectedLookupDoesNotIncrementUsageCounters() throws Exception {
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);

        assertThat(currencyUsageRepository.findByCurrencyCode(FROM_CURRENCY)).isEmpty();

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", FROM_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isBadRequest());

        assertThat(currencyUsageRepository.findByCurrencyCode(FROM_CURRENCY)).isEmpty();
    }

    @Test
    void getUsageAnalyticsReflectsMixedQueriedAndNeverQueriedCurrencies() throws Exception {
        // Real dev DB may carry rows from other tests/manual runs; establish a clean, deterministic
        // baseline within this test's transaction (rolled back afterwards, per the class convention).
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        // Three currencies with rate data seeded; only EUR and GBP get looked up (successfully),
        // JPY has rate data but is never queried.
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert("JPY", new BigDecimal("150.000000"), RATE_DATE);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].queryCount")
                        .value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].lastQueriedAt")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == '" + TO_CURRENCY + "')].queryCount")
                        .value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == '" + TO_CURRENCY + "')].lastQueriedAt")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == 'JPY')].queryCount")
                        .value(org.hamcrest.Matchers.contains(0)));
    }

    @Test
    void getUsageAnalyticsIncludesNeverQueriedCurrencyWithZeroCountAndNullTimestamp() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);

        assertThat(currencyUsageRepository.findByCurrencyCode(FROM_CURRENCY)).isEmpty();

        mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value(FROM_CURRENCY))
                .andExpect(jsonPath("$.currencies[0].queryCount").value(0))
                .andExpect(jsonPath("$.currencies[0].lastQueriedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getUsageAnalyticsReturnsEmptyResultWhenNoExchangeRatesExist() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        assertThat(exchangeRateRepository.findAll()).isEmpty();

        mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies").isArray())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(0)));
    }
}
