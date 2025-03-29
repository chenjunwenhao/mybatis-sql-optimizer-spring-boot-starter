package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostgreSQLAdviceGenerator implements SqlOptimizationAdvice {

    // 阈值常量
    private static final double HIGH_PLANNING_TIME_THRESHOLD = 10.0; // ms
    private static final long HIGH_BUFFER_READ_THRESHOLD = 1000L; // blocks
    private static final long HIGH_TEMP_USAGE_THRESHOLD = 100L; // blocks

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

    private void handleSeqScan(Map<String, Object> node, long actualRows, List<String> adviceList) {
        String relationName = String.valueOf(node.get("Relation Name"));

        adviceList.add("检测到全表扫描(Seq Scan)表: " + relationName +
                "，扫描行数: " + actualRows + "，建议添加合适索引");

        if (node.containsKey("Filter") && !"false".equals(String.valueOf(node.get("Filter")))) {
            adviceList.add("表 " + relationName + " 有未使用索引的过滤条件: " + node.get("Filter"));
        }
    }

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

    private void handleHashOperation(Map<String, Object> node, String nodeType,
                                     List<String> adviceList) {
        if (node.containsKey("Plan Width") &&
                Integer.parseInt(node.get("Plan Width").toString()) > 100) {
            adviceList.add(nodeType + " 操作处理宽行(宽度: " + node.get("Plan Width") +
                    " bytes)，建议减少查询字段");
        }

        if (node.containsKey("Hash Buckets")) {
            int buckets = Integer.parseInt(node.get("Hash Buckets").toString());
            int batches = node.containsKey("Hash Batches") ?
                    Integer.parseInt(node.get("Hash Batches").toString()) : 1;

            if (batches > 1) {
                adviceList.add("Hash操作使用了多批次(batches=" + batches + ")，建议增加work_mem");
            }
        }
    }

    private void handleParallelQuery(Map<String, Object> node, List<String> adviceList) {
        int workers = Integer.parseInt(node.get("Workers").toString());
        int plannedWorkers = node.containsKey("Workers Planned") ?
                Integer.parseInt(node.get("Workers Planned").toString()) : workers;

        if (workers < plannedWorkers) {
            adviceList.add("并行查询未获得足够工作进程(planned=" + plannedWorkers +
                    ", actual=" + workers + ")，检查max_worker_processes设置");
        }

        if (node.containsKey("Worker Number")) {
            adviceList.add("检测到并行查询执行，工作进程数: " + workers +
                    "，考虑调整max_parallel_workers_per_gather参数");
        }
    }

    private void checkConfigurationAdvice(SqlExplainResult result, List<String> adviceList) {
        // 根据缓冲区使用情况给出建议
        if (result.getSharedHitBlocks() != null && result.getSharedReadBlocks() != null) {
            long totalBufferAccess = result.getSharedHitBlocks() + result.getSharedReadBlocks();
            if (totalBufferAccess > 0) {
                double hitRatio = (double)result.getSharedHitBlocks() / totalBufferAccess;
                if (hitRatio < 0.9) {
                    adviceList.add(String.format("共享缓冲区命中率较低(%.2f%%)，建议增加shared_buffers", hitRatio*100));
                }
            }
        }

        // 临时文件使用建议
        if (result.getTempWrittenBlocks() != null && result.getTempWrittenBlocks() > 0) {
            adviceList.add("检测到临时文件使用(" + result.getTempWrittenBlocks() + " blocks)，建议增加work_mem参数");
        }

        // JIT编译建议
        if (result.getExplainResults().stream()
                .anyMatch(plan -> plan.containsKey("JIT") &&
                        "true".equals(String.valueOf(plan.get("JIT"))))) {
            adviceList.add("检测到JIT编译，复杂查询考虑调整jit_相关参数");
        }
    }

    @Override
    public boolean supports(DatabaseType dbType) {
        return dbType.equals(DatabaseType.POSTGRE);
    }
}
