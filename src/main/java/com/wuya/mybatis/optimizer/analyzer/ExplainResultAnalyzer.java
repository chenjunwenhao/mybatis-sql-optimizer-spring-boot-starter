package com.wuya.mybatis.optimizer.analyzer;


import com.wuya.mybatis.optimizer.SqlExplainResult;
import org.apache.ibatis.mapping.BoundSql;

import java.sql.Connection;

/**
 * 分析器接口
 * 用于定义分析SQL执行计划和获取数据库类型的操作
 * 
 * @author chenjunwen
 * @date 2020-09-02 16:08:04
 */
public interface ExplainResultAnalyzer {
    /**
     * 分析给定SQL的执行计划
     * 
     * @param connection 数据库连接，用于执行SQL和获取数据库信息
     * @param boundSql 包含执行SQL所需的所有数据的对象
     * @return SqlExplainResult对象，包含SQL的执行计划分析结果
     * @throws Exception 如果分析过程中发生错误，则抛出异常
     */
    SqlExplainResult analyze(Connection connection, BoundSql boundSql) throws Exception;
    
    /**
     * 获取当前分析器支持的数据库类型
     * 
     * @return DatabaseType枚举值，表示支持的数据库类型
     */
    DatabaseType getDatabaseType();
}
