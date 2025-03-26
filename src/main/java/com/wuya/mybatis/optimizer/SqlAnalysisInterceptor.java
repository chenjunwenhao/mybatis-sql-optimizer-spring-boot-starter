package com.wuya.mybatis.optimizer;

import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import com.wuya.mybatis.optimizer.analyzer.ExplainResultAnalyzer;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlAnalysisInterceptor implements Interceptor, DisposableBean {
    private final SqlOptimizerProperties properties;
    private final List<ExplainResultAnalyzer> analyzers;
    private final List<SqlOptimizationAdvice> adviceGenerators;
    private final DataSource dataSource;
    private final SqlAnalysisReporter reporter;
    private final AsyncSqlAnalysisExecutor asyncExecutor;
    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisInterceptor.class);

    public SqlAnalysisInterceptor(SqlOptimizerProperties properties,
                                  List<ExplainResultAnalyzer> analyzers,
                                  List<SqlOptimizationAdvice> adviceGenerators,
                                  DataSource dataSource,
                                  SqlAnalysisReporter reporter) {
        this.properties = properties;
        this.analyzers = analyzers;
        this.adviceGenerators = adviceGenerators != null ? adviceGenerators : Collections.emptyList();
        this.dataSource = dataSource;
        this.reporter = reporter;
        this.asyncExecutor = properties.isAsyncAnalysis() ?
                new AsyncSqlAnalysisExecutor(properties.getAsyncThreads()) : null;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled() ||
                (properties.getSamplingRate() < 1.0 &&
                        ThreadLocalRandom.current().nextDouble() >= properties.getSamplingRate())) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 只分析超过阈值的SQL或配置了explainAll
        if (properties.isExplainAll() || executionTime > properties.getThresholdMillis()) {
            analyzeSql(invocation, executionTime);
//            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
//            Object parameter = invocation.getArgs()[1];
//            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
//            String sql = boundSql.getSql();
//
//            // 获取连接执行EXPLAIN
//            SqlExplainResult explainResult = explainSql(invocation, sql);
//            explainResult.setExecutionTime(executionTime);
//
//            // 生成优化建议
//            List<String> adviceList = new ArrayList<>();
//            for (SqlOptimizationAdvice adviceGenerator : adviceGenerators) {
//                adviceList.addAll(adviceGenerator.generateAdvice(explainResult));
//            }
//            explainResult.setAdviceList(adviceList);
//
//            // 记录日志
//            logExplainResult(explainResult);
        }

        return result;
    }

    private void analyzeSql(Invocation invocation, long executionTime) {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        String sql = boundSql.getSql();
        Runnable analysisTask = () -> {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseType dbType = DatabaseType.fromUrl(connection.getMetaData().getURL());
                SqlExplainResult explainResult = analyzers.stream()
                        .filter(a -> a.getDatabaseType() == dbType)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No analyzer found for database: " + dbType))
                        .analyze(connection, boundSql);

                explainResult.setExecutionTime(executionTime);

                List<String> adviceList = adviceGenerators.stream()
                        .filter(advice -> advice.supports(dbType))
                        .flatMap(advice -> advice.generateAdvice(explainResult).stream())
                        .collect(Collectors.toList());
                explainResult.setAdviceList(adviceList);

                reporter.report(explainResult, dbType);
            } catch (Exception e) {
                throw new RuntimeException("SQL分析失败", e);
            }
        };

        if (properties.isAsyncAnalysis() && asyncExecutor != null) {
            asyncExecutor.submit(analysisTask);
        } else {
            analysisTask.run();
        }
    }
    @Override
    public void destroy() throws Exception {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
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
    public void setProperties(Properties properties) {
        // 不需要从mybatis配置中获取属性
    }

}