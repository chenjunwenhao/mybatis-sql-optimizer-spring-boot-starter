package com.wuya.mybatis.optimizer.helper;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SqlFunctionHelper 是用于审计 SQL 语句中函数使用情况的辅助类。
 * 它主要检查 SQL 语句中是否使用了不在白名单内的函数，特别是那些可能影响列上索引性能的函数。
 * @author chenjunwen
 * @date 2023-09-07
 */
public class SqlFunctionHelper {

    /**
     * 检测 SQL 中所有对列使用的函数（排除白名单）
     *
     * @param sql             待审计的 SQL 语句
     * @param whereFunctionAllowed 函数白名单，包含允许使用的函数名
     * @return 返回一个警告列表，如果 SQL 中使用了非白名单内的函数，则添加相应警告
     * @throws Exception 如果 SQL 解析失败，则抛出异常
     */
    public static List<String> audit(String sql, Set<String> whereFunctionAllowed) throws Exception {
        List<String> warnings = new ArrayList<>();
        Statement stmt = CCJSqlParserUtil.parse(sql);

        stmt.accept(new StatementVisitorAdapter() {
            @Override
            public void visit(Select select) {
                select.getSelectBody().accept(new SelectVisitorAdapter() {
                    @Override
                    public void visit(PlainSelect plainSelect) {
                        analyzeExpression(plainSelect.getWhere(), warnings, whereFunctionAllowed);
                    }
                });
            }
        });
        return warnings;
    }

    /**
     * 分析表达式，识别并记录使用了非白名单函数的警告
     *
     * @param expr            待分析的 SQL 表达式
     * @param warnings        警告列表，如果发现违规使用函数，则添加相应警告
     * @param whereFunctionAllowed 函数白名单，包含允许使用的函数名
     */
    private static void analyzeExpression(Expression expr, List<String> warnings, Set<String> whereFunctionAllowed) {
        if (expr == null) return;

        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Function function) {
                // 1. 跳过白名单函数
                if (whereFunctionAllowed.contains(function.getName().toUpperCase())) {
                    return;
                }
                // 2. 检测是否作用于列
                if (isFunctionOnColumn(function)) {
                    warnings.add(String.format(
                            "警告: 对列 `%s` 使用函数 `%s()`，可能导致索引失效。白名单函数: %s",
                            getColumnName(function),
                            function.getName(),
                            whereFunctionAllowed
                    ));
                }
            }
        });
    }

    /**
     * 判断函数是否应用于列
     *
     * @param function SQL 函数对象
     * @return 如果函数应用于列，则返回 true，否则返回 false
     */
    private static boolean isFunctionOnColumn(Function function) {
        return function.getParameters().getExpressions().stream()
                .anyMatch(e -> e instanceof Column);
    }

    /**
     * 获取函数作用的列名
     *
     * @param function SQL 函数对象
     * @return 返回函数作用的列名，如果找不到列，则返回 "unknown"
     */
    private static String getColumnName(Function function) {
        return function.getParameters().getExpressions().stream()
                .filter(e -> e instanceof Column)
                .map(e -> ((Column) e).getColumnName())
                .findFirst()
                .orElse("unknown");
    }
}
