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

//        if (SqlHepler.shouldExplain(boundSql.getSql())) {
        List<Map<String, Object>> maps = mybatisExplain(invocation);
//        }
        // 获取原始 SQL
        String originalSql = boundSql.getSql();
//
//        // 获取动态参数对象
//        Object parameterObject = boundSql.getParameterObject();
//        // 获取参数映射
//        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
//
//        // 在 SQL 前加上 EXPLAIN
//        String explainSql = "EXPLAIN " + originalSql;
//
//        // 创建 PreparedStatement 执行查询
//        PreparedStatement preparedStatement = connection.prepareStatement(explainSql);
//
//        // 如果参数对象不为 null，设置参数值
//        if (parameterObject != null) {
//            for (int i = 0; i < parameterMappings.size(); i++) {
//                ParameterMapping parameterMapping = parameterMappings.get(i);
//                String propertyName = parameterMapping.getProperty(); // 获取参数名称
//                Object value = SqlHepler.getParameterValue(parameterObject, propertyName); // 获取参数值
//
//                // 将参数值设置到 PreparedStatement 中
//                preparedStatement.setObject(i + 1, value);
//            }
//        }
//
//        // 执行查询并返回结果
//        ResultSet rs = preparedStatement.executeQuery();

        // 创建并填充SQL执行结果对象
        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);

        // 解析并存储执行计划的结果
//        List<Map<String, Object>> explainResults = new ArrayList<>();
//        ResultSetMetaData metaData = rs.getMetaData();
//        int colCount = metaData.getColumnCount();
//        // 标准表格格式(MySQL等)
//        while (rs.next()) {
//            Map<String, Object> row = new LinkedHashMap<>();
//            for (int i = 1; i <= colCount; i++) {
//                try {
//                    String colName = metaData.getColumnLabel(i);
//                    Object value = rs.getObject(i);
//                    row.put(colName, value != null ? value.toString() : null);
//                } catch (SQLException e) {
//                    row.put(metaData.getColumnName(i), "[ERROR]");
//                }
//            }
//            explainResults.add(row);
//        }
        result.setExplainResults(maps);
//        result.setExplainResults(explainResults);
        return result;
    }

    private List<Map<String, Object>> mybatisExplain(Invocation invocation) throws SQLException {

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        List<Map<String, Object>> explainResults = executeExplain(ms, parameter);

        return explainResults;
    }

    private List<Map<String, Object>> executeExplain(MappedStatement originalMs, Object parameter) throws SQLException {

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

//            analyzeExplainResults(originalMs.getId(), handler.getResults());
            return handler.getResults();
        } finally {
            closeResources(executor, tx);
        }
    }
    private void closeResources(Executor executor, Transaction tx) {
        if (executor != null) executor.close(false);
        try {
            if (tx != null) tx.close();
        } catch (SQLException e) {
            logger.debug("关闭Transaction失败", e);
        }
    }
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
    private MappedStatement createExplainMappedStatement(MappedStatement originalMs,
                                                         BoundSql explainBoundSql) {
        String id = originalMs.getId() + "-Explain";
        Configuration config = originalMs.getConfiguration();

        if (config.hasStatement(id)) {
            return config.getMappedStatement(id);
        }

        SqlSource sqlSource = new StaticSqlSource(
                config,
                explainBoundSql.getSql(),
                explainBoundSql.getParameterMappings()
        );

        MappedStatement.Builder builder = new MappedStatement.Builder(
                config, id, sqlSource, originalMs.getSqlCommandType())
                .resource(originalMs.getResource())
                .resultMaps(Collections.singletonList(
                        new ResultMap.Builder(config, id + "-ResultMap", HashMap.class,
                                Collections.emptyList()).build()))
                .flushCacheRequired(false)
                .useCache(false);

        MappedStatement explainMs = builder.build();
        config.addMappedStatement(explainMs);
        return explainMs;
    }
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
