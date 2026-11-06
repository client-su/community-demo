package com.community.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 临时验证用：第 03 步结束时删除。
 * 后面接入真实业务接口后，这里没有任何存在价值。
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping("/ok")
    public Result<Map<String, Object>> ok() {
        return Result.ok(Map.of("id", 1, "username", "alice"));
    }

    @GetMapping("/ok-empty")
    public Result<Void> okEmpty() {
        return Result.ok();
    }

    @GetMapping("/fail")
    public Result<Void> fail() {
        return Result.fail(ErrorCode.USERNAME_EXISTS);
    }

    @GetMapping("/biz")
    public Result<Void> biz() {
        throw new BizException(ErrorCode.USERNAME_EXISTS);
    }

    @GetMapping("/biz-custom")
    public Result<Void> bizCustom() {
        throw new BizException(ErrorCode.BAD_REQUEST, "用户名长度需 4-32 位");
    }

    @GetMapping("/biz-401")
    public Result<Void> biz401() {
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    @GetMapping("/biz-403")
    public Result<Void> biz403() {
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    @GetMapping("/biz-404")
    public Result<Void> biz404() {
        throw new BizException(ErrorCode.CONTENT_NOT_FOUND);
    }

    @GetMapping("/biz-429")
    public Result<Void> biz429() {
        throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
    }

    /** 模拟数据库唯一键冲突被包装成 DuplicateKeyException */
    @GetMapping("/biz-dup")
    public Result<Void> bizDup() {
        throw new DuplicateKeyException("Duplicate entry 'alice' for key 'user.uk_username'");
    }

    /** 模拟未预期异常：应当返回 500 且不泄漏这条内部信息 */
    @GetMapping("/boom")
    public Result<Void> boom() {
        throw new IllegalStateException("模拟连接池耗尽：url=jdbc:mysql://10.0.0.9/community");
    }

    @PostMapping("/validate")
    public Result<String> validate(@Valid @RequestBody CreateReq req) {
        return Result.ok(req.name());
    }

    public record CreateReq(
            @NotBlank(message = "不能为空")
            @Size(min = 4, max = 32, message = "长度需 4-32 位")
            String name) {
    }
}
