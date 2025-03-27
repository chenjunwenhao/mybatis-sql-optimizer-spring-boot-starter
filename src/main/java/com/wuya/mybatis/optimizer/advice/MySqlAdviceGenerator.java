package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MySqlAdviceGenerator implements SqlOptimizationAdvice {
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        List<String> adviceList = new ArrayList<>();

        for (Map<String, Object> row : explainResult.getExplainResults()) {
            String type = String.valueOf(row.get("type"));
            String key = String.valueOf(row.get("key"));

            if ("ALL".equalsIgnoreCase(type) && "null".equals(key)) {
                adviceList.add("检测到全表扫描，建议为表 " + row.get("table") + " 添加索引");
            } else if ("index".equalsIgnoreCase(type)) {
                adviceList.add("检测到全索引扫描（索引：" + key + "），建议优化查询条件");
            } else if ("ref".equalsIgnoreCase(type) && "null".equals(key)) {
                adviceList.add("查询未使用任何索引，表：" + row.get("table"));
            }

            Object extra = row.get("Extra");
            if (extra != null) {
                if ("Using temporary".equals(String.valueOf(extra))) {
                    adviceList.add("检测到使用临时表，建议优化GROUP BY或ORDER BY子句");
                }
                if ("Using filesort".equals(String.valueOf(extra))) {
                    adviceList.add("检测到文件排序，建议为ORDER BY子句添加索引");
                }
            }

            if ("index_merge".equalsIgnoreCase(type)) {
                adviceList.add("检测到索引合并，表：" + row.get("table") + "，建议创建复合索引以获得更好性能");
            }

            if ("range".equalsIgnoreCase(type)) {
                adviceList.add("检测到范围扫描，索引：" + key + "，请检查范围是否过大");
            }

            if ("DEPENDENT SUBQUERY".equalsIgnoreCase(type) || "UNCACHEABLE SUBQUERY".equalsIgnoreCase(type)) {
                adviceList.add("检测到低效子查询，建议重写为JOIN操作");
            }

            if ("DERIVED".equalsIgnoreCase(type)) {
                adviceList.add("检测到派生表(FROM子句中的子查询)，建议简化查询");
            }
            // 1. 索引选择性检测
            if (row.containsKey("rows") && row.containsKey("filtered")) {
                long rows = Long.parseLong(String.valueOf(row.get("rows")));
                double filtered = Double.parseDouble(String.valueOf(row.get("filtered")));
                if (filtered > 50.0 && rows > 1000) {
                    adviceList.add("索引选择性不足，索引 " + key + " 过滤了" + filtered + "%数据，建议优化索引或查询条件");
                }
            }

            // 2. 大表JOIN检测
            if (row.containsKey("join_type") && row.containsKey("rows")) {
                long estimatedRows = Long.parseLong(String.valueOf(row.get("rows")));
                if (estimatedRows > 100000) {
                    adviceList.add("大表JOIN操作（估计行数：" + estimatedRows + "），建议考虑分页或优化JOIN策略");
                }
            }

            // 3. 未使用覆盖索引
            if ("Using index condition".equals(String.valueOf(extra))) {
                adviceList.add("未使用覆盖索引，查询需要回表操作，建议扩展索引包含所有查询字段");
            }
        }

        return adviceList;
    }
    @Override
    public boolean supports(DatabaseType dbType) {
        return dbType.equals(DatabaseType.MYSQL);
    }
}