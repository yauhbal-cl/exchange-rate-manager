package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.CurrencyQueryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CurrencyQueryEventRepository extends JpaRepository<CurrencyQueryEvent, Long> {

    /**
     * Appends one query-event row for each of the two currencies involved in a rate lookup, both
     * timestamped with the same {@code now()} (PostgreSQL's {@code transaction_timestamp()}) —
     * deliberately the same value {@link CurrencyUsageRepository#incrementUsage} already writes to
     * {@code last_queried_at}, so both statements in the same transaction produce byte-identical
     * timestamps.
     */
    @Modifying
    @Query(value = "INSERT INTO currency_query_event (currency_code, queried_at) " +
                   "VALUES (:firstCurrencyCode, now()), (:secondCurrencyCode, now())",
           nativeQuery = true)
    void insertEvents(@Param("firstCurrencyCode") String firstCurrencyCode,
                       @Param("secondCurrencyCode") String secondCurrencyCode);

    /**
     * Returns every query-event timestamp for the given currency codes within the last
     * {@code windowDays} days, ordered by {@code (currencyCode, queriedAt, id)}. The {@code id}
     * tie-break is deliberate — two events can share an identical {@code queried_at}, and plain
     * timestamp ordering alone isn't deterministic when duplicates are allowed.
     * <p>
     * The caller must never invoke this with an empty {@code currencyCodes} list — an empty
     * {@code IN ()} is invalid SQL; the calling service is responsible for skipping the call in
     * that case.
     */
    @Query(value = """
            SELECT currency_code AS currencyCode, queried_at AS queriedAt
            FROM currency_query_event
            WHERE currency_code IN (:currencyCodes)
              AND queried_at >= now() - (:windowDays || ' days')::interval
            ORDER BY currency_code ASC, queried_at ASC, id ASC
            """, nativeQuery = true)
    List<CurrencyQueryEventProjection> findQueryTimestamps(@Param("currencyCodes") List<String> currencyCodes,
                                                            @Param("windowDays") Integer windowDays);

    /**
     * Interface-based projection for {@link #findQueryTimestamps(List, Integer)}; getter names
     * map to the native query's column aliases (case-insensitive, underscore-to-camelCase).
     */
    interface CurrencyQueryEventProjection {

        String getCurrencyCode();

        Instant getQueriedAt();
    }
}
