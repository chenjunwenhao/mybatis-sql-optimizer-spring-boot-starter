package com.wuya.mybatis.optimizer;

import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

/**
 * SQL分析报告生成器接口
 * 实现该接口的类能够接收SQL解释结果和数据库类型，生成相应的分析报告
 * @author chenjunwen
 * @date 2019-07-09
 */
public interface SqlAnalysisReporter {
    /**
     * 生成SQL分析报告的方法
     *
     * @param result SQL解释结果，包含SQL执行计划的解析信息
     * @param dbType 数据库类型，指示当前SQL查询所针对的数据库系统
     * @param id mybatis配置文件中的id，用于区分不同的SQL查询
     */
    void report(SqlExplainResult result, DatabaseType dbType, String id);
}
