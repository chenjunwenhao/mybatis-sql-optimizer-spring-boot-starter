package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class SqlExplainResult {
    // getters and setters
    private String sql;
    private List<Map<String, Object>> explainResults;
    private long executionTime;
    private List<String> adviceList;

}