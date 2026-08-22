package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.CurrencyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CurrencyUsageRepository extends JpaRepository<CurrencyUsage, Long> {

    Optional<CurrencyUsage> findByCurrencyCode(String currencyCode);

    /**
     * Atomically increments the query counter for {@code currencyCode}, inserting a new row
     * with count 1 if none exists yet. A single native upsert statement — no read-modify-write
     * in Java — per Constitution Principle V (usage counters must be updated atomically at the
     * DB level to avoid the race condition an in-memory increment would introduce).
     */
    @Modifying
    @Query(value = "INSERT INTO currency_usage (currency_code, query_count, last_queried_at) " +
                   "VALUES (:currencyCode, 1, now()) " +
                   "ON CONFLICT (currency_code) " +
                   "DO UPDATE SET query_count = currency_usage.query_count + 1, last_queried_at = now()",
           nativeQuery = true)
    void incrementUsage(@Param("currencyCode") String currencyCode);

    /**
     * Returns usage-analytics rows for every currency that has ever appeared in
     * {@code exchange_rates} (distinct), left-joined against {@code currency_usage} so
     * never-queried currencies come back with {@code queryCount = 0} and
     * {@code lastQueriedAt = null} instead of being omitted.
     */
    @Query(value = """
            SELECT er.currency_code AS currencyCode,
                   COALESCE(cu.query_count, 0) AS queryCount,
                   cu.last_queried_at AS lastQueriedAt
            FROM (SELECT DISTINCT currency_code FROM exchange_rates) er
            LEFT JOIN currency_usage cu ON cu.currency_code = er.currency_code
            """, nativeQuery = true)
    List<CurrencyUsageProjection> findAllCurrencyUsage();

    /**
     * Interface-based projection for {@link #findAllCurrencyUsage()}; getter names map to the
     * native query's column aliases (case-insensitive, underscore-to-camelCase).
     */
    interface CurrencyUsageProjection {

        String getCurrencyCode();

        Long getQueryCount();

        Instant getLastQueriedAt();
    }
}
