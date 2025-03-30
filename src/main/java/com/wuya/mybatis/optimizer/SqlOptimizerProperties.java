package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置属性类，用于控制SQL优化器的行为
 * 包含了一系列配置项，如是否启用优化器、采样率、是否进行异步分析等
 * @author wuya
 * @date 2020-06-09 14:06
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "mybatis.optimizer")
public class SqlOptimizerProperties {
    // 是否启用SQL优化器
    private boolean enabled = true;
    // 是否解释所有SQL语句，无论其执行时间是否超过阈值
    private boolean explainAll = true;
    // 执行时间超过此阈值的SQL语句会被分析
    private long thresholdMillis = 100;

    // 是否分析MySQL索引使用情况
    private boolean mysqlIndex = true;
    // 是否分析PostgreSQL索引使用情况
    private boolean postgreIndex = true;
    // 是否分析WHERE子句
    private boolean analyzeWhereClauses = true;
    // 是否分析SELECT语句
    private boolean analyzeSelect = true;
    // 是否分析WHERE语句
    private boolean analyzeWhere = true;
    // 是否分析JOIN操作
    private boolean analyzeJoin = true;
    // 是否分析通用情况
    private boolean analyzeCommon = true;

    // 采样率，决定分析的SQL语句比例
    private double sampleRate = 1;

    // 是否进行异步分析
    private boolean asyncAnalysis = true;
    // 异步分析线程数
    private int asyncThreads = 2;
    // 异步分析队列大小
    private int asyncQueueSize = 1000;

    // 允许在WHERE子句中使用的函数白名单
    private Set<String> whereFunctionAllowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ABS", "ROUND", "FLOOR", "CEILING", "COALESCE", "NULLIF"
    )));

    /**
     * 返回允许在WHERE子句中使用的所有函数的大写不可变Set
     * 这个方法确保了函数名的标准化和不可变性
     * @return 大写不可变 Set
     */
    public Set<String> getAllowedFunctionsUpper() {
        return whereFunctionAllowed.stream()
                .map(String::toUpperCase)
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        Collections::unmodifiableSet
                ));
    }
}
