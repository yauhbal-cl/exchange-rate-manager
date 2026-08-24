package com.exchangerate.manager.controller;

import com.exchangerate.manager.AbstractIntegrationTest;
import com.exchangerate.manager.repository.CurrencyQueryEventRepository;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
    private static final String TREND_ENDPOINT = "/api/v1/exchange/trend";

    // GBP never appears in SpreadLookup's explicit tiers, so it falls to the DEFAULT spread
    // (2.75). EUR is explicitly configured at 0.00 (see application.yml's exchange-rates.spreads
    // — EUR is Fixer.io's base currency), but since Section 6.1's formula applies
    // MAX(toSpread, fromSpread), GBP's 2.75 dominates the EUR/GBP pair below regardless of EUR's
    // own value. Keep the math context identical to ExchangeRateService's so the hand-computed
    // expectation matches bit-for-bit under HALF_UP rounding.
    private static final MathContext RATE_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("2.75");

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 1);
    private static final String FROM_CURRENCY = "EUR";
    private static final String TO_CURRENCY = "GBP";
    private static final BigDecimal FROM_RATE_TO_USD = new BigDecimal("1.080000");
    private static final BigDecimal TO_RATE_TO_USD = new BigDecimal("0.860000");

    // Shared counterpart for the EUR-zero-spread / USD-non-zero-spread tests below, matching the
    // spec's own worked examples (specs/008-eur-base-spread-correction/spec.md, Acceptance
    // Scenarios 1-2 under US1). PLN isn't in any SpreadLookup tier, so it always falls to the
    // 2.75 DEFAULT_SPREAD, same as GBP above; distinct from GBP purely so failures are easy to
    // tell apart by currency code in test output.
    private static final String DEFAULT_TIER_COUNTERPART_CURRENCY = "PLN";
    private static final BigDecimal DEFAULT_TIER_COUNTERPART_RATE_TO_USD = new BigDecimal("0.230000");

    // Obviously-fake 3-letter code, never seeded into exchange_rates by any test in this class.
    private static final String UNKNOWN_CURRENCY = "ZZZ";

    // A date with no rate data seeded for it, used to exercise the "no rate data for date" path.
    private static final LocalDate NO_DATA_DATE = LocalDate.of(1999, 1, 1);

    // Currency pair dedicated to the /trend tests, distinct from FROM_CURRENCY/TO_CURRENCY above
    // to avoid collisions with the /exchange tests in this class. Neither is in SpreadLookup's
    // explicit tiers, so both fall to DEFAULT_SPREAD, same as FROM_CURRENCY/TO_CURRENCY.
    // CHF/AUD are reserved for ExchangeRateServiceConcurrencyIT's non-transactional, real-commit
    // concurrency test — using them here would collide with its committed currency_usage rows.
    private static final String TREND_FROM_CURRENCY = "NZD";
    private static final String TREND_TO_CURRENCY = "SEK";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @Autowired
    private CurrencyQueryEventRepository currencyQueryEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static BigDecimal computeExpectedRate(BigDecimal fromRateToUsd, BigDecimal toRateToUsd) {
        BigDecimal rateRatio = toRateToUsd.divide(fromRateToUsd, RATE_MATH_CONTEXT);
        BigDecimal spreadFactor = BigDecimal.valueOf(100)
                .subtract(DEFAULT_SPREAD)
                .divide(BigDecimal.valueOf(100), RATE_MATH_CONTEXT);
        return rateRatio.multiply(spreadFactor, RATE_MATH_CONTEXT);
    }

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

    // The two tests below map directly to spec.md's US1 Acceptance Scenarios 1 and 2: EUR must
    // never contribute a non-zero spread (it's Fixer.io's base currency), and USD must never be
    // mistaken for that base currency just because it's used for internal rate normalization —
    // USD gets the same 2.75 DEFAULT_SPREAD as any other unlisted currency.
    //
    // Caveat: because the formula is MAX(toSpread, fromSpread) (Section 6.1) and every non-EUR
    // currency's spread (2.75 default, or 3.25/4.50/6.00 group tiers) is >= EUR's 0.00, the
    // *numeric* rate produced here would be identical even under the old bug that hardcoded USD
    // (not EUR) as the zero-spread currency — the DEFAULT_TIER_COUNTERPART_CURRENCY leg always
    // dominates the max() either way. These tests document and lock in the currently-correct,
    // spec-mandated per-currency spread assignment; the regression-proof unit coverage that
    // actually distinguishes "EUR resolves to 0.00" from "USD resolves to 0.00" lives in
    // SpreadLookupTest, which asserts spreadFor(...) directly without going through max().
    @Test
    void getExchangeRateAppliesZeroSpreadForEurLegAgainstDefaultTierCurrency() throws Exception {
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(
                DEFAULT_TIER_COUNTERPART_CURRENCY, DEFAULT_TIER_COUNTERPART_RATE_TO_USD, RATE_DATE);

        BigDecimal expectedRate = computeExpectedRate(FROM_RATE_TO_USD, DEFAULT_TIER_COUNTERPART_RATE_TO_USD);

        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", DEFAULT_TIER_COUNTERPART_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value(FROM_CURRENCY))
                .andExpect(jsonPath("$.toCurrency").value(DEFAULT_TIER_COUNTERPART_CURRENCY))
                .andReturn();

        BigDecimal actualRate = new BigDecimal(com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.rate")
                .toString());

        assertThat(actualRate).isEqualByComparingTo(expectedRate);
    }

    @Test
    void getExchangeRateAppliesUsdDefaultSpreadNotZeroWhenEurIsNotInvolved() throws Exception {
        String usdCurrency = "USD";
        BigDecimal usdRateToUsd = new BigDecimal("1.000000");

        exchangeRateRepository.upsert(usdCurrency, usdRateToUsd, RATE_DATE);
        exchangeRateRepository.upsert(
                DEFAULT_TIER_COUNTERPART_CURRENCY, DEFAULT_TIER_COUNTERPART_RATE_TO_USD, RATE_DATE);

        BigDecimal expectedRate = computeExpectedRate(usdRateToUsd, DEFAULT_TIER_COUNTERPART_RATE_TO_USD);

        MvcResult result = mockMvc.perform(get(ENDPOINT)
                        .param("from", usdCurrency)
                        .param("to", DEFAULT_TIER_COUNTERPART_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value(usdCurrency))
                .andExpect(jsonPath("$.toCurrency").value(DEFAULT_TIER_COUNTERPART_CURRENCY))
                .andReturn();

        BigDecimal actualRate = new BigDecimal(com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.rate")
                .toString());

        // Must equal the DEFAULT_SPREAD (2.75) adjusted rate — USD's own configured spread —
        // not an unadjusted (0%-spread) rate, which is what the old USD-hardcoded-to-zero bug
        // would have wrongly produced whenever USD's spread happened to be the max() winner.
        assertThat(actualRate).isEqualByComparingTo(expectedRate);
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
    void rejectedLookupsAndManualRefreshDoNotAddQueryEvents() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);

        long before = currencyQueryEventRepository.count();

        // (a) same-currency rejection — mirrors getExchangeRateReturns400ForSameCurrencyOnBothSides.
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", FROM_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isBadRequest());

        // (b) unknown-currency rejection — mirrors getExchangeRateReturns400ForUnknownCurrency.
        mockMvc.perform(get(ENDPOINT)
                        .param("from", UNKNOWN_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isBadRequest());

        // (c) no rate data for the requested date — mirrors getExchangeRateReturns404WhenNoRateDataForDate.
        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", NO_DATA_DATE.toString()))
                .andExpect(status().isNotFound());

        // (d) manual refresh (POST /api/v1/exchange/refresh) deliberately not exercised here: it
        // calls out to the real FixerClient/Fixer.io, which isn't mocked in this test class, so
        // invoking it would make this test's outcome depend on network access/an external
        // provider's availability. Sub-cases (a)-(c) above already cover every rejected-lookup
        // path that's reachable without an external call.
        assertThat(currencyQueryEventRepository.count()).isEqualTo(before);
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
    void getUsageAnalyticsIncludesNonNullQueryTimestampsArrayForQueriedCurrency() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM_CURRENCY)
                        .param("to", TO_CURRENCY)
                        .param("date", RATE_DATE.toString()))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].queryTimestamps")
                        .exists())
                .andReturn();

        // Filter-path reads return one list entry per matching currency, and each entry here is
        // itself the (array-valued) queryTimestamps field, i.e. a List<List<String>> — see
        // JsonPath.parse(...).read(...) usage elsewhere in this file for the flat/scalar-field
        // equivalent of this pattern.
        List<List<String>> matchedQueryTimestamps = com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].queryTimestamps");

        assertThat(matchedQueryTimestamps).hasSize(1);
        assertThat(matchedQueryTimestamps.get(0))
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(1)
                .allSatisfy(timestamp -> assertThat(timestamp).isNotNull());
    }

    @Test
    void getUsageAnalyticsNeverQueriedCurrencyHasEmptyQueryTimestampsArray() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);

        assertThat(currencyUsageRepository.findByCurrencyCode(FROM_CURRENCY)).isEmpty();

        mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value(FROM_CURRENCY))
                .andExpect(jsonPath("$.currencies[0].queryTimestamps").exists())
                .andExpect(jsonPath("$.currencies[0].queryTimestamps", org.hamcrest.Matchers.hasSize(0)));
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

    @Test
    void getExchangeRateTrendReturnsSpreadAdjustedSeriesForExplicitRange() throws Exception {
        LocalDate day1 = RATE_DATE;
        LocalDate day2 = RATE_DATE.plusDays(1);
        BigDecimal fromRateDay1 = new BigDecimal("1.000000");
        BigDecimal toRateDay1 = new BigDecimal("1.500000");
        BigDecimal fromRateDay2 = new BigDecimal("1.100000");
        BigDecimal toRateDay2 = new BigDecimal("1.600000");

        exchangeRateRepository.upsert(TREND_FROM_CURRENCY, fromRateDay1, day1);
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, toRateDay1, day1);
        exchangeRateRepository.upsert(TREND_FROM_CURRENCY, fromRateDay2, day2);
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, toRateDay2, day2);

        BigDecimal expectedRateDay1 = computeExpectedRate(fromRateDay1, toRateDay1);
        BigDecimal expectedRateDay2 = computeExpectedRate(fromRateDay2, toRateDay2);

        MvcResult result = mockMvc.perform(get(TREND_ENDPOINT)
                        .param("from", TREND_FROM_CURRENCY)
                        .param("to", TREND_TO_CURRENCY)
                        .param("startDate", day1.toString())
                        .param("endDate", day2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value(TREND_FROM_CURRENCY))
                .andExpect(jsonPath("$.toCurrency").value(TREND_TO_CURRENCY))
                .andExpect(jsonPath("$.points", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.points[0].rateDate").value(day1.toString()))
                .andExpect(jsonPath("$.points[1].rateDate").value(day2.toString()))
                .andReturn();

        var parsed = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        assertThat(new BigDecimal(parsed.read("$.points[0].rate").toString()))
                .isEqualByComparingTo(expectedRateDay1);
        assertThat(new BigDecimal(parsed.read("$.points[1].rate").toString()))
                .isEqualByComparingTo(expectedRateDay2);
    }

    @Test
    void getExchangeRateTrendReturnsEmptyArrayWhenNoDataInRange() throws Exception {
        exchangeRateRepository.upsert(TREND_FROM_CURRENCY, new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, new BigDecimal("1.500000"), RATE_DATE);

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("from", TREND_FROM_CURRENCY)
                        .param("to", TREND_TO_CURRENCY)
                        .param("startDate", NO_DATA_DATE.toString())
                        .param("endDate", NO_DATA_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getExchangeRateTrendReturns400ForUnknownCurrency() throws Exception {
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, new BigDecimal("1.500000"), RATE_DATE);

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("from", UNKNOWN_CURRENCY)
                        .param("to", TREND_TO_CURRENCY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(UNKNOWN_CURRENCY)));
    }

    @Test
    void getExchangeRateTrendReturns400WhenStartDateAfterEndDate() throws Exception {
        exchangeRateRepository.upsert(TREND_FROM_CURRENCY, new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, new BigDecimal("1.500000"), RATE_DATE);

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("from", TREND_FROM_CURRENCY)
                        .param("to", TREND_TO_CURRENCY)
                        .param("startDate", RATE_DATE.toString())
                        .param("endDate", RATE_DATE.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getExchangeRateTrendDoesNotIncrementUsageCounters() throws Exception {
        exchangeRateRepository.upsert(TREND_FROM_CURRENCY, new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert(TREND_TO_CURRENCY, new BigDecimal("1.500000"), RATE_DATE);

        assertThat(currencyUsageRepository.findByCurrencyCode(TREND_FROM_CURRENCY)).isEmpty();
        assertThat(currencyUsageRepository.findByCurrencyCode(TREND_TO_CURRENCY)).isEmpty();

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("from", TREND_FROM_CURRENCY)
                        .param("to", TREND_TO_CURRENCY)
                        .param("startDate", RATE_DATE.toString())
                        .param("endDate", RATE_DATE.toString()))
                .andExpect(status().isOk());

        assertThat(currencyUsageRepository.findByCurrencyCode(TREND_FROM_CURRENCY)).isEmpty();
        assertThat(currencyUsageRepository.findByCurrencyCode(TREND_TO_CURRENCY)).isEmpty();
    }

    @Test
    void getUsageAnalyticsWithLimitReturnsTopRankedCurrenciesInOrder() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert("AAA", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("BBB", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("CCC", new BigDecimal("1.000000"), RATE_DATE);

        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "AAA", 5, Timestamp.from(Instant.now()));
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "BBB", 10, Timestamp.from(Instant.now()));

        mockMvc.perform(get(USAGE_ENDPOINT).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value("BBB"))
                .andExpect(jsonPath("$.currencies[1].currencyCode").value("AAA"));
    }

    @Test
    void getUsageAnalyticsOmittedLimitReturnsAllCurrenciesSameOrdering() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert("AAA", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("BBB", new BigDecimal("1.000000"), RATE_DATE);

        mockMvc.perform(get(USAGE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void getUsageAnalyticsReturns400ForNonPositiveLimit() throws Exception {
        mockMvc.perform(get(USAGE_ENDPOINT).param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUsageAnalyticsWithRecentDaysExcludesStaleAndNeverQueriedCurrencies() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert("AAA", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("BBB", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("CCC", new BigDecimal("1.000000"), RATE_DATE);

        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "AAA", 1, Timestamp.from(Instant.now()));
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "BBB", 1, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get(USAGE_ENDPOINT).param("recentDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value("AAA"));
    }

    @Test
    void getUsageAnalyticsReturns400ForNonPositiveRecentDays() throws Exception {
        mockMvc.perform(get(USAGE_ENDPOINT).param("recentDays", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUsageAnalyticsCombinesLimitAndRecentDays() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert("AAA", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("BBB", new BigDecimal("1.000000"), RATE_DATE);
        exchangeRateRepository.upsert("CCC", new BigDecimal("1.000000"), RATE_DATE);

        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "AAA", 3, Timestamp.from(Instant.now()));
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                "BBB", 5, Timestamp.from(Instant.now()));

        mockMvc.perform(get(USAGE_ENDPOINT).param("limit", "1").param("recentDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value("BBB"));
    }

    // NOT-yet-implemented behavior: UsageAnalyticsService currently hardcodes
    // DEFAULT_HISTORY_WINDOW_DAYS (90) for the queryTimestamps history window regardless of the
    // recentDays query parameter, instead of trimming that window to recentDays. queryCount itself
    // must stay a lifetime count either way (it comes straight from currency_usage.query_count via
    // findCurrencyUsage, untouched by any history-window trimming). This test is expected to FAIL
    // against today's implementation: with recentDays=7, all 5 seeded events are still within the
    // hardcoded 90-day window, so all 5 timestamps come back instead of the 2 that fall inside the
    // last 7 days.
    @Test
    void getUsageAnalyticsWithRecentDaysTrimsQueryTimestampsWithoutChangingQueryCount() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                FROM_CURRENCY, 5, Timestamp.from(Instant.now()));

        // 2 events inside the requested 7-day recentDays window.
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                FROM_CURRENCY, Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                FROM_CURRENCY, Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)));

        // 3 events older than 7 days but still within the (hardcoded) 90-day default window.
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                FROM_CURRENCY, Timestamp.from(Instant.now().minus(20, ChronoUnit.DAYS)));
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                FROM_CURRENCY, Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)));
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                FROM_CURRENCY, Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)));

        MvcResult result = mockMvc.perform(get(USAGE_ENDPOINT).param("recentDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value(FROM_CURRENCY))
                .andExpect(jsonPath("$.currencies[0].queryCount").value(5))
                .andReturn();

        List<List<String>> matchedQueryTimestamps = com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].queryTimestamps");

        assertThat(matchedQueryTimestamps).hasSize(1);
        assertThat(matchedQueryTimestamps.get(0)).hasSize(2);
    }

    // SC-010: no cap on returned history size — a recentDays wider than the seeded data (and
    // wider than the current 90-day hardcoded default) must return every seeded event, none
    // dropped/truncated. Against today's implementation this may or may not fail depending on
    // whether all seeded offsets happen to fall inside the hardcoded 90-day window; the offsets
    // below deliberately include some beyond 90 days (up to 99) specifically to exercise that gap.
    @Test
    void getUsageAnalyticsWithLargeRecentDaysReturnsFullWindowUntruncated() throws Exception {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                FROM_CURRENCY, 15, Timestamp.from(Instant.now()));

        int[] dayOffsets = {1, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80, 90, 99};
        for (int offset : dayOffsets) {
            jdbcTemplate.update(
                    "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                    FROM_CURRENCY, Timestamp.from(Instant.now().minus(offset, ChronoUnit.DAYS)));
        }

        MvcResult result = mockMvc.perform(get(USAGE_ENDPOINT).param("recentDays", "180"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.currencies[0].currencyCode").value(FROM_CURRENCY))
                .andReturn();

        List<List<String>> matchedQueryTimestamps = com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.currencies[?(@.currencyCode == '" + FROM_CURRENCY + "')].queryTimestamps");

        assertThat(matchedQueryTimestamps).hasSize(1);
        assertThat(matchedQueryTimestamps.get(0)).hasSize(dayOffsets.length);
    }
}
