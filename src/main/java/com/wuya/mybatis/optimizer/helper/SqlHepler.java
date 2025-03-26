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
 * @author wuya
 * @date 2023-08-09 16:08
 */
public class SqlHepler {
    public static Object getParameterValue(Object parameterObject, String propertyName) {
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

    public static String capitalize(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

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
}
