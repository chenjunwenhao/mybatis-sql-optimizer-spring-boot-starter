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


    // PostgreSQL特有指标
    private Double planningTime;  // 计划时间(ms)
//    private Double executionTime; // 执行时间(ms)
    private Long sharedHitBlocks; // 共享缓冲区命中块数
    private Long sharedReadBlocks; // 共享缓冲区读取块数
    private Long tempReadBlocks;  // 临时文件读取块数
    private Long tempWrittenBlocks; // 临时文件写入块数

}