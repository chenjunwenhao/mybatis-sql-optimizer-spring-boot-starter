package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

}