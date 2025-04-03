package com.wuya.mybatis.optimizer.analyzer;


import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.helper.ParameterWrapper;
import com.wuya.mybatis.optimizer.helper.SqlHepler;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyAccessorFactory;

import java.beans.PropertyDescriptor;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL分析器实现
 * 该类用于解析MySQL数据库中SQL语句的执行计划
 * @author wuya
 * @date 2020-06-09 16:09
 */
public class MysqlExplainResultAnalyzer implements ExplainResultAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MysqlExplainResultAnalyzer.class);

    /**
     * 分析SQL语句的执行计划
     *
     * @param connection 数据库连接对象，用于执行SQL语句
     * @param boundSql   包含SQL语句和参数信息的对象
     * @param invocation
     * @return 返回包含原始SQL和执行计划解析结果的对象
     * @throws Exception 执行过程中可能抛出的异常
     */
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql, Invocation invocation) throws Exception {

        // 执行EXPLAIN分析SQL语句，并返回分析结果
        List<Map<String, Object>> maps = mybatisExplain(invocation);
        
        // 获取原始 SQL
        String originalSql = boundSql.getSql();

        // 创建并填充SQL执行结果对象
        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);
        result.setExplainResults(maps);
        return result;
    }

    /**
     * 解析MyBatis查询语句并返回执行计划信息
     * 该方法主要用于内部调试和性能分析，通过执行查询的EXPLAIN形式来获取SQL语句的执行计划
     * 
     * @param invocation MyBatis拦截器中的调用对象，包含执行的查询信息和参数
     * @return 返回一个包含执行计划信息的列表，每条信息是一个键值对映射
     * @throws SQLException 如果执行过程中发生SQL异常
     */
    private List<Map<String, Object>> mybatisExplain(Invocation invocation) throws SQLException {
        // 从invocation中获取MappedStatement对象，它包含了映射信息和查询定义
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        // 获取查询参数，这可能是单个参数或者一个参数对象
        Object parameter = invocation.getArgs()[1];
        // 调用方法执行EXPLAIN查询，并将结果保存到explainResults列表中
        return executeExplain(ms, parameter);
    }

    /**
     * 执行EXPLAIN分析SQL语句，并返回分析结果
     * 此方法用于获取SQL执行计划，帮助开发者优化SQL性能
     * 
     * @param originalMs 原始的MappedStatement对象，包含映射信息
     * @param parameter 传递给SQL语句的参数对象
     * @return 返回一个包含EXPLAIN结果的列表，每个结果是一个键值对映射
     * @throws SQLException 如果执行SQL过程中发生错误
     */
    private List<Map<String, Object>> executeExplain(MappedStatement originalMs, Object parameter) throws SQLException {
    
        // 获取配置对象，用于创建事务和执行器
        Configuration config = originalMs.getConfiguration();
        Transaction tx = null;
        Executor executor = null;
    
        try {
            // 创建事务和Executor
            tx = config.getEnvironment().getTransactionFactory()
                    .newTransaction(config.getEnvironment().getDataSource(), null, false);
            executor = config.newExecutor(tx);
    
            // 创建增强版BoundSql
            BoundSql originalBoundSql = originalMs.getBoundSql(parameter);
            BoundSql explainBoundSql = createExplainBoundSql(config, originalBoundSql, parameter);
    
            // 创建EXPLAIN专用的MappedStatement
            MappedStatement explainMs = createExplainMappedStatement(originalMs, explainBoundSql);
    
            // 执行EXPLAIN
            ExplainResultHandler handler = new ExplainResultHandler();
            executor.query(explainMs, explainBoundSql.getParameterObject(),
                    RowBounds.DEFAULT, handler);
    
            return handler.getResults();
        } finally {
            // 关闭资源，确保事务和执行器被正确关闭
            closeResources(executor, tx);
        }
    }

    /**
     *  关闭Executor和Transaction资源
     * @param executor
     * @param tx
     */
    private void closeResources(Executor executor, Transaction tx) {
        if (executor != null) executor.close(false);
        try {
            if (tx != null) tx.close();
        } catch (SQLException e) {
            logger.debug("关闭Transaction失败", e);
        }
    }
    /**
     * 创建用于解释查询计划的BoundSql对象
     * 此方法基于原有的BoundSql对象生成一个新的BoundSql对象，新对象的SQL语句前添加了"EXPLAIN"关键字，
     * 用于获取SQL语句的执行计划信息这有助于在调试和优化查询性能时，分析数据库如何执行查询
     * 
     * @param config MyBatis配置对象，包含了MyBatis的全局配置信息
     * @param originalBoundSql 原始的BoundSql对象，包含原始的SQL语句和参数映射等信息
     * @param parameter 传递给SQL语句的参数，可能需要被包装以适应特定的参数处理需求
     * @return 返回一个新的BoundSql对象，其SQL语句为原始SQL语句前添加了"EXPLAIN"关键字，用于获取执行计划
     */
    private BoundSql createExplainBoundSql(Configuration config, BoundSql originalBoundSql, Object parameter) {
        // 包装参数
        Object wrappedParameter = ParameterWrapper.wrap(parameter);
    
        return new BoundSql(
                config,
                "EXPLAIN " + originalBoundSql.getSql(),
                originalBoundSql.getParameterMappings(),
                wrappedParameter
        );
    }
    /**
     * 创建用于解释查询的MappedStatement
     * 
     * @param originalMs 原始的MappedStatement对象，用于获取原始的映射信息
     * @param explainBoundSql 包含解释查询SQL和参数映射的BoundSql对象
     * @return 返回一个新的MappedStatement对象，用于执行解释查询
     * 
     * 此方法的目的是构建一个新的MappedStatement，专门用于执行带有EXPLAIN前缀的SQL查询，
     * 以便可以获取查询的执行计划信息这个新的MappedStatement不会使用缓存，并且配置了简单的ResultMap，
     * 以适应EXPLAIN查询返回的特定格式数据
     */
    private MappedStatement createExplainMappedStatement(MappedStatement originalMs,
                                                         BoundSql explainBoundSql) {
        // 生成新的MappedStatement的ID
        String id = originalMs.getId() + "-Explain";
        Configuration config = originalMs.getConfiguration();
    
        // 检查是否已经存在相同的MappedStatement，如果存在则直接返回
        if (config.hasStatement(id)) {
            return config.getMappedStatement(id);
        }
    
        // 创建SqlSource，用于生成解释查询的SQL
        SqlSource sqlSource = new StaticSqlSource(
                config,
                explainBoundSql.getSql(),
                explainBoundSql.getParameterMappings()
        );
    
        // 构建新的MappedStatement对象
        MappedStatement.Builder builder = new MappedStatement.Builder(
                config, id, sqlSource, originalMs.getSqlCommandType())
                .resource(originalMs.getResource())
                .resultMaps(Collections.singletonList(
                        new ResultMap.Builder(config, id + "-ResultMap", HashMap.class,
                                Collections.emptyList()).build()))
                .flushCacheRequired(false)
                .useCache(false);
    
        // 完成MappedStatement的构建并添加到配置中
        MappedStatement explainMs = builder.build();
        config.addMappedStatement(explainMs);
        return explainMs;
    }

    /**
     * 处理EXPLAIN结果
     * @author wuya
     */
    private static class ExplainResultHandler implements ResultHandler<Map<String, Object>> {
        private final List<Map<String, Object>> results = new ArrayList<>();

        @Override
        public void handleResult(ResultContext<? extends Map<String, Object>> resultContext) {
            results.add(new LinkedHashMap<>(resultContext.getResultObject()));
        }

        public List<Map<String, Object>> getResults() {
            return Collections.unmodifiableList(results);
        }
    }
    /**
     * 获取数据库类型
     * @return 返回数据库类型为MySQL
     */
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }
}
