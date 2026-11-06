package com.community.common;

import org.springframework.web.bind.annotation.GetMapping;
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
}
