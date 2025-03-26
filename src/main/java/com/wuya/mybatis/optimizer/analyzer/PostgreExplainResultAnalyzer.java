package com.wuya.mybatis.optimizer.analyzer;


import com.wuya.mybatis.optimizer.SqlExplainResult;

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
    public SqlExplainResult analyze(Connection connection, String sql) throws Exception {
        SqlExplainResult result = new SqlExplainResult();
        result.setSql(sql);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) " + sql)) {

            List<Map<String, Object>> explainResults = new ArrayList<>();
            while (rs.next()) {
                String jsonResult = rs.getString(1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("EXPLAIN", jsonResult);
                explainResults.add(row);
            }
            result.setExplainResults(explainResults);
        }

        return result;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRE;
    }
}