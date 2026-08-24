package com.exchangerate.manager.service;

import com.exchangerate.manager.repository.CurrencyQueryEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.stereotype.Service;

/**
 * Purges {@code currency_query_event} rows older than the 365-day retention window, in bounded
 * batches, without ever touching {@code currency_usage}.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional} at the {@link #purgeExpiredEvents()}
 * level: each {@link CurrencyQueryEventRepository#deleteExpiredBatch(int)} call must run in its
 * own transaction so a single purge run never holds one long-lived transaction (or lock) against
 * the table. Since this method has no surrounding transaction of its own, Spring Data's implicit
 * per-repository-method transaction applies independently to every {@code deleteExpiredBatch}
 * call in the loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryEventPurgeService {

    private static final int BATCH_SIZE = 10_000;

    private final CurrencyQueryEventRepository currencyQueryEventRepository;

    @SchedulerLock(name = "query-event-retention-purge")
    public void purgeExpiredEvents() {
        int totalDeleted = 0;
        int deletedInBatch;
        do {
            deletedInBatch = currencyQueryEventRepository.deleteExpiredBatch(BATCH_SIZE);
            totalDeleted += deletedInBatch;
        } while (deletedInBatch > 0);

        log.info("Query-event retention purge removed {} row(s) older than 365 days", totalDeleted);
    }
}
