package com.wuya.mybatis.optimizer;

import java.util.List;

public interface SqlOptimizationAdvice {
    List<String> generateAdvice(SqlExplainResult explainResult);
}