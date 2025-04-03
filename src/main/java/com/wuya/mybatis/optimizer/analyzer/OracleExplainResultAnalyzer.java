package com.wuya.mybatis.optimizer.analyzer;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Invocation;
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

/**
 * Oracle数据库执行计划分析器
 * 该类实现了ExplainResultAnalyzer接口，用于分析Oracle数据库的SQL执行计划
 * @author chenjunwen
 * @date 2023-07-07
 */
public class OracleExplainResultAnalyzer implements ExplainResultAnalyzer{

    private static final Logger logger = LoggerFactory.getLogger(OracleExplainResultAnalyzer.class);

    /**
     * 分析SQL语句的执行计划
     *
     * @param connection 数据库连接对象，用于执行SQL语句
     * @param boundSql   包含SQL语句的信息
     * @param invocation
     * @return SqlExplainResult对象，包含分析结果
     * @throws Exception 如果分析过程中发生错误，则抛出异常
     */
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql, Invocation invocation) throws Exception {
        // 获取SQL语句
        String sql = boundSql.getSql();

        // 创建SqlExplainResult对象，用于存储分析结果
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

    /**
     * 获取数据库类型
     * 
     * @return DatabaseType 枚举值，表示Oracle数据库
     */
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.ORACLE;
    }
}
