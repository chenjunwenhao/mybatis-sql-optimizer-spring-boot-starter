package com.wuya.mybatis.optimizer;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * SQL分析结果类
 * 该类用于存储SQL语句的解析结果，包括SQL执行计划、执行时间以及优化建议等信息
 * @author chenjunwen
 * @date 2019-07-09
 */
@Setter
@Getter
public class SqlExplainResult {
    /**
     * SQL语句
     * 用于记录原始的SQL语句
     */
    private String sql;

    /**
     * 解析结果列表
     * 包含SQL执行计划的详细信息，格式为键值对
     */
    private List<Map<String, Object>> explainResults;

    /**
     * SQL语句的执行时间（毫秒）
     * 用于衡量SQL语句的执行效率
     */
    private long executionTime;

    /**
     * 优化建议列表
     * 包含针对当前SQL语句的性能优化建议
     */
    private List<String> adviceList;

    // PostgreSQL特有指标
    /**
     * 计划时间（ms）
     * PostgreSQL特有的计划时间，用于衡量SQL执行计划的生成时间
     */
    private Double planningTime;  

    /**
     * 共享缓冲区命中块数
     * 表示从共享缓冲区中直接读取的数据块数量，命中率高有助于提升性能
     */
    private Long sharedHitBlocks; 

    /**
     * 共享缓冲区读取块数
     * 表示需要从磁盘读取并加载到共享缓冲区的数据块数量，读取量大可能影响性能
     */
    private Long sharedReadBlocks; 

    /**
     * 临时文件读取块数
     * 表示SQL执行过程中从临时文件中读取的数据块数量，使用临时文件通常会降低性能
     */
    private Long tempReadBlocks;  

    /**
     * 临时文件写入块数
     * 表示SQL执行过程中写入到临时文件的数据块数量，写入量大可能影响性能
     */
    private Long tempWrittenBlocks; 
}
