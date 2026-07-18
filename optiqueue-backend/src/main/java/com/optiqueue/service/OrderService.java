package com.optiqueue.service;

import com.optiqueue.dto.OrderDtos.OrderDetailResponse;
import com.optiqueue.dto.OrderDtos.OrderResponse;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.entity.Order;
import com.optiqueue.entity.OrderStatus;
import com.optiqueue.entity.Product;
import com.optiqueue.exception.ApiException;
import com.optiqueue.exception.NotFoundException;
import com.optiqueue.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Allowed status transitions; anything not listed is rejected. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private final OrderPlacementService orderPlacementService;
    private final OrderRepository orderRepository;
    private final ProductCacheService productCacheService;

    /**
     * Retries the whole transactional placement (fresh transaction + fresh
     * entity reads each attempt) when an optimistic-lock conflict occurs.
     * After 3 failed attempts the exception propagates and the global handler
     * maps it to 409 STOCK_CONFLICT.
     */
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2, random = true))
    public OrderResponse placeOrder(String username, PlaceOrderRequest request) {
        OrderResponse response = OrderResponse.from(orderPlacementService.placeOrderOnce(username, request));
        // Stock changed and the transaction has committed → drop stale cache entries.
        productCacheService.evictProducts(
                request.items().stream().map(i -> i.productId()).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(Long id, Authentication auth) {
        Order order = orderRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
        if (!privileged && !order.getUser().getUsername().equals(auth.getName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not your order");
        }
        return OrderDetailResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMine(String username, Pageable pageable) {
        return orderRepository.findByUserUsername(username, pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
        if (!ALLOWED_TRANSITIONS.get(order.getStatus()).contains(newStatus)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TRANSITION",
                    "Cannot change status from %s to %s".formatted(order.getStatus(), newStatus));
        }
        if (newStatus == OrderStatus.CANCELLED) {
            // Return reserved stock; protected by the same optimistic lock.
            for (var item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
            productCacheService.evictProducts(
                    order.getItems().stream().map(i -> i.getProduct().getId()).toList());
        }
        order.setStatus(newStatus);
        return OrderResponse.from(order);
    }
}
