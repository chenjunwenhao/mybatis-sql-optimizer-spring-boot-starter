package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WhereClauseAdviceGenerator implements SqlOptimizationAdvice {
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?i)(YEAR\\(|MONTH\\(|DATE\\(|UPPER\\(|LOWER\\()");

    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();
        String sql = explainResult.getSql().toUpperCase();

        // 检查WHERE子句中的函数使用
        Matcher matcher = FUNCTION_PATTERN.matcher(sql);
        while (matcher.find()) {
            adviceList.add("Function " + matcher.group(1) + " used in WHERE clause may prevent index usage");
        }

        // 检查LIKE以通配符开头
        if (sql.contains("LIKE '%") || sql.contains("LIKE \'%")) {
            adviceList.add("LIKE with leading wildcard prevents index usage, consider full-text search if needed");
        }

        // 检查OR条件
        if (sql.matches("(?i).*\\bWHERE\\b.*\\bOR\\b.*")) {
            adviceList.add("Multiple OR conditions in WHERE clause, consider using UNION ALL for better performance");
        }

        return adviceList;
    }
}