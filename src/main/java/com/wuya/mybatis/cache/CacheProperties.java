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
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mybatis.optimizer.cache")
public class CacheProperties {
    private boolean enabled = true;
    private String spec = "maximumSize=1000,expireAfterWrite=1h,recordStats"; // Caffeine原生配置字符串
    private Integer maxSize = 1000;
    private Duration expireTime = Duration.ofHours(1);
    private boolean recordStats = true;
}
