package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;

/**
 * SELECT规则分析器
 * 该类用于分析SQL语句中的SELECT查询，并提供优化建议
 * 主要关注于SELECT查询中的一些常见性能问题，如使用SELECT *、无条件DISTINCT查询等
 * @author chenjunwen
 * @date 2020-08-01 15:07
 */
public class SelectAdviceGenerator implements SqlOptimizationAdvice {
    /**
     * 根据SQL解析结果生成优化建议
     * 该方法会检查SQL语句中是否包含一些可能影响性能的SELECT查询模式，
     * 并生成相应的优化建议列表
     * 
     * @param explainResult SQL解析结果，包含SQL语句及其相关信息
     * @return 优化建议列表，包含一个或多个建议字符串
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        // 初始化优化建议列表
        List<String> adviceList = new ArrayList<>();
        // 获取SQL语句，并转换为大写以进行不区分大小写的比较
        String sql = explainResult.getSql().toUpperCase();

        // 检查SQL语句中是否包含SELECT *模式
        if (sql.contains("SELECT *")) {
            // 如果包含，添加建议避免使用SELECT *，而应明确指定需要的列
            adviceList.add("避免使用SELECT *，明确指定需要的列");
        }

        // 检查SQL语句中是否包含SELECT DISTINCT且不包含WHERE子句的模式
        if (sql.contains("SELECT DISTINCT") && !sql.contains("WHERE")) {
            // 如果包含，添加建议指出无条件的DISTINCT查询可能导致性能问题
            adviceList.add("无条件的DISTINCT查询可能导致性能问题");
        }

        // 返回优化建议列表
        return adviceList;
    }

    /**
     * 指示该分析器是否支持指定的数据库类型
     * 该方法始终返回true，表示该分析器支持所有数据库类型
     * 
     * @param dbType 数据库类型，表示需要分析的数据库类型
     * @return 始终返回true，表示支持所有数据库类型
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 表示该分析器支持所有数据库类型
        return true;
    }
}
