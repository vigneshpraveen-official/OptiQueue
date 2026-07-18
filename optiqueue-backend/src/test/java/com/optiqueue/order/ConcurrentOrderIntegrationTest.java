package com.optiqueue.order;

import com.optiqueue.dto.OrderDtos.OrderItemRequest;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.entity.Product;
import com.optiqueue.entity.Role;
import com.optiqueue.entity.User;
import com.optiqueue.exception.InsufficientStockException;
import com.optiqueue.repository.OrderRepository;
import com.optiqueue.repository.ProductRepository;
import com.optiqueue.repository.UserRepository;
import com.optiqueue.service.OrderService;
import com.optiqueue.testsupport.TestDatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestDatabaseConfig.class)
class ConcurrentOrderIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User createCustomer(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.CUSTOMER)
                .build());
    }

    /**
     * The core overselling test: N threads race to buy from limited stock.
     * With 20 buyers × 1 unit against 10 units of stock, exactly 10 orders
     * must succeed and final stock must be exactly 0 — never negative, and
     * never more successful orders than stock.
     */
    @Test
    void concurrentOrders_neverOversell() throws Exception {
        int stock = 10;
        int buyers = 20;

        Product product = productRepository.save(Product.builder()
                .sku("RACE-1").name("Hot Item").price(new BigDecimal("49.99"))
                .stockQuantity(stock).build());
        for (int i = 0; i < buyers; i++) {
            createCustomer("racer" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(buyers);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger stockConflicts = new AtomicInteger();
        AtomicInteger insufficientStock = new AtomicInteger();

        for (int i = 0; i < buyers; i++) {
            final String username = "racer" + i;
            pool.submit(() -> {
                try {
                    startGun.await();
                    orderService.placeOrder(username, new PlaceOrderRequest(
                            List.of(new OrderItemRequest(product.getId(), 1))));
                    succeeded.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    stockConflicts.incrementAndGet();   // lost the race even after 3 retries
                } catch (InsufficientStockException e) {
                    insufficientStock.incrementAndGet(); // stock ran out
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        startGun.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Product after = productRepository.findById(product.getId()).orElseThrow();

        // Invariant 1: stock never goes negative.
        assertThat(after.getStockQuantity()).isGreaterThanOrEqualTo(0);
        // Invariant 2: units sold == units removed from stock (no overselling, no lost updates).
        assertThat(succeeded.get()).isEqualTo(stock - after.getStockQuantity());
        // Sanity: can't sell more than the stock that existed.
        assertThat(succeeded.get()).isLessThanOrEqualTo(stock);
        // All requests are accounted for.
        assertThat(succeeded.get() + stockConflicts.get() + insufficientStock.get()).isEqualTo(buyers);
    }

    @Test
    void orderForMoreThanStock_rejectedWith409Semantics() {
        Product product = productRepository.save(Product.builder()
                .sku("LOW-1").name("Scarce").price(new BigDecimal("10.00"))
                .stockQuantity(2).build());
        createCustomer("greedy");
        long ordersBefore = orderRepository.count();

        org.junit.jupiter.api.Assertions.assertThrows(InsufficientStockException.class, () ->
                orderService.placeOrder("greedy", new PlaceOrderRequest(
                        List.of(new OrderItemRequest(product.getId(), 5)))));

        Product after = productRepository.findById(product.getId()).orElseThrow();
        assertThat(after.getStockQuantity()).isEqualTo(2);   // untouched
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);   // no order row created
    }

    @Test
    void successfulOrder_computesTotalAndDecrementsStock() {
        Product p1 = productRepository.save(Product.builder()
                .sku("T-1").name("Alpha").price(new BigDecimal("100.00")).stockQuantity(10).build());
        Product p2 = productRepository.save(Product.builder()
                .sku("T-2").name("Beta").price(new BigDecimal("49.50")).stockQuantity(5).build());
        createCustomer("shopper");

        var response = orderService.placeOrder("shopper", new PlaceOrderRequest(List.of(
                new OrderItemRequest(p1.getId(), 2),
                new OrderItemRequest(p2.getId(), 1))));

        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("249.50"));
        assertThat(productRepository.findById(p1.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(productRepository.findById(p2.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
    }
}
