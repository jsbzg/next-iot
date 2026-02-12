package com.nextiot.common.util;

import com.alibaba.fastjson2.JSON;

/**
 * JSON 工具类
 * 基于 FastJSON2 封装
 */
public class JsonUtil {

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param obj 要转换的对象
     * @return JSON 字符串
     */
    public static String toJsonString(Object obj) {
        return JSON.toJSONString(obj);
    }

    /**
     * 将 JSON 字符串转换为对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类
     * @param <T>   泛型类型
     * @return 转换后的对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    /**
     * 格式化 JSON 字符串
     *
     * @param json JSON 字符串
     * @return 格式化后的 JSON 字符串
     */
    public static String formatJson(String json) {
        return JSON.toJSONString(JSON.parseObject(json), true);
    }
}
