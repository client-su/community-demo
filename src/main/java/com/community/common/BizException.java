package com.community.common;

/**
 * 业务异常：任何一层发现"业务规则不满足"时抛出，由 {@link GlobalExceptionHandler} 统一转成响应。
 *
 * <p>为什么不继承受检异常：业务错误不是调用方必须处理的编程错误，
 * 强制 throws 会让 Service 层每个方法签名都被污染。
 *
 * <p>为什么带 Enum 而不仅是 code：处理器需要 httpStatus，枚举里已经带好了，
 * 不用在异常处理器里再维护一份 code → HTTP 状态码的映射表。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认文案，例如带上具体字段与限制：{@code new BizException(BAD_REQUEST, "用户名长度需 4-32 位")} */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 需要保留底层原因时使用（日志里会打出 cause，但不会返回给客户端） */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // 不做 getStackTrace 优化：业务异常频率低于日志/序列化成本，
    // 保留完整栈更容易定位"这个错误到底是谁抛的"。真正的高频场景再按需关闭（super(msg, null, false, false)）。
}
