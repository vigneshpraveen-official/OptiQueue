package com.optiqueue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache backend is selected by CACHE_TYPE env var (spring.cache.type):
 *   none   — caching disabled (early phases / benchmark "before" runs)
 *   simple — in-memory ConcurrentMap (local verification without Redis)
 *   redis  — Upstash/production (this class then applies TTL + JSON values)
 *
 * Cache names:
 *   products — pages of the product list  (evicted wholesale on any change)
 *   product  — single product by id       (evicted per-key)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";
    public static final String PRODUCT_CACHE = "product";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(
            @Value("${optiqueue.cache.ttl-seconds:300}") long ttlSeconds) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .prefixCacheNameWith("optiqueue:");
        return builder -> builder.cacheDefaults(config);
    }
}
