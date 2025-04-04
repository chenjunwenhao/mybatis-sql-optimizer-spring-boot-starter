package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JOIN操作优化建议生成器
 * 这个类实现了SqlOptimizationAdvice接口，用于分析SQL执行计划，并生成针对JOIN操作的优化建议
 * @author chenjunwen
 * @date 2023-07-06 15:09:09
 */
public class JoinAdviceGenerator implements SqlOptimizationAdvice {

    /**
     * 根据SQL执行计划生成优化建议
     * 
     * @param explainResult SQL执行计划分析结果
     * @return 包含优化建议的列表
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        // 遍历SQL执行计划中的每一行
        for (Map<String, Object> row : explainResult.getExplainResults()) {
            // MySQL执行计划分析
            if ("ALL".equals(row.get("type"))) {
                adviceList.add("全表扫描JOIN操作检测到，考虑添加适当的索引");
            }

            // PostgreSQL执行计划分析
            if (row.containsKey("EXPLAIN") && isProblematicJoin(row.get("EXPLAIN").toString())) {
                adviceList.addAll(analyzeJoinPerformance(row.get("EXPLAIN").toString()));
            }
        }

        return adviceList;
    }

    /**
     * 判断当前优化建议生成器是否支持指定的数据库类型
     * 
     * @param dbType 数据库类型
     * @return 如果支持返回true，否则返回false
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 目前这个优化建议生成器支持所有数据库类型
        return true;
    }

    /**
     * 判断是否是有问题的JOIN操作
     * - 检测逻辑需要区分：
     * - 驱动表（外表）的扫描方式
     * - 被驱动表（内表）是否有效使用索引
     * @param explainPlan
     * @return
     */
    boolean isProblematicJoin(String explainPlan) {
        return hasLargeTableSeqScan(explainPlan) ||
                hasInefficientNestedLoop(explainPlan);
    }

    /**
     * 判断是否有大表扫描
     * @param explainPlan
     * @return
     */
    boolean hasLargeTableSeqScan(String explainPlan) {
        // 使用正则提取执行计划中的扫描信息
        Pattern pattern = Pattern.compile(
                "Seq Scan on (\\w+).*rows=(\\d+).*" +
                        "->.*Index Scan on \\w+.*"  // 确保被驱动表用了索引
        );
        Matcher matcher = pattern.matcher(explainPlan);

        if (matcher.find()) {
            String tableName = matcher.group(1);
            int rows = Integer.parseInt(matcher.group(2));
            return rows > 10000;  // 自定义大表阈值
        }
        return false;
    }

    /**
     * 判断是否有inefficient nested loop
     * @param explainPlan
     * @return
     */
    boolean hasInefficientNestedLoop(String explainPlan) {
        // 当外层大表循环次数过多时，即使内表用索引也可能低效
        Pattern pattern = Pattern.compile(
                "Nested Loop.*loops=(\\d+).*" +
                        "->.*Seq Scan on (\\w+).*rows=(\\d+).*" +
                        "->.*Index Scan.*"
        );
        Matcher matcher = pattern.matcher(explainPlan);

        if (matcher.find()) {
            int loops = Integer.parseInt(matcher.group(1));
            int rows = Integer.parseInt(matcher.group(3));
            return loops * rows > 10000;  // 总处理行数阈值
        }
        return false;
    }

    /**
     * 分析JOIN性能
     * @param explainPlan
     * @return
     */
    public List<String> analyzeJoinPerformance(String explainPlan) {
        List<String> adviceList = new ArrayList<>();

        // 规则1：大表作为驱动表且 Seq Scan
        if (hasLargeTableSeqScan(explainPlan)) {
            adviceList.add(
                    "🚨 驱动表使用顺序扫描且数据量大："+
                    "1. 考虑为驱动表添加条件索引"+
                    " 2. 改用 Hash Join 或 Merge Join"+
                    "3. 执行 ANALYZE 更新统计信息");
        }

        // 规则2：低效嵌套循环
        if (hasInefficientNestedLoop(explainPlan)) {
            adviceList.add(
          "  🚨 检测到高成本嵌套循环："+
          "  1. 设置 enable_nestloop=off 强制使用其他JOIN算法"+
          "  2. 增大 work_mem 提升 Hash Join 性能"+
          "  3. 检查连接条件的数据类型是否匹配");
        }

        // 规则3：缺失JOIN条件索引（补充检测）
        if (explainPlan.contains("Hash Join") &&
                explainPlan.contains("Seq Scan")) {
            adviceList.add(
           " ℹ️ Hash Join 需要全表扫描："+
           " 如果这是高频查询，考虑添加索引改用 Nested Loop");
        }

        return adviceList;
    }
}
