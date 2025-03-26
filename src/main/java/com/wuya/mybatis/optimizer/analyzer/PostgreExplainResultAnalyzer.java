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
 * PostgreSQL分析器实现
 * @author wuya
 * @date 2020-08-05 16:09
 */
public class PostgreExplainResultAnalyzer implements ExplainResultAnalyzer {
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql) throws Exception {
        String originalSql = boundSql.getSql();
        // 执行查询并返回结果
        ResultSet rs = SqlHepler.getResult(connection, boundSql,"EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) ");
//        preparedStatement.executeQuery();

        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);
        List<Map<String, Object>> explainResults = new ArrayList<>();
        while (rs.next()) {
            String jsonResult = rs.getString(1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("EXPLAIN", jsonResult);
            explainResults.add(row);
        }
        result.setExplainResults(explainResults);

        return result;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRE;
    }
}