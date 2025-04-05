package com.wuya.mybatis.optimizer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.wuya.mybatis.cache.CacheFactory;
import com.wuya.mybatis.exception.SqlOptimizerException;
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

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.wuya.mybatis.optimizer.helper.SqlHepler.shouldExplain;

@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlAnalysisInterceptor implements Interceptor, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisInterceptor.class);
    private final SqlOptimizerProperties properties;
    private final List<ExplainResultAnalyzer> analyzers;
    private final List<SqlOptimizationAdvice> adviceGenerators;
    private final List<SqlAnalysisReporter> reporters;
    private final AsyncSqlAnalysisExecutor asyncExecutor;
    private final Cache<String, SqlExplainResult> analysisCache;

    public SqlAnalysisInterceptor(SqlOptimizerProperties properties,
                                  List<ExplainResultAnalyzer> analyzers,
                                  List<SqlOptimizationAdvice> adviceGenerators,
                                  List<SqlAnalysisReporter> reporters, CacheFactory cacheFactory) {
        this.properties = properties;
        this.analyzers = analyzers;
        this.adviceGenerators = adviceGenerators != null ? adviceGenerators : Collections.emptyList();
        this.reporters = reporters;
        this.asyncExecutor = properties.isAsyncAnalysis() ?
                new AsyncSqlAnalysisExecutor(properties.getAsyncThreads(),properties.getAsyncQueueSize()) : null;
        this.analysisCache = cacheFactory.getCache();
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

        // 判断是否需要执行分析
        if (!shouldExplain(sql)) return;

        Runnable analysisTask = () -> {
            try (Connection connection = mappedStatement.getConfiguration()
                    .getEnvironment()
                    .getDataSource()
                    .getConnection()) {
                DatabaseType dbType = DatabaseType.fromUrl(connection.getMetaData().getURL());
                // 缓存分析结果
                Supplier<SqlExplainResult> sqlExplainResultSupplier = () -> {
                    try {
                         return analyzers.stream()
                                .filter(a -> a.getDatabaseType() == dbType)
                                .findFirst()
                                .orElseThrow(() -> new SqlOptimizerException("No analyzer found for database: " + dbType))
                                .analyze(connection, boundSql,invocation);
                    } catch (Exception e) {
                        throw new SqlOptimizerException("get SqlExplainResult fail message: ",e);
                    }
                };

                SqlExplainResult explainResult;
                if (analysisCache != null) {
                    explainResult = analysisCache.get(sql, k -> sqlExplainResultSupplier.get());
                } else {
                    explainResult = sqlExplainResultSupplier.get();
                }

                // 记录统计信息（可选）
                logCacheStats();
                // 设置执行时间
                Objects.requireNonNull(explainResult).setExecutionTime(executionTime);
                // 生成优化建议
                List<String> adviceList = adviceGenerators.stream()
                        .filter(advice -> advice.supports(dbType))
                        .flatMap(advice -> advice.generateAdvice(explainResult).stream())
                        .collect(Collectors.toList());
                explainResult.setAdviceList(adviceList);

                // 报告结果
                reporters.forEach(reporter -> reporter.report(explainResult, dbType,mappedStatement.getId()));
            } catch (Exception e) {
                throw new SqlOptimizerException("SQL分析失败", e);
            }
        };

        // 选择同步/异步执行分析
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
    private void logCacheStats() {
        if (analysisCache != null) {
            CacheStats stats = analysisCache.stats();
            logger.info("[Cache] {} | Size={}/{} | Hit={}% | Load={}({}ms) | Evict={}",
                    analysisCache.getClass().getSimpleName(),
                    analysisCache.estimatedSize(),
                    analysisCache.policy().eviction().map(Policy.Eviction::getMaximum).orElse(-1L),
                    String.format("%.1f", stats.hitRate() * 100),
                    stats.loadCount(),
                    String.format("%.2f", stats.averageLoadPenalty() / 1_000_000.0),
                    stats.evictionCount());
        }
    }
}