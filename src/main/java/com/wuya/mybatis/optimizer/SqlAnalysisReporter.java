package com.wuya.mybatis.optimizer;

import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

public interface SqlAnalysisReporter {
    void report(SqlExplainResult result, DatabaseType dbType);
}
