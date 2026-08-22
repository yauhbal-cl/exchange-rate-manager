package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.ExchangeRate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Modifying
    @Query(value = "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) " +
                   "VALUES (:currencyCode, :rateToUsd, :rateDate) " +
                   "ON CONFLICT (currency_code, rate_date) DO UPDATE SET rate_to_usd = EXCLUDED.rate_to_usd",
           nativeQuery = true)
    void upsert(@Param("currencyCode") String currencyCode,
                @Param("rateToUsd") BigDecimal rateToUsd,
                @Param("rateDate") LocalDate rateDate);
}
