package com.wuya.mybatis.optimizer.helper;

import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;

import java.util.*;

public class SqlFunctionHelper {

    /**
     * 检测 SQL 中所有对列使用的函数（排除白名单）
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

    private static void analyzeExpression(Expression expr, List<String> warnings, Set<String> whereFunctionAllowed) {
        if (expr == null) return;

        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Function function) {
                // 1. 跳过白名单函数
                if (whereFunctionAllowed
                        .stream()
                        .map(String::toUpperCase)
                        .anyMatch(func -> func.contains(function.getName().toUpperCase()))) {
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

    private static boolean isFunctionOnColumn(Function function) {
        return function.getParameters().getExpressions().stream()
                .anyMatch(e -> e instanceof Column);
    }

    private static String getColumnName(Function function) {
        return function.getParameters().getExpressions().stream()
                .filter(e -> e instanceof Column)
                .map(e -> ((Column) e).getColumnName())
                .findFirst()
                .orElse("unknown");
    }
}