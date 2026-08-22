package com.exchangerate.manager.controller;

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
 * Full-HTTP integration test for {@code GET /api/v1/exchange} against the real docker-compose
 * PostgreSQL instance (see {@code ExchangeRateRepositoryTest} for the same convention: no
 * H2/Testcontainers, real DB, {@code @Transactional} rollback-per-test for isolation).
 *
 * <p>Scope is the happy path only (T014): an explicit past {@code date} query param, with both
 * currencies present in the store. Error paths (missing/unknown currency, no data for date, etc.)
 * are T017/US2's job, and usage-counter assertions are T019/T020's job — neither is covered here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ExchangeControllerIT {

    private static final String ENDPOINT = "/api/v1/exchange";

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

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
}
