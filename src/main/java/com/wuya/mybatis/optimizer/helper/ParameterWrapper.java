package com.wuya.mybatis.optimizer.helper;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParameterWrapper {
    private static final String COLLECTION_KEY = "collection";
    private static final String LIST_KEY = "list";
    private static final String ITEM_PREFIX = "__frch_item_";
    private static final String PARAM_KEY = "param";

    public static Map<String, Object> wrap(Object parameter) {
        Map<String, Object> paramMap = new LinkedHashMap<>();

        if (parameter == null) {
            return paramMap;
        }

        // 处理集合/数组参数
        if (parameter instanceof Collection || parameter.getClass().isArray()) {
            handleCollection(paramMap, convertToCollection(parameter));
            return paramMap;
        }

        // 处理Map类型参数
        if (parameter instanceof Map) {
            paramMap.putAll((Map<? extends String, ?>) parameter);
            return paramMap;
        }

        // 处理普通JavaBean
        handleJavaBean(paramMap, parameter);
        return paramMap;
    }

    private static void handleCollection(Map<String, Object> paramMap, Collection<?> collection) {
        paramMap.put(COLLECTION_KEY, collection);
        paramMap.put(LIST_KEY, collection);

        int index = 0;
        for (Object item : collection) {
            String itemKey = ITEM_PREFIX + index++;
            paramMap.put(itemKey, item);

            if (isComplexType(item)) {
                expandObjectProperties(paramMap, itemKey, item);
            }
        }
    }

    private static void handleJavaBean(Map<String, Object> paramMap, Object bean) {
        paramMap.put(PARAM_KEY, bean);
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);

        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            if (pd.getReadMethod() == null || "class".equals(pd.getName())) {
                continue;
            }

            Object value = wrapper.getPropertyValue(pd.getName());
            paramMap.put(pd.getName(), value);

            if (isComplexType(value)) {
                expandObjectProperties(paramMap, pd.getName(), value);
            }
        }
    }

    private static void expandObjectProperties(Map<String, Object> paramMap,
                                               String prefix,
                                               Object obj) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(obj);
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            if (pd.getReadMethod() == null || "class".equals(pd.getName())) {
                continue;
            }

            String nestedKey = prefix + "." + pd.getName();
            Object value = wrapper.getPropertyValue(pd.getName());
            paramMap.put(nestedKey, value);

            if (isComplexType(value)) {
                expandObjectProperties(paramMap, nestedKey, value);
            }
        }
    }

    private static boolean isComplexType(Object obj) {
        return obj != null &&
                !ClassUtils.isPrimitiveOrWrapper(obj.getClass()) &&
                !(obj instanceof String) &&
                !(obj instanceof Collection) &&
                !(obj instanceof Map);
    }

    private static Collection<?> convertToCollection(Object parameter) {
        if (parameter.getClass().isArray()) {
            return Arrays.asList((Object[]) parameter);
        }
        return (Collection<?>) parameter;
    }
}
