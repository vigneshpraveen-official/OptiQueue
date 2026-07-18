package com.optiqueue.order;

import com.optiqueue.config.RetryConfig;
import com.optiqueue.dto.OrderDtos.OrderItemRequest;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.entity.Order;
import com.optiqueue.entity.OrderStatus;
import com.optiqueue.repository.OrderRepository;
import com.optiqueue.service.OrderPlacementService;
import com.optiqueue.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the @Retryable wiring on OrderService.placeOrder: optimistic-lock
 * failures are retried up to 3 attempts total, then propagated.
 * Uses a minimal Spring context (RetryConfig + OrderService) with the
 * transactional core mocked out.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RetryConfig.class, OrderService.class})
class OrderServiceRetryTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private OrderRepository orderRepository;

    private PlaceOrderRequest request() {
        return new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 1)));
    }

    private Order confirmedOrder() {
        Order order = new Order();
        order.setId(42L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("10.00"));
        return order;
    }

    @Test
    void retriesTwiceThenSucceeds() {
        when(orderPlacementService.placeOrderOnce(anyString(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Product", 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException("Product", 1L))
                .thenReturn(confirmedOrder());

        var response = orderService.placeOrder("alice", request());

        assertThat(response.orderId()).isEqualTo(42L);
        verify(orderPlacementService, times(3)).placeOrderOnce(anyString(), any());
    }

    @Test
    void givesUpAfterThreeAttempts() {
        when(orderPlacementService.placeOrderOnce(anyString(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Product", 1L));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> orderService.placeOrder("alice", request()));
        verify(orderPlacementService, times(3)).placeOrderOnce(anyString(), any());
    }

    @Test
    void noRetryOnFirstTrySuccess() {
        when(orderPlacementService.placeOrderOnce(anyString(), any()))
                .thenReturn(confirmedOrder());

        orderService.placeOrder("alice", request());
        verify(orderPlacementService, times(1)).placeOrderOnce(anyString(), any());
    }
}
