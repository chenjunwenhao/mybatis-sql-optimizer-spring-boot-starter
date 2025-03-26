package com.wuya.mybatis.optimizer.analyzer;


import com.wuya.mybatis.optimizer.SqlExplainResult;

import java.sql.Connection;

/**
 * 分析器接口
 * @author chenjunwen
 * @date 2020-09-02 16:08:04
 */
public interface ExplainResultAnalyzer {
    SqlExplainResult analyze(Connection connection, String sql) throws Exception;
    DatabaseType getDatabaseType();
}