package com.wuya.mybatis.optimizer.report;

import com.wuya.mybatis.optimizer.SqlAnalysisReporter;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAnalysisReporter implements SqlAnalysisReporter {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAnalysisReporter.class);

    @Override
    public void report(SqlExplainResult result, DatabaseType dbType) {
        logger.info("===== SQL分析报告 [{}] =====", dbType.getName());
        logger.info("SQL: {}", result.getSql());
        logger.info("执行时间: {}ms", result.getExecutionTime());

        logger.info("执行计划:");
        result.getExplainResults().forEach(row ->
                row.forEach((k, v) -> logger.info("  {}: {}", k, v)));

        if (!result.getAdviceList().isEmpty()) {
            logger.info("优化建议:");
            result.getAdviceList().forEach(advice -> logger.info("  - {}", advice));
        } else {
            logger.info("无优化建议");
        }
    }
}