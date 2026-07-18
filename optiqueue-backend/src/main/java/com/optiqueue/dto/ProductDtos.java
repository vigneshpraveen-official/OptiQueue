package com.optiqueue.dto;

import com.optiqueue.entity.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductDtos {

    public record CreateProductRequest(
            @NotBlank @Size(max = 50) String sku,
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            @Min(0) int stockQuantity
    ) {}

    public record UpdateProductRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price
    ) {}

    public record RestockRequest(
            @Min(1) int quantity
    ) {}

    public record ProductResponse(
            Long id, String sku, String name, BigDecimal price, int stockQuantity
    ) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getStockQuantity());
        }
    }
}
