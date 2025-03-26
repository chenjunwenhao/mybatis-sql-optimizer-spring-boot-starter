package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WhereClauseAdviceGenerator implements SqlOptimizationAdvice {
    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("(?i)(YEAR\\(|MONTH\\(|DATE\\(|UPPER\\(|LOWER\\()");

    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();
        String sql = explainResult.getSql().toUpperCase();

        if (sql.contains("LIKE '%") || sql.contains("LIKE \'%")) {
            adviceList.add("LIKE条件以通配符开头，无法使用索引");
        }

        if (FUNCTION_PATTERN.matcher(sql).find()) {
            adviceList.add("WHERE条件中使用函数，可能导致索引失效");
        }

        if (sql.matches(".*\\bWHERE\\b.*\\bOR\\b.*")) {
            adviceList.add("多个OR条件，考虑使用UNION ALL优化");
        }

        return adviceList;
    }

    @Override
    public boolean supports(DatabaseType dbType) {
        return true;
    }
}