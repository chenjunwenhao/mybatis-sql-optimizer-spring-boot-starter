package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置属性类
 * @author wuya
 * @date 2020-06-09 14:06
 * mybatis:
 *   optimizer:
 *     enabled: true
 *     sampling-rate: 0.3
 *     async-analysis: true
 *     threshold-millis: 50
 *     analyze-select: true
 *     analyze-where: true
 *     analyze-join: true
 *     explain-all: true
 *     where-function-allowed: # 覆盖默认白名单
 *       - "ABS"
 *       - "ROUND"
 *       - "CONVERT"
 *       - "CAST"
 *
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "mybatis.optimizer")
public class SqlOptimizerProperties {
    // getters and setters
    private boolean enabled = true;
    private boolean explainAll = false; // 是否解释所有SQL
    private long thresholdMillis = 100; // 超过此阈值的SQL才会被分析
    private boolean suggestIndex = true;
    private boolean analyzeJoins = true;
    private boolean analyzeWhereClauses = true;

    private boolean analyzeSelect = true;
    private boolean analyzeWhere = true;
    private boolean analyzeJoin = true;
    private double samplingRate = 0.2;
    private boolean asyncAnalysis = true;
    private int asyncThreads = 2;
    private int asyncQueueSize = 1000;

    private Set<String> whereFunctionAllowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ABS", "ROUND", "FLOOR", "CEILING", "COALESCE", "NULLIF"
    )));;
}