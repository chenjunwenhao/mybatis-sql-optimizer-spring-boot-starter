package com.wuya.mybatis.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 *
 cache:
 enabled: true  # 是否启用缓存
 spec: "maximumSize=1000,expireAfterWrite=1h,recordStats" # Caffeine原生配置
 # 或分项配置：
 # max-size: 1000
 # expire-time: 1h
 # record-stats: true
 * @author chenjunwen
 * @date 2023-07-07 14:06
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mybatis.optimizer.cache")
public class CacheProperties {
    /**
     * 表示是否启用缓存，默认值为 true。
     */
    private boolean enabled = true;
    
    /**
     * Caffeine 原生配置字符串，定义缓存的最大大小、过期时间和是否记录统计信息等。
     */
    private String spec = "maximumSize=1000,expireAfterWrite=1h,recordStats";
    
    /**
     * 缓存的最大大小，默认值为 1000。
     */
    private Integer maxSize = 1000;
    
    /**
     * 缓存的过期时间，默认值为 1 小时。
     */
    private Duration expireTime = Duration.ofHours(1);
    
    /**
     * 表示是否记录缓存操作的统计信息，默认值为 true。
     */
    private boolean recordStats = true;
}
