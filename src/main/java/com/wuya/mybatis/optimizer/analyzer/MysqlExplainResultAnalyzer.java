package com.wuya.mybatis.optimizer.analyzer;


import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.helper.SqlHepler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL分析器实现
 * @author wuya
 * @date 2020-06-09 16:09
 */
public class MysqlExplainResultAnalyzer implements ExplainResultAnalyzer {
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql) throws Exception {
        // 获取原始 SQL
        String originalSql = boundSql.getSql();

        // 获取动态参数对象
        Object parameterObject = boundSql.getParameterObject();
        // 获取参数映射
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        // 在 SQL 前加上 EXPLAIN
        String explainSql = "EXPLAIN " + originalSql;

        // 创建 PreparedStatement 执行查询
        PreparedStatement preparedStatement = connection.prepareStatement(explainSql);

        // 如果参数对象不为 null，设置参数值
        if (parameterObject != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                String propertyName = parameterMapping.getProperty(); // 获取参数名称
                Object value = SqlHepler.getParameterValue(parameterObject, propertyName); // 获取参数值

                // 将参数值设置到 PreparedStatement 中
                preparedStatement.setObject(i + 1, value);
            }
        }

        // 执行查询并返回结果
        ResultSet rs = preparedStatement.executeQuery();

        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);

        List<Map<String, Object>> explainResults = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int colCount = metaData.getColumnCount();
        // 标准表格格式(MySQL等)
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                try {
                    String colName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(colName, value != null ? value.toString() : null);
                } catch (SQLException e) {
                    row.put(metaData.getColumnName(i), "[ERROR]");
                }
            }
            explainResults.add(row);
        }
        result.setExplainResults(explainResults);
        return result;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }
}