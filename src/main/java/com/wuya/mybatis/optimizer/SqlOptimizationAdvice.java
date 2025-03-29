package com.wuya.mybatis.optimizer;

import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.List;

/**
 * 优化建议接口
 * @author wuya
 * @date 2019-08-01
 */
public interface SqlOptimizationAdvice {
    /**
     * 生成优化建议
     * @param explainResult
     * @return
     */
    List<String> generateAdvice(SqlExplainResult explainResult);
    /**
     * 是否支持该数据库类型
     * @param dbType
     * @return
     */
    boolean supports(DatabaseType dbType);
}