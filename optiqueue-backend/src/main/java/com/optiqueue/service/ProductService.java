package com.optiqueue.service;

import com.optiqueue.config.CacheConfig;
import com.optiqueue.dto.PageResponse;
import com.optiqueue.dto.ProductDtos.CreateProductRequest;
import com.optiqueue.dto.ProductDtos.ProductResponse;
import com.optiqueue.dto.ProductDtos.RestockRequest;
import com.optiqueue.dto.ProductDtos.UpdateProductRequest;
import com.optiqueue.entity.Product;
import com.optiqueue.exception.ApiException;
import com.optiqueue.exception.NotFoundException;
import com.optiqueue.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE,
            key = "'page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize + ':sort:' + #pageable.sort.toString()")
    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id")
    public ProductResponse get(Long id) {
        return ProductResponse.from(productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id)));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, allEntries = true)
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, allEntries = true)})
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        product.setName(request.name());
        product.setPrice(request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, allEntries = true)})
    public ProductResponse restock(Long id, RestockRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        product.setStockQuantity(product.getStockQuantity() + request.quantity());
        return ProductResponse.from(product);
    }
}
