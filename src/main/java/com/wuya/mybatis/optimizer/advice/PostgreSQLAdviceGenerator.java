package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 针对PostgreSQL的SQL优化建议生成器
 * 该类实现了SqlOptimizationAdvice接口，用于提供SQL性能优化的建议
 * @author chenjunwen
 * @date 2023-08-08
 */
public class PostgreSQLAdviceGenerator implements SqlOptimizationAdvice {

    /**
     * 高规划时间阈值，用于标识规划时间过长的情况
     * 单位：毫秒(ms)
     */
    private static final double HIGH_PLANNING_TIME_THRESHOLD = 10.0; // ms
    
    /**
     * 高缓冲区读取阈值，用于标识缓冲区读取量过大的情况
     * 单位：数据块(blocks)
     */
    private static final long HIGH_BUFFER_READ_THRESHOLD = 1000L; // blocks
    
    /**
     * 高临时使用阈值，用于标识临时存储使用量过大的情况
     * 单位：数据块(blocks)
     */
    private static final long HIGH_TEMP_USAGE_THRESHOLD = 100L; // blocks

    /**
     * 根据SQL解释结果生成优化建议列表
     * 
     * @param explainResult SQL的解释结果对象，包含执行计划和性能数据
     * @return 优化建议的字符串列表
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        // 1. 检查基础指标
        checkBasicMetrics(explainResult, adviceList);

        // 2. 分析执行计划树
        analyzePlanNodes(explainResult, adviceList);

        // 3. 检查配置相关建议
        checkConfigurationAdvice(explainResult, adviceList);

        return adviceList;
    }

    /**
     * 检查基础性能指标，并在必要时添加建议到列表中
     * 
     * @param result SQL解释结果对象
     * @param adviceList 保存优化建议的列表
     */
    private void checkBasicMetrics(SqlExplainResult result, List<String> adviceList) {
        // 计划时间分析
        if (result.getPlanningTime() != null && result.getPlanningTime() > HIGH_PLANNING_TIME_THRESHOLD) {
            adviceList.add(String.format("SQL计划时间过长(%.2fms)，建议检查统计信息是否最新(执行ANALYZE)",
                    result.getPlanningTime()));
        }

        // 执行时间分析
        if (result.getExecutionTime() != 0 && result.getPlanningTime() != null &&
                result.getExecutionTime() > 0 &&
                result.getPlanningTime() > result.getExecutionTime() * 0.2) {
            adviceList.add("计划时间占比较高(计划时间/执行时间=" +
                    String.format("%.2f", result.getPlanningTime()/result.getExecutionTime()) +
                    ")，建议优化复杂查询条件");
        }

        // 缓冲区分析
        if (result.getSharedReadBlocks() != null && result.getSharedReadBlocks() > HIGH_BUFFER_READ_THRESHOLD) {
            adviceList.add("检测到大量共享缓冲区读取(" + result.getSharedReadBlocks() + " blocks)，建议增加shared_buffers或优化查询");
        }

        // 临时文件分析
        if (result.getTempWrittenBlocks() != null && result.getTempWrittenBlocks() > HIGH_TEMP_USAGE_THRESHOLD) {
            adviceList.add("检测到大量临时文件使用(" + result.getTempWrittenBlocks() + " blocks)，建议增加work_mem参数");
        }
    }

