package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JOIN操作优化建议生成器
 * 这个类实现了SqlOptimizationAdvice接口，用于分析SQL执行计划，并生成针对JOIN操作的优化建议
 * @author chenjunwen
 * @date 2023-07-06 15:09:09
 */
public class JoinAdviceGenerator implements SqlOptimizationAdvice {

    /**
     * 根据SQL执行计划生成优化建议
     * 
     * @param explainResult SQL执行计划分析结果
     * @return 包含优化建议的列表
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        // 遍历SQL执行计划中的每一行
        for (Map<String, Object> row : explainResult.getExplainResults()) {
            // MySQL执行计划分析
            if ("ALL".equals(row.get("type"))) {
                adviceList.add("全表扫描JOIN操作检测到，考虑添加适当的索引");
            }

            // PostgreSQL执行计划分析
            if (row.containsKey("EXPLAIN") && row.get("EXPLAIN").toString().contains("Seq Scan")) {
                adviceList.add("顺序扫描JOIN操作检测到，考虑优化JOIN条件");
            }
        }

        return adviceList;
    }

    /**
     * 判断当前优化建议生成器是否支持指定的数据库类型
     * 
     * @param dbType 数据库类型
     * @return 如果支持返回true，否则返回false
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 目前这个优化建议生成器支持所有数据库类型
        return true;
    }
}
