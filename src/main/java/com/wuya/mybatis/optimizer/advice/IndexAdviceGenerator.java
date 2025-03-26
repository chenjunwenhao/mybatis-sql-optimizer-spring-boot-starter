package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class IndexAdviceGenerator implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        for (Map<String, Object> row : explainResult.getExplainResults()) {
            String type = String.valueOf(row.get("type"));
            String key = String.valueOf(row.get("key"));

            if ("ALL".equalsIgnoreCase(type) && key == null) {
                adviceList.add("Consider adding an index for full table scan on table: " + row.get("table"));
            } else if ("index".equalsIgnoreCase(type)) {
                adviceList.add("Full index scan detected on index: " + key + ", consider optimizing query conditions");
            }
        }

        return adviceList;
    }
    @Override
    public boolean supports(DatabaseType dbType) {
        return dbType.equals(DatabaseType.MYSQL);
    }
}