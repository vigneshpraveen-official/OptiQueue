package com.optiqueue.dto;

import com.optiqueue.entity.Order;
import com.optiqueue.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDtos {

    public record OrderItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {}

    public record PlaceOrderRequest(
            @NotEmpty List<@Valid OrderItemRequest> items
    ) {}

    public record OrderResponse(Long orderId, OrderStatus status, BigDecimal totalAmount) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount());
        }
    }

    public record OrderItemView(Long productId, String productName, int quantity, BigDecimal unitPrice) {}

    public record OrderDetailResponse(
            Long orderId,
            String username,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemView> items
    ) {
        public static OrderDetailResponse from(Order order) {
            return new OrderDetailResponse(
                    order.getId(),
                    order.getUser().getUsername(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getCreatedAt(),
                    order.getItems().stream()
                            .map(i -> new OrderItemView(
                                    i.getProduct().getId(),
                                    i.getProduct().getName(),
                                    i.getQuantity(),
                                    i.getUnitPrice()))
                            .toList());
        }
    }

    public record UpdateStatusRequest(@NotNull OrderStatus status) {}
}
