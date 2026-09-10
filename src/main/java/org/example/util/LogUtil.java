package org.example.util;

/**
 * 简单日志工具。
 * <p>课程强调以 Debug 断点观察为主，此工具仅用于骨架阶段输出关键事件，后续可替换为统一日志方案。</p>
 */
public final class LogUtil {

    private LogUtil() {
    }

    /** 输出一条普通日志。 */
    public static void info(String message) {
        System.out.println(message);
    }
}