    /**
     * 分析执行计划中的节点，并提供优化建议
     * 
     * @param result SQL解释结果对象
     * @param adviceList 保存优化建议的列表
     */
    private void analyzePlanNodes(SqlExplainResult result, List<String> adviceList) {
        if (result.getExplainResults() == null) return;

        for (Map<String, Object> plan : result.getExplainResults()) {
            if (!plan.containsKey("PlanNodes")) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) plan.get("PlanNodes");

            for (Map<String, Object> node : nodes) {
                String nodeType = String.valueOf(node.get("Node Type"));
                long actualRows = node.containsKey("Actual Rows") ?
                        Long.parseLong(node.get("Actual Rows").toString()) : 0;
                double actualTime = node.containsKey("Actual Total Time") ?
                        Double.parseDouble(node.get("Actual Total Time").toString()) : 0;

                // 顺序扫描分析
                if ("Seq Scan".equals(nodeType)) {
                    handleSeqScan(node, actualRows, adviceList);
                }
                // 索引扫描分析
                else if ("Index Scan".equals(nodeType) || "Index Only Scan".equals(nodeType)) {
                    handleIndexScan(node, nodeType, actualRows, adviceList);
                }
                // 连接操作分析
                else if (nodeType.contains("Join")) {
                    handleJoinOperation(node, nodeType, actualTime, adviceList);
                }
                // 排序操作分析
                else if ("Sort".equals(nodeType)) {
                    handleSortOperation(node, adviceList);
                }
                // 聚合操作分析
                else if ("Aggregate".equals(nodeType)) {
                    handleAggregateOperation(node, adviceList);
                }
                // 哈希操作分析
                else if ("Hash".equals(nodeType) || "Hash Join".equals(nodeType)) {
                    handleHashOperation(node, nodeType, adviceList);
                }
                // 并行查询分析
                else if (node.containsKey("Workers") &&
                        Integer.parseInt(node.get("Workers").toString()) > 0) {
                    handleParallelQuery(node, adviceList);
                }
            }
        }
    }

    /**
     * 处理顺序扫描节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param actualRows 实际扫描的行数
     * @param adviceList 保存优化建议的列表
     */
    private void handleSeqScan(Map<String, Object> node, long actualRows, List<String> adviceList) {
        String relationName = String.valueOf(node.get("Relation Name"));

        adviceList.add("检测到全表扫描(Seq Scan)表: " + relationName +
                "，扫描行数: " + actualRows + "，建议添加合适索引");

        if (node.containsKey("Filter") && !"false".equals(String.valueOf(node.get("Filter")))) {
            adviceList.add("表 " + relationName + " 有未使用索引的过滤条件: " + node.get("Filter"));
        }
    }

    /**
     * 处理索引扫描节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param nodeType 节点类型
     * @param actualRows 实际扫描的行数
     * @param adviceList 保存优化建议的列表
     */
    private void handleIndexScan(Map<String, Object> node, String nodeType, long actualRows,
                                 List<String> adviceList) {
        String indexName = String.valueOf(node.get("Index Name"));
        String relationName = String.valueOf(node.get("Relation Name"));

        if (actualRows > 10000) {
            adviceList.add(nodeType + " 扫描大量行(" + actualRows + ")，索引: " + indexName +
                    "，表: " + relationName + "，建议优化查询条件");
        }

        if ("Index Only Scan".equals(nodeType) && node.containsKey("Heap Fetches")) {
            long heapFetches = Long.parseLong(node.get("Heap Fetches").toString());
            if (heapFetches > 0) {
                adviceList.add("Index Only Scan 检测到 " + heapFetches + " 次堆取操作，索引: " +
                        indexName + "，建议执行VACUUM或增加索引包含列");
            }
        }
    }

    /**
     * 处理连接操作节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param nodeType 节点类型
     * @param actualTime 实际耗时
     * @param adviceList 保存优化建议的列表
     */
    private void handleJoinOperation(Map<String, Object> node, String nodeType,
                                     double actualTime, List<String> adviceList) {
        if (actualTime > 100.0) { // 超过100ms认为高成本
            adviceList.add("高成本连接操作(" + nodeType + ")，耗时: " +
                    String.format("%.2fms", actualTime) + "，建议检查连接条件");
        }

        if ("Nested Loop".equals(nodeType) &&
                node.containsKey("Inner Unique") &&
                !Boolean.parseBoolean(node.get("Inner Unique").toString())) {
            adviceList.add("Nested Loop连接检测到非唯一内表，可能导致性能问题");
        }
    }

    /**
     * 处理排序操作节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param adviceList 保存优化建议的列表
     */
    private void handleSortOperation(Map<String, Object> node, List<String> adviceList) {
        if (node.containsKey("Sort Key")) {
            adviceList.add("检测到排序操作，排序键: " + node.get("Sort Key") +
                    "，建议为这些字段创建索引");
        }

        if (node.containsKey("Sort Method") &&
                String.valueOf(node.get("Sort Method")).contains("external")) {
            adviceList.add("排序操作使用了磁盘临时文件，建议增加work_mem参数");
        }
    }

    /**
     * 处理聚合操作节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param adviceList 保存优化建议的列表
     */
    private void handleAggregateOperation(Map<String, Object> node, List<String> adviceList) {
        if ("HashAggregate".equals(node.get("Strategy"))) {
            adviceList.add("检测到Hash聚合操作，考虑调整hash_mem_multiplier参数");
        } else if ("SortedAggregate".equals(node.get("Strategy"))) {
            adviceList.add("检测到排序聚合操作，建议确保数据已正确排序");
        }

        if (node.containsKey("Group Key")) {
            adviceList.add("聚合操作使用分组键: " + node.get("Group Key") +
                    "，建议为这些字段创建索引");
        }
    }

    /**
     * 处理哈希操作节点的分析逻辑
     * 
     * @param node 执行计划节点
     * @param nodeType 节点类型
     * @param adviceList 保存优化建议的列表
     */
    private void handleHashOperation(Map<String, Object> node, String nodeType,
                                     List<String> adviceList) {
        // 提取 Plan Width 并解析为整数
        if (node.containsKey("Plan Width")) {
            try {
                int planWidth = Integer.parseInt(node.get("Plan Width").toString());
                if (planWidth > 100) {
                    adviceList.add(nodeType + " 操作处理宽行(宽度: " + planWidth +
                            " bytes)，建议减少查询字段");
                }
            } catch (NumberFormatException e) {
                // 记录日志，忽略非法值
                System.err.println("Invalid 'Plan Width' value: " + node.get("Plan Width"));
            }
        }
    
        // 提取 Hash Buckets 和 Hash Batches 并解析为整数
        if (node.containsKey("Hash Buckets")) {
            try {
//                int buckets = Integer.parseInt(node.get("Hash Buckets").toString());
                int batches = getBatchesValue(node);
    
                if (batches > 1) {
                    adviceList.add("Hash操作使用了多批次(batches=" + batches + ")，建议增加work_mem");
                }
            } catch (NumberFormatException e) {
                // 记录日志，忽略非法值
                System.err.println("Invalid 'Hash Buckets' or 'Hash Batches' value: " + 
                                   node.get("Hash Buckets") + ", " + node.get("Hash Batches"));
            }
        }
    }
    
    // 辅助方法：获取 Hash Batches 的值，默认为 1
    private int getBatchesValue(Map<String, Object> node) {
        if (node.containsKey("Hash Batches")) {
            try {
                return Integer.parseInt(node.get("Hash Batches").toString());
            } catch (NumberFormatException e) {
                // 记录日志，返回默认值
                System.err.println("Invalid 'Hash Batches' value: " + node.get("Hash Batches"));
            }
        }
        return 1; // 默认值
    }
