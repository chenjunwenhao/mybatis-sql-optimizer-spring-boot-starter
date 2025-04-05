package com.wuya.mybatis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.util.StringUtils;

public class CacheFactory {
    private final CacheProperties properties;

    public CacheFactory(CacheProperties properties) {
        this.properties = properties;
    }

    // 延迟创建真实缓存（仅在启用时初始化）
    public <K, V> Cache<K, V> getCache() {
        return properties.isEnabled() ? createRealCache() : null;
    }

    private <K, V> Cache<K, V> createRealCache() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        if (StringUtils.hasText(properties.getSpec())) {
            return Caffeine.from(properties.getSpec()).build();
        }
        if (properties.getMaxSize() != null) {
            builder.maximumSize(properties.getMaxSize());
        }
        if (properties.getExpireTime() != null) {
            builder.expireAfterWrite(properties.getExpireTime());
        }
        if (properties.isRecordStats()) {
            builder.recordStats();
        }
        return builder.build();
    }
}
