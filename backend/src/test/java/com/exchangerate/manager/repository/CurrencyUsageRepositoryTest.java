package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.CurrencyUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer test for {@link CurrencyUsageRepository}, run against the real
 * docker-compose PostgreSQL instance (not H2/Testcontainers) so that the unique
 * constraint and CHECK constraints on {@code currency_usage} are exercised for real.
 */
@SpringBootTest
@Transactional
class CurrencyUsageRepositoryTest {

    private static final String TEST_CURRENCY_CODE = "ZZZ";

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

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
