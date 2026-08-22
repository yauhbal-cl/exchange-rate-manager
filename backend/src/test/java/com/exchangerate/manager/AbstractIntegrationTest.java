package com.exchangerate.manager;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL instance.
 *
 * <p>Deliberately NOT annotated with {@code @Testcontainers}/{@code @Container}: that JUnit5
 * extension stops even a {@code static} container in {@code afterAll} once the owning test class
 * finishes, so each subclass would start (and immediately lose) its own container instead of
 * sharing one. Starting it manually in a static initializer, with no JUnit5 lifecycle management
 * attached, is the documented Testcontainers "singleton container" pattern: the container starts
 * once per JVM on first use and is left running (torn down by Ryuk at JVM exit) so it is genuinely
 * shared across every subclass in the same test run.
 *
 * <p>{@code fixer.api-key} has no default in {@code application.yml} (it's a real external
 * secret, and production should fail fast without it), so the full Spring context can't start in
 * tests unless something supplies it. None of these tests actually call out to Fixer.io — they
 * only need the {@code FixerClient} bean to construct — so a fixed dummy value here is enough,
 * and keeps the test suite independent of any developer/CI environment variable.
 */
@TestPropertySource(properties = "fixer.api-key=test-fixer-api-key")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    static {
        POSTGRES.start();
    }
}
