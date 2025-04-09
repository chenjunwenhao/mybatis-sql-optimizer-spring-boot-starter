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

/**
 * SQL分析拦截器实现类
 * 实现了Interceptor和DisposableBean接口，用于拦截MyBatis操作并进行SQL性能分析
 * @author chenjunwen
 * @date 2023-07-06 15:08
 */
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

    // SQL优化属性配置
    private final SqlOptimizerProperties properties;
    // SQL解释结果分析器列表
    private final List<ExplainResultAnalyzer> analyzers;
    // SQL优化建议生成器列表
    private final List<SqlOptimizationAdvice> adviceGenerators;
    // SQL分析报告器列表
    private final List<SqlAnalysisReporter> reporters;
    // 异步SQL分析执行器
    private final AsyncSqlAnalysisExecutor asyncExecutor;
    // SQL分析缓存
    private final Cache<String, SqlExplainResult> analysisCache;

    /**
     * 构造函数
     *
     * @param properties SQL优化属性配置
     * @param analyzers SQL解释结果分析器列表
     * @param adviceGenerators SQL优化建议生成器列表
     * @param reporters SQL分析报告器列表
     * @param cacheFactory 缓存工厂，用于创建SQL分析缓存
     */
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

    /**
     * 拦截MyBatis操作，对SQL进行分析
     *
     * @param invocation MyBatis拦截器调用对象
     * @return 操作结果
     * @throws Throwable 拦截处理异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 判断是否启用SQL分析以及是否满足采样率条件
        if (!properties.isEnabled() ||
                (properties.getSampleRate() < 1.0 &&
                        ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate())) {
            return invocation.proceed();
        }

        // 记录SQL执行开始时间
        long startTime = System.currentTimeMillis();
        // 执行MyBatis操作并获取结果
        Object result = invocation.proceed();
        // 记录SQL执行结束时间
        long endTime = System.currentTimeMillis();
        // 计算SQL执行时间
        long executionTime = endTime - startTime;

        // 只分析超过阈值的SQL或配置了explainAll
        if (properties.isExplainAll() || executionTime > properties.getThresholdMillis()) {
            analyzeSql(invocation, executionTime);
        }

        return result;
    }

    /**
     * 分析SQL性能并生成优化建议
     *
     * @param invocation MyBatis拦截器调用对象
     * @param executionTime SQL执行时间
     */
    private void analyzeSql(Invocation invocation, long executionTime) {
        // 获取MappedStatement对象
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        // 获取参数对象
        Object parameter = invocation.getArgs()[1];
        // 获取BoundSql对象
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        // 获取SQL语句
        String sql = boundSql.getSql();

        // 判断是否需要执行分析
        if (!shouldExplain(sql)) return;

        // 定义SQL分析任务
        Runnable analysisTask = () -> {
            try (Connection connection = mappedStatement.getConfiguration()
                    .getEnvironment()
                    .getDataSource()
                    .getConnection()) {
                // 获取数据库类型
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

                // 获取分析结果，是否从缓存中获取
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

    /**
     * 销毁方法，用于释放资源
     * 在Spring容器关闭时调用
     */
    @Override
    public void destroy() throws Exception {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }
    }

    /**
     * 创建并返回一个代理对象
     *
     * @param target 目标对象
     * @return 代理对象
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 设置属性，不需要从MyBatis配置中获取属性
     *
     * @param properties MyBatis配置属性
     */
    @Override
    public void setProperties(Properties properties) {
        // 不需要从mybatis配置中获取属性
    }

    /**
     * 记录缓存统计信息
     */
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
