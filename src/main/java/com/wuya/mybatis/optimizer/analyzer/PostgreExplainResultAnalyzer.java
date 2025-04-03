package com.wuya.mybatis.optimizer.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.helper.SqlHepler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Invocation;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
