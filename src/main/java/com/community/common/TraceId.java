package com.community.common;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求链路 id 工具：借助 SLF4J MDC（底层 ThreadLocal）让同一请求的所有日志自动带上 traceId。
 *
 * <p>日志格式里加 {@code %X{traceId}} 即可输出，无需在每个 log 调用里手写。
 */
public final class TraceId {

    /** 写进 MDC 的 key，日志配置里引用同名占位符 */
    public static final String MDC_KEY = "traceId";

    /** 与前端/网关约定的响应头 */
    public static final String HEADER = "X-Trace-Id";

    /** 只允许 8-64 位的字母数字与 . _ -，防止日志注入（换行、控制字符） */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private TraceId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 外部传入的 traceId 只有格式合法才接受，否则忽略并重新生成 */
    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    public static void put(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    /** 当前线程的 traceId，未设置时返回 null */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
