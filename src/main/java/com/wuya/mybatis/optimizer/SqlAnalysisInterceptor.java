package com.wuya.mybatis.optimizer;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.*;

@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlAnalysisInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisInterceptor.class);

    private final SqlOptimizerProperties properties;
    private final List<SqlOptimizationAdvice> adviceGenerators;

    public SqlAnalysisInterceptor(SqlOptimizerProperties properties,
                                  List<SqlOptimizationAdvice> adviceGenerators) {
        this.properties = properties;
        this.adviceGenerators = adviceGenerators != null ? adviceGenerators : Collections.emptyList();
    }
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 只分析超过阈值的SQL或配置了explainAll
        if (properties.isExplainAll() || executionTime > properties.getThresholdMillis()) {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            String sql = boundSql.getSql();

            // 获取连接执行EXPLAIN
            SqlExplainResult explainResult = explainSql(invocation, sql);
            explainResult.setExecutionTime(executionTime);

            // 生成优化建议
            List<String> adviceList = new ArrayList<>();
            for (SqlOptimizationAdvice adviceGenerator : adviceGenerators) {
                adviceList.addAll(adviceGenerator.generateAdvice(explainResult));
            }
            explainResult.setAdviceList(adviceList);

            // 记录日志
            logExplainResult(explainResult);
        }

        return result;
    }

    private SqlExplainResult explainSql(Invocation invocation, String sql) throws Exception {
        SqlExplainResult result = new SqlExplainResult();
        result.setSql(sql);

        Executor executor = (Executor) invocation.getTarget();
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];

        Connection connection = executor.getTransaction().getConnection();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {

            List<Map<String, Object>> explainResults = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    row.put(columnName, rs.getObject(i));
                }
                explainResults.add(row);
            }
            result.setExplainResults(explainResults);
        }

        return result;
    }

    private void logExplainResult(SqlExplainResult result) {
        logger.info("SQL Analysis Report:");
        logger.info("SQL: {}", result.getSql());
        logger.info("Execution Time: {} ms", result.getExecutionTime());

        logger.info("EXPLAIN Results:");
        for (Map<String, Object> row : result.getExplainResults()) {
            row.forEach((k, v) -> logger.info("  {}: {}", k, v));
        }

        if (!result.getAdviceList().isEmpty()) {
            logger.info("Optimization Suggestions:");
            for (String advice : result.getAdviceList()) {
                logger.info("  - {}", advice);
            }
        } else {
            logger.info("No optimization suggestions.");
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 不需要从mybatis配置中获取属性
    }
}