/**
 * 处理并行查询情况，给出配置建议
 * 
 * @param node 包含并行查询相关信息的节点
 * @param adviceList 保存配置建议的列表
 */
private void handleParallelQuery(Map<String, Object> node, List<String> adviceList) {
    // 实际分配的工作进程数
    int workers = Integer.parseInt(node.get("Workers").toString());
    // 计划分配的工作进程数，如果没有设置，则使用实际分配数
    int plannedWorkers = node.containsKey("Workers Planned") ?
            Integer.parseInt(node.get("Workers Planned").toString()) : workers;

    // 如果实际工作进程数小于计划数，提示用户检查max_worker_processes设置
    if (workers < plannedWorkers) {
        adviceList.add("并行查询未获得足够工作进程(planned=" + plannedWorkers +
                ", actual=" + workers + ")，检查max_worker_processes设置");
    }

    // 如果指定了工作进程数，提示用户考虑调整max_parallel_workers_per_gather参数
    if (node.containsKey("Worker Number")) {
        adviceList.add("检测到并行查询执行，工作进程数: " + workers +
                "，考虑调整max_parallel_workers_per_gather参数");
    }
}

/**
 * 检查并给出数据库配置建议
 * 
 * @param result SQL解释结果对象，包含数据库性能相关信息
 * @param adviceList 保存配置建议的列表
 */
private void checkConfigurationAdvice(SqlExplainResult result, List<String> adviceList) {
    // 根据缓冲区使用情况给出建议
    if (result.getSharedHitBlocks() != null && result.getSharedReadBlocks() != null) {
        long totalBufferAccess = result.getSharedHitBlocks() + result.getSharedReadBlocks();
        if (totalBufferAccess > 0) {
            double hitRatio = (double)result.getSharedHitBlocks() / totalBufferAccess;
            // 如果共享缓冲区命中率低于90%，建议增加shared_buffers大小
            if (hitRatio < 0.9) {
                adviceList.add(String.format("共享缓冲区命中率较低(%.2f%%)，建议增加shared_buffers", hitRatio*100));
            }
        }
    }

    // 如果有临时文件被写入，建议增加work_mem参数
    if (result.getTempWrittenBlocks() != null && result.getTempWrittenBlocks() > 0) {
        adviceList.add("检测到临时文件使用(" + result.getTempWrittenBlocks() + " blocks)，建议增加work_mem参数");
    }

    // 如果检测到JIT编译，提示用户考虑调整jit_相关参数
    if (result.getExplainResults().stream()
            .anyMatch(plan -> plan.containsKey("JIT") &&
                    "true".equals(String.valueOf(plan.get("JIT"))))) {
        adviceList.add("检测到JIT编译，复杂查询考虑调整jit_相关参数");
    }
}

/**
 * 判断是否支持指定的数据库类型
 * 
 * @param dbType 数据库类型枚举
 * @return 如果支持PostgreSQL则返回true，否则返回false
 */
@Override
public boolean supports(DatabaseType dbType) {
    return dbType.equals(DatabaseType.POSTGRE);
}
}
