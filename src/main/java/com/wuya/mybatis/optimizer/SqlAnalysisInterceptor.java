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
import java.util.Collections;
import java.util.List;
import java.util.Properties;
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
    private final List<SqlAnalysisReporter> reporters;
    private final AsyncSqlAnalysisExecutor asyncExecutor;
    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisInterceptor.class);

    public SqlAnalysisInterceptor(SqlOptimizerProperties properties,
                                  List<ExplainResultAnalyzer> analyzers,
                                  List<SqlOptimizationAdvice> adviceGenerators,
                                  DataSource dataSource,
                                  List<SqlAnalysisReporter> reporters) {
        this.properties = properties;
        this.analyzers = analyzers;
        this.adviceGenerators = adviceGenerators != null ? adviceGenerators : Collections.emptyList();
        this.dataSource = dataSource;
        this.reporters = reporters;
        this.asyncExecutor = properties.isAsyncAnalysis() ?
                new AsyncSqlAnalysisExecutor(properties.getAsyncThreads(),properties.getAsyncQueueSize()) : null;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled() ||
                (properties.getSampleRate() < 1.0 &&
                        ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate())) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 只分析超过阈值的SQL或配置了explainAll
        if (properties.isExplainAll() || executionTime > properties.getThresholdMillis()) {
            analyzeSql(invocation, executionTime);
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

                reporters.forEach(reporter -> reporter.report(explainResult, dbType));
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


    @Override
    public void setProperties(Properties properties) {
        // 不需要从mybatis配置中获取属性
    }

}