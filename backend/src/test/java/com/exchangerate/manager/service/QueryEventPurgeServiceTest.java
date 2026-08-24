package com.exchangerate.manager.service;

import com.exchangerate.manager.AbstractIntegrationTest;
import com.exchangerate.manager.repository.CurrencyQueryEventRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Real-DB test for {@link QueryEventPurgeService}'s 365-day retention purge (FR-022–FR-025,
 * SC-011, SC-012).
 *
 * <p>Deliberately NOT {@code @Transactional} at the class or method level, for the same reason
 * {@link ExchangeRateServiceConcurrencyIT} avoids it: wrapping these tests in one enclosing,
 * rolled-back transaction would hide exactly what's being verified. {@link
 * QueryEventPurgeService#purgeExpiredEvents()} is itself deliberately not {@code @Transactional} —
 * each {@code deleteExpiredBatch} call commits independently, per that class's Javadoc — and its
 * {@code @SchedulerLock} needs a real commit against the {@code shedlock} table to acquire and
 * release the lock; both of those only behave correctly against genuinely committed state, not a
 * transaction the test later rolls back. The concurrent-lookup scenario further needs the purge
 * and the rate lookup to run as two independently committing transactions on two different
 * threads, which a single enclosing test transaction would also prevent (a transaction only ever
 * sees its own uncommitted writes, so it could never race a concurrent write the way this test
 * needs to). As a consequence, every row this test seeds is committed for real and is NOT rolled
 * back afterward. Every seeded currency code uses the "PG*" prefix, unused by any other test file
 * in this suite, so leftover rows can't collide with another test's assertions; each test method
 * also deletes its own currency codes' rows in an {@code @AfterEach} so repeated runs don't
 * accumulate ever-growing tables. Data seeded through a repository's own {@code @Modifying} native
 * query (e.g. {@link ExchangeRateRepository#upsert}) is written via {@link TransactionTemplate},
 * exactly like {@link ExchangeRateServiceConcurrencyIT}, since such a call needs an active
 * transaction even for one-off setup writes; plain {@link JdbcTemplate} statements need no such
 * wrapping since they run in autocommit mode outside of any Spring-managed transaction.
 */
@SpringBootTest
class QueryEventPurgeServiceTest extends AbstractIntegrationTest {

    private static final Instant EXPIRED_INSTANT = Instant.now().minus(400, ChronoUnit.DAYS);
    private static final Instant RETAINED_INSTANT = Instant.now().minus(10, ChronoUnit.DAYS);

    // Currency codes are unique to this test class ("PG*" prefix) — not used by any other test
    // file's seed data (see CurrencyUsageRepositoryTest's "TA*" and CurrencyQueryEventRepositoryTest's
    // "TQ*" for the equivalent convention on those files).
    private static final String MIXED_RETENTION_CURRENCY = "PGA";
    private static final String USAGE_INVARIANT_CURRENCY = "PGB";
    private static final String BATCH_LOOP_CURRENCY = "PGC";
    private static final String LOOKUP_FROM_CURRENCY = "PGD";
    private static final String LOOKUP_TO_CURRENCY = "PGE";
    private static final String CONCURRENT_PURGE_CURRENCY = "PGF";

    private static final LocalDate LOOKUP_RATE_DATE = LocalDate.of(2026, 8, 1);
    private static final BigDecimal LOOKUP_FROM_RATE_TO_USD = new BigDecimal("1.500000");
    private static final BigDecimal LOOKUP_TO_RATE_TO_USD = new BigDecimal("1.000000");

    // More than one BATCH_SIZE (10_000, private to QueryEventPurgeService) worth of expired rows,
    // so a single purgeExpiredEvents() call is only proven correct if its internal loop actually
    // runs more than once.
    private static final int MORE_THAN_ONE_BATCH = 10_005;

    @Autowired
    private QueryEventPurgeService queryEventPurgeService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyQueryEventRepository currencyQueryEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpSeededRows() {
        for (String currencyCode : List.of(
                MIXED_RETENTION_CURRENCY,
                USAGE_INVARIANT_CURRENCY,
                BATCH_LOOP_CURRENCY,
                LOOKUP_FROM_CURRENCY,
                LOOKUP_TO_CURRENCY,
                CONCURRENT_PURGE_CURRENCY)) {
            jdbcTemplate.update("DELETE FROM currency_query_event WHERE currency_code = ?", currencyCode);
            jdbcTemplate.update("DELETE FROM currency_usage WHERE currency_code = ?", currencyCode);
        }
    }

    private void insertQueryEvent(String currencyCode, Instant queriedAt) {
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                currencyCode, Timestamp.from(queriedAt));
    }

    private int countEvents(String currencyCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_query_event WHERE currency_code = ?",
                Integer.class, currencyCode);
        return count == null ? 0 : count;
    }

    private int countEventsWithinRetention(String currencyCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_query_event "
                        + "WHERE currency_code = ? AND queried_at >= now() - INTERVAL '365 days'",
                Integer.class, currencyCode);
        return count == null ? 0 : count;
    }

    @Test
    void purgeRemovesExpiredEventsAndKeepsRetainedEventsForSameCurrency() {
        insertQueryEvent(MIXED_RETENTION_CURRENCY, EXPIRED_INSTANT);
        insertQueryEvent(MIXED_RETENTION_CURRENCY, EXPIRED_INSTANT.minus(5, ChronoUnit.DAYS));
        insertQueryEvent(MIXED_RETENTION_CURRENCY, RETAINED_INSTANT);

        queryEventPurgeService.purgeExpiredEvents();

        assertThat(countEvents(MIXED_RETENTION_CURRENCY)).isEqualTo(1);
        assertThat(countEventsWithinRetention(MIXED_RETENTION_CURRENCY)).isEqualTo(1);
    }

    @Test
    void purgeLeavesCurrencyUsageCountersByteIdentical() {
        jdbcTemplate.update(
                "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) VALUES (?, ?, ?)",
                USAGE_INVARIANT_CURRENCY, 777L, Timestamp.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        insertQueryEvent(USAGE_INVARIANT_CURRENCY, EXPIRED_INSTANT);
        insertQueryEvent(USAGE_INVARIANT_CURRENCY, EXPIRED_INSTANT.minus(20, ChronoUnit.DAYS));
        insertQueryEvent(USAGE_INVARIANT_CURRENCY, RETAINED_INSTANT);

        Long queryCountBefore = jdbcTemplate.queryForObject(
                "SELECT query_count FROM currency_usage WHERE currency_code = ?",
                Long.class, USAGE_INVARIANT_CURRENCY);
        Timestamp lastQueriedAtBefore = jdbcTemplate.queryForObject(
                "SELECT last_queried_at FROM currency_usage WHERE currency_code = ?",
                Timestamp.class, USAGE_INVARIANT_CURRENCY);

        queryEventPurgeService.purgeExpiredEvents();

        Long queryCountAfter = jdbcTemplate.queryForObject(
                "SELECT query_count FROM currency_usage WHERE currency_code = ?",
                Long.class, USAGE_INVARIANT_CURRENCY);
        Timestamp lastQueriedAtAfter = jdbcTemplate.queryForObject(
                "SELECT last_queried_at FROM currency_usage WHERE currency_code = ?",
                Timestamp.class, USAGE_INVARIANT_CURRENCY);

        assertThat(queryCountAfter).isEqualTo(queryCountBefore);
        assertThat(lastQueriedAtAfter).isEqualTo(lastQueriedAtBefore);
        // Sanity-check the purge actually did something for this currency, so the byte-identical
        // assertions above aren't vacuously true.
        assertThat(countEvents(USAGE_INVARIANT_CURRENCY)).isEqualTo(1);
    }

    @Test
    void purgeDeletesMoreThanOneBatchWorthOfExpiredRowsInASingleRun() {
        List<Object[]> batchArgs = new ArrayList<>(MORE_THAN_ONE_BATCH);
        for (int i = 0; i < MORE_THAN_ONE_BATCH; i++) {
            batchArgs.add(new Object[] {
                    BATCH_LOOP_CURRENCY, Timestamp.from(EXPIRED_INSTANT.minus(i, ChronoUnit.SECONDS))
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)", batchArgs);
        assertThat(countEvents(BATCH_LOOP_CURRENCY)).isEqualTo(MORE_THAN_ONE_BATCH);

        queryEventPurgeService.purgeExpiredEvents();

        assertThat(countEvents(BATCH_LOOP_CURRENCY)).isZero();
    }

    @Test
    void concurrentLookupSucceedsAndSurvivesAPurgeRunningAtTheSameTime() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            exchangeRateRepository.upsert(LOOKUP_FROM_CURRENCY, LOOKUP_FROM_RATE_TO_USD, LOOKUP_RATE_DATE);
            exchangeRateRepository.upsert(LOOKUP_TO_CURRENCY, LOOKUP_TO_RATE_TO_USD, LOOKUP_RATE_DATE);
        });
        // Expired events for a currency that plays no part in the lookup, purely so the purge has
        // real (non-trivial) work to do while it races the lookup.
        insertQueryEvent(CONCURRENT_PURGE_CURRENCY, EXPIRED_INSTANT);
        insertQueryEvent(CONCURRENT_PURGE_CURRENCY, EXPIRED_INSTANT.minus(3, ChronoUnit.DAYS));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<?> purgeFuture = executor.submit(() -> {
                awaitLatch(startLatch);
                queryEventPurgeService.purgeExpiredEvents();
            });
            Future<ExchangeRateLookupResult> lookupFuture = executor.submit(() -> {
                awaitLatch(startLatch);
                return exchangeRateService.lookup(LOOKUP_FROM_CURRENCY, LOOKUP_TO_CURRENCY, LOOKUP_RATE_DATE);
            });

            // Release both threads at (roughly) the same instant so they genuinely run concurrently
            // rather than sequentially.
            startLatch.countDown();

            try {
                purgeFuture.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Concurrent purge failed", e);
            }
            try {
                lookupFuture.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail("Concurrent rate lookup failed while a retention purge was running", e);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(countEvents(CONCURRENT_PURGE_CURRENCY)).isZero();

        List<CurrencyQueryEventRepository.CurrencyQueryEventProjection> survivingEvents =
                currencyQueryEventRepository.findQueryTimestamps(
                        List.of(LOOKUP_FROM_CURRENCY, LOOKUP_TO_CURRENCY), 1);
        List<String> survivingCurrencyCodes = survivingEvents.stream()
                .map(CurrencyQueryEventRepository.CurrencyQueryEventProjection::getCurrencyCode)
                .toList();
        assertThat(survivingCurrencyCodes).containsExactlyInAnyOrder(LOOKUP_FROM_CURRENCY, LOOKUP_TO_CURRENCY);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
