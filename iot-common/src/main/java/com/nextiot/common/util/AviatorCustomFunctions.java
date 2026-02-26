package com.nextiot.common.util;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aviator 自定义函数注册类
 * 提供字符串处理、类型转换等工具函数供 AviatorScript 使用
 */
public class AviatorCustomFunctions {

    /**
     * 注册所有自定义函数
     * 必须在 AviatorEvaluator 编译表达式之前调用
     */
    public static void registerAllFunctions() {
        // 字符串处理函数
        AviatorEvaluator.addFunction(new SplitFunction());
        AviatorEvaluator.addFunction(new SubstringFunction());
        AviatorEvaluator.addFunction(new LengthFunction());
        AviatorEvaluator.addFunction(new IndexOfFunction());
        AviatorEvaluator.addFunction(new ReplaceFunction());
        AviatorEvaluator.addFunction(new TrimFunction());
        AviatorEvaluator.addFunction(new ToUpperCaseFunction());
        AviatorEvaluator.addFunction(new ToLowerCaseFunction());

        // 类型转换函数
        AviatorEvaluator.addFunction(new HexToIntFunction());
        AviatorEvaluator.addFunction(new ByteAtFunction());
        AviatorEvaluator.addFunction(new LongValueFunction());
        AviatorEvaluator.addFunction(new DoubleValueFunction());
    }

    // ==================== 字符串处理函数 ====================

    /**
     * split(str, delimiter) - 字符串分割
     * 示例：split("a,b,c", ",") -> ["a", "b", "c"]
     */
    private static class SplitFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "split";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            String str = FunctionUtils.getStringValue(arg1, env);
            String delimiter = FunctionUtils.getStringValue(arg2, env);

            if (str == null || delimiter == null) {
                return AviatorNil.NIL;
            }

            String[] parts = str.split(java.util.regex.Pattern.quote(delimiter));
            List<Object> list = new ArrayList<>();
            for (String part : parts) {
                list.add(part);
            }

            // 使用 AviatorRuntimeJavaType 包装 List
            return AviatorRuntimeJavaType.valueOf(list);
        }
    }

    /**
     * substring(str, start, end) - 子串提取
     * 示例：substring("hello", 1, 4) -> "ell"
     * 示例：substring("hello", 0, -1) -> "ell"
     */
    private static class SubstringFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "substring";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2, AviatorObject arg3) {
            String str = FunctionUtils.getStringValue(arg1, env);
            int start = FunctionUtils.getNumberValue(arg2, env).intValue();
            int end = FunctionUtils.getNumberValue(arg3, env).intValue();

            if (str == null) {
                return AviatorNil.NIL;
            }

            int length = str.length();
            // 处理负数索引（Python 风格）
            if (end < 0) {
                end = length + end;
            }
            if (start < 0) {
                start = length + start;
            }

            // 边界检查
            start = Math.max(0, Math.min(start, length));
            end = Math.max(start, Math.min(end, length));

            return new AviatorString(str.substring(start, end));
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            String str = FunctionUtils.getStringValue(arg1, env);
            int start = FunctionUtils.getNumberValue(arg2, env).intValue();

            if (str == null) {
                return AviatorNil.NIL;
            }

            return new AviatorString(str.substring(start));
        }
    }

    /**
     * length(str) - 获取字符串长度
     * 示例：length("hello") -> 5
     */
    private static class LengthFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "length";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);

            if (str == null) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }

            return AviatorRuntimeJavaType.valueOf((long) str.length());
        }
    }

    /**
     * index_of(str, sub) - 查找子串索引
     * 示例：index_of("hello", "ell") -> 1
     * 示例：index_of("hello", "xyz") -> -1
     */
    private static class IndexOfFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "index_of";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            String str = FunctionUtils.getStringValue(arg1, env);
            String sub = FunctionUtils.getStringValue(arg2, env);

            if (str == null || sub == null) {
                return AviatorRuntimeJavaType.valueOf(-1L);
            }

            return AviatorRuntimeJavaType.valueOf((long) str.indexOf(sub));
        }
    }

    /**
     * replace(str, target, replacement) - 字符串替换
     * 示例：replace("hello world", "world", "Aviator") -> "hello Aviator"
     */
    private static class ReplaceFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "replace";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2, AviatorObject arg3) {
            String str = FunctionUtils.getStringValue(arg1, env);
            String target = FunctionUtils.getStringValue(arg2, env);
            String replacement = FunctionUtils.getStringValue(arg3, env);

            if (str == null || target == null) {
                return AviatorNil.NIL;
            }

            return new AviatorString(str.replace(target, replacement != null ? replacement : ""));
        }
    }

    /**
     * trim(str) - 去除首尾空白
     * 示例：trim("  hello  ") -> "hello"
     */
    private static class TrimFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "trim";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);

            if (str == null) {
                return AviatorNil.NIL;
            }

            return new AviatorString(str.trim());
        }
    }

    /**
     * to_upper_case(str) - 转大写
     */
    private static class ToUpperCaseFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "to_upper_case";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);
            return str == null ? AviatorNil.NIL : new AviatorString(str.toUpperCase());
        }
    }

    /**
     * to_lower_case(str) - 转小写
     */
    private static class ToLowerCaseFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "to_lower_case";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);
            return str == null ? AviatorNil.NIL : new AviatorString(str.toLowerCase());
        }
    }

    // ==================== 类型转换函数 ====================

    /**
     * hex_to_int(hex) - 十六进制转整数
     * 示例：hex_to_int("4A") -> 74
     * 示例：hex_to_int("0x4A") -> 74
     */
    private static class HexToIntFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "hex_to_int";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String hex = FunctionUtils.getStringValue(arg1, env);

            if (hex == null) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }

            try {
                // 支持 0x 前缀
                if (hex.startsWith("0x") || hex.startsWith("0X")) {
                    hex = hex.substring(2);
                }
                return AviatorRuntimeJavaType.valueOf(Long.parseLong(hex, 16));
            } catch (NumberFormatException e) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }
        }
    }

    /**
     * byte_at(str, index) - 获取指定位置的字节值
     * 示例：byte_at("AB", 0) -> 65 ('A')
     */
    private static class ByteAtFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "byte_at";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
            String str = FunctionUtils.getStringValue(arg1, env);
            int index = FunctionUtils.getNumberValue(arg2, env).intValue();

            if (str == null || index < 0 || index >= str.length()) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }

            return AviatorRuntimeJavaType.valueOf((long) str.charAt(index));
        }
    }

    /**
     * long_value(str) - 字符串转长整数
     * 示例：long_value("123") -> 123L
     */
    private static class LongValueFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "long_value";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);

            if (str == null) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }

            try {
                return AviatorRuntimeJavaType.valueOf(Long.parseLong(str));
            } catch (NumberFormatException e) {
                return AviatorRuntimeJavaType.valueOf(0L);
            }
        }
    }

    /**
     * double_value(str) - 字符串转双精度浮点数
     * 示例：double_value("123.45") -> 123.45
     */
    private static class DoubleValueFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "double_value";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String str = FunctionUtils.getStringValue(arg1, env);

            if (str == null) {
                return AviatorRuntimeJavaType.valueOf(0.0);
            }

            try {
                return AviatorRuntimeJavaType.valueOf(Double.parseDouble(str));
            } catch (NumberFormatException e) {
                return AviatorRuntimeJavaType.valueOf(0.0);
            }
        }
    }
}
