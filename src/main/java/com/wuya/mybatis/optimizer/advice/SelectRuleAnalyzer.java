package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;

/**
 * SELECT规则分析器
 * @author wuya
 * @date 2020-08-01 15:07
 */
public class SelectRuleAnalyzer implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();
        String sql = explainResult.getSql().toUpperCase();

        if (sql.contains("SELECT *")) {
            adviceList.add("避免使用SELECT *，明确指定需要的列");
        }

        if (sql.contains("SELECT DISTINCT") && !sql.contains("WHERE")) {
            adviceList.add("无条件的DISTINCT查询可能导致性能问题");
        }

        return adviceList;
    }

    @Override
    public boolean supports(DatabaseType dbType) {
        return true;
    }
}