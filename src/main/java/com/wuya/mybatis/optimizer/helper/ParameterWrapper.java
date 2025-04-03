package com.wuya.mybatis.optimizer.helper;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 参数包装器，用于将各种类型的参数转换为统一的Map结构
 * @author chenjunwen
 * @date 2023-03-07 09:08
 */
public class ParameterWrapper {
    // 定义特殊键名常量
    private static final String COLLECTION_KEY = "collection";
    private static final String LIST_KEY = "list";
    private static final String ITEM_PREFIX = "__frch_item_";
    private static final String PARAM_KEY = "param";

    /**
     * 主要的参数包装方法，根据参数类型选择不同的处理方式
     * @param parameter 输入参数，可以是任意类型
     * @return 包装后的参数Map
     */
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

    /**
     * 处理集合类型参数，将集合中的每个元素都加入到参数Map中
     * @param paramMap 参数Map
     * @param collection 集合对象
     */
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

    /**
     * 处理JavaBean类型参数，将JavaBean的每个属性都加入到参数Map中
     * @param paramMap 参数Map
     * @param bean JavaBean对象
     */
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

    /**
     * 展开对象的属性，将嵌套的对象属性也加入到参数Map中
     * @param paramMap 参数Map
     * @param prefix 嵌套属性的前缀
     * @param obj 对象
     */
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

    /**
     * 判断对象是否为复杂类型（既不是基本类型或其包装类，也不是String、Collection或Map）
     * @param obj 对象
     * @return 是否为复杂类型
     */
    private static boolean isComplexType(Object obj) {
        return obj != null &&
                !ClassUtils.isPrimitiveOrWrapper(obj.getClass()) &&
                !(obj instanceof String) &&
                !(obj instanceof Collection) &&
                !(obj instanceof Map);
    }

    /**
     * 将数组或集合类型的参数转换为Collection
     * @param parameter 参数，可以是数组或集合类型
     * @return 转换后的Collection对象
     */
    private static Collection<?> convertToCollection(Object parameter) {
        if (parameter.getClass().isArray()) {
            return Arrays.asList((Object[]) parameter);
        }
        return (Collection<?>) parameter;
    }
}
