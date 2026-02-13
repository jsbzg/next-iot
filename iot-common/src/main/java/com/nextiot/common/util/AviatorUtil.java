package com.nextiot.common.util;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aviator 表达式引擎工具类
 * 用于动态解析报文和规则检测
 */
public class AviatorUtil {

    private static final Logger log = LoggerFactory.getLogger(AviatorUtil.class);

    // 编译缓存
    private static final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

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
            Expression compiled = compileExpression(expression);
            Object result = compiled.execute(env);
            return result != null && Boolean.TRUE.equals(result) || matchesNumericResult(result);
        } catch (Exception e) {
            log.error("[AVIATOR-ERROR] Expression execution failed: {}, env: {}, error: ", expression, env, e);
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
            Expression compiled = compileExpression(expression);
            return compiled.execute(env);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编译表达式（带缓存）
     */
    private static Expression compileExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, expr ->
            AviatorEvaluator.compile(expr)
        );
    }

    /**
     * 打印缓存统计信息（用于性能监控）
     */
    public static void printCacheStats() {
        System.out.println("Aviator 表达式缓存数量: " + expressionCache.size());
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