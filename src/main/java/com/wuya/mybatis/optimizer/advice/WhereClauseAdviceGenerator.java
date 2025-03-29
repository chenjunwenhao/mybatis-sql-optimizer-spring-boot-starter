package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.SqlOptimizerProperties;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.wuya.mybatis.optimizer.helper.SqlFunctionHelper.audit;

@Component
public class WhereClauseAdviceGenerator implements SqlOptimizationAdvice {

    @Resource
    private SqlOptimizerProperties properties;

    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();
        String sql = explainResult.getSql().toUpperCase();

        if (sql.contains("LIKE '%") || sql.contains("LIKE \'%")) {
            adviceList.add("LIKE条件以通配符开头，无法使用索引");
        }

        // WHERE条件中使用函数，可能导致索引失效
        try {
            Set<String> whereFunctionAllowed = properties.getWhereFunctionAllowed();
            List<String> audit = audit(sql,whereFunctionAllowed);
            adviceList.addAll(audit);
        } catch (Exception e) {
            throw new RuntimeException("jsqlparser SQL分析失败", e);
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