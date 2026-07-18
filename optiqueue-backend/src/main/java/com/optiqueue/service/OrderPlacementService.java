package com.optiqueue.service;

import com.optiqueue.dto.OrderDtos.OrderItemRequest;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.entity.Order;
import com.optiqueue.entity.OrderItem;
import com.optiqueue.entity.OrderStatus;
import com.optiqueue.entity.Product;
import com.optiqueue.entity.User;
import com.optiqueue.exception.InsufficientStockException;
import com.optiqueue.exception.NotFoundException;
import com.optiqueue.repository.OrderRepository;
import com.optiqueue.repository.ProductRepository;
import com.optiqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transactional core of order placement, kept in its own bean so that
 * {@link OrderService}'s @Retryable wrapper opens a FRESH transaction (and
 * fresh JPA persistence context) on every retry attempt. If retry and
 * transaction lived on the same method, a retry would re-run inside the same
 * aborted transaction with stale entity state.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public Order placeOrderOnce(String username, PlaceOrderRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User", username));

        // Merge duplicate product lines, keep deterministic order.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product", productId));
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(productId, quantity, product.getStockQuantity());
            }
            // The decrement is protected by the @Version column: at commit,
            // Hibernate issues UPDATE ... WHERE id=? AND version=?; if a rival
            // transaction committed first, 0 rows match and an
            // ObjectOptimisticLockingFailureException is raised → retried by OrderService.
            product.setStockQuantity(product.getStockQuantity() - quantity);

            order.addItem(OrderItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }
}
