package com.wuya.mybatis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.util.StringUtils;

/**
 * 缓存工厂类，用于创建缓存对象
 * @author chenjunwen
 * @date 2023-07-06
 */
public class CacheFactory {
    // 缓存配置属性
    private final CacheProperties properties;

    /**
     * 构造函数，初始化缓存工厂
     *
     * @param properties 缓存配置属性
     */
    public CacheFactory(CacheProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取缓存对象如果缓存未启用，返回null
     * 
     * @param <K> 缓存键的类型
     * @param <V> 缓存值的类型
     * @return 缓存对象或null
     */
    public <K, V> Cache<K, V> getCache() {
        return properties.isEnabled() ? createRealCache() : null;
    }

    /**
     * 创建真实的缓存对象根据配置属性初始化缓存
     * 
     * @param <K> 缓存键的类型
     * @param <V> 缓存值的类型
     * @return 缓存对象
     */
    private <K, V> Cache<K, V> createRealCache() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        // 如果配置了缓存规格Caffeine原生配置，直接从规格创建缓存
        if (StringUtils.hasText(properties.getSpec())) {
            return Caffeine.from(properties.getSpec()).build();
        }
        // 配置缓存的最大容量
        if (properties.getMaxSize() != null) {
            builder.maximumSize(properties.getMaxSize());
        }
        // 配置缓存的过期时间
        if (properties.getExpireTime() != null) {
            builder.expireAfterWrite(properties.getExpireTime());
        }
        // 是否记录缓存的统计信息
        if (properties.isRecordStats()) {
            builder.recordStats();
        }
        // 构建并返回缓存对象
        return builder.build();
    }
}
