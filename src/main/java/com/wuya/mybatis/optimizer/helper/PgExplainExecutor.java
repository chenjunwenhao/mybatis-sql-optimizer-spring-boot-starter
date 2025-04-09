package com.wuya.mybatis.optimizer.helper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;

import java.sql.*;
/**
 * PostgreSQL EXPLAIN执行器
 * @author chenjunwen
 * @version 1.0
 * @date 2021/08/01 09:09:09
 */
public class PgExplainExecutor {

    /**
     * 执行EXPLAIN并返回结果
     * @param connection 数据库连接
     * @param originalSql 原始SQL
     * @param boundSql MyBatis BoundSql对象
     * @param config MyBatis配置
     * @return 执行计划结果（JSON格式）
     */
    public static ResultSet executeExplain(Connection connection,
                                        String originalSql,
                                        BoundSql boundSql,
                                        Configuration config) throws SQLException {
        String explainSql = "EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) " + originalSql;

        try (PreparedStatement ps = connection.prepareStatement(explainSql)) {
            // 绑定所有参数
            PgParameterBinder.bindParameters(ps, boundSql, config);

            // 执行并获取结果
            try (ResultSet rs = ps.executeQuery()) {
                return rs;
            }
        }
    }
}
