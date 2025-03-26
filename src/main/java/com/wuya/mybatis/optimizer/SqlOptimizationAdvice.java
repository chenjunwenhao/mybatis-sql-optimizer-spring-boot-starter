package com.wuya.mybatis.optimizer;

import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.List;

/**
 * 优化建议接口
 * @author wuya
 * @date 2019-08-01
 */
public interface SqlOptimizationAdvice {
    List<String> generateAdvice(SqlExplainResult explainResult);

    boolean supports(DatabaseType dbType);
}