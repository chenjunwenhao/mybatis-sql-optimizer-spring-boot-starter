package com.wuya.mybatis.optimizer.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.wuya.mybatis.optimizer.helper.PgExplainExecutor.executeExplain;

/**
 * PostgreSQL分析器实现
 * @author chenjunwen
 * @date 2020-08-05 16:09
 */
public class PostgreExplainResultAnalyzer implements ExplainResultAnalyzer {
    /**
     * 分析SQL执行计划
     *
     * @param connection 数据库连接
     * @param boundSql   MyBatis的BoundSql对象，包含SQL语句和参数
     * @param invocation
     * @return SqlExplainResult对象，包含分析结果
     * @throws Exception 执行SQL或解析结果时可能抛出的异常
     */
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql, Invocation invocation) throws Exception {

        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];



        String originalSql = boundSql.getSql();
        // 执行查询并返回结果
        ResultSet rs = executeExplain(connection, originalSql, boundSql, ms.getConfiguration());

        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);
        List<Map<String, Object>> explainResults = new ArrayList<>();
        while (rs.next()) {
            String jsonResult = rs.getString(1);

            // 解析JSON格式的EXPLAIN结果
            try {
                Map<String, Object> planMap = parseExplainJson(jsonResult);
                parse(result, jsonResult);
                explainResults.add(planMap);
            } catch (Exception e) {
                // 解析失败时保留原始JSON
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("EXPLAIN", jsonResult);
                explainResults.add(row);
            }
        }
        result.setExplainResults(explainResults);

        return result;
    }
    /**
     * 解析SQL执行计划的JSON表示，并将解析结果存储在SqlExplainResult对象中
     * 此方法主要负责解析JSON格式的SQL执行计划，并将解析出的信息填充到result对象中
     * 
     * @param result 用于存储解析结果的对象
     * @param jsonExplain SQL执行计划的JSON字符串表示
     * @throws Exception 当解析JSON过程中发生错误时抛出
     */
    private void parse(SqlExplainResult result, String jsonExplain) throws Exception {
        // 创建ObjectMapper实例，用于JSON解析
        ObjectMapper mapper = new ObjectMapper();
        // 将JSON字符串解析为JsonNode对象，便于后续处理
        JsonNode root = mapper.readTree(jsonExplain);
    
        // 提取并解析基础执行计划
        JsonNode plan = root.path(0).path("Plan");
        // 递归解析执行计划节点，并将结果存储在Map中
        Map<String, Object> planMap = parsePlanNode(plan);
        // 将解析后的执行计划添加到结果列表中
        result.getExplainResults().add(planMap);
    
        // 设置基础指标
        // 从执行计划中提取并设置实际总执行时间
        result.setExecutionTime(plan.path("Actual Total Time").asLong());
        // 从根节点中提取并设置查询计划时间
        result.setPlanningTime(root.path(0).path("Planning Time").asDouble());
    
        // 处理缓冲区信息
        JsonNode buffers = root.path(0).path("Buffers");
        // 如果缓冲区信息存在，则进一步解析并设置相关指标
        if (!buffers.isMissingNode()) {
            // 从缓冲区信息中提取并设置共享块命中数
            result.setSharedHitBlocks(buffers.path("Shared Hit Blocks").asLong());
            // 从缓冲区信息中提取并设置共享块读取数
            result.setSharedReadBlocks(buffers.path("Shared Read Blocks").asLong());
            // 从缓冲区信息中提取并设置临时块读取数
            result.setTempReadBlocks(buffers.path("Temp Read Blocks").asLong());
            // 从缓冲区信息中提取并设置临时块写入数
            result.setTempWrittenBlocks(buffers.path("Temp Written Blocks").asLong());
        }
    }

    /**
     * 解析给定的Plan节点并将其转换为一个映射
     * 该方法主要用于将JSON格式的Plan节点解析到一个Map中，以便于后续处理和访问
     * 它递归地解析节点及其子节点，并将相关信息存储在映射中
     *
     * @param node 代表Plan节点的JsonNode对象
     * @return 包含解析后信息的Map对象
     */
    private Map<String, Object> parsePlanNode(JsonNode node) {
        // 创建一个有序的映射来存储解析后的信息
        Map<String, Object> map = new LinkedHashMap<>();
    
        // 基础字段
        // 将节点类型、总成本、计划行数、实际行数和实际时间添加到映射中
        map.put("Node Type", node.path("Node Type").asText());
        map.put("Total Cost", node.path("Total Cost").asDouble());
        map.put("Plan Rows", node.path("Plan Rows").asInt());
        map.put("Actual Rows", node.path("Actual Rows").asInt());
        map.put("Actual Time", node.path("Actual Total Time").asDouble() + " ms");
    
        // 递归处理子节点
        // 如果当前节点有子节点，则递归调用parsePlanNode方法解析每个子节点
        if (node.has("Plans")) {
            List<Map<String, Object>> subPlans = new ArrayList<>();
            node.path("Plans").forEach(subPlan -> subPlans.add(parsePlanNode(subPlan)));
            map.put("Sub Plans", subPlans);
        }
    
        // 返回填充了解析信息的映射
        return map;
    }
    /**
     * 获取数据库类型
     * @return DatabaseType枚举值，表示支持的数据库类型
     */
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRE;
    }


    /**
     * 解析JSON格式的EXPLAIN输出
     * @param jsonResult JSON格式的EXPLAIN结果字符串
     * @return 解析后的Map对象，包含执行计划信息
     * @throws Exception 解析JSON时可能抛出的异常
     */
    private Map<String, Object> parseExplainJson(String jsonResult) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> planMap = mapper.readValue(jsonResult, new TypeReference<Map<String, Object>>() {});

        // 提取计划树并扁平化处理
        List<Map<String, Object>> planNodes = flattenPlanTree(planMap.get("Plan"));
        planMap.put("PlanNodes", planNodes);

        return planMap;
    }


    /**
     * 扁平化处理计划树结构
     * @param planNode 当前计划节点对象
     * @return 扁平化后的计划节点列表
     */
    private List<Map<String, Object>> flattenPlanTree(Object planNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (planNode instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) planNode;

            // 创建当前节点的扁平化副本
            Map<String, Object> flatNode = new LinkedHashMap<>(node);
            flatNode.remove("Plans"); // 移除子节点引用

            result.add(flatNode);

            // 递归处理子节点
            if (node.containsKey("Plans")) {
                Object plans = node.get("Plans");
                if (plans instanceof List) {
                    for (Object child : (List<?>) plans) {
                        result.addAll(flattenPlanTree(child));
                    }
                }
            }
        }
        return result;
    }
}
