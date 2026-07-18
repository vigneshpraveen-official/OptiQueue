package com.optiqueue.product;

import com.optiqueue.dto.ProductDtos.CreateProductRequest;
import com.optiqueue.dto.ProductDtos.RestockRequest;
import com.optiqueue.dto.OrderDtos.OrderItemRequest;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.entity.Role;
import com.optiqueue.entity.User;
import com.optiqueue.repository.ProductRepository;
import com.optiqueue.repository.UserRepository;
import com.optiqueue.service.OrderService;
import com.optiqueue.service.ProductService;
import com.optiqueue.testsupport.TestDatabaseConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies @Cacheable/@CacheEvict wiring using the in-memory 'simple' cache
 * backend — the caching semantics are identical when CACHE_TYPE=redis, only
 * the store differs (verified against Upstash in Phase 5 live checks).
 */
@SpringBootTest(properties = "spring.cache.type=simple")
@Import(TestDatabaseConfig.class)
class ProductCachingIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Test
    void productDetail_isCached_andEvictedOnRestock() {
        var created = productService.create(new CreateProductRequest(
                "CACHE-1", "Cached Widget", new BigDecimal("10.00"), 5));
        Mockito.clearInvocations(productRepository);

        productService.get(created.id());
        productService.get(created.id());
        // second read served from cache
        verify(productRepository, times(1)).findById(anyLong());

        productService.restock(created.id(), new RestockRequest(5));   // evicts
        Mockito.clearInvocations(productRepository);

        var fresh = productService.get(created.id());
        verify(productRepository, times(1)).findById(anyLong());
        assertThat(fresh.stockQuantity()).isEqualTo(10);
    }

    @Test
    void productList_isCached_andEvictedOnOrderPlacement() {
        var created = productService.create(new CreateProductRequest(
                "CACHE-2", "List Widget", new BigDecimal("20.00"), 8));
        userRepository.save(User.builder()
                .username("cachebuyer").passwordHash(passwordEncoder.encode("password123"))
                .role(Role.CUSTOMER).build());
        Mockito.clearInvocations(productRepository);

        var page = PageRequest.of(0, 20);
        productService.list(page);
        productService.list(page);
        verify(productRepository, times(1)).findAll(page);

        // placing an order changes stock → both caches evicted
        orderService.placeOrder("cachebuyer", new PlaceOrderRequest(
                List.of(new OrderItemRequest(created.id(), 2))));
        Mockito.clearInvocations(productRepository);

        var result = productService.list(page);
        verify(productRepository, times(1)).findAll(page);
        assertThat(result.content().stream()
                .filter(p -> p.id().equals(created.id()))
                .findFirst().orElseThrow().stockQuantity()).isEqualTo(6);

        // detail cache for that product must also reflect new stock
        assertThat(productService.get(created.id()).stockQuantity()).isEqualTo(6);
    }
}
