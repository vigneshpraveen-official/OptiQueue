package com.optiqueue;

import com.optiqueue.testsupport.TestDatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestDatabaseConfig.class)
class OptiqueueBackendApplicationTests {

    /**
     * Boots the full Spring context against an embedded Postgres:
     * proves Flyway migrations apply and JPA entity mappings match the schema
     * (ddl-auto=validate would fail the boot on any mismatch).
     */
    @Test
    void contextLoads() {
    }
}
