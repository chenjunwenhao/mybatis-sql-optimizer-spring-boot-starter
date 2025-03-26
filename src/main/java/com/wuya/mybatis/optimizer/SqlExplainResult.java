package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * SQL分析结果类
 * @author chenjunwen
 * @date 2019-07-09
 */
@Setter
@Getter
public class SqlExplainResult {
    private String sql;
    private List<Map<String, Object>> explainResults;
    private long executionTime;
    private List<String> adviceList;

}