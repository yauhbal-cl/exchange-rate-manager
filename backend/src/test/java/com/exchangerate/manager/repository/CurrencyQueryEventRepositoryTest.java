package com.exchangerate.manager.repository;

import com.exchangerate.manager.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Repository-layer test for {@link CurrencyQueryEventRepository}, run against a
 * Testcontainers-managed PostgreSQL instance (see {@link AbstractIntegrationTest}) so that the
 * native {@code now()} insert and the window/ordering query are exercised for real.
 */
@SpringBootTest
@Transactional
class CurrencyQueryEventRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CurrencyQueryEventRepository currencyQueryEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // currency_code is CHAR(3) in the schema, so all seeded codes here must be exactly 3 chars;
    // "TQ*" is used to keep these test-owned codes distinct from other test classes' fixtures.

    private Long insertEvent(String currencyCode, Instant queriedAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?) RETURNING id",
                Long.class, currencyCode, Timestamp.from(queriedAt));
    }

    /**
     * Postgres' {@code TIMESTAMPTZ} column stores microsecond precision, so an inserted
     * {@link Instant} with finer-grained nanoseconds gets rounded (the driver rounds, it does not
     * simply truncate) on write — a rounding direction that isn't reliably reproducible by
     * re-deriving it client-side (e.g. via {@code truncatedTo(MICROS)}). Expected values are
     * therefore read back from the database rather than recomputed from the original
     * {@link Instant}.
     */
    private Instant readPersistedQueriedAt(String currencyCode, long id) {
        return jdbcTemplate.queryForObject(
                        "SELECT queried_at FROM currency_query_event WHERE currency_code = ? AND id = ?",
                        Timestamp.class, currencyCode, id)
                .toInstant();
    }

    @Test
    void insertEventsWritesIdenticalTimestampForBothCurrencies() {
        currencyQueryEventRepository.insertEvents("TQA", "TQB");

        Timestamp firstTimestamp = jdbcTemplate.queryForObject(
                "SELECT queried_at FROM currency_query_event WHERE currency_code = ?",
                Timestamp.class, "TQA");
        Timestamp secondTimestamp = jdbcTemplate.queryForObject(
                "SELECT queried_at FROM currency_query_event WHERE currency_code = ?",
                Timestamp.class, "TQB");

        assertThat(firstTimestamp).isEqualTo(secondTimestamp);
    }

    @Test
    void duplicateIdenticalTimestampRowsBothPersist() {
        Instant sharedInstant = Instant.now();
        Timestamp sharedTimestamp = Timestamp.from(sharedInstant);

        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                "TQC", sharedTimestamp);
        jdbcTemplate.update(
                "INSERT INTO currency_query_event (currency_code, queried_at) VALUES (?, ?)",
                "TQC", sharedTimestamp);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_query_event WHERE currency_code = ? AND queried_at = ?",
                Integer.class, "TQC", sharedTimestamp);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findQueryTimestampsOrdersByCurrencyCodeThenTimestampThenId() {
        Instant sharedInstant = Instant.now();

        // TQE rows share the exact same queried_at; insertion order must be preserved via id.
        Long firstTqeId = insertEvent("TQE", sharedInstant);
        Long secondTqeId = insertEvent("TQE", sharedInstant);
        Long tqdId = insertEvent("TQD", sharedInstant.minus(1, ChronoUnit.HOURS));

        Instant persistedTqdQueriedAt = readPersistedQueriedAt("TQD", tqdId);
        Instant persistedFirstTqeQueriedAt = readPersistedQueriedAt("TQE", firstTqeId);
        Instant persistedSecondTqeQueriedAt = readPersistedQueriedAt("TQE", secondTqeId);

        List<CurrencyQueryEventRepository.CurrencyQueryEventProjection> result =
                currencyQueryEventRepository.findQueryTimestamps(List.of("TQD", "TQE"), 3650);

        assertThat(result)
                .extracting(
                        CurrencyQueryEventRepository.CurrencyQueryEventProjection::getCurrencyCode,
                        CurrencyQueryEventRepository.CurrencyQueryEventProjection::getQueriedAt)
                .containsExactly(
                        tuple("TQD", persistedTqdQueriedAt),
                        tuple("TQE", persistedFirstTqeQueriedAt),
                        tuple("TQE", persistedSecondTqeQueriedAt));
    }

    @Test
    void findQueryTimestampsExcludesRowsOutsideWindow() {
        Instant recent = Instant.now();
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);

        Long recentId = insertEvent("TQF", recent);
        insertEvent("TQF", old);

        Instant persistedRecentQueriedAt = readPersistedQueriedAt("TQF", recentId);

        List<CurrencyQueryEventRepository.CurrencyQueryEventProjection> result =
                currencyQueryEventRepository.findQueryTimestamps(List.of("TQF"), 7);

        assertThat(result)
                .extracting(CurrencyQueryEventRepository.CurrencyQueryEventProjection::getCurrencyCode)
                .containsOnly("TQF");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQueriedAt()).isEqualTo(persistedRecentQueriedAt);
    }

    @Test
    void findQueryTimestampsReturnsEmptyListForCurrencyWithNoRows() {
        List<CurrencyQueryEventRepository.CurrencyQueryEventProjection> result =
                currencyQueryEventRepository.findQueryTimestamps(List.of("TQZ"), 90);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
