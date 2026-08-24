package com.exchangerate.manager.repository;

import com.exchangerate.manager.AbstractIntegrationTest;
import com.exchangerate.manager.entity.CurrencyUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer test for {@link CurrencyUsageRepository}, run against a Testcontainers-managed
 * PostgreSQL instance (see {@link AbstractIntegrationTest}) so that the unique constraint and
 * CHECK constraints on {@code currency_usage} are exercised for real.
 */
@SpringBootTest
@Transactional
class CurrencyUsageRepositoryTest extends AbstractIntegrationTest {

    private static final String TEST_CURRENCY_CODE = "ZZZ";

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // currency_code is CHAR(3) in the schema, so all seeded codes here must be exactly 3 chars;
    // "TA*" is used to keep these test-owned codes distinct from other test classes' fixtures.

    private static void seedExchangeRate(ExchangeRateRepository repository, String currencyCode) {
        repository.upsert(currencyCode, new BigDecimal("1.2345"), LocalDate.now());
    }

    /**
     * Seeds a {@code currency_usage} row with a specific {@code queryCount} and
     * {@code lastQueriedAt}. {@link CurrencyUsage#getLastQueriedAt()} is DB-generated
     * ({@code insertable = false}, defaulted to {@code now()} on insert), so a plain JPA
     * {@code save()} cannot control it for recency tests — the row is inserted via the entity
     * repository first, then {@code last_queried_at} is overwritten with a direct native update
     * to the desired instant.
     */
    private void seedUsage(String currencyCode, long queryCount, Instant lastQueriedAt) {
        seedExchangeRate(exchangeRateRepository, currencyCode);
        CurrencyUsage currencyUsage = new CurrencyUsage();
        currencyUsage.setCurrencyCode(currencyCode);
        currencyUsage.setQueryCount(queryCount);
        currencyUsageRepository.save(currencyUsage);
        jdbcTemplate.update(
                "UPDATE currency_usage SET last_queried_at = ? WHERE currency_code = ?",
                java.sql.Timestamp.from(lastQueriedAt), currencyCode);
    }

    @Test
    void findCurrencyUsageOrdersByQueryCountDescending() {
        seedUsage("TAA", 3L, Instant.now());
        seedUsage("TAB", 10L, Instant.now());
        seedUsage("TAC", 1L, Instant.now());

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(null, null);

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .filter(code -> code.equals("TAA") || code.equals("TAB") || code.equals("TAC"))
                .toList();

        assertThat(codes).containsExactly("TAB", "TAA", "TAC");
    }

    @Test
    void findCurrencyUsageTieBreaksByCurrencyCodeAscending() {
        seedUsage("TAE", 5L, Instant.now());
        seedUsage("TAD", 5L, Instant.now());

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(null, null);

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .filter(code -> code.equals("TAD") || code.equals("TAE"))
                .toList();

        assertThat(codes).containsExactly("TAD", "TAE");
    }

    @Test
    void findCurrencyUsageLimitTruncatesToTopRankedRows() {
        seedUsage("TAF", 1L, Instant.now());
        seedUsage("TAG", 5L, Instant.now());
        seedUsage("TAH", 3L, Instant.now());

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(2, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCurrencyCode()).isEqualTo("TAG");
        assertThat(result.get(1).getCurrencyCode()).isEqualTo("TAH");
    }

    @Test
    void findCurrencyUsageLimitLargerThanAvailableRowsReturnsAllWithoutPadding() {
        seedUsage("TAI", 2L, Instant.now());
        seedUsage("TAJ", 4L, Instant.now());

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(1000, null);

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .filter(code -> code.equals("TAI") || code.equals("TAJ"))
                .toList();

        assertThat(codes).containsExactly("TAJ", "TAI");
    }

    @Test
    void findCurrencyUsageRecentDaysFiltersOnLastQueriedAt() {
        seedUsage("TAK", 4L, Instant.now().minus(1, ChronoUnit.HOURS));
        seedUsage("TAL", 6L, Instant.now().minus(30, ChronoUnit.DAYS));
        seedExchangeRate(exchangeRateRepository, "TAM");

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(null, 7);

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .toList();

        assertThat(codes).contains("TAK");
        assertThat(codes).doesNotContain("TAL", "TAM");
    }

    @Test
    void findCurrencyUsageWithoutRecentDaysIncludesNeverQueriedCurrencies() {
        seedExchangeRate(exchangeRateRepository, "TAN");

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findCurrencyUsage(null, null);

        CurrencyUsageRepository.CurrencyUsageProjection neverQueried = result.stream()
                .filter(row -> row.getCurrencyCode().equals("TAN"))
                .findFirst()
                .orElseThrow();

        assertThat(neverQueried.getQueryCount()).isZero();
        assertThat(neverQueried.getLastQueriedAt()).isNull();
    }

    @Test
    void findAllCurrencyUsageIncludesEveryDistinctCurrencyFromExchangeRates() {
        seedUsage("TAO", 7L, Instant.now());
        seedExchangeRate(exchangeRateRepository, "TAP");

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findAllCurrencyUsage();

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .toList();

        assertThat(codes).contains("TAO", "TAP");
    }

    @Test
    void findAllCurrencyUsageReturnsZeroCountAndNullLastQueriedAtForNeverQueriedCurrency() {
        seedExchangeRate(exchangeRateRepository, "TAQ");

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findAllCurrencyUsage();

        CurrencyUsageRepository.CurrencyUsageProjection neverQueried = result.stream()
                .filter(row -> row.getCurrencyCode().equals("TAQ"))
                .findFirst()
                .orElseThrow();

        assertThat(neverQueried.getQueryCount()).isZero();
        assertThat(neverQueried.getLastQueriedAt()).isNull();
    }

    @Test
    void findAllCurrencyUsageDoesNotApplyRecencyFiltering() {
        seedUsage("TAR", 2L, Instant.now().minus(365, ChronoUnit.DAYS));

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findAllCurrencyUsage();

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .toList();

        assertThat(codes).contains("TAR");
    }

    @Test
    void findAllCurrencyUsageReturnsAllRowsWithoutRankingOrLimitTruncation() {
        seedUsage("TAS", 1L, Instant.now());
        seedUsage("TAT", 2L, Instant.now());
        seedUsage("TAU", 3L, Instant.now());

        List<CurrencyUsageRepository.CurrencyUsageProjection> result =
                currencyUsageRepository.findAllCurrencyUsage();

        List<String> codes = result.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .filter(code -> code.equals("TAS") || code.equals("TAT") || code.equals("TAU"))
                .toList();

        assertThat(codes).containsExactlyInAnyOrder("TAS", "TAT", "TAU");
    }

    @Test
    void savesCurrencyUsageAndFindsItByCurrencyCode() {
        CurrencyUsage currencyUsage = new CurrencyUsage();
        currencyUsage.setCurrencyCode(TEST_CURRENCY_CODE);
        currencyUsage.setQueryCount(5L);
        currencyUsage.setLastQueriedAt(Instant.now());

        currencyUsageRepository.save(currencyUsage);

        Optional<CurrencyUsage> found = currencyUsageRepository.findByCurrencyCode(TEST_CURRENCY_CODE);

        assertThat(found).isPresent();
        assertThat(found.get().getCurrencyCode()).isEqualTo(TEST_CURRENCY_CODE);
        assertThat(found.get().getQueryCount()).isEqualTo(5L);
    }
}
