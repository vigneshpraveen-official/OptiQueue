package com.optiqueue.controller;

import com.optiqueue.dto.PageResponse;
import com.optiqueue.dto.ProductDtos.CreateProductRequest;
import com.optiqueue.dto.ProductDtos.ProductResponse;
import com.optiqueue.dto.ProductDtos.RestockRequest;
import com.optiqueue.dto.ProductDtos.UpdateProductRequest;
import com.optiqueue.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return productService.list(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PutMapping("/{id}/restock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductResponse restock(@PathVariable Long id, @Valid @RequestBody RestockRequest request) {
        return productService.restock(id, request);
    }
}
