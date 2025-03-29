package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;


public class CommonAdviceGenerator implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        // 添加通用分析规则
        if (explainResult.getExecutionTime() > 5000) {
            adviceList.add("SQL执行时间超过5秒，建议优化");
        }

        return adviceList;
    }

    @Override
    public boolean supports(DatabaseType dbType) {
        return true;
    }
}