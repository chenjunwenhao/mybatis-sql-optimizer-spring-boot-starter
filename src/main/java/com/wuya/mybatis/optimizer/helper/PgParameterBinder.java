package com.wuya.mybatis.optimizer.helper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.*;
import java.lang.reflect.Array;

public class PgParameterBinder {

    /**
     * 安全绑定所有类型参数到PreparedStatement
     * @param ps PreparedStatement对象
     * @param boundSql MyBatis的BoundSql对象
     * @param config MyBatis配置
     */
    public static void bindParameters(PreparedStatement ps,
                                      BoundSql boundSql,
                                      Configuration config) throws SQLException {
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();

        MetaObject metaObject = parameterObject == null ?
                null : config.newMetaObject(parameterObject);

        for (int i = 0; i < mappings.size(); i++) {
            ParameterMapping mapping = mappings.get(i);
            String property = mapping.getProperty();
            Object value = resolveParameterValue(metaObject, property, config);
            setParameter(ps, i + 1, value, mapping.getJdbcType());
        }
    }

    /**
     * 递归解析参数值（支持嵌套对象和集合）
     */
    private static Object resolveParameterValue(MetaObject metaObject,
                                                String property,
                                                Configuration config) {
        // 处理集合索引（如list[0], array[1]）
        if (property.contains("[") && property.contains("]")) {
            return resolveCollectionElement(metaObject, property, config);
        }

        // 处理嵌套属性（如user.address.city）
        if (property.contains(".")) {
            return resolveNestedProperty(metaObject, property, config);
        }

        // 基本类型直接返回
        return metaObject == null ? null : metaObject.getValue(property);
    }

    /**
     * 处理集合元素访问
     */
    private static Object resolveCollectionElement(MetaObject metaObject,
                                                   String property,
                                                   Configuration config) {
        String[] parts = property.split("\\[|\\]|\\.");
        Object current = metaObject.getValue(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;

            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            // 处理数组索引
            if (part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = getCollectionElement(current, index);
            }
            // 处理Map键或对象属性
            else {
                current = getObjectProperty(current, part, config);
            }
        }

        return current;
    }

    /**
     * 处理嵌套属性访问
     */
    private static Object resolveNestedProperty(MetaObject metaObject,
                                                String property,
                                                Configuration config) {
        String[] parts = property.split("\\.");
        Object current = metaObject.getValue(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            current = getObjectProperty(current, parts[i], config);
        }

        return current;
    }

    private static Object getCollectionElement(Object collection, int index) {
        if (collection instanceof List) {
            return ((List<?>) collection).get(index);
        } else if (collection.getClass().isArray()) {
            return Array.get(collection, index);
        } else if (collection instanceof Map) {
            return ((Map<?, ?>) collection).get(index);
        }
        throw new IllegalArgumentException("不支持的集合类型: " + collection.getClass());
    }

    private static Object getObjectProperty(Object obj, String property, Configuration config) {
        MetaObject objMeta = config.newMetaObject(obj);
        return objMeta.getValue(property);
    }

    /**
     * 安全设置参数值（包含Null处理）
     */
    private static void setParameter(PreparedStatement ps,
                                     int index,
                                     Object value,
                                     JdbcType jdbcType) throws SQLException {
        if (value == null) {
            if (jdbcType == null) {
                ps.setNull(index, Types.OTHER);
            } else {
                ps.setNull(index, jdbcType.TYPE_CODE);
            }
        } else {
            // 处理PostgreSQL特殊类型
            if (value instanceof UUID) {
                ps.setObject(index, value, Types.OTHER);
            } else if (value instanceof Enum) {
                ps.setString(index, ((Enum<?>) value).name());
            } else {
                ps.setObject(index, value);
            }
        }
    }
}