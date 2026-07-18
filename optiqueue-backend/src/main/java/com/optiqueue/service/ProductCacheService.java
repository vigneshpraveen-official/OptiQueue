package com.optiqueue.service;

import com.optiqueue.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Programmatic cache eviction for flows where the products being touched are
 * only known at runtime (order placement / cancellation touch N products).
 */
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private final CacheManager cacheManager;

    public void evictProducts(Collection<Long> productIds) {
        Cache byId = cacheManager.getCache(CacheConfig.PRODUCT_CACHE);
        if (byId != null) {
            productIds.forEach(byId::evict);
        }
        Cache pages = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (pages != null) {
            pages.clear();   // stock shown in list pages changed → drop all pages
        }
    }
}
