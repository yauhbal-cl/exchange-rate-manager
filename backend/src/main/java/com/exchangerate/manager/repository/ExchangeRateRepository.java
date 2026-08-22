package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.ExchangeRate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate);

    boolean existsByCurrencyCode(String currencyCode);

    @Query(value = "SELECT MAX(rate_date) FROM exchange_rates a " +
                   "WHERE currency_code = :from " +
                   "AND EXISTS (" +
                   "  SELECT 1 FROM exchange_rates b " +
                   "  WHERE b.currency_code = :to AND b.rate_date = a.rate_date" +
                   ")",
           nativeQuery = true)
    Optional<LocalDate> findLatestCommonDate(@Param("from") String from, @Param("to") String to);

    /**
     * Returns one row per {@code rate_date} within {@code [startDate, endDate]} for which both
     * {@code from} and {@code to} have stored rates, joined on {@code rate_date}. Dates missing
     * either currency's data are never returned. Ordered chronologically ascending.
     */
    @Query(value = "SELECT a.rate_date AS rateDate, " +
                   "       a.rate_to_usd AS fromRateToUsd, " +
                   "       b.rate_to_usd AS toRateToUsd " +
                   "FROM exchange_rates a " +
                   "JOIN exchange_rates b ON b.rate_date = a.rate_date AND b.currency_code = :to " +
                   "WHERE a.currency_code = :from " +
                   "AND a.rate_date BETWEEN :startDate AND :endDate " +
                   "ORDER BY a.rate_date ASC",
           nativeQuery = true)
    List<RateTrendProjection> findTrend(@Param("from") String from,
                                         @Param("to") String to,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * Interface-based projection for {@link #findTrend}; getter names map to the native query's
     * column aliases (case-insensitive, underscore-to-camelCase).
     */
    interface RateTrendProjection {

        LocalDate getRateDate();

        BigDecimal getFromRateToUsd();

        BigDecimal getToRateToUsd();
    }

    @Modifying
    @Query(value = "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) " +
                   "VALUES (:currencyCode, :rateToUsd, :rateDate) " +
                   "ON CONFLICT (currency_code, rate_date) DO UPDATE SET rate_to_usd = EXCLUDED.rate_to_usd",
           nativeQuery = true)
    void upsert(@Param("currencyCode") String currencyCode,
                @Param("rateToUsd") BigDecimal rateToUsd,
                @Param("rateDate") LocalDate rateDate);
}
