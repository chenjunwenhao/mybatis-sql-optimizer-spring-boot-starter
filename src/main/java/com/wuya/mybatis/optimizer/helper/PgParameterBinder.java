package com.wuya.mybatis.optimizer.helper;

import com.wuya.mybatis.exception.SqlOptimizerException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.*;
import java.lang.reflect.Array;

/**
 * PostgreSQL参数绑定助手类
 * 该类负责将MyBatis的参数对象转换为JDBC PreparedStatement所需的参数格式
 * @author chenjunwen
 * @date 2023-07-07
 */
public class PgParameterBinder {

    /**
     * 绑定参数到PreparedStatement
     * 
     * @param ps PreparedStatement对象，用于执行SQL语句
     * @param boundSql BoundSql对象，包含SQL语句和参数映射信息
     * @param config Configuration对象，包含MyBatis配置信息
     * @throws SQLException 当设置参数出错时抛出
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
     * 解析参数值
     * 
     * @param metaObject MetaObject对象，用于访问参数对象的属性
     * @param property 参数属性名
     * @param config Configuration对象，包含MyBatis配置信息
     * @return 解析后的参数值
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
     * 解析集合元素
     * 
     * @param metaObject MetaObject对象，用于访问参数对象的属性
     * @param property 参数属性名
     * @param config Configuration对象，包含MyBatis配置信息
     * @return 集合元素的值
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
     * 解析嵌套属性
     * 
     * @param metaObject MetaObject对象，用于访问参数对象的属性
     * @param property 参数属性名
     * @param config Configuration对象，包含MyBatis配置信息
     * @return 嵌套属性的值
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

    /**
     * 获取集合元素
     * 
     * @param collection 集合对象
     * @param index 元素索引
     * @return 集合元素的值
     * @throws SqlOptimizerException 当集合类型不支持时抛出
     */
    private static Object getCollectionElement(Object collection, int index) {
        if (collection instanceof List) {
            return ((List<?>) collection).get(index);
        } else if (collection.getClass().isArray()) {
            return Array.get(collection, index);
        } else if (collection instanceof Map) {
            return ((Map<?, ?>) collection).get(index);
        }
        throw new SqlOptimizerException("不支持的集合类型: " + collection.getClass());
    }

    /**
     * 获取对象属性
     * 
     * @param obj 对象
     * @param property 属性名
     * @param config Configuration对象，包含MyBatis配置信息
     * @return 属性值
     */
    private static Object getObjectProperty(Object obj, String property, Configuration config) {
        MetaObject objMeta = config.newMetaObject(obj);
        return objMeta.getValue(property);
    }

    /**
     * 设置PreparedStatement参数
     * 
     * @param ps PreparedStatement对象
     * @param index 参数索引
     * @param value 参数值
     * @param jdbcType JDBC类型
     * @throws SQLException 当设置参数出错时抛出
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
