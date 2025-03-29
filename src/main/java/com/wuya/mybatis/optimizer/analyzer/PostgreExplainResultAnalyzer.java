package com.wuya.mybatis.optimizer.analyzer;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.helper.SqlHepler;
import org.apache.ibatis.mapping.BoundSql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL分析器实现
 * @author wuya
 * @date 2020-08-05 16:09
 */
public class PostgreExplainResultAnalyzer implements ExplainResultAnalyzer {
    @Override
    public SqlExplainResult analyze(Connection connection, BoundSql boundSql) throws Exception {
        String originalSql = boundSql.getSql();
        // 执行查询并返回结果
        ResultSet rs = SqlHepler.getResult(connection, boundSql,"EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) ");
//        preparedStatement.executeQuery();

        SqlExplainResult result = new SqlExplainResult();
        result.setSql(originalSql);
        List<Map<String, Object>> explainResults = new ArrayList<>();
        while (rs.next()) {
            String jsonResult = rs.getString(1);

            // 解析JSON格式的EXPLAIN结果
            try {
                Map<String, Object> planMap = parseExplainJson(jsonResult);
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

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRE;
    }


    // 解析JSON格式的EXPLAIN输出
    private Map<String, Object> parseExplainJson(String jsonResult) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> planMap = mapper.readValue(jsonResult, new TypeReference<Map<String, Object>>() {});

        // 提取计划树并扁平化处理
        List<Map<String, Object>> planNodes = flattenPlanTree(planMap.get("Plan"));
        planMap.put("PlanNodes", planNodes);

        return planMap;
    }


    // 扁平化处理计划树结构
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