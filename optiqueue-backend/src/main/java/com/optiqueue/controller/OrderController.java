package com.optiqueue.controller;

import com.optiqueue.dto.OrderDtos.OrderDetailResponse;
import com.optiqueue.dto.OrderDtos.OrderResponse;
import com.optiqueue.dto.OrderDtos.PlaceOrderRequest;
import com.optiqueue.dto.OrderDtos.UpdateStatusRequest;
import com.optiqueue.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request,
                                                    Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(auth.getName(), request));
    }

    @GetMapping("/{id}")
    public OrderDetailResponse getOrder(@PathVariable Long id, Authentication auth) {
        return orderService.getOrder(id, auth);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Page<OrderResponse> listAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return orderService.listAll(pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<OrderResponse> listMine(@PageableDefault(size = 20, sort = "id") Pageable pageable,
                                        Authentication auth) {
        return orderService.listMine(auth.getName(), pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
