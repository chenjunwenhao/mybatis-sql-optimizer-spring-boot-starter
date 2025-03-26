package com.wuya.mybatis.optimizer.analyzer;

import com.wuya.mybatis.optimizer.SqlAnalysisInterceptor;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import org.apache.ibatis.mapping.BoundSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OracleExplainResultAnalyzer implements ExplainResultAnalyzer{

    private static final Logger logger = LoggerFactory.getLogger(OracleExplainResultAnalyzer.class);

    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql) throws Exception {
        String sql = boundSql.getSql();

        SqlExplainResult result = new SqlExplainResult();
        result.setSql(sql);

        try (Statement stmt = connection.createStatement()) {
            // 1. 先清除可能存在的旧解释计划
            try {
                stmt.execute("DELETE FROM plan_table WHERE statement_id = 'MYBATIS_ANALYZER'");
            } catch (SQLException e) {
                // 忽略错误，plan_table可能不存在
            }

            // 2. 生成新的解释计划
            stmt.execute("EXPLAIN PLAN SET statement_id = 'MYBATIS_ANALYZER' FOR " + sql);

            // 3. 使用DBMS_XPLAN获取格式化的执行计划
            String query = "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY("
                    + "table_name => 'PLAN_TABLE', "
                    + "statement_id => 'MYBATIS_ANALYZER', "
                    + "format => 'ALL'))";
            List<Map<String, Object>> explainResults = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(
                    query)) {

                while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("PLAN_TABLE_OUTPUT", rs.getString(1));
                explainResults.add(row);
            }
        } catch (Exception e) {
                logger.error("Oracle执行计划分析失败，请确保有DBMS_XPLAN权限和PLAN_TABLE访问权", e);
            }
        result.setExplainResults(explainResults);
    }

        return result;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.ORACLE;
    }
}
