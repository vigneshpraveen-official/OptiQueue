package com.optiqueue.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Boots a real PostgreSQL server embedded in the test JVM (no Docker needed)
 * and overrides the application DataSource with it. Flyway migrations run
 * against it exactly as they would against Neon in production.
 */
@TestConfiguration
public class TestDatabaseConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        return embeddedPostgres.getPostgresDatabase();
    }
}
