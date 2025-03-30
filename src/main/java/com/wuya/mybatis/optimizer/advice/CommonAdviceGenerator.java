package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用建议生成器，用于根据SQL解析结果生成优化建议
 * 它实现了SqlOptimizationAdvice接口，提供SQL性能优化的通用建议
 * @author chenjunwen
 * @date 2023-08-08
 */
public class CommonAdviceGenerator implements SqlOptimizationAdvice {

    /**
     * 根据SQL解析结果生成优化建议列表
     * 
     * @param explainResult SQL解析结果对象，包含SQL执行的详细信息
     * @return 优化建议列表，每个建议都是一个字符串
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        // 添加通用分析规则
        // 如果SQL执行时间超过5秒，则添加优化建议
        if (explainResult.getExecutionTime() > 5000) {
            adviceList.add("SQL执行时间超过5秒，建议优化");
        }

        return adviceList;
    }

    /**
     * 判断当前建议生成器是否支持指定的数据库类型
     * 
     * @param dbType 数据库类型枚举，表示不同的数据库
     * @return 布尔值，表示是否支持指定的数据库类型
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 通用建议生成器支持所有数据库类型
        return true;
    }
}
