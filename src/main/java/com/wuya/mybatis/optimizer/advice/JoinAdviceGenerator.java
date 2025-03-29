package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JoinAdviceGenerator implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

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
    @Override
    public boolean supports(DatabaseType dbType) {
        return true;
    }
}