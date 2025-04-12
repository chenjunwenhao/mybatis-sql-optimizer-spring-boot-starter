package com.wuya.mybatis.optimizer.helper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * sql 语句帮助类
 * @author chenjunwen
 * @date 2023-08-09 16:08
 */
public class SqlHepler {
    
    /**
     * 从参数对象中获取指定名称的参数值
     * 
     * @param parameterObject 参数对象，可以是普通对象或 Map
     * @param propertyName 参数名称
     * @return 参数值
     */
    public static Object getParameterValue(Object parameterObject, String propertyName) {

        if (parameterObject == null || propertyName == null || propertyName.isEmpty()) {
            return null;
        }

        // 1. 处理基本类型和String
        if (isBasicTypeOrWrapper(parameterObject)) {
            return parameterObject;
        }

        // 2. 处理 Map
        if (parameterObject instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameterObject;
            return paramMap.get(propertyName);
        } else {
            try {
                // 如果是普通对象，通过反射获取
                return parameterObject.getClass().getMethod("get" + capitalize(propertyName)).invoke(parameterObject);
            } catch (Exception e) {
                // 处理反射异常
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     *  判断对象是否为基本类型、包装类型、String、Number、Boolean、Character、Enum之一
     * @param obj
     * @return
     */
    private static boolean isBasicTypeOrWrapper(Object obj) {
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive()
                || obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Character
                || obj instanceof Enum;
    }
    /**
     * 将字符串的首字母大写
     * 
     * @param str 输入字符串
     * @return 首字母大写后的字符串
     */
    public static String capitalize(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 执行 SQL 语句并返回结果集
     * 
     * @param connection 数据库连接对象
     * @param boundSql 包含 SQL 语句和参数映射的对象
     * @param explainPre SQL 语句前缀，用于添加 EXPLAIN 等关键字
     * @return SQL 执行结果集
     * @throws Exception 数据库操作可能抛出的异常
     */
    public static ResultSet getResult(Connection connection, BoundSql boundSql, String explainPre)  throws Exception {
        // 获取原始 SQL
        String originalSql = boundSql.getSql();

        // 获取动态参数对象
        Object parameterObject = boundSql.getParameterObject();
        // 获取参数映射
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        // 在 SQL 前加上 EXPLAIN
        String explainSql = explainPre + originalSql;

        // 创建 PreparedStatement 执行查询
        PreparedStatement preparedStatement = connection.prepareStatement(explainSql);
        // 如果参数对象不为 null，设置参数值
        if (parameterObject != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                String propertyName = parameterMapping.getProperty(); // 获取参数名称
                Object value = SqlHepler.getParameterValue(parameterObject, propertyName); // 获取参数值

                // 将参数值设置到 PreparedStatement 中
                preparedStatement.setObject(i + 1, value);
            }
        }

        ResultSet resultSet = preparedStatement.executeQuery();
        return resultSet;
    }

    /**
     * 排除已经添加 EXPLAIN 的语句
     * @param sql
     * @return
     */
    public static boolean shouldExplain(String sql) {
        // 示例：不分析自带EXPLAIN的语句
        return !sql.contains("EXPLAIN") || !sql.contains("explain");
    }

    /**
     *  去除注释和压缩 SQL
     *  CCJSqlParserUtil.parse(sql) 解决 CCJSqlParserException sql解析问题
     * @param rawSql
     * @return
     */
    public static String prepareSql(String rawSql) {
        return rawSql.replaceAll("/\\*.*?\\*/", "")  // 移除注释
                .replaceAll("--.*?\n", " ")    // 移除行注释
                .replaceAll("(?m)^\\s+", "")   // 移除行首空白
                .replaceAll("\\s+", " ")       // 压缩空白
                .trim();
    }
}
