package com.wuya.mybatis.optimizer.report;

import com.wuya.mybatis.optimizer.SqlAnalysisReporter;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的SQL分析报告生成器
 * 该类实现了SqlAnalysisReporter接口，用于生成和输出SQL执行的分析报告
 * @author chenjunwen
 * @date 2023-07-06
 */
public class DefaultAnalysisReporter implements SqlAnalysisReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultAnalysisReporter.class);

    /**
     * 生成并输出SQL分析报告
     *
     * @param result SQL解释结果，包含SQL执行的详细信息和分析结果
     * @param dbType 数据库类型，表明SQL执行的数据库环境
     * @param id SQL执行的标识符，用于标识SQL语句 mybatis的id
     */
    @Override
    public void report(SqlExplainResult result, DatabaseType dbType, String id) {
        // 输出SQL分析报告的标题和数据库类型
        logger.info("===== SQL分析报告 [{}:{}] =====", dbType.getName(),id);
        // 输出SQL语句和执行时间
        logger.info("SQL: {}", result.getSql());
        logger.info("执行时间: {}ms", result.getExecutionTime());

        // 输出执行计划，详细展示SQL执行的每一步
        logger.info("执行计划:");
        result.getExplainResults().forEach(row ->
                row.forEach((k, v) -> logger.info("  {}: {}", k, v)));

        // 根据是否有优化建议，输出优化建议列表或无优化建议提示
        if (!result.getAdviceList().isEmpty()) {
            logger.info("优化建议:");
            result.getAdviceList().forEach(advice -> logger.info("  - {}", advice));
        } else {
            logger.info("无优化建议");
        }
    }
}
