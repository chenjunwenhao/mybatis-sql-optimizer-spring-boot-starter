package com.wuya.mybatis.optimizer.advice;

import com.wuya.mybatis.optimizer.SqlExplainResult;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.analyzer.DatabaseType;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Limit 规则分析器
 * 该类用于分析SQL语句中的Limit查询，并提供优化建议
 * @author chenjunwen
 * @date 2020-08-01 15:07
 */
public class LimitAdviceGenerator implements SqlOptimizationAdvice {

    private static final Logger logger = LoggerFactory.getLogger(LimitAdviceGenerator.class);
    /**
     * 根据SQL解析结果生成优化建议
     * 并生成相应的优化建议列表
     * 
     * @param explainResult SQL解析结果，包含SQL语句及其相关信息
     * @return 优化建议列表，包含一个或多个建议字符串
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        // 初始化优化建议列表
        List<String> adviceList = new ArrayList<>();
        // 获取SQL语句，并转换为大写以进行不区分大小写的比较
        String sql = explainResult.getSql().toUpperCase();

        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select) {
                Select selectBody = ((Select) stmt).getSelectBody();

                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    Limit limit = plainSelect.getLimit();

                    if (limit != null) {
                        // 2. 检测深度分页
                        checkDeepOffset(limit, adviceList);

                        // 3. 检测不合理的 LIMIT 值
                        checkLimitValue(limit, adviceList);

                        // 4. 检测缺少 ORDER BY
                        checkMissingOrderBy(plainSelect, adviceList);

                        // 5. 检测硬编码 LIMIT
                        checkHardcodedLimit(limit, adviceList);
                    }
                }
            }
        } catch (JSQLParserException e) {
            logger.error("jsqlparser SQL分析失败", e);
        }

        // 返回优化建议列表
        return adviceList;
    }

    /**
     * 指示该分析器是否支持指定的数据库类型
     * 该方法始终返回true，表示该分析器支持所有数据库类型
     * 
     * @param dbType 数据库类型，表示需要分析的数据库类型
     * @return 始终返回true，表示支持所有数据库类型
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 表示该分析器支持所有数据库类型
        return true;
    }

    /**
     * 检查深度分页
     *
     * @param limit Limit对象，表示SQL语句中的LIMIT子句
     * @param adviceList 优化建议列表，用于添加新的优化建议
     */
    private static void checkDeepOffset(Limit limit, List<String> adviceList) {
        if (limit.getOffset() != null) {
            try {
                long offset = Long.parseLong(limit.getOffset().toString());
                if (offset > 10000) {
                    adviceList.add("🚨 深度分页警告: OFFSET " + offset + " 过大\n" +
                            "  优化方案:\n" +
                            "  1. 改用 WHERE id > last_id LIMIT n\n" +
                            "  2. 使用延迟关联: SELECT t.* FROM table t JOIN (SELECT id ...) tmp ON t.id=tmp.id");
                }
            } catch (NumberFormatException ignored) {
                // 忽略非数字的 OFFSET（如参数化查询）
                logger.error("Invalid OFFSET value: " + limit.getOffset());
            }
        }
    }

    /**
     * 检查不合理的LIMIT值
     * @param limit
     * @param adviceList
     */
    private static void checkLimitValue(Limit limit, List<String> adviceList) {
        try {
            long limitValue = Long.parseLong(limit.getRowCount().toString());
            if (limitValue > 1000) {
                adviceList.add("⚠️ 大结果集警告: LIMIT " + limitValue + " 可能返回过多数据\n" +
                        "  建议分批查询（如每次 LIMIT 500）");
            } else if (limitValue == 1) {
                adviceList.add("⚠️ 单行限制: LIMIT 1 可能意外截断数据，请确认是否预期");
            }
        } catch (NumberFormatException ignored) {
            // 忽略非数字的 LIMIT（如参数化查询）
            logger.error("Invalid LIMIT value: " + limit.getRowCount());
        }
    }

    /**
     * 检查缺少ORDER BY
     * @param select
     * @param adviceList
     */
    private static void checkMissingOrderBy(PlainSelect select, List<String> adviceList) {
        if (select.getLimit() != null && select.getOrderByElements() == null) {
            adviceList.add("⚠️ 稳定性警告: 使用 LIMIT 但未指定 ORDER BY\n" +
                    "  建议添加如 ORDER BY create_time DESC");
        }
    }

    /**
     * 检查硬编码LIMIT
     * @param limit
     * @param adviceList
     */
    private static void checkHardcodedLimit(Limit limit, List<String> adviceList) {
        if (limit.toString().matches("(?i)LIMIT\\s+\\d+")) {
            adviceList.add("ℹ️ 规范建议: LIMIT 值建议使用参数化查询（如 LIMIT :pageSize）");
        }
    }

}
