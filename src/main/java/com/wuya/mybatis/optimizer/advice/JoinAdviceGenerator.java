package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JoinAdviceGenerator implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        for (Map<String, Object> row : explainResult.getExplainResults()) {
            String selectType = String.valueOf(row.get("select_type"));
            String type = String.valueOf(row.get("type"));

            if (selectType.contains("DEPENDENT SUBQUERY")) {
                adviceList.add("Dependent subquery detected, consider rewriting as JOIN");
            }

            if ("ALL".equalsIgnoreCase(type) && row.get("table") != null && row.get("table").toString().contains("JOIN")) {
                adviceList.add("Full table scan in JOIN operation detected, consider adding appropriate indexes");
            }
        }

        return adviceList;
    }
}