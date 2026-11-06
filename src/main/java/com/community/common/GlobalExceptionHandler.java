package com.community.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：所有异常在这里收口成 {@link Result}。
 *
 * <p>日志策略：
 * <ul>
 *   <li>业务异常（用户输入错、重复点赞）→ WARN，不打堆栈，避免淹没真正的故障</li>
 *   <li>未知异常 → ERROR + 完整堆栈，但响应体只给固定文案，不泄漏内部细节</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：错误码里已经带好 HTTP 状态码，直接用 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("biz-error traceId={} code={} message={}",
                TraceId.current(), errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, ex.getMessage()));
    }

    /** @Valid 校验失败：把每个字段的错误拼成一句可读文案 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + defaultMessage(fe))
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /** 查询参数/路径参数上的约束校验（如 @RequestParam @Min） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /** 请求体不是合法 JSON / 类型不匹配 / 缺少必填参数：都是客户端问题 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        log.warn("bad-request traceId={} type={} message={}",
                TraceId.current(), ex.getClass().getSimpleName(), ex.getMessage());
        return badRequest(ErrorCode.BAD_REQUEST.getMessage());
    }

    /** 唯一键冲突：由数据库保护兜底，转成 409 而不是 500 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("duplicate-key traceId={} message={}", TraceId.current(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(Result.fail(ErrorCode.CONFLICT));
    }

    /** 路径不存在：Spring 6.1+ 对未匹配路由抛 NoResourceFoundException */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException ex) {
        log.warn("not-found traceId={} path={}", TraceId.current(), ex.getResourcePath());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED, "不支持 " + ex.getMethod() + " 方法"));
    }

    /** 兜底：任何未预期的异常都不把细节返回给客户端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled-error traceId={}", TraceId.current(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }

    private static ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    private static String defaultMessage(FieldError fe) {
        return fe.getDefaultMessage() == null ? "不合法" : fe.getDefaultMessage();
    }
}
