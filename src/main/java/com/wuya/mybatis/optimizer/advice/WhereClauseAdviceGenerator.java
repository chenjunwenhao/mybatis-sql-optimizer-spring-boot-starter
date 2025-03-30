package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.SqlOptimizerProperties;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.wuya.mybatis.optimizer.helper.SqlFunctionHelper.audit;

/**
 * WHERE子句优化建议生成器
 * 该类实现了SqlOptimizationAdvice接口，用于生成SQL优化建议
 * 主要关注WHERE子句中可能导致索引失效的条件，如LIKE通配符使用、函数使用、OR条件等
 * @author chenjunwen
 * @date 2023-07-06
 */
public class WhereClauseAdviceGenerator implements SqlOptimizationAdvice {

    // 允许在SQL中使用的上层函数集合
    private final Set<String> allowedFunctionsUpper;

    /**
     * 构造函数，通过@Autowired注解自动注入SqlOptimizerProperties
     * @param properties SqlOptimizerProperties配置类，用于获取允许的函数集合
     */
    @Autowired
    public WhereClauseAdviceGenerator(SqlOptimizerProperties properties) {
        this.allowedFunctionsUpper = properties.getAllowedFunctionsUpper();
    }

    /**
     * 生成SQL优化建议
     * 该方法分析SQL的WHERE子句，找出可能的问题，并生成相应的优化建议
     * @param explainResult SQL解析结果，包含SQL文本等信息
     * @return 优化建议列表，每个元素是一条优化建议
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();
        String sql = explainResult.getSql().toUpperCase();

        // 检查LIKE条件是否以通配符开头，如果是，则添加建议
        if (sql.contains("LIKE '%") || sql.contains("LIKE \'%")) {
            adviceList.add("LIKE条件以通配符开头，无法使用索引");
        }

        // WHERE条件中使用函数，可能导致索引失效
        try {
            List<String> audit = audit(sql, allowedFunctionsUpper);
            adviceList.addAll(audit);
        } catch (Exception e) {
            throw new RuntimeException("jsqlparser SQL分析失败", e);
        }

        // 检查是否存在多个OR条件，如果是，则建议使用UNION ALL优化
        if (sql.matches(".*\\bWHERE\\b.*\\bOR\\b.*")) {
            adviceList.add("多个OR条件，考虑使用UNION ALL优化");
        }

        return adviceList;
    }

    /**
     * 检查是否支持指定的数据库类型
     * 本优化建议生成器支持所有数据库类型
     * @param dbType 数据库类型
     * @return 总是返回true，表示支持所有数据库类型
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        return true;
    }
}
