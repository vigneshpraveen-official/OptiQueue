package com.optiqueue.service;

import com.optiqueue.dto.ProductDtos.CreateProductRequest;
import com.optiqueue.dto.ProductDtos.ProductResponse;
import com.optiqueue.dto.ProductDtos.RestockRequest;
import com.optiqueue.dto.ProductDtos.UpdateProductRequest;
import com.optiqueue.entity.Product;
import com.optiqueue.exception.ApiException;
import com.optiqueue.exception.NotFoundException;
import com.optiqueue.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id)));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU_TAKEN",
                    "SKU '%s' already exists".formatted(request.sku()));
        }
        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        product.setName(request.name());
        product.setPrice(request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse restock(Long id, RestockRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        product.setStockQuantity(product.getStockQuantity() + request.quantity());
        return ProductResponse.from(product);
    }
}
