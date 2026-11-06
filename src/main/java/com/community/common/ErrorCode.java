package com.community.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>编码约定：5 位 = HTTP 状态码(3 位) + 同族序号(2 位)。
 * 例如 40901 = 409 Conflict 家族的第 01 个业务错误「用户名已存在」。
 * 好处：看日志或前端报错就能立刻知道该返回什么 HTTP 状态码。
 */
public enum ErrorCode {

    OK(0, "成功", HttpStatus.OK),

    BAD_REQUEST(40000, "参数校验失败", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40300, "没有操作权限", HttpStatus.FORBIDDEN),

    NOT_FOUND(40400, "请求的资源不存在", HttpStatus.NOT_FOUND),
    CONTENT_NOT_FOUND(40401, "内容不存在或已删除", HttpStatus.NOT_FOUND),

    CONFLICT(40900, "资源冲突", HttpStatus.CONFLICT),
    USERNAME_EXISTS(40901, "用户名已被占用", HttpStatus.CONFLICT),
    ALREADY_LIKED(40902, "已经点过赞了", HttpStatus.CONFLICT),

    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(50000, "服务器开小差了，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
