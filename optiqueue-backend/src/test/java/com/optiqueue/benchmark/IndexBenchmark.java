package com.optiqueue.benchmark;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Phase 6 benchmark: measures the latency of the application's hot queries on
 * a realistic dataset BEFORE and AFTER the V2 secondary indexes, on a real
 * PostgreSQL (embedded, same binary family as production Postgres).
 *
 * Run:
 *   JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw -q test-compile exec:java \
 *     -Dexec.mainClass=com.optiqueue.benchmark.IndexBenchmark -Dexec.classpathScope=test
 *
 * Dataset: 10_000 products, 2_000 users, 100_000 orders, ~200_000 order_items.
 * Method: each query runs WARMUP+MEASURED times with random parameters;
 * reported figure is the average of the measured runs (server-side execution
 * time via EXPLAIN ANALYZE would exclude planning; we use wall-clock over JDBC
 * which matches what the application actually experiences).
 */
public final class IndexBenchmark {

    private static final int PRODUCTS = 10_000;
    private static final int USERS = 2_000;
    private static final int ORDERS = 100_000;
    private static final int WARMUP = 5;
    private static final int MEASURED = 30;
    private static final Random RND = new Random(42);

    record Bench(String label, String sql, java.util.function.Supplier<Object> param) {}

    public static void main(String[] args) throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.start()) {
            DataSource ds = pg.getPostgresDatabase();

            // Apply ONLY V1 (schema without secondary indexes)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("1")
                    .load()
                    .migrate();

            System.out.println("Seeding " + PRODUCTS + " products, " + USERS + " users, "
                    + ORDERS + " orders…");
            seed(ds);

            List<Bench> benches = List.of(
                    new Bench("orders by user_id      (order history)",
                            "SELECT * FROM orders WHERE user_id = ?",
                            () -> (long) (RND.nextInt(USERS) + 1)),
                    new Bench("orders by status       (staff dashboard)",
                            "SELECT count(*) FROM orders WHERE status = ?",
                            () -> "SHIPPED"),
                    new Bench("order_items by order   (order detail)",
                            "SELECT * FROM order_items WHERE order_id = ?",
                            () -> (long) (RND.nextInt(ORDERS) + 1)),
                    new Bench("items sold per product (analytics join)",
                            "SELECT coalesce(sum(quantity),0) FROM order_items WHERE product_id = ?",
                            () -> (long) (RND.nextInt(PRODUCTS) + 1)));

            System.out.println("\n=== BEFORE indexes (V1 schema) ===");
            double[] before = runAll(ds, benches);

            System.out.println("\nApplying V2 secondary indexes…");
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("ANALYZE");
            }

            System.out.println("\n=== AFTER indexes (V2 schema) ===");
            double[] after = runAll(ds, benches);

            System.out.println("\n=== SUMMARY (avg ms over " + MEASURED + " runs) ===");
            System.out.printf("%-42s %10s %10s %10s%n", "query", "before", "after", "faster");
            double totalBefore = 0, totalAfter = 0;
            for (int i = 0; i < benches.size(); i++) {
                totalBefore += before[i];
                totalAfter += after[i];
                System.out.printf("%-42s %8.2fms %8.2fms %9.1f%%%n",
                        benches.get(i).label(), before[i], after[i],
                        100.0 * (before[i] - after[i]) / before[i]);
            }
            System.out.printf("%-42s %8.2fms %8.2fms %9.1f%%%n",
                    "OVERALL", totalBefore, totalAfter,
                    100.0 * (totalBefore - totalAfter) / totalBefore);
        }
    }

    private static double[] runAll(DataSource ds, List<Bench> benches) throws Exception {
        double[] results = new double[benches.size()];
        try (Connection c = ds.getConnection()) {
            for (int i = 0; i < benches.size(); i++) {
                results[i] = time(c, benches.get(i));
                System.out.printf("  %-42s %8.2f ms avg%n", benches.get(i).label(), results[i]);
            }
        }
        return results;
    }

    private static double time(Connection c, Bench bench) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(bench.sql())) {
            for (int i = 0; i < WARMUP; i++) {
                bind(ps, bench.param().get());
                consume(ps);
            }
            long total = 0;
            for (int i = 0; i < MEASURED; i++) {
                bind(ps, bench.param().get());
                long t0 = System.nanoTime();
                consume(ps);
                total += System.nanoTime() - t0;
            }
            return total / 1e6 / MEASURED;
        }
    }

    private static void bind(PreparedStatement ps, Object value) throws Exception {
        if (value instanceof Long l) ps.setLong(1, l);
        else ps.setString(1, (String) value);
    }

    private static void consume(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { /* drain */ }
        }
    }

    private static void seed(DataSource ds) throws Exception {
        String[] statuses = {"PENDING", "CONFIRMED", "SHIPPED", "CANCELLED"};
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(username, password_hash, role) VALUES (?, 'x', 'CUSTOMER')")) {
                for (int i = 1; i <= USERS; i++) {
                    ps.setString(1, "user" + i);
                    ps.addBatch();
                    if (i % 5000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products(sku, name, price, stock_quantity) VALUES (?, ?, ?, 1000)")) {
                for (int i = 1; i <= PRODUCTS; i++) {
                    ps.setString(1, "SKU-" + i);
                    ps.setString(2, "Product " + i);
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(10 + RND.nextInt(990)));
                    ps.addBatch();
                    if (i % 5000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders(user_id, status, total_amount) VALUES (?, ?, ?)")) {
                for (int i = 1; i <= ORDERS; i++) {
                    ps.setLong(1, RND.nextInt(USERS) + 1);
                    ps.setString(2, statuses[RND.nextInt(statuses.length)]);
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(10 + RND.nextInt(4990)));
                    ps.addBatch();
                    if (i % 5000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO order_items(order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
                int n = 0;
                for (int order = 1; order <= ORDERS; order++) {
                    int lines = 1 + RND.nextInt(3);   // 1-3 items per order → ~200k rows
                    for (int l = 0; l < lines; l++) {
                        ps.setLong(1, order);
                        ps.setLong(2, RND.nextInt(PRODUCTS) + 1);
                        ps.setInt(3, 1 + RND.nextInt(5));
                        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(10 + RND.nextInt(990)));
                        ps.addBatch();
                        if (++n % 5000 == 0) ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            c.commit();
            try (Statement s = c.createStatement()) {
                s.execute("ANALYZE");
            }
            c.commit();
        }
    }
}
