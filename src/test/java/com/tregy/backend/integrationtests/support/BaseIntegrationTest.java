package com.tregy.backend.integrationtests.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Every integration test shares one Spring context and one PostgreSQL 17 container, started
 * once per JVM. The schema comes from the real Flyway migrations (never H2, never
 * ddl-auto=create), so CHECK constraints and unique indexes behave like production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate cleaner;

    /**
     * Every test starts from an empty database, whatever ran before it. The database is shared
     * across test classes and their order is not fixed (it differs between machines), so a test
     * must never depend on what an earlier class left behind. Children are deleted before parents.
     */
    @BeforeEach
    void emptyDatabase() {
        for (String table : new String[]{"content_item", "generation_run", "sync_event", "srs_state",
                "learner_theme", "learner_profile", "refresh_token", "device", "app_user"}) {
            cleaner.update("delete from " + table);
        }
    }
}
