package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.CurrencyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyUsageRepository extends JpaRepository<CurrencyUsage, Long> {

    Optional<CurrencyUsage> findByCurrencyCode(String currencyCode);
}
