package com.community.common;

/**
 * 统一响应体。
 *
 * <p>用 record 而非可变 POJO：响应一旦构造就不该再被改，避免"某个拦截器偷偷改了 data"这类问题。
 * Jackson 能直接序列化 record（Spring Boot 3 自带支持），无需额外配置。
 *
 * @param code    业务码，0 表示成功；非 0 时与 {@link ErrorCode} 对应
 * @param message 给人看的提示，可直接展示给用户
 * @param data    业务数据，失败时为 null
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 用默认文案之外的说明覆盖，例如带上具体的字段名 */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }
}
