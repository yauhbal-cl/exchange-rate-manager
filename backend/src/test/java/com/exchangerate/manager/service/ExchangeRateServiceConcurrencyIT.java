package com.exchangerate.manager.service;

import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated concurrency test for FR-009/SC-003: N simultaneous successful
 * {@link ExchangeRateService#lookup} calls against the same currency pair must not lose any
 * usage-counter increments.
 *
 * <p>Deliberately NOT {@code @Transactional} at the class or method level (unlike the other
 * real-DB tests in this codebase, e.g. {@code ExchangeRateServiceTest}'s Mockito-only sibling or
 * {@code ExchangeControllerIT}'s rollback-per-test convention) — per research.md's "Testing
 * approach", wrapping this test in a single transaction would hide the very row-lock contention
 * being verified, since a single transaction only ever sees its own uncommitted writes and never
 * truly contends with itself. As a consequence, the seeded {@code exchange_rates} rows for this
 * test are NOT rolled back and persist in the DB afterward; this is an accepted tradeoff for this
 * specific test. The seed date is unique to this class (not used by any other test file) and
 * {@code upsert} is idempotent per {@code (currency_code, rate_date)}, so re-running this test
 * cannot corrupt any other test's assertions. The chosen currency codes (CHF/AUD) are not used by
 * any other test file, so the persisted {@code currency_usage} rows this test creates cannot
 * collide with another test's "never queried" assertions either.
 */
@SpringBootTest
class ExchangeRateServiceConcurrencyIT {

    private static final int CONCURRENT_LOOKUPS = 20;
    private static final int THREAD_POOL_SIZE = 10;

    // Unique to this test class — not used by any other test file's seed data, so this test's
    // persisted (non-rolled-back) currency_usage rows can't collide with another test's
    // "never queried" assertions (e.g. ExchangeControllerIT uses EUR/GBP/ZZZ).
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 15);
    private static final String FROM_CURRENCY = "CHF";
    private static final String TO_CURRENCY = "AUD";
    private static final BigDecimal FROM_RATE_TO_USD = new BigDecimal("1.080000");
    private static final BigDecimal TO_RATE_TO_USD = new BigDecimal("1.000000");

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentSuccessfulLookupsDoNotLoseUsageCounterIncrements() throws InterruptedException {
        // Seed in its own short-lived transaction (via TransactionTemplate, since this test class
        // is deliberately not @Transactional) — a @Modifying native query requires an active
        // transaction even for one-off setup writes.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            exchangeRateRepository.upsert(FROM_CURRENCY, FROM_RATE_TO_USD, RATE_DATE);
            exchangeRateRepository.upsert(TO_CURRENCY, TO_RATE_TO_USD, RATE_DATE);
        });

        long fromCountBefore = currentQueryCount(FROM_CURRENCY);
        long toCountBefore = currentQueryCount(TO_CURRENCY);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(CONCURRENT_LOOKUPS);

        try {
            for (int i = 0; i < CONCURRENT_LOOKUPS; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    exchangeRateService.lookup(FROM_CURRENCY, TO_CURRENCY, RATE_DATE);
                }));
            }

            // Release all threads at (roughly) the same instant so they genuinely race against
            // the DB row lock rather than running sequentially.
            startLatch.countDown();

            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException("Concurrent lookup failed", e);
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        long fromCountAfter = currentQueryCount(FROM_CURRENCY);
        long toCountAfter = currentQueryCount(TO_CURRENCY);

        assertThat(fromCountAfter - fromCountBefore).isEqualTo(CONCURRENT_LOOKUPS);
        assertThat(toCountAfter - toCountBefore).isEqualTo(CONCURRENT_LOOKUPS);
    }

    private long currentQueryCount(String currencyCode) {
        return currencyUsageRepository.findByCurrencyCode(currencyCode)
                .map(usage -> usage.getQueryCount())
                .orElse(0L);
    }
}
