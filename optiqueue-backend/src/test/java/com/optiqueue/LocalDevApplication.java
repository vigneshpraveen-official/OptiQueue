package com.optiqueue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Runs the real application against an embedded PostgreSQL — full local dev
 * server with NO cloud accounts, Docker, or local Postgres install required.
 *
 * Start it with:
 *   JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw test-compile exec:java \
 *     -Dexec.mainClass=com.optiqueue.LocalDevApplication -Dexec.classpathScope=test
 *
 * Data lives only for the lifetime of the process. The in-memory 'simple'
 * cache backend is enabled so cache behavior is visible locally too.
 */
public final class LocalDevApplication {

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.start();
        String url = pg.getJdbcUrl("postgres", "postgres");

        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        if (System.getProperty("spring.cache.type") == null && System.getenv("CACHE_TYPE") == null) {
            System.setProperty("spring.cache.type", "simple");
        }

        System.out.println(">>> Embedded Postgres at " + url);
        OptiqueueBackendApplication.main(args);
    }
}
