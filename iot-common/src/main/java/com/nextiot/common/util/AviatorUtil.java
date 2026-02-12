package com.nextiot.common.util;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;

import java.util.Map;

/**
 * Aviator 表达式引擎工具类
 * 用于动态解析报文和规则检测
 */
public class AviatorUtil {

    static {
        // 初始化 Aviator 引擎
        AviatorEvaluator.setCachedExpression(true);
        AviatorEvaluator.setOptimize(AviatorEvaluator.EVAL);
    }

    /**
     * 计算布尔表达式（用于规则匹配）
     * @param expression 表达式，如 "value > 80"
     * @param env 环境变量
     * @return 表达式计算结果
     */
    public static Boolean evalBoolean(String expression, Map<String, Object> env) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression compiled = AviatorEvaluator.compile(expression);
            Object result = compiled.execute(env);
            return result != null && Boolean.TRUE.equals(result) || matchesNumericResult(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算表达式并返回对象（用于数据提取）
     * @param expression 表达式
     * @param env 环境变量
     * @return 表达式计算结果
     */
    public static Object eval(String expression, Map<String, Object> env) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression compiled = AviatorEvaluator.compile(expression);
            return compiled.execute(env);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 打印编译缓存统计信息（用于性能监控）
     */
    public static void printCacheStats() {
        // 可扩展用于监控表达式编译缓存命中情况
    }

    /**
     * 判断数值结果是否为真
     */
    private static boolean matchesNumericResult(Object result) {
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        return false;
    }
